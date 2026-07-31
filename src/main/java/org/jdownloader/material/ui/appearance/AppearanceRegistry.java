package org.jdownloader.material.ui.appearance;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import javafx.beans.value.ChangeListener;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Control;
import javafx.scene.control.MenuItem;
import javafx.scene.control.RadioButton;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.TextInputControl;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import org.jdownloader.material.appearance.AppearanceState;
import org.jdownloader.material.appearance.AppearanceTargetId;

/**
 * Universal scene access to per-element appearance editing. Explicit ids are
 * preferred; unregistered nodes receive deterministic structural ids so the
 * context action is never missing while root wiring is progressively added.
 */
public final class AppearanceRegistry implements AutoCloseable {

    public static final String TARGET_PROPERTY = "jdm.appearance.targetId";
    public static final String HOST_CONTEXT_MENU_PROPERTY = "jdm.appearance.hostContextMenu";
    private static final String SCENE_REGISTRY_PROPERTY = "jdm.appearance.registry";

    private final Scene scene;
    private final AppearanceService service;
    private final Function<String, String> text;
    private final Map<Node, Registration> registrations = new IdentityHashMap<>();
    private final List<Scene> attachedScenes = new ArrayList<>();
    private final EventHandler<ContextMenuEvent> contextHandler = this::handleContextMenu;
    private final EventHandler<MouseEvent> mouseHandler = this::handleMouse;
    private final EventHandler<KeyEvent> keyHandler = this::handleKey;
    private AppearanceEditorPopover editor;
    private boolean closed;

    AppearanceRegistry(Scene scene, AppearanceService service, Function<String, String> text) {
        this.scene = Objects.requireNonNull(scene, "scene");
        this.service = Objects.requireNonNull(service, "service");
        this.text = Objects.requireNonNull(text, "text");
        attachScene(scene);
        registerTarget(scene.getRoot(), AppearanceTargetId.of("app.root"));
    }

    public void attachScene(Scene additionalScene) {
        Objects.requireNonNull(additionalScene, "additionalScene");
        if (attachedScenes.contains(additionalScene)) return;
        attachedScenes.add(additionalScene);
        additionalScene.getProperties().put(SCENE_REGISTRY_PROPERTY, this);
        additionalScene.addEventFilter(ContextMenuEvent.CONTEXT_MENU_REQUESTED, contextHandler);
        additionalScene.addEventFilter(MouseEvent.MOUSE_PRESSED, mouseHandler);
        additionalScene.addEventFilter(KeyEvent.KEY_PRESSED, keyHandler);
        AppearanceCss.applyGlobal(additionalScene.getRoot(), service.profile());
    }

    public void detachScene(Scene additionalScene) {
        if (additionalScene == null || !attachedScenes.remove(additionalScene)) return;
        additionalScene.getProperties().remove(SCENE_REGISTRY_PROPERTY, this);
        additionalScene.removeEventFilter(ContextMenuEvent.CONTEXT_MENU_REQUESTED, contextHandler);
        additionalScene.removeEventFilter(MouseEvent.MOUSE_PRESSED, mouseHandler);
        additionalScene.removeEventFilter(KeyEvent.KEY_PRESSED, keyHandler);
    }

    public AppearanceTargetId registerTarget(Node node, AppearanceTargetId targetId) {
        Objects.requireNonNull(node, "node");
        Objects.requireNonNull(targetId, "targetId");
        Registration existing = registrations.remove(node);
        if (existing != null) existing.dispose();
        node.getProperties().put(TARGET_PROPERTY, targetId);
        Registration registration = new Registration(node, targetId);
        registrations.put(node, registration);
        registration.install();
        service.apply(node, targetId, AppearanceState.NORMAL);
        return targetId;
    }

    public void registerSubtree(Parent root, String prefix) {
        String stablePrefix = AppearanceTargetId.segment(prefix);
        registerTarget(root, AppearanceTargetId.of(stablePrefix));
        registerChildren(root, stablePrefix);
    }

    public AppearanceTargetId targetId(Node node) {
        Registration registration = registrations.get(node);
        if (registration != null) return registration.id;
        Object explicit = node.getProperties().get(TARGET_PROPERTY);
        if (explicit instanceof AppearanceTargetId id) return registerTarget(node, id);
        if (explicit instanceof String value && !value.isBlank()) return registerTarget(node, AppearanceTargetId.of(value));
        return registerTarget(node, deriveTargetId(node));
    }

    public void openEditor(Node anchor) {
        if (closed || anchor == null) return;
        if (editor == null) editor = new AppearanceEditorPopover(service, this, text);
        editor.show(anchor, targetId(anchor));
    }

    /**
     * Opens the editor installed for an anchor's scene. Explicit tab/group
     * context menus use this instead of synthesizing another context-menu event.
     *
     * @return {@code true} when an active scene registry handled the request
     */
    public static boolean openEditorFor(Node anchor) {
        if (anchor == null || anchor.getScene() == null) return false;
        Object candidate = anchor.getScene().getProperties().get(SCENE_REGISTRY_PROPERTY);
        if (!(candidate instanceof AppearanceRegistry registry) || registry.closed) return false;
        registry.openEditor(anchor);
        return true;
    }

    /** Attaches a utility/dialog Scene to the live registry installed for its owner node. */
    public static boolean attachSceneFor(Node owner, Scene auxiliary) {
        Objects.requireNonNull(auxiliary, "auxiliary");
        AppearanceRegistry registry = registryFor(owner, null);
        if (registry == null) return false;
        registry.attachScene(auxiliary);
        return true;
    }

    /** Detaches a previously attached utility/dialog Scene and removes its registry association. */
    public static boolean detachSceneFor(Node owner, Scene auxiliary) {
        if (auxiliary == null) return false;
        AppearanceRegistry registry = registryFor(owner, auxiliary);
        if (registry == null
                || auxiliary.getProperties().get(SCENE_REGISTRY_PROPERTY) != registry) return false;
        registry.detachScene(auxiliary);
        return true;
    }

    /**
     * Associates an owned context menu with any Node, including non-Control
     * tab/group headers. The scene filter augments this menu and deliberately
     * leaves its ContextMenuEvent unconsumed so the owner's handler can show it.
     */
    public static ContextMenu installContextMenu(Node owner, ContextMenu menu) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(menu, "menu");
        owner.getProperties().put(HOST_CONTEXT_MENU_PROPERTY, menu);
        if (owner.getScene() != null) {
            Object candidate = owner.getScene().getProperties().get(SCENE_REGISTRY_PROPERTY);
            if (candidate instanceof AppearanceRegistry registry && !registry.closed) {
                registry.ensureEditItem(menu, owner);
            }
        }
        return menu;
    }

    public static void uninstallContextMenu(Node owner, ContextMenu menu) {
        if (owner != null) owner.getProperties().remove(HOST_CONTEXT_MENU_PROPERTY, menu);
    }

    public void refresh(AppearanceTargetId id) {
        registrations.values().stream().filter(registration -> registration.id.equals(id))
                .forEach(Registration::refresh);
    }

    public void refreshAll() {
        attachedScenes.forEach(attached -> AppearanceCss.applyGlobal(attached.getRoot(), service.profile()));
        registrations.values().forEach(Registration::refresh);
    }

    private void handleContextMenu(ContextMenuEvent event) {
        if (closed || event.isConsumed()) return;
        Node target = node(event.getTarget());
        if (target == null) return;
        HostedContextMenu hosted = hostedContextMenu(target);
        if (hosted != null) {
            ensureEditItem(hosted.menu(), hosted.owner());
            // The owner's existing handler remains responsible for placement/showing.
            return;
        }
        Control control = controlAncestor(target);
        if (control instanceof TextInputControl input && input.getContextMenu() == null) {
            input.setContextMenu(textInputContextMenu(input));
            return;
        }
        if (control != null && control.getContextMenu() != null) {
            ensureEditItem(control.getContextMenu(), target);
            return;
        }
        ContextMenu menu = new ContextMenu(editItem(target));
        menu.getStyleClass().add("appearance-context-menu");
        menu.show(target, event.getScreenX(), event.getScreenY());
        event.consume();
    }

    private void handleMouse(MouseEvent event) {
        if (closed || event.getButton() != MouseButton.SECONDARY || !event.isShiftDown()) return;
        Node target = node(event.getTarget());
        if (target == null) return;
        HostedContextMenu hosted = hostedContextMenu(target);
        if (hosted != null) target = hosted.owner();
        openEditor(target);
        event.consume();
    }

    private void handleKey(KeyEvent event) {
        if (closed || event.isConsumed()) return;
        boolean directShortcut = event.getCode() == KeyCode.A && event.isControlDown() && event.isShiftDown();
        if (!directShortcut) return;
        Scene sourceScene = event.getSource() instanceof Scene value ? value : scene;
        Node target = sourceScene.getFocusOwner() == null ? sourceScene.getRoot() : sourceScene.getFocusOwner();
        openEditor(target);
        event.consume();
    }

    private void ensureEditItem(ContextMenu menu, Node target) {
        boolean exists = menu.getItems().stream().anyMatch(item -> Boolean.TRUE.equals(item.getProperties().get(TARGET_PROPERTY)));
        if (exists) return;
        if (!menu.getItems().isEmpty()) menu.getItems().add(new SeparatorMenuItem());
        menu.getItems().add(editItem(target));
    }

    private MenuItem editItem(Node target) {
        MenuItem item = new MenuItem(label("appearance.action.edit", "Edit appearance…"));
        item.getProperties().put(TARGET_PROPERTY, Boolean.TRUE);
        item.setOnAction(event -> openEditor(target));
        return item;
    }

    private ContextMenu textInputContextMenu(TextInputControl input) {
        MenuItem undo = action("appearance.context.undo", "Undo", input::undo);
        MenuItem redo = action("appearance.context.redo", "Redo", input::redo);
        MenuItem cut = action("appearance.context.cut", "Cut", input::cut);
        MenuItem copy = action("appearance.context.copy", "Copy", input::copy);
        MenuItem paste = action("appearance.context.paste", "Paste", input::paste);
        MenuItem delete = action("appearance.context.delete", "Delete", () -> input.replaceSelection(""));
        MenuItem selectAll = action("appearance.context.select_all", "Select all", input::selectAll);
        ContextMenu menu = new ContextMenu(undo, redo, new SeparatorMenuItem(), cut, copy, paste, delete,
                new SeparatorMenuItem(), selectAll, new SeparatorMenuItem(), editItem(input));
        menu.setOnShowing(event -> {
            boolean selected = input.getSelection().getLength() > 0;
            undo.setDisable(!input.isUndoable());
            redo.setDisable(!input.isRedoable());
            cut.setDisable(!input.isEditable() || !selected);
            copy.setDisable(!selected);
            paste.setDisable(!input.isEditable() || !Clipboard.getSystemClipboard().hasString());
            delete.setDisable(!input.isEditable() || !selected);
            selectAll.setDisable(input.getLength() == 0 || (input.getSelection().getStart() == 0
                    && input.getSelection().getEnd() == input.getLength()));
        });
        return menu;
    }

    private MenuItem action(String key, String fallback, Runnable action) {
        MenuItem item = new MenuItem(label(key, fallback));
        item.setOnAction(event -> action.run());
        return item;
    }

    private void registerChildren(Parent parent, String prefix) {
        List<Node> children = parent.getChildrenUnmodifiable();
        for (int index = 0; index < children.size(); index++) {
            Node child = children.get(index);
            String segment = child.getId() == null || child.getId().isBlank()
                    ? child.getClass().getSimpleName() + "-" + index : child.getId();
            String id = prefix + "." + AppearanceTargetId.segment(segment);
            registerTarget(child, AppearanceTargetId.of(id));
            if (child instanceof Parent nested) registerChildren(nested, id);
        }
    }

    private AppearanceTargetId deriveTargetId(Node node) {
        List<String> segments = new ArrayList<>();
        Node current = node;
        while (current != null && segments.size() < 12) {
            String value = current.getId();
            if (value == null || value.isBlank()) {
                int index = current.getParent() == null ? 0
                        : current.getParent().getChildrenUnmodifiable().indexOf(current);
                value = current.getClass().getSimpleName() + "-" + Math.max(0, index);
            }
            segments.addFirst(AppearanceTargetId.segment(value));
            current = current.getParent();
        }
        String id = String.join(".", segments);
        if (id.length() > AppearanceTargetId.MAX_LENGTH) {
            id = id.substring(id.length() - AppearanceTargetId.MAX_LENGTH);
        }
        return AppearanceTargetId.of(id);
    }

    private String label(String key, String fallback) {
        String translated = text.apply(key);
        return translated == null || translated.isBlank() || translated.equals(key) ? fallback : translated;
    }

    private static Node node(Object target) { return target instanceof Node value ? value : null; }

    private static Control controlAncestor(Node node) {
        for (Node current = node; current != null; current = current.getParent()) {
            if (current instanceof Control control) return control;
        }
        return null;
    }

    private static HostedContextMenu hostedContextMenu(Node node) {
        for (Node current = node; current != null; current = current.getParent()) {
            Object candidate = current.getProperties().get(HOST_CONTEXT_MENU_PROPERTY);
            if (candidate instanceof ContextMenu menu) return new HostedContextMenu(current, menu);
        }
        return null;
    }

    private static AppearanceRegistry registryFor(Node owner, Scene fallback) {
        Scene ownerScene = owner == null ? null : owner.getScene();
        Object candidate = ownerScene == null ? null
                : ownerScene.getProperties().get(SCENE_REGISTRY_PROPERTY);
        if (!(candidate instanceof AppearanceRegistry) && fallback != null) {
            candidate = fallback.getProperties().get(SCENE_REGISTRY_PROPERTY);
        }
        return candidate instanceof AppearanceRegistry registry && !registry.closed ? registry : null;
    }

    private record HostedContextMenu(Node owner, ContextMenu menu) { }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        for (Scene attached : List.copyOf(attachedScenes)) detachScene(attached);
        registrations.values().forEach(Registration::dispose);
        registrations.clear();
        if (editor != null) editor.close();
        editor = null;
    }

    private final class Registration {
        private final Node node;
        private final AppearanceTargetId id;
        private final ChangeListener<Boolean> hover = (observable, previous, current) -> refresh();
        private final ChangeListener<Boolean> focus = (observable, previous, current) -> refresh();
        private final ChangeListener<Boolean> disabled = (observable, previous, current) -> refresh();
        private final ChangeListener<Boolean> selected = (observable, previous, current) -> refresh();
        private boolean pressedState;
        private final EventHandler<MouseEvent> mousePressed = event -> {
            pressedState = true;
            refresh();
        };
        private final EventHandler<MouseEvent> mouseReleased = event -> {
            pressedState = false;
            refresh();
        };

        private Registration(Node node, AppearanceTargetId id) {
            this.node = node;
            this.id = id;
        }

        private void install() {
            node.hoverProperty().addListener(hover);
            node.focusedProperty().addListener(focus);
            node.disableProperty().addListener(disabled);
            node.addEventHandler(MouseEvent.MOUSE_PRESSED, mousePressed);
            node.addEventHandler(MouseEvent.MOUSE_RELEASED, mouseReleased);
            if (node instanceof ToggleButton toggle) toggle.selectedProperty().addListener(selected);
            if (node instanceof CheckBox check) check.selectedProperty().addListener(selected);
            if (node instanceof RadioButton radio) radio.selectedProperty().addListener(selected);
        }

        private AppearanceState state() {
            if (node.isDisabled()) return AppearanceState.DISABLED;
            if (pressedState) return AppearanceState.PRESSED;
            if (node instanceof ToggleButton toggle && toggle.isSelected()) return AppearanceState.SELECTED;
            if (node instanceof CheckBox check && check.isSelected()) return AppearanceState.CHECKED;
            if (node instanceof RadioButton radio && radio.isSelected()) return AppearanceState.CHECKED;
            if (node.isFocused()) return AppearanceState.FOCUSED;
            if (node.isHover()) return AppearanceState.HOVER;
            return AppearanceState.NORMAL;
        }

        private void refresh() { service.apply(node, id, state()); }

        private void dispose() {
            node.hoverProperty().removeListener(hover);
            node.focusedProperty().removeListener(focus);
            node.disableProperty().removeListener(disabled);
            node.removeEventHandler(MouseEvent.MOUSE_PRESSED, mousePressed);
            node.removeEventHandler(MouseEvent.MOUSE_RELEASED, mouseReleased);
            if (node instanceof ToggleButton toggle) toggle.selectedProperty().removeListener(selected);
            if (node instanceof CheckBox check) check.selectedProperty().removeListener(selected);
            if (node instanceof RadioButton radio) radio.selectedProperty().removeListener(selected);
        }
    }
}

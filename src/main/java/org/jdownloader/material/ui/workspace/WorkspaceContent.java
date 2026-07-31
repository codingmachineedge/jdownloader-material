package org.jdownloader.material.ui.workspace;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import javafx.scene.Node;
import org.jdownloader.material.search.SearchSpec;

/** One live tab page plus its lifecycle, search, and unsaved-work contract. */
public record WorkspaceContent(Node node, Consumer<SearchSpec> search, Runnable dispose,
                               BooleanSupplier hasUnsavedWork, String unsavedDescription) {
    public WorkspaceContent {
        node = Objects.requireNonNull(node, "node");
        search = search == null ? ignored -> { } : search;
        dispose = dispose == null ? () -> { } : dispose;
        hasUnsavedWork = hasUnsavedWork == null ? () -> false : hasUnsavedWork;
        unsavedDescription = Objects.requireNonNullElse(unsavedDescription, "");
    }

    public static WorkspaceContent simple(Node node, Consumer<SearchSpec> search, Runnable dispose) {
        return new WorkspaceContent(node, search, dispose, () -> false, "");
    }
}

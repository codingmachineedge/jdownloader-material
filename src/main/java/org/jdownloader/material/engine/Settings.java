package org.jdownloader.material.engine;

import java.nio.file.Path;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

/**
 * Observable application settings. The GUI binds controls directly to these
 * properties; the direct HTTP backend persists them and reacts to each one.
 */
public final class Settings {

    public static final int MAX_APPEARANCE_PROFILE_CHARS = 4 * 1024 * 1024;

    /** What to do when a target file already exists. */
    public enum IfExists {
        ASK("Auto-rename (no prompt)"), SKIP("Skip file"), OVERWRITE("Overwrite"), RENAME("Auto-rename");
        private final String label;
        IfExists(String label) { this.label = label; }
        public String label() { return label; }
        @Override public String toString() { return label; }
    }

    // General
    private final StringProperty downloadFolder =
            new SimpleStringProperty(this, "downloadFolder",
                    Path.of(System.getProperty("user.home"), "Downloads").toString());
    private final IntegerProperty maxSimultaneousDownloads = new SimpleIntegerProperty(this, "maxSimultaneousDownloads", 3);
    private final javafx.beans.property.ObjectProperty<IfExists> ifFileExists =
            new javafx.beans.property.SimpleObjectProperty<>(this, "ifFileExists", IfExists.ASK);

    // LinkGrabber
    private final BooleanProperty clipboardMonitoring = new SimpleBooleanProperty(this, "clipboardMonitoring", true);
    private final BooleanProperty autoConfirm = new SimpleBooleanProperty(this, "autoConfirm", false);
    private final BooleanProperty autoStart = new SimpleBooleanProperty(this, "autoStart", false);
    private final BooleanProperty addAtTop = new SimpleBooleanProperty(this, "addAtTop", false);

    // Connection
    private final BooleanProperty speedLimitEnabled = new SimpleBooleanProperty(this, "speedLimitEnabled", false);
    private final IntegerProperty speedLimitKbps = new SimpleIntegerProperty(this, "speedLimitKbps", 2000);
    private final IntegerProperty maxConnectionsPerHost = new SimpleIntegerProperty(this, "maxConnectionsPerHost", 8);

    // Network recovery. The historic property name is retained internally so
    // existing local state keeps its automatic-retry preference.
    private final BooleanProperty autoReconnect = new SimpleBooleanProperty(this, "autoReconnect", false);

    // Appearance
    private final BooleanProperty darkTheme = new SimpleBooleanProperty(this, "darkTheme", false);
    private final BooleanProperty speedInTitle = new SimpleBooleanProperty(this, "speedInTitle", true);
    private final ObjectProperty<LanguageMode> language =
            new SimpleObjectProperty<>(this, "language", LanguageMode.ENGLISH);
    // Start fully professional. Playfulness is an explicit, disclosed opt-in;
    // this also keeps first-run and bilingual chrome compact at narrow widths.
    private final IntegerProperty englishFunnyLevel = new SimpleIntegerProperty(this, "englishFunnyLevel", 1);
    private final IntegerProperty cantoneseFunnyLevel = new SimpleIntegerProperty(this, "cantoneseFunnyLevel", 1);
    private final BooleanProperty funnyLevelDisclosed = new SimpleBooleanProperty(this, "funnyLevelDisclosed", false);
    private final BooleanProperty dimSumSurpriseEnabled = new SimpleBooleanProperty(this, "dimSumSurpriseEnabled", true);
    private final BooleanProperty firstRunCompleted = new SimpleBooleanProperty(this, "firstRunCompleted", false);
    private final BooleanProperty reducedMotion = new SimpleBooleanProperty(this, "reducedMotion", false);
    private final BooleanProperty quietHours = new SimpleBooleanProperty(this, "quietHours", false);
    private final BooleanProperty notificationHistoryEnabled = new SimpleBooleanProperty(this, "notificationHistoryEnabled", true);
    /** Deterministic non-secret appearance payload captured by encrypted backups and local Git history. */
    private final StringProperty appearanceProfilePayload =
            new SimpleStringProperty(this, "appearanceProfilePayload", "");
    private final StringProperty externalEditorSelection = new SimpleStringProperty(this, "externalEditorSelection", "auto");
    private final StringProperty externalEditorCommand = new SimpleStringProperty(this, "externalEditorCommand", "");
    private final StringProperty remoteApiBaseUrl = new SimpleStringProperty(this, "remoteApiBaseUrl",
            "http://127.0.0.1:3128");

    public StringProperty downloadFolderProperty() { return downloadFolder; }
    public IntegerProperty maxSimultaneousDownloadsProperty() { return maxSimultaneousDownloads; }
    public javafx.beans.property.ObjectProperty<IfExists> ifFileExistsProperty() { return ifFileExists; }
    public BooleanProperty clipboardMonitoringProperty() { return clipboardMonitoring; }
    public BooleanProperty autoConfirmProperty() { return autoConfirm; }
    public BooleanProperty autoStartProperty() { return autoStart; }
    public BooleanProperty addAtTopProperty() { return addAtTop; }
    public BooleanProperty speedLimitEnabledProperty() { return speedLimitEnabled; }
    public IntegerProperty speedLimitKbpsProperty() { return speedLimitKbps; }
    public IntegerProperty maxConnectionsPerHostProperty() { return maxConnectionsPerHost; }
    public BooleanProperty autoReconnectProperty() { return autoReconnect; }
    public BooleanProperty darkThemeProperty() { return darkTheme; }
    public BooleanProperty speedInTitleProperty() { return speedInTitle; }
    public ObjectProperty<LanguageMode> languageProperty() { return language; }
    public IntegerProperty englishFunnyLevelProperty() { return englishFunnyLevel; }
    public IntegerProperty cantoneseFunnyLevelProperty() { return cantoneseFunnyLevel; }
    public BooleanProperty funnyLevelDisclosedProperty() { return funnyLevelDisclosed; }
    public BooleanProperty dimSumSurpriseEnabledProperty() { return dimSumSurpriseEnabled; }
    public BooleanProperty firstRunCompletedProperty() { return firstRunCompleted; }
    public BooleanProperty reducedMotionProperty() { return reducedMotion; }
    public BooleanProperty quietHoursProperty() { return quietHours; }
    public BooleanProperty notificationHistoryEnabledProperty() { return notificationHistoryEnabled; }
    public StringProperty appearanceProfilePayloadProperty() { return appearanceProfilePayload; }
    public void setAppearanceProfilePayload(String value) {
        String payload = value == null ? "" : value;
        if (payload.length() > MAX_APPEARANCE_PROFILE_CHARS) {
            throw new IllegalArgumentException("Appearance profile payload exceeds the 4 MiB character limit");
        }
        appearanceProfilePayload.set(payload);
    }
    public StringProperty externalEditorSelectionProperty() { return externalEditorSelection; }
    public StringProperty externalEditorCommandProperty() { return externalEditorCommand; }
    public StringProperty remoteApiBaseUrlProperty() { return remoteApiBaseUrl; }
}

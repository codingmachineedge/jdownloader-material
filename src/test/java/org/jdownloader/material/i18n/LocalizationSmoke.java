package org.jdownloader.material.i18n;

import java.util.Properties;
import org.jdownloader.material.engine.LanguageMode;
import org.jdownloader.material.engine.Settings;
import org.jdownloader.material.engine.SettingsIO;

/** Manual smoke check for the three persisted presentation modes. */
public final class LocalizationSmoke {

    private LocalizationSmoke() {
    }

    public static void main(String[] args) {
        Settings settings = new Settings();
        I18n i18n = new I18n(settings.languageProperty());

        require("Start".equals(i18n.text("toolbar.start")), "English toolbar copy is unavailable");

        settings.languageProperty().set(LanguageMode.HONG_KONG_CANTONESE);
        require("開波".equals(i18n.text("toolbar.start")), "Cantonese toolbar copy is unavailable");

        settings.languageProperty().set(LanguageMode.BILINGUAL);
        String bilingual = i18n.text("toolbar.start");
        require(bilingual.contains("Start") && bilingual.contains("開波"),
                "Bilingual toolbar copy does not show both languages");
        require("English".equals(i18n.languageName(LanguageMode.ENGLISH)),
                "Language picker English option is not self-named");
        require(i18n.languageName(LanguageMode.HONG_KONG_CANTONESE).contains("香港粵語"),
                "Language picker Cantonese option is not self-named");
        require(i18n.languageName(LanguageMode.BILINGUAL).contains("English")
                        && i18n.languageName(LanguageMode.BILINGUAL).contains("香港粵語"),
                "Language picker bilingual option does not show both languages");

        Properties backup = SettingsIO.snapshot(settings);
        require(!backup.containsKey("maxChunksPerDownload")
                        && !backup.containsKey("reconnectMethod")
                        && !backup.containsKey("myjdEmail")
                        && !backup.containsKey("myjdPassword"),
                "Settings backup still contains retired unavailable-setting fields");
        Settings restored = new Settings();
        SettingsIO.apply(backup, restored);
        require(restored.languageProperty().get() == LanguageMode.BILINGUAL,
                "Presentation language was not preserved in settings backup");

        Properties legacyBackup = new Properties();
        legacyBackup.setProperty("maxSimultaneousDownloads", "5");
        legacyBackup.setProperty("maxChunksPerDownload", "12");
        legacyBackup.setProperty("reconnectMethod", "Router (UPnP)");
        legacyBackup.setProperty("myjdEmail", "legacy@example.test");
        legacyBackup.setProperty("myjdPassword", "retired-secret");
        Settings legacyRestored = new Settings();
        SettingsIO.apply(legacyBackup, legacyRestored);
        require(legacyRestored.maxSimultaneousDownloadsProperty().get() == 5,
                "Supported settings were not restored from a legacy backup");

        System.out.println("Localization smoke check passed");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}

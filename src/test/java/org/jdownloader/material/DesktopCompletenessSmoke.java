package org.jdownloader.material;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.random.RandomGenerator;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Constants;
import org.jdownloader.material.changelog.ChangelogEntry;
import org.jdownloader.material.changelog.ChangelogService;
import org.jdownloader.material.dimsum.DimSumDish;
import org.jdownloader.material.dimsum.DimSumSurpriseService;
import org.jdownloader.material.engine.LanguageMode;
import org.jdownloader.material.engine.Settings;
import org.jdownloader.material.engine.SettingsIO;
import org.jdownloader.material.engine.history.HistorySnapshot;
import org.jdownloader.material.i18n.I18n;
import org.jdownloader.material.integration.ExternalEditorActions;
import org.jdownloader.material.integration.ExternalEditorService;
import org.jdownloader.material.notification.AppNotification;
import org.jdownloader.material.notification.NotificationService;
import org.jdownloader.material.notification.NotificationSeverity;
import org.jdownloader.material.workspace.GitWorkspaceStore;
import org.jdownloader.material.workspace.WorkspaceGroup;
import org.jdownloader.material.workspace.WorkspacePage;
import org.jdownloader.material.workspace.WorkspaceSnapshot;
import org.jdownloader.material.workspace.WorkspaceStyle;
import org.jdownloader.material.workspace.WorkspaceTab;

/**
 * Plain-main desktop foundation coverage that deliberately avoids launching a
 * JavaFX window. Every filesystem-backed service receives its own temp folder.
 */
public final class DesktopCompletenessSmoke {

    private static final int EXPECTED_ASSERTIONS = 195;
    private static int assertions;

    private DesktopCompletenessSmoke() {
    }

    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("jdm-desktop-completeness-");
        workspaceSchemaTwo(root.resolve("workspace-v2"));
        workspaceSchemaOneMigration(root.resolve("workspace-v1"));
        settingsRoundTrip(root.resolve("settings"));
        funnyLevelsRetainFacts();
        dimSumPolicy();
        notificationHistory(root.resolve("notifications"));
        changelogCoverage(root.resolve("changelog"));
        externalEditorParsing(root.resolve("external editor test"));
        historyContentEquality();
        if (assertions != EXPECTED_ASSERTIONS) {
            throw new IllegalStateException("Expected " + EXPECTED_ASSERTIONS
                    + " assertions but ran " + assertions);
        }
        System.out.println("Desktop completeness smoke passed: " + assertions
                + " assertions; isolated data: " + root);
    }

    private static void workspaceSchemaTwo(Path root) throws Exception {
        UUID firstGroupId = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
        UUID secondGroupId = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
        WorkspaceGroup firstGroup = new WorkspaceGroup(firstGroupId, "Queue α", "#12345678",
                "download", "A", true, true);
        WorkspaceGroup secondGroup = new WorkspaceGroup(secondGroupId, "Review 香港", "#ABCDEF",
                "fact-check", "2", false, false);
        WorkspaceTab pinned = tab("10000000-0000-0000-0000-000000000001",
                WorkspacePage.SETTINGS, "Pinned settings");
        WorkspaceTab closeOne = tab("10000000-0000-0000-0000-000000000002",
                WorkspacePage.CHANGELOG, "Close one");
        WorkspaceTab closeTwo = tab("10000000-0000-0000-0000-000000000003",
                WorkspacePage.NOTIFICATIONS, "Close two");
        UUID seededId;

        try (GitWorkspaceStore store = new GitWorkspaceStore(root)) {
            WorkspaceSnapshot seeded = store.load().get();
            seededId = seeded.tabs().getFirst().id();
            require(seeded.tabs().size() == 1, "Schema-v2 workspace did not seed Downloads");

            store.createGroup(firstGroup).get();
            store.createGroup(secondGroup).get();
            store.moveGroup(secondGroupId, 0).get();
            store.open(pinned).get();
            store.open(closeOne).get();
            store.open(closeTwo).get();
            store.setPinned(pinned.id(), true).get();
            store.moveToGroup(pinned.id(), firstGroupId, 0).get();
            store.moveToGroup(closeOne.id(), secondGroupId, 1).get();
            WorkspaceSnapshot ordered = store.moveTab(closeTwo.id(), 2).get();

            require(ids(ordered.groups()).equals(List.of(secondGroupId, firstGroupId)),
                    "Group order was not explicitly persisted in memory");
            require(ids(ordered.tabs()).equals(List.of(pinned.id(), closeOne.id(), closeTwo.id(), seededId)),
                    "Tab order was not explicitly persisted in memory");
            require(ordered.tab(pinned.id()).pinned(), "Pinned metadata was not applied");
            require(firstGroupId.equals(ordered.tab(pinned.id()).groupId()),
                    "Pinned tab membership was not applied");
            require(secondGroupId.equals(ordered.tab(closeOne.id()).groupId()),
                    "Second tab membership was not applied");

            String headBeforeNoOp = head(root);
            long eventsBeforeNoOp = eventCount(root);
            WorkspaceSnapshot unchanged = store.setPinned(pinned.id(), true).get();
            require(unchanged.equals(ordered), "No-op pin changed the workspace snapshot");
            require(head(root).equals(headBeforeNoOp), "No-op pin created a Git commit");
            require(eventCount(root) == eventsBeforeNoOp, "No-op pin created an event record");

            WorkspaceSnapshot closed = store.closeTabs(List.of(closeOne.id(), closeTwo.id())).get();
            require(ids(closed.tabs()).equals(List.of(pinned.id(), seededId)),
                    "Bulk close did not preserve the reviewed survivors and their order");
            require(closed.tab(closeOne.id()) == null && closed.tab(closeTwo.id()) == null,
                    "Bulk close left a reviewed tab open");
            require(pinned.id().equals(closed.selectedTabId()),
                    "Bulk close did not select the first survivor after closing the selected tab");
            store.flush().get();
        }

        Properties workspace = readProperties(root.resolve("workspace.properties"));
        require("2".equals(workspace.getProperty("schema")), "Workspace was not written as schema 2");
        require("2".equals(workspace.getProperty("groupCount")), "Schema 2 omitted ordered groups");
        require("true".equals(workspace.getProperty("tab.0.pinned")), "Schema 2 omitted pin state");
        require(firstGroupId.toString().equals(workspace.getProperty("tab.0.groupId")),
                "Schema 2 omitted group membership");
        require("false".equals(readProperties(root.resolve("tabs").resolve(closeOne.id() + ".properties"))
                .getProperty("open")), "First bulk-closed descriptor was not retained as closed");
        require("false".equals(readProperties(root.resolve("tabs").resolve(closeTwo.id() + ".properties"))
                .getProperty("open")), "Second bulk-closed descriptor was not retained as closed");
        require(hasEvent(root, "bulk-close", 2), "Bulk-close event did not record both closed tabs");

        try (GitWorkspaceStore reopened = new GitWorkspaceStore(root)) {
            WorkspaceSnapshot restored = reopened.load().get();
            require(ids(restored.tabs()).equals(List.of(pinned.id(), seededId)),
                    "Schema-v2 tab order did not survive reopen");
            require(ids(restored.groups()).equals(List.of(secondGroupId, firstGroupId)),
                    "Schema-v2 group order did not survive reopen");
            require(restored.tab(pinned.id()).pinned(), "Pin state did not survive reopen");
            require(firstGroupId.equals(restored.tab(pinned.id()).groupId()),
                    "Group membership did not survive reopen");
            require(restored.group(firstGroupId).collapsed() && restored.group(firstGroupId).pinned(),
                    "Group collapsed/pinned state did not survive reopen");
            require("#ABCDEFFF".equals(restored.group(secondGroupId).color()),
                    "Group color normalization did not survive reopen");
            require("fact-check".equals(restored.group(secondGroupId).icon())
                            && "2".equals(restored.group(secondGroupId).badge()),
                    "Group decorations did not survive reopen");
            require(restored.tab(closeOne.id()) == null && restored.tab(closeTwo.id()) == null,
                    "Bulk-closed tabs reopened after restart");
        }
    }

    private static void workspaceSchemaOneMigration(Path root) throws Exception {
        Files.createDirectories(root);
        UUID first = UUID.fromString("20000000-0000-0000-0000-000000000001");
        UUID second = UUID.fromString("20000000-0000-0000-0000-000000000002");
        Properties legacy = new Properties();
        legacy.setProperty("schema", "1");
        legacy.setProperty("applicationName", "Legacy desk");
        legacy.setProperty("selectedTabId", second.toString());
        legacy.setProperty("tabCount", "2");
        legacyTab(legacy, 0, first, WorkspacePage.DOWNLOADS, "Legacy downloads");
        legacyTab(legacy, 1, second, WorkspacePage.SETTINGS, "Legacy settings");
        writeProperties(root.resolve("workspace.properties"), legacy);

        try (GitWorkspaceStore store = new GitWorkspaceStore(root)) {
            WorkspaceSnapshot loaded = store.load().get();
            require("Legacy desk".equals(loaded.applicationName()), "Schema-v1 application name was not read");
            require(ids(loaded.tabs()).equals(List.of(first, second)), "Schema-v1 tab order was not read");
            require(second.equals(loaded.selectedTabId()), "Schema-v1 selected tab was not read");
            require(loaded.groups().isEmpty(), "Schema-v1 data unexpectedly invented groups");
            require(loaded.tabs().stream().noneMatch(WorkspaceTab::pinned),
                    "Schema-v1 tabs did not receive the unpinned migration default");
            require(loaded.tabs().stream().allMatch(tab -> tab.groupId() == null),
                    "Schema-v1 tabs did not receive the ungrouped migration default");
            store.renameApplication("Migrated desk").get();
            store.flush().get();
        }

        Properties migrated = readProperties(root.resolve("workspace.properties"));
        require("2".equals(migrated.getProperty("schema")), "Schema-v1 mutation did not migrate to schema 2");
        require("0".equals(migrated.getProperty("groupCount")), "Schema-v1 migration invented group records");
        require("false".equals(migrated.getProperty("tab.0.pinned"))
                        && migrated.getProperty("tab.0.groupId", "missing").isEmpty(),
                "Schema-v1 migration did not persist safe pin/group defaults");

        try (GitWorkspaceStore reopened = new GitWorkspaceStore(root)) {
            WorkspaceSnapshot restored = reopened.load().get();
            require("Migrated desk".equals(restored.applicationName()), "Migrated schema-v2 state did not reopen");
            require(ids(restored.tabs()).equals(List.of(first, second)),
                    "Migration changed the legacy tab order");
            require(restored.groups().isEmpty(), "Migrated workspace unexpectedly gained groups");
        }
    }

    private static void settingsRoundTrip(Path root) throws Exception {
        Files.createDirectories(root);
        Settings source = new Settings();
        source.languageProperty().set(LanguageMode.BILINGUAL);
        source.englishFunnyLevelProperty().set(5);
        source.cantoneseFunnyLevelProperty().set(1);
        source.funnyLevelDisclosedProperty().set(true);
        source.dimSumSurpriseEnabledProperty().set(false);
        source.firstRunCompletedProperty().set(true);
        source.reducedMotionProperty().set(true);
        source.quietHoursProperty().set(true);
        source.notificationHistoryEnabledProperty().set(false);
        source.externalEditorSelectionProperty().set("vscode");
        source.externalEditorCommandProperty().set("\"C:\\Program Files\\編輯器\\edit.exe\" \"%file%\"");
        source.remoteApiBaseUrlProperty().set("https://下載.example.test:9443/api/");

        Properties snapshot = SettingsIO.snapshot(source);
        Set<String> newKeys = Set.of("language", "englishFunnyLevel", "cantoneseFunnyLevel",
                "funnyLevelDisclosed", "dimSumSurpriseEnabled", "firstRunCompleted", "reducedMotion",
                "quietHours", "notificationHistoryEnabled", "externalEditorSelection",
                "externalEditorCommand", "remoteApiBaseUrl");
        require(snapshot.stringPropertyNames().containsAll(newKeys),
                "Settings snapshot omitted a new non-secret desktop field");
        require(snapshot.stringPropertyNames().stream().noneMatch(key -> key.toLowerCase().contains("password")),
                "Settings snapshot unexpectedly included a password field");

        Path backup = root.resolve("desktop completeness.jdmbackup");
        char[] passphrase = "plain-main-test-passphrase".toCharArray();
        Properties decoded;
        try {
            SettingsIO.exportTo(backup, snapshot, passphrase);
            decoded = SettingsIO.importFrom(backup, passphrase);
        } finally {
            Arrays.fill(passphrase, '\0');
        }
        require(Files.size(backup) > 32, "Encrypted settings backup was not created");
        require(new String(Files.readAllBytes(backup), StandardCharsets.ISO_8859_1)
                        .indexOf("編輯器") < 0,
                "Encrypted backup exposed the Unicode editor command as plaintext");

        Settings restored = new Settings();
        SettingsIO.apply(decoded, restored);
        require(restored.languageProperty().get() == LanguageMode.BILINGUAL, "Language mode did not round-trip");
        require(restored.englishFunnyLevelProperty().get() == 5, "English funny level did not round-trip");
        require(restored.cantoneseFunnyLevelProperty().get() == 1, "Cantonese funny level did not round-trip");
        require(restored.funnyLevelDisclosedProperty().get(), "Funny-level disclosure did not round-trip");
        require(!restored.dimSumSurpriseEnabledProperty().get(), "Dim-sum opt-out did not round-trip");
        require(restored.firstRunCompletedProperty().get(), "First-run state did not round-trip");
        require(restored.reducedMotionProperty().get(), "Reduced-motion setting did not round-trip");
        require(restored.quietHoursProperty().get(), "Quiet-hours setting did not round-trip");
        require(!restored.notificationHistoryEnabledProperty().get(),
                "Notification-history setting did not round-trip");
        require("vscode".equals(restored.externalEditorSelectionProperty().get()),
                "Detected external-editor choice did not round-trip");
        require(source.externalEditorCommandProperty().get().equals(restored.externalEditorCommandProperty().get()),
                "External-editor command did not round-trip");
        require(source.remoteApiBaseUrlProperty().get().equals(restored.remoteApiBaseUrlProperty().get()),
                "Remote API URL did not round-trip");
    }

    private static void funnyLevelsRetainFacts() {
        var mode = new javafx.beans.property.SimpleObjectProperty<>(LanguageMode.ENGLISH);
        var englishLevel = new javafx.beans.property.SimpleIntegerProperty(1);
        var cantoneseLevel = new javafx.beans.property.SimpleIntegerProperty(1);
        I18n i18n = new I18n(mode, englishLevel, cantoneseLevel);
        String englishFact = "Settings exported to FACT-42.";
        String cantoneseFact = "設定已匯出到 FACT-42。";
        Set<String> englishVoices = new HashSet<>();
        Set<String> cantoneseVoices = new HashSet<>();
        Set<String> bilingualVoices = new HashSet<>();

        for (int level = 1; level <= 5; level++) {
            englishLevel.set(level);
            mode.set(LanguageMode.ENGLISH);
            String copy = i18n.text("status.backup.exported", "FACT-42");
            require(copy.contains(englishFact), "English level " + level + " dropped factual copy");
            englishVoices.add(copy);

            cantoneseLevel.set(level);
            mode.set(LanguageMode.HONG_KONG_CANTONESE);
            copy = i18n.text("status.backup.exported", "FACT-42");
            require(copy.contains(cantoneseFact), "Cantonese level " + level + " dropped factual copy");
            cantoneseVoices.add(copy);

            mode.set(LanguageMode.BILINGUAL);
            copy = i18n.text("status.backup.exported", "FACT-42");
            require(copy.contains(englishFact) && copy.contains(cantoneseFact),
                    "Bilingual level " + level + " dropped one language's factual copy");
            bilingualVoices.add(copy);
        }
        require(englishVoices.size() == 5, "English funny levels do not produce five voices");
        require(cantoneseVoices.size() == 5, "Cantonese funny levels do not produce five voices");
        require(bilingualVoices.size() == 5, "Bilingual funny levels do not produce five voices");

        mode.set(LanguageMode.ENGLISH);
        englishLevel.set(3);
        cantoneseLevel.set(1);
        String englishBefore = i18n.text("status.backup.exported", "FACT-42");
        cantoneseLevel.set(5);
        require(englishBefore.equals(i18n.text("status.backup.exported", "FACT-42")),
                "Cantonese funny level changed English-only copy");

        mode.set(LanguageMode.HONG_KONG_CANTONESE);
        cantoneseLevel.set(3);
        englishLevel.set(1);
        String cantoneseBefore = i18n.text("status.backup.exported", "FACT-42");
        englishLevel.set(5);
        require(cantoneseBefore.equals(i18n.text("status.backup.exported", "FACT-42")),
                "English funny level changed Cantonese-only copy");

        mode.set(LanguageMode.BILINGUAL);
        englishLevel.set(2);
        cantoneseLevel.set(4);
        String bilingual = i18n.text("status.backup.exported", "FACT-42");
        mode.set(LanguageMode.ENGLISH);
        String expectedEnglish = i18n.text("status.backup.exported", "FACT-42");
        mode.set(LanguageMode.HONG_KONG_CANTONESE);
        String expectedCantonese = i18n.text("status.backup.exported", "FACT-42");
        require(bilingual.contains(expectedEnglish) && bilingual.contains(expectedCantonese),
                "Bilingual copy did not independently compose the selected voices");

        mode.set(LanguageMode.BILINGUAL);
        englishLevel.set(3);
        String englishChanged = i18n.text("status.backup.exported", "FACT-42");
        require(!englishChanged.equals(bilingual) && englishChanged.contains(expectedCantonese),
                "Changing English funny level disturbed or failed to update the Cantonese half");
        englishLevel.set(2);
        cantoneseLevel.set(5);
        String cantoneseChanged = i18n.text("status.backup.exported", "FACT-42");
        require(!cantoneseChanged.equals(bilingual) && cantoneseChanged.contains(expectedEnglish),
                "Changing Cantonese funny level disturbed or failed to update the English half");
    }

    private static void dimSumPolicy() {
        Settings firstRun = new Settings();
        SequenceRandom firstRunRandom = new SequenceRandom(0, 0);
        DimSumSurpriseService firstLaunch = new DimSumSurpriseService(firstRun, firstRunRandom);
        require(firstLaunch.choose(false, false, false).isEmpty(), "First run showed a dim-sum surprise");
        require(firstRun.firstRunCompletedProperty().get(), "First run was not marked complete");
        require(firstRunRandom.calls() == 0, "First-run suppression consumed a random draw");
        require(firstLaunch.choose(false, false, false).isEmpty() && firstRunRandom.calls() == 0,
                "First launch evaluated the chance more than once");

        SequenceRandom winningRandom = new SequenceRandom(0, 3);
        DimSumSurpriseService winningLaunch = new DimSumSurpriseService(firstRun, winningRandom);
        DimSumDish winner = winningLaunch.choose(false, false, false).orElseThrow();
        require("egg-tart".equals(winner.id()), "Winning draw did not use the deterministic dish draw");
        require(winningRandom.bounds().equals(List.of(100, 4)),
                "Winning launch did not make exactly one chance draw and one dish draw");
        require(winningLaunch.choose(false, false, false).isEmpty() && winningRandom.calls() == 2,
                "Winning launch drew again after evaluation");

        SequenceRandom justOutside = new SequenceRandom(1);
        require(new DimSumSurpriseService(readySurpriseSettings(), justOutside)
                        .choose(false, false, false).isEmpty(),
                "Chance boundary 1 incorrectly won the 1% draw");
        require(justOutside.bounds().equals(List.of(100)), "Losing launch made a dish draw");
        SequenceRandom upperBoundary = new SequenceRandom(99);
        require(new DimSumSurpriseService(readySurpriseSettings(), upperBoundary)
                        .choose(false, false, false).isEmpty(),
                "Chance boundary 99 incorrectly won the 1% draw");
        require(upperBoundary.bounds().equals(List.of(100)), "Upper-bound losing launch made a dish draw");

        Settings optedOut = readySurpriseSettings();
        optedOut.dimSumSurpriseEnabledProperty().set(false);
        requireSuppressed("opt-out", optedOut, false, false, false);
        requireSuppressed("startup error", readySurpriseSettings(), true, false, false);
        requireSuppressed("update", readySurpriseSettings(), false, true, false);
        requireSuppressed("task in progress", readySurpriseSettings(), false, false, true);
        Settings quiet = readySurpriseSettings();
        quiet.quietHoursProperty().set(true);
        requireSuppressed("quiet hours", quiet, false, false, false);
    }

    private static void notificationHistory(Path root) throws Exception {
        Path disabled = root.resolve("history-disabled");
        int[] disabledActionCalls = {0};
        try (NotificationService notifications = new NotificationService(disabled, () -> false)) {
            UUID actionable = notifications.show(NotificationSeverity.INFO, "Retry available", "The action stays live.",
                    "Retry", () -> disabledActionCalls[0]++);
            require(notifications.active().size() == 1
                            && notifications.active().getFirst().id().equals(actionable),
                    "History-disabled notification was not kept as an active toast");
            require(notifications.history().isEmpty(),
                    "History-disabled notification was appended to reviewable history");
            notifications.invokeAction(actionable);
            require(disabledActionCalls[0] == 1, "History-disabled active notification action was not callable");
            require(Files.notExists(disabled.resolve("notifications.properties")),
                    "History-disabled notification was persisted to disk");
        }

        Path cleared = root.resolve("cleared-history");
        int[] retainedActionCalls = {0};
        try (NotificationService notifications = new NotificationService(cleared)) {
            UUID actionable = notifications.show(NotificationSeverity.WARNING, "Review", "History can be cleared.",
                    "Review", () -> retainedActionCalls[0]++);
            require(notifications.history().size() == 1 && notifications.active().size() == 1,
                    "Actionable notification was not added to both active and history collections");
            notifications.clearHistory();
            require(notifications.history().isEmpty(), "clearHistory did not clear reviewable history");
            require(notifications.active().stream().anyMatch(item -> item.id().equals(actionable)),
                    "clearHistory incorrectly dismissed the active notification");
            notifications.invokeAction(actionable);
            require(retainedActionCalls[0] == 1,
                    "clearHistory incorrectly removed the active notification action");
            await("cleared notification persistence", () -> {
                try {
                    return "0".equals(readProperties(cleared.resolve("notifications.properties"))
                            .getProperty("count"));
                } catch (IOException notYetWritten) {
                    return false;
                }
            });
            require(Files.isRegularFile(cleared.resolve("notifications.properties")),
                    "Cleared notification history was not persisted as an empty history");
        }

        Path persistence = root.resolve("persistence");
        UUID infoId;
        UUID warningId;
        try (NotificationService notifications = new NotificationService(persistence)) {
            infoId = notifications.info("Saved", "The settings were saved.");
            warningId = notifications.warning("Check path", "The destination is unavailable.");
            require(notifications.history().size() == 2, "Notifications were not added to history");
            require(notifications.active().size() == 2, "Notifications were not added to the active corner stack");
            require(notifications.history().getFirst().id().equals(warningId),
                    "Notification history is not newest-first");

            notifications.markRead(infoId);
            require(notification(notifications.history(), infoId).read(), "markRead did not update history");
            notifications.dismiss(warningId);
            require(notifications.active().stream().noneMatch(item -> item.id().equals(warningId)),
                    "Dismissed notification remained active");
            require(notification(notifications.history(), warningId).read(),
                    "Dismiss did not mark the history record read");

            Path file = persistence.resolve("notifications.properties");
            await("notification read/dismiss persistence", () -> persistedNotificationsRead(file, infoId, warningId));
        }

        try (NotificationService reopened = new NotificationService(persistence)) {
            require(reopened.history().size() == 2, "Notification history did not survive reopen");
            require(notification(reopened.history(), infoId).read()
                            && notification(reopened.history(), warningId).read(),
                    "Notification read state did not survive reopen");
            require(reopened.active().isEmpty(), "Persisted notification history incorrectly reopened as active toasts");
        }

        Path bounded = root.resolve("bounded");
        Files.createDirectories(bounded);
        UUID oldestLoaded = notificationId(0);
        UUID newestLoaded = notificationId(499);
        writeNotificationFixture(bounded.resolve("notifications.properties"), 505);
        try (NotificationService notifications = new NotificationService(bounded)) {
            require(notifications.history().size() == 500, "Loaded notification history exceeded its 500-item bound");
            require(notifications.history().getFirst().id().equals(newestLoaded),
                    "Loaded notification history was not timestamp-sorted newest-first");
            UUID newest = notifications.error("Newest", "Bounded history remains reviewable.");
            require(notifications.history().size() == 500, "New notification exceeded the history bound");
            require(notifications.history().getFirst().id().equals(newest),
                    "New notification was not inserted at the front of bounded history");
            require(notifications.history().stream().noneMatch(item -> item.id().equals(oldestLoaded)),
                    "Bounded history did not evict its oldest record");
            await("bounded notification persistence", () -> {
                try {
                    Properties saved = readProperties(bounded.resolve("notifications.properties"));
                    return "500".equals(saved.getProperty("count"))
                            && newest.toString().equals(saved.getProperty("item.0.id"));
                } catch (IOException notYetWritten) {
                    return false;
                }
            });
        }
    }

    private static void changelogCoverage(Path root) throws Exception {
        Files.createDirectories(root);
        ChangelogService changelog = new ChangelogService();
        Set<String> actualVersions = new HashSet<>();
        for (ChangelogEntry entry : changelog.entries()) actualVersions.add(entry.version());
        for (int version = 2; version <= 28; version++) {
            require(actualVersions.contains("v0.1." + version),
                    "Bundled changelog omitted git tag v0.1." + version);
        }
        require(actualVersions.size() == changelog.entries().size(), "Bundled changelog contains duplicate versions");

        LocalDate from = LocalDate.of(2026, 7, 9);
        LocalDate to = LocalDate.of(2026, 7, 10);
        List<ChangelogEntry> dateRange = changelog.filter(from, to, null);
        require(dateRange.size() == 9, "Changelog date range did not include the expected nine releases");
        require(dateRange.stream().allMatch(entry -> !entry.date().isBefore(from) && !entry.date().isAfter(to)),
                "Changelog date range leaked an out-of-range release");
        List<ChangelogEntry> searched = changelog.filter(from, to, ChangelogService.plainSearch("workspace"));
        require(searched.size() == 1 && "v0.1.10".equals(searched.getFirst().version()),
                "Changelog text search did not compose with its date range");

        String markdown = changelog.markdown(dateRange, LanguageMode.BILINGUAL);
        require(markdown.contains("Exported range: 2026-07-09 through 2026-07-10"),
                "Changelog Markdown omitted the exact visible export range");
        require(markdown.contains("v0.1.2") && markdown.contains("v0.1.10"),
                "Changelog Markdown omitted an endpoint release");
        require(markdown.contains("Used a macOS-compatible package version.")
                        && markdown.contains("改用 macOS 相容嘅套件版本。"),
                "Bilingual changelog export omitted one language");
        Path exported = root.resolve("range export.md");
        require(changelog.exportMarkdown(exported, dateRange, LanguageMode.BILINGUAL).equals(exported.toAbsolutePath()),
                "Changelog export returned the wrong target path");
        require(Files.readString(exported, StandardCharsets.UTF_8).equals(markdown),
                "Changelog file export did not match the visible Markdown range");

        String versionKey = "jdownloader.material.version";
        String dateKey = "jdownloader.material.releaseDate";
        String commitKey = "jdownloader.material.commit";
        String previousVersion = System.getProperty(versionKey);
        String previousDate = System.getProperty(dateKey);
        String previousCommit = System.getProperty(commitKey);
        String runtimeVersion = "v9.9.9-test";
        LocalDate runtimeDate = LocalDate.of(2031, 2, 3);
        String runtimeCommit = "c0ffee42-runtime";
        try {
            System.setProperty(versionKey, runtimeVersion);
            System.setProperty(dateKey, runtimeDate.toString());
            System.setProperty(commitKey, runtimeCommit);
            ChangelogService runtimeChangelog = new ChangelogService();
            List<ChangelogEntry> runtimeEntries = runtimeChangelog.entries().stream()
                    .filter(entry -> runtimeVersion.equals(entry.version())).toList();
            require(runtimeEntries.size() == 1, "Runtime release metadata did not appear exactly once");
            ChangelogEntry runtimeEntry = runtimeEntries.getFirst();
            require(runtimeVersion.equals(runtimeEntry.version()) && runtimeDate.equals(runtimeEntry.date())
                            && runtimeCommit.equals(runtimeEntry.commit()),
                    "Runtime release metadata changed its version, date, or commit facts");
            require(runtimeEntry.english().contains("No additional categorized changes were recorded")
                            && runtimeEntry.cantonese().contains("冇另外記錄分類改動"),
                    "Runtime release copy invented categorized changes");

            Settings voice = new Settings();
            voice.languageProperty().set(LanguageMode.ENGLISH);
            voice.englishFunnyLevelProperty().set(1);
            I18n runtimeI18n = new I18n(voice.languageProperty(), voice.englishFunnyLevelProperty(),
                    voice.cantoneseFunnyLevelProperty());
            String seriousRendered = runtimeEntry.localized(runtimeI18n);
            Path seriousFile = root.resolve("runtime-serious.md");
            runtimeChangelog.exportMarkdown(seriousFile, List.of(runtimeEntry), runtimeI18n);
            String seriousExport = Files.readString(seriousFile, StandardCharsets.UTF_8);
            require(seriousRendered.equals(runtimeEntry.english()),
                    "Funny level 1 changed the factual runtime release copy");

            voice.englishFunnyLevelProperty().set(5);
            String playfulRendered = runtimeEntry.localized(runtimeI18n);
            Path playfulFile = root.resolve("runtime-playful.md");
            runtimeChangelog.exportMarkdown(playfulFile, List.of(runtimeEntry), runtimeI18n);
            String playfulExport = Files.readString(playfulFile, StandardCharsets.UTF_8);
            require(!playfulRendered.equals(seriousRendered) && playfulRendered.contains(runtimeEntry.english()),
                    "Funny level 5 did not restyle rendered runtime copy while retaining its facts");
            require(!playfulExport.equals(seriousExport),
                    "Funny level change did not reach exported runtime changelog copy");
            require(List.of(seriousExport, playfulExport).stream().allMatch(copy ->
                            copy.contains(runtimeVersion) && copy.contains(runtimeDate.toString())
                                    && copy.contains(runtimeCommit)),
                    "Funny-level export changed a runtime version, date, or commit fact");
            require(runtimeVersion.equals(runtimeEntry.version()) && runtimeDate.equals(runtimeEntry.date())
                            && runtimeCommit.equals(runtimeEntry.commit()),
                    "Voice rendering mutated immutable runtime release metadata");
        } finally {
            restoreProperty(versionKey, previousVersion);
            restoreProperty(dateKey, previousDate);
            restoreProperty(commitKey, previousCommit);
        }
        require(Objects.equals(previousVersion, System.getProperty(versionKey))
                        && Objects.equals(previousDate, System.getProperty(dateKey))
                        && Objects.equals(previousCommit, System.getProperty(commitKey)),
                "Runtime changelog test did not restore system properties");
    }

    private static void externalEditorParsing(Path root) throws Exception {
        Files.createDirectories(root);
        List<String> parsed = ExternalEditorService.parseCommandLine(
                "\"C:\\Program Files\\編輯器\\Editor.exe\" --label \"香港 file.txt\" plain");
        require(parsed.equals(List.of("C:\\Program Files\\編輯器\\Editor.exe", "--label", "香港 file.txt", "plain")),
                "External-editor parser lost spaces or Unicode text");
        IOException unmatched = expectIOException(() -> ExternalEditorService.parseCommandLine(
                "\"C:\\Program Files\\Editor.exe --wait"));
        require(unmatched.getMessage().toLowerCase().contains("unmatched quote"),
                "External-editor parser did not explain an unmatched quote");

        Path selectedFolder = Files.createDirectories(root.resolve("folder with spaces 香港"));
        Path selected = selectedFolder.resolve("下載 清單.txt");
        Files.writeString(selected, "test", StandardCharsets.UTF_8);
        List<List<String>> launches = new ArrayList<>();
        int[] detectionCalls = {0};
        ExternalEditorService.Editor detected = new ExternalEditorService.Editor("fixture-editor", "Fixture Editor",
                "\"C:\\Program Files\\Fixture Editor\\fixture.exe\" \"--literal=&&\" \"%file%\" \"%folder%\"");
        ExternalEditorService service = new ExternalEditorService(arguments -> {
            launches.add(List.copyOf(arguments));
            return new CompletedProcess();
        }, () -> {
            detectionCalls[0]++;
            return List.of(detected);
        });
        Settings settings = new Settings();
        settings.externalEditorSelectionProperty().set("fixture-editor");
        settings.externalEditorCommandProperty().set("cmd.exe /c exit 37");
        I18n i18n = new I18n(settings.languageProperty(), settings.englishFunnyLevelProperty(),
                settings.cantoneseFunnyLevelProperty());
        try (NotificationService notifications = new NotificationService(root.resolve("notifications"));
             ExternalEditorActions actions = new ExternalEditorActions(settings, i18n, notifications,
                     service, Runnable::run)) {
            require(actions.refreshDetectedEditors().get().equals(List.of(detected)),
                    "Injected Windows editor detection did not reach the actions workflow");
            require(actions.detectedEditors().equals(List.of(detected)),
                    "Detected editor list was not cached for chooser/action reuse");
            require(detectionCalls[0] == 1, "Editor detection did not run exactly once when refreshed");
            require(actions.openPath(selected).get(), "Detected-editor file action reported failure");
            require(launches.size() == 1, "Detected-editor action did not launch exactly once");
            require(launches.getFirst().equals(List.of(
                            "C:\\Program Files\\Fixture Editor\\fixture.exe", "--literal=&&",
                            selected.toAbsolutePath().normalize().toString(),
                            selectedFolder.toAbsolutePath().normalize().toString())),
                    "Detected-editor action did not expand Unicode file/folder placeholders into direct argv");
            require(launches.getFirst().stream().noneMatch(argument -> argument.equalsIgnoreCase("cmd.exe")),
                    "Detected-editor choice executed the separately saved custom command");
            require(notifications.active().getLast().severity() == NotificationSeverity.SUCCESS,
                    "Successful editor launch did not create a non-blocking success notification");

            settings.externalEditorSelectionProperty().set(ExternalEditorService.CUSTOM_SELECTION);
            settings.externalEditorCommandProperty().set(
                    "\"C:\\Custom Tools\\editor.exe\" \"%folder%\" && cmd.exe /c exit 37");
            require(actions.openPath(selectedFolder).get(), "Custom editor folder action reported failure");
            require(launches.size() == 2, "Custom editor action did not launch exactly once");
            require(launches.getLast().equals(List.of("C:\\Custom Tools\\editor.exe",
                            selectedFolder.toAbsolutePath().normalize().toString(),
                            "&&", "cmd.exe", "/c", "exit", "37")),
                    "Custom command metacharacters were not preserved as literal direct arguments");
        }

        List<List<String>> unavailableLaunches = new ArrayList<>();
        Settings unavailableSettings = new Settings();
        I18n unavailableI18n = new I18n(unavailableSettings.languageProperty(),
                unavailableSettings.englishFunnyLevelProperty(), unavailableSettings.cantoneseFunnyLevelProperty());
        ExternalEditorService unavailableService = new ExternalEditorService(arguments -> {
            unavailableLaunches.add(List.copyOf(arguments));
            return new CompletedProcess();
        }, List::of);
        try (NotificationService notifications = new NotificationService(root.resolve("unavailable-notifications"));
             ExternalEditorActions actions = new ExternalEditorActions(unavailableSettings, unavailableI18n,
                     notifications, unavailableService, Runnable::run)) {
            require(actions.refreshDetectedEditors().get().isEmpty(),
                    "No-editor fixture unexpectedly detected an editor");
            require(!actions.openPath(selected).get(), "No-editor action incorrectly reported success");
            require(unavailableLaunches.isEmpty(), "No-editor state attempted to launch a process");
            require(notifications.active().getLast().severity() == NotificationSeverity.ERROR
                            && notifications.active().getLast().title().contains("No external editor is available"),
                    "No-editor state did not create a factual non-blocking error notification");
        }
    }

    private static void historyContentEquality() {
        HistorySnapshot first = HistorySnapshot.fromText("beta=2\r\nalpha=1\r\n",
                "queue.one=url\r\n", "link.one=url\r\n");
        HistorySnapshot sameContent = HistorySnapshot.fromText("alpha=1\nbeta=2\n",
                "queue.one=url\n", "link.one=url\n");
        HistorySnapshot changedDownload = HistorySnapshot.fromText("alpha=1\nbeta=2\n",
                "queue.one=changed\n", "link.one=url\n");
        require(first.contentEquals(sameContent), "Canonical history content was not equal");
        require(sameContent.contentEquals(first), "History content equality was not symmetric");
        require(!first.contentEquals(changedDownload), "Changed history content compared equal");
        require(!first.contentEquals(null), "History content compared equal to null");
        require(HistorySnapshot.empty().contentEquals(HistorySnapshot.empty()),
                "Empty history snapshots did not compare equal");

        byte[] defensiveCopy = first.settingsBytes();
        defensiveCopy[0] ^= 1;
        require(first.contentEquals(sameContent), "Mutating a returned byte array changed snapshot equality");
    }

    private static WorkspaceTab tab(String id, WorkspacePage page, String title) {
        return new WorkspaceTab(UUID.fromString(id), page, title, WorkspaceStyle.DEFAULT);
    }

    private static List<UUID> ids(List<?> records) {
        List<UUID> ids = new ArrayList<>();
        for (Object record : records) {
            if (record instanceof WorkspaceTab tab) ids.add(tab.id());
            else if (record instanceof WorkspaceGroup group) ids.add(group.id());
            else throw new IllegalArgumentException("Unsupported workspace record " + record);
        }
        return ids;
    }

    private static String head(Path root) throws Exception {
        try (Git git = Git.open(root.toFile())) {
            var head = git.getRepository().resolve(Constants.HEAD);
            return head == null ? "" : head.name();
        }
    }

    private static long eventCount(Path root) throws IOException {
        Path events = root.resolve("events");
        if (!Files.isDirectory(events)) return 0;
        try (var files = Files.list(events)) {
            return files.filter(Files::isRegularFile).count();
        }
    }

    private static boolean hasEvent(Path root, String action, int closedCount) throws IOException {
        try (var files = Files.list(root.resolve("events"))) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                Properties event = readProperties(file);
                if (action.equals(event.getProperty("action"))
                        && Integer.toString(closedCount).equals(event.getProperty("closedCount"))) return true;
            }
        }
        return false;
    }

    private static void legacyTab(Properties properties, int index, UUID id, WorkspacePage page, String title) {
        String prefix = "tab." + index + ".";
        properties.setProperty(prefix + "id", id.toString());
        properties.setProperty(prefix + "page", page.name());
        properties.setProperty(prefix + "title", title);
        properties.setProperty(prefix + "fontFamily", "System");
        properties.setProperty(prefix + "fontSize", "13");
        properties.setProperty(prefix + "bold", "false");
        properties.setProperty(prefix + "italic", "false");
        properties.setProperty(prefix + "color", "#1D1B20");
    }

    private static Settings readySurpriseSettings() {
        Settings settings = new Settings();
        settings.firstRunCompletedProperty().set(true);
        settings.dimSumSurpriseEnabledProperty().set(true);
        settings.quietHoursProperty().set(false);
        return settings;
    }

    private static void requireSuppressed(String reason, Settings settings,
                                          boolean startupError, boolean updating, boolean taskInProgress) {
        SequenceRandom random = new SequenceRandom(0, 0);
        DimSumSurpriseService service = new DimSumSurpriseService(settings, random);
        require(service.choose(startupError, updating, taskInProgress).isEmpty(),
                "Dim-sum surprise ignored " + reason + " suppression");
        require(random.calls() == 0, "Dim-sum " + reason + " suppression consumed a random draw");
        require(service.choose(false, false, false).isEmpty() && random.calls() == 0,
                "Dim-sum " + reason + " suppression evaluated twice in one launch");
    }

    private static AppNotification notification(List<AppNotification> notifications, UUID id) {
        return notifications.stream().filter(item -> item.id().equals(id)).findFirst().orElseThrow();
    }

    private static boolean persistedNotificationsRead(Path file, UUID... ids) {
        try {
            Properties persisted = readProperties(file);
            Set<UUID> expected = new HashSet<>(List.of(ids));
            int count = Integer.parseInt(persisted.getProperty("count", "0"));
            for (int index = 0; index < count; index++) {
                String prefix = "item." + index + ".";
                UUID id = UUID.fromString(persisted.getProperty(prefix + "id"));
                if (expected.contains(id) && Boolean.parseBoolean(persisted.getProperty(prefix + "read"))) {
                    expected.remove(id);
                }
            }
            return expected.isEmpty();
        } catch (IOException | RuntimeException notYetWritten) {
            return false;
        }
    }

    private static void writeNotificationFixture(Path file, int count) throws IOException {
        Properties fixture = new Properties();
        fixture.setProperty("schema", "1");
        fixture.setProperty("count", Integer.toString(count));
        Instant base = Instant.parse("2026-01-01T00:00:00Z");
        for (int index = 0; index < count; index++) {
            String prefix = "item." + index + ".";
            fixture.setProperty(prefix + "id", notificationId(index).toString());
            fixture.setProperty(prefix + "timestamp", base.plusSeconds(index).toString());
            fixture.setProperty(prefix + "severity", NotificationSeverity.INFO.name());
            fixture.setProperty(prefix + "title", "Notification " + index);
            fixture.setProperty(prefix + "body", "Bounded history fixture " + index);
            fixture.setProperty(prefix + "actionLabel", "");
            fixture.setProperty(prefix + "read", Boolean.toString(index % 2 == 0));
        }
        writeProperties(file, fixture);
    }

    private static UUID notificationId(int index) {
        return UUID.fromString("30000000-0000-0000-0000-" + String.format("%012d", index));
    }

    private static void await(String label, BooleanSupplier complete) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
        while (System.nanoTime() < deadline) {
            if (complete.getAsBoolean()) return;
            Thread.sleep(20);
        }
        throw new IllegalStateException("Timed out waiting for " + label);
    }

    private static IOException expectIOException(ThrowingRunnable action) throws Exception {
        try {
            action.run();
        } catch (IOException expected) {
            return expected;
        }
        throw new IllegalStateException("Expected IOException was not thrown");
    }

    private static Properties readProperties(Path file) throws IOException {
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(file)) {
            properties.load(input);
        }
        return properties;
    }

    private static void writeProperties(Path file, Properties properties) throws IOException {
        Files.createDirectories(file.toAbsolutePath().getParent());
        try (OutputStream output = Files.newOutputStream(file, StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING)) {
            properties.store(output, "DesktopCompletenessSmoke fixture");
        }
    }

    private static void restoreProperty(String key, String value) {
        if (value == null) System.clearProperty(key);
        else System.setProperty(key, value);
    }

    private static void require(boolean condition, String message) {
        assertions++;
        if (!condition) throw new IllegalStateException(message);
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private static final class SequenceRandom implements RandomGenerator {
        private final int[] values;
        private final List<Integer> bounds = new ArrayList<>();
        private int index;

        private SequenceRandom(int... values) {
            this.values = values.clone();
        }

        @Override
        public int nextInt(int bound) {
            if (index >= values.length) throw new IllegalStateException("Unexpected random draw");
            int value = values[index++];
            if (value < 0 || value >= bound) {
                throw new IllegalStateException("Scripted random value " + value + " is outside 0.." + (bound - 1));
            }
            bounds.add(bound);
            return value;
        }

        @Override
        public long nextLong() {
            if (index >= values.length) throw new IllegalStateException("Unexpected random draw");
            return values[index++];
        }

        private int calls() {
            return index;
        }

        private List<Integer> bounds() {
            return List.copyOf(bounds);
        }
    }

    private static final class CompletedProcess extends Process {
        @Override public OutputStream getOutputStream() { return OutputStream.nullOutputStream(); }
        @Override public InputStream getInputStream() { return InputStream.nullInputStream(); }
        @Override public InputStream getErrorStream() { return InputStream.nullInputStream(); }
        @Override public int waitFor() { return 0; }
        @Override public int exitValue() { return 0; }
        @Override public void destroy() { }
    }
}

package org.jdownloader.material.workspace;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipFile;

/** Manual smoke check for the asynchronous, embedded-Git workspace store. */
public final class GitWorkspaceStoreSmoke {

    private GitWorkspaceStoreSmoke() {
    }

    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("jdm-workspace-smoke-");
        GitWorkspaceStore store = new GitWorkspaceStore(root);
        WorkspaceSnapshot seeded = store.load().get();
        require(seeded.tabs().size() == 1, "A new workspace did not seed Downloads");
        require(Files.isDirectory(root.resolve(".git")), "Workspace did not create its private Git repository");

        WorkspaceSnapshot opened = store.open(WorkspacePage.SETTINGS, "Preferences").get();
        WorkspaceTab settings = opened.tabs().stream()
                .filter(tab -> tab.page() == WorkspacePage.SETTINGS).findFirst().orElseThrow();
        WorkspaceTab styled = settings.withTitle("My preferences")
                .withStyle(new WorkspaceStyle("Arial", 18, true, true, "#12Ab34CC"));
        WorkspaceSnapshot updated = store.update(styled).get();
        require("#12AB34CC".equals(updated.tab(styled.id()).style().color()), "Style color was not normalized");
        WorkspaceSnapshot renamed = store.renameApplication("My Download Desk").get();
        require("My Download Desk".equals(renamed.applicationName()), "Application rename was not saved");

        Path portable = Files.createTempFile("jdm-workspace-smoke-", ".jdmtabs");
        store.exportSnapshot(portable).get();
        require(Files.size(portable) > 0, "Portable tab export was empty");
        Path repositoryZip = Files.createTempFile("jdm-workspace-smoke-", ".zip");
        store.exportRepository(repositoryZip).get();
        try (ZipFile zip = new ZipFile(repositoryZip.toFile())) {
            require(zip.stream().anyMatch(entry -> entry.getName().endsWith(".git/HEAD")),
                    "Repository export did not contain Git history");
        }

        WorkspaceSnapshot closed = store.closeTab(settings.id()).get();
        require(closed.tab(settings.id()) == null, "Closed tab remained open in the current workspace");
        require(Files.isRegularFile(root.resolve("tabs").resolve(settings.id() + ".properties")),
                "Closed tab descriptor was deleted instead of retained");
        require(Files.list(root.resolve("events")).count() >= 5, "Every workspace change was not recorded");

        WorkspaceSnapshot imported = store.importSnapshot(portable).get();
        require(imported.tabs().size() == 2, "Portable workspace import did not restore both tabs");
        store.flush().get();
        store.close();

        GitWorkspaceStore reopenedStore = new GitWorkspaceStore(root);
        WorkspaceSnapshot reopened = reopenedStore.load().get();
        require("My Download Desk".equals(reopened.applicationName()), "Imported application name did not survive reopen");
        require(reopened.tabs().size() == 2, "Imported tabs did not survive reopen");
        reopenedStore.close();
        System.out.println("Git workspace smoke check passed: " + root);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}

# Local version history

## Status

Implemented for Downloads, LinkGrabber, non-secret Settings and workspace structure.

## Behavior contract

Each settled semantic creation, edit or deletion records a complete relevant snapshot labelled with
what changed. This includes language, both funny levels, disclosure, dim-sum/quiet/reduced-motion,
notification-history, external-editor and loopback-bridge settings. Unchanged state records
nothing. Undo, redo and restore append new revisions; they never rewrite or discard the path being
replaced, so restoring can itself be undone.

History repositories live beside application data, never as `.git` directories inside a user's
download folder. The History page supports browse, preview, scope filter, undo, redo and restore
with meaningful labels. Restore changes model/settings state only; downloaded files and `.part`
contents are not changed.

## Workspace history

`GitWorkspaceStore` owns `~/.jdownloader-material/workspace/`, an independent Git timeline for the
open-tab list, selected tab, order, pinning, groups, group decorations and application name. Open,
close, move, pin, group, select, rename, import and bulk-close operations append commits. Snapshot
export/import is schema- and size-bounded; it exports current structure, not the private Git object
database.

## Configuration and failure modes

History remains local unless the user explicitly exports a snapshot. A transfer/settings
history-write failure logs and reports a non-blocking warning but does not fail the primary
operation. An interrupted multi-store write recovers from the durable manifest. A workspace write
failure keeps the active view usable, reports a persistent notification, and reloads the last
durable snapshot where the operation can safely do so.

## Security and privacy

Snapshots preserve live-data encryption. Authenticated-encryption AAD binds to stable identifiers,
not transient row ids. Credentials and downloaded/partial file bytes never enter history. Remote
API passwords are used only in transient password fields and are cleared after request assembly;
they are not Settings. Direct URLs are retained for faithful restore and therefore remain private
local data. Neither Git store configures a remote.

## Verification

History smokes test create/edit/delete, unchanged suppression, labels, undo/redo/restore chains,
restore-after-delete, restart recovery, terminal metadata and forbidden-byte exclusion. Workspace
smokes cover structural mutations, append-only commits, import/export validation and no-op
suppression. Manual verification confirms the history panels, write-failure notifications and
absence of configured remotes.

# Data, recovery and history

| Feature | Contract | Status |
| --- | --- | --- |
| Transfer/settings history | [Local version history](local-version-history.md) | Implemented |
| Workspace history | [Local version history](local-version-history.md#workspace-history) | Implemented |
| Restart journal | [Architecture](../ARCHITECTURE.md#restart-state) | Implemented |

The app owns Downloads, LinkGrabber records, settings and workspace structure. Transfer/settings
state and workspace structure use separate private append-only JGit repositories. Appearance and
notification history use bounded atomic local stores; their Settings controls remain inside the
main settings snapshot.

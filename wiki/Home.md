# JDownloader Material

JDownloader Material is an independently implemented JavaFX Windows desktop app for direct HTTP(S) files plus optional strict-loopback control of an installed JDownloader instance. Its mint/deep-teal Material 3 workspace combines browser-style tabs, bounded RE2/J search, live per-element appearance, notifications, changelog and append-only history while network and disk work continue in the background.

- [Install or run the app](Getting-Started)
- [Learn the interface](Interface)
- [Browse feature contracts and implementation status](Feature-Contracts)
- [Explore the interactive GitHub Pages demo](https://ding-ding-projects.github.io/jdownloader-material/)
- [Download the latest release](https://github.com/Ding-Ding-Projects/jdownloader-material/releases/latest)

Normal direct downloads use `DirectHttpEngine`. The app does not embed JDownloader core or use My.JDownloader cloud. Stock account, captcha, plugin, extraction, update and system pages are outbound controls for an already-installed local JDownloader process and degrade safely when its loopback API is unavailable. All content lives in persistent, pinnable and groupable workspace tabs.

## Screenshot gallery

The gallery below contains all 26 deterministic captures from the current running JavaFX application, including tabs, changelog, notifications, the loopback bridge, narrow bilingual layout and dim sum. Select any image to open its repository file.

### Downloads

| Light | Dark |
| --- | --- |
| [![Downloads, light](https://raw.githubusercontent.com/Ding-Ding-Projects/jdownloader-material/main/docs/screenshots/downloads-light.png)](https://github.com/Ding-Ding-Projects/jdownloader-material/blob/main/docs/screenshots/downloads-light.png) | [![Downloads, dark](https://raw.githubusercontent.com/Ding-Ding-Projects/jdownloader-material/main/docs/screenshots/downloads-dark.png)](https://github.com/Ding-Ding-Projects/jdownloader-material/blob/main/docs/screenshots/downloads-dark.png) |

| Status feedback | Properties, light | Properties, dark |
| --- | --- | --- |
| [![Downloads status feedback](https://raw.githubusercontent.com/Ding-Ding-Projects/jdownloader-material/main/docs/screenshots/downloads-status-light.png)](https://github.com/Ding-Ding-Projects/jdownloader-material/blob/main/docs/screenshots/downloads-status-light.png) | [![Download properties, light](https://raw.githubusercontent.com/Ding-Ding-Projects/jdownloader-material/main/docs/screenshots/downloads-properties-light.png)](https://github.com/Ding-Ding-Projects/jdownloader-material/blob/main/docs/screenshots/downloads-properties-light.png) | [![Download properties, dark](https://raw.githubusercontent.com/Ding-Ding-Projects/jdownloader-material/main/docs/screenshots/downloads-properties-dark.png)](https://github.com/Ding-Ding-Projects/jdownloader-material/blob/main/docs/screenshots/downloads-properties-dark.png) |

| Hong Kong Cantonese | Bilingual |
| --- | --- |
| [![Downloads, Hong Kong Cantonese](https://raw.githubusercontent.com/Ding-Ding-Projects/jdownloader-material/main/docs/screenshots/downloads-cantonese.png)](https://github.com/Ding-Ding-Projects/jdownloader-material/blob/main/docs/screenshots/downloads-cantonese.png) | [![Downloads, bilingual](https://raw.githubusercontent.com/Ding-Ding-Projects/jdownloader-material/main/docs/screenshots/downloads-bilingual.png)](https://github.com/Ding-Ding-Projects/jdownloader-material/blob/main/docs/screenshots/downloads-bilingual.png) |

### LinkGrabber

| Light | Dark | Hong Kong Cantonese |
| --- | --- | --- |
| [![LinkGrabber, light](https://raw.githubusercontent.com/Ding-Ding-Projects/jdownloader-material/main/docs/screenshots/linkgrabber-light.png)](https://github.com/Ding-Ding-Projects/jdownloader-material/blob/main/docs/screenshots/linkgrabber-light.png) | [![LinkGrabber, dark](https://raw.githubusercontent.com/Ding-Ding-Projects/jdownloader-material/main/docs/screenshots/linkgrabber-dark.png)](https://github.com/Ding-Ding-Projects/jdownloader-material/blob/main/docs/screenshots/linkgrabber-dark.png) | [![LinkGrabber, Hong Kong Cantonese](https://raw.githubusercontent.com/Ding-Ding-Projects/jdownloader-material/main/docs/screenshots/linkgrabber-cantonese.png)](https://github.com/Ding-Ding-Projects/jdownloader-material/blob/main/docs/screenshots/linkgrabber-cantonese.png) |

### History

| Light | Dark | Bilingual |
| --- | --- | --- |
| [![History, light](https://raw.githubusercontent.com/Ding-Ding-Projects/jdownloader-material/main/docs/screenshots/history-light.png)](https://github.com/Ding-Ding-Projects/jdownloader-material/blob/main/docs/screenshots/history-light.png) | [![History, dark](https://raw.githubusercontent.com/Ding-Ding-Projects/jdownloader-material/main/docs/screenshots/history-dark.png)](https://github.com/Ding-Ding-Projects/jdownloader-material/blob/main/docs/screenshots/history-dark.png) | [![History, bilingual](https://raw.githubusercontent.com/Ding-Ding-Projects/jdownloader-material/main/docs/screenshots/history-bilingual.png)](https://github.com/Ding-Ding-Projects/jdownloader-material/blob/main/docs/screenshots/history-bilingual.png) |

### Settings

| General, light | General, dark |
| --- | --- |
| [![Settings, light](https://raw.githubusercontent.com/Ding-Ding-Projects/jdownloader-material/main/docs/screenshots/settings-light.png)](https://github.com/Ding-Ding-Projects/jdownloader-material/blob/main/docs/screenshots/settings-light.png) | [![Settings, dark](https://raw.githubusercontent.com/Ding-Ding-Projects/jdownloader-material/main/docs/screenshots/settings-dark.png)](https://github.com/Ding-Ding-Projects/jdownloader-material/blob/main/docs/screenshots/settings-dark.png) |

| Appearance, light | Appearance, dark | Appearance, bilingual |
| --- | --- | --- |
| [![Appearance settings, light](https://raw.githubusercontent.com/Ding-Ding-Projects/jdownloader-material/main/docs/screenshots/settings-appearance-light.png)](https://github.com/Ding-Ding-Projects/jdownloader-material/blob/main/docs/screenshots/settings-appearance-light.png) | [![Appearance settings, dark](https://raw.githubusercontent.com/Ding-Ding-Projects/jdownloader-material/main/docs/screenshots/settings-appearance-dark.png)](https://github.com/Ding-Ding-Projects/jdownloader-material/blob/main/docs/screenshots/settings-appearance-dark.png) | [![Appearance settings, bilingual](https://raw.githubusercontent.com/Ding-Ding-Projects/jdownloader-material/main/docs/screenshots/settings-appearance-bilingual.png)](https://github.com/Ding-Ding-Projects/jdownloader-material/blob/main/docs/screenshots/settings-appearance-bilingual.png) |

### Add Links drawer

| Light | Dark | Bilingual |
| --- | --- | --- |
| [![Add Links drawer, light](https://raw.githubusercontent.com/Ding-Ding-Projects/jdownloader-material/main/docs/screenshots/add-links-light.png)](https://github.com/Ding-Ding-Projects/jdownloader-material/blob/main/docs/screenshots/add-links-light.png) | [![Add Links drawer, dark](https://raw.githubusercontent.com/Ding-Ding-Projects/jdownloader-material/main/docs/screenshots/add-links-dark.png)](https://github.com/Ding-Ding-Projects/jdownloader-material/blob/main/docs/screenshots/add-links-dark.png) | [![Add Links drawer, bilingual](https://raw.githubusercontent.com/Ding-Ding-Projects/jdownloader-material/main/docs/screenshots/add-links-bilingual.png)](https://github.com/Ding-Ding-Projects/jdownloader-material/blob/main/docs/screenshots/add-links-bilingual.png) |

### Workspace services

| Changelog | Installed-JDownloader bridge | Notification history |
| --- | --- | --- |
| [![Searchable changelog](https://raw.githubusercontent.com/Ding-Ding-Projects/jdownloader-material/main/docs/screenshots/changelog-light.png)](https://github.com/Ding-Ding-Projects/jdownloader-material/blob/main/docs/screenshots/changelog-light.png) | [![Installed-JDownloader plugins bridge](https://raw.githubusercontent.com/Ding-Ding-Projects/jdownloader-material/main/docs/screenshots/plugins-bridge-light.png)](https://github.com/Ding-Ding-Projects/jdownloader-material/blob/main/docs/screenshots/plugins-bridge-light.png) | [![Bilingual notification history](https://raw.githubusercontent.com/Ding-Ding-Projects/jdownloader-material/main/docs/screenshots/notifications-bilingual.png)](https://github.com/Ding-Ding-Projects/jdownloader-material/blob/main/docs/screenshots/notifications-bilingual.png) |

### Responsive layout and startup delight

| Bilingual compact width | Non-blocking dim-sum surprise |
| --- | --- |
| [![Downloads at 880 by 560 pixels in bilingual mode](https://raw.githubusercontent.com/Ding-Ding-Projects/jdownloader-material/main/docs/screenshots/downloads-bilingual-narrow.png)](https://github.com/Ding-Ding-Projects/jdownloader-material/blob/main/docs/screenshots/downloads-bilingual-narrow.png) | [![Shrimp-dumpling startup surprise](https://raw.githubusercontent.com/Ding-Ding-Projects/jdownloader-material/main/docs/screenshots/dim-sum-light.png)](https://github.com/Ding-Ding-Projects/jdownloader-material/blob/main/docs/screenshots/dim-sum-light.png) |

Continue with [Getting started](Getting-Started), or read the [design system](Design-System) behind the rewrite.

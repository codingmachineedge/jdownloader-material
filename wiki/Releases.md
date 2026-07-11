# Releases

Installer releases and GitHub Pages are both published only from `main`. Feature branches are development inputs; merging or pushing the final commit to `main` is the publication event.

## Native installers

Each push to `main` starts the release workflow. It stages a draft `v0.1.<run-number>` release for the commit and builds exactly four self-contained assets:

- `JDownloader-Material-windows-x64.exe`
- `JDownloader-Material-linux-x64.deb`
- `JDownloader-Material-macos-arm64.dmg`
- `JDownloader-Material-macos-x64.dmg`

Each installer bundles Java 25. Assets upload directly to the draft; retained Actions artifacts are not part of the delivery. The validation job publishes only after it finds the exact four-file set and removes an incomplete draft when preparation or any platform build fails. Windows and macOS installers are currently unsigned.

[Latest release](https://github.com/codingmachineedge/jdownloader-material/releases/latest) · [All releases](https://github.com/codingmachineedge/jdownloader-material/releases)

## GitHub Pages

The Pages workflow also runs on pushes to `main` (or a manual dispatch), uploads `site/`, and deploys it to:

https://codingmachineedge.github.io/jdownloader-material/

Before merging an interface change, keep the app, 21 screenshots, README, repository docs, tracked `wiki/` source, and `site/` demo aligned. After the merge, verify both Actions workflows, the exact installer assets, and the live Pages URL.

Development and capture commands are in [Development](Development).

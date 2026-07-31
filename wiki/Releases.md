# Releases

Installer verification and release publication run for every branch push and manual dispatch;
release-created tag pushes are excluded so they cannot start another release. GitHub Pages
publishes the verified static site from `main` or a Pages manual dispatch. Local workflow source is
not remote proof; inspect the exact run after each push.

## Windows installer

The release workflow first discovers and executes every non-TUI desktop `*Smoke.java` main under
Ubuntu/Xvfb, runs the static Pages guard and validates matching bundled dim-sum catalogs/images.
Only a passing run stages a unique, non-reused `v0.1.<run-number>-run.<attempt>` draft and builds:

- `JDownloader-Material-windows-x64.exe`

The EXE bundles Java 25 and the project icon. Its runtime options carry the exact release tag, UTC
release date and source commit into the in-app changelog instead of inventing that metadata at
runtime. It uploads directly to the draft; retained Actions artifacts are not part of delivery. One
original project-bundled dim-sum photograph also uploads, and release notes identify its
English/Cantonese name and filename. Publication requires exactly the EXE plus that photo. A failed
run removes only its incomplete draft; an already-published release is never refreshed or
overwritten. The installer is currently unsigned.

[Latest release](https://github.com/Ding-Ding-Projects/jdownloader-material/releases/latest) ·
[All releases](https://github.com/Ding-Ding-Projects/jdownloader-material/releases)

## GitHub Pages and wiki

The Pages workflow guards and publishes `site/` at
https://ding-ding-projects.github.io/jdownloader-material/. The landing page is a persistent,
keyboard-operable tabbed interface with discrete panels, overflow/search, reduced-motion behavior
and per-tab appearance editing; it must never regress to one long marketing scroll.

Before completion, keep the app, screenshots, README, categorized docs, tracked wiki, roadmap,
handoff and site aligned. After push, verify both Actions workflows, exact EXE/photo assets,
immutable tag target, live Pages URL and separate wiki remote tip.

Development and capture commands are in [Development](Development).

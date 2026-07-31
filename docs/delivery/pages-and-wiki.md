# Pages and wiki

## Status

GitHub Pages and the GitHub wiki already exist. Their tracked source is implemented in this working
tree; the live surfaces remain unverified until their respective pushes complete.

## Behavior contract

Pages publishes only the static `site/` directory after `site/smoke-check.mjs` succeeds. Its
canonical URL is `https://ding-ding-projects.github.io/jdownloader-material/`. Tracked wiki source
lives under `wiki/` and is synchronized to the separate
`Ding-Ding-Projects/jdownloader-material.wiki.git` repository after the base repository push.

The Pages landing page uses eight persistent browser-style tabs and discrete panels rather than one
marketing scroll. Tabs support overflow, reordering, pinning, grouping/collapse, keyboard movement,
a searchable manager and previewed containing/inverse bulk close. Current-strip, per-group,
group-name and master discovery searches keep independent plain-first bounded ECMAScript builders.

The static surface also provides English, Hong Kong Cantonese and bilingual modes; independent
professional-by-default 1–5 funny controls and first-run disclosure; corner notifications/history;
the eligible local 1% dim-sum delight; a searchable Settings panel; and a live per-element/state M3
appearance workbench with profiles, typography and color translation. Eleven in-site feature
articles each cover behavior, configuration, failure modes, security, verification and related
documentation. Both Pages and wiki keep implementation facts separate from remote release evidence.

## Configuration and persistence

`.github/workflows/pages.yml` handles Pages pushes and manual dispatches. Site preferences and
appearance profiles stay in the visitor's browser; regex samples are evaluated locally and are not
persisted. Four copied dim-sum images are bundled below `site/assets/dimsum/` and hash-match the
release-safe catalog. The wiki's Git repository uses its own history and default `master` branch.
Updates to either surface must not create an automation loop back into the base repository.

Browsers do not expose an enumerable installed-font list. The appearance workbench therefore offers
free-entry local family names plus searchable known choices and a CJK-safe fallback; it does not
pretend that list is complete.

## Failure modes

A failing Pages source guard—including missing tab roles/keyboard support, visible long-scroll
panels, shared search state, unsafe regex handling, unreviewed bulk close, unavailable narrow
overflow, missing appearance/localization/notification/dim-sum controls, incomplete feature
articles or stale canonical links—prevents deployment. A failed wiki push leaves tracked source as
the recovery point; it must be reported rather than described as synchronized.

## Security

The static site contains no secrets, analytics, or credential intake. External links use HTTPS.
Wiki synchronization uses normal Git authentication and never embeds credentials in remotes or
files.

## Verification

`node site/smoke-check.mjs` currently passes 155 assertions, validates eleven in-site feature
articles and decodes/hash-checks four local dim-sum images; `node --check` passes for each site
script. Off-screen Chrome verification covered Settings, bilingual persistence, first-run
disclosure/dismissal, notification toast/count, appearance editing, independent regex state and
adversarial rejection, tab management and the 560 px layout. After push, still verify the exact
Pages run/live URL and compare the separate wiki remote tip and rendered pages with tracked source.

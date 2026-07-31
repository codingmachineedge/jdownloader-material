# Release pipeline

## Status

The workflow implements this contract locally. It is not remotely verified until the change is
pushed and the resulting Actions run and release are inspected.

## Behavior contract

- Every branch push and manual dispatch starts the desktop verification gate. Release-created tag
  pushes are deliberately excluded so publishing cannot trigger another release run. Deleted branch
  refs are ignored as cleanup events, so removing a merged branch cannot publish a duplicate build.
- Verification discovers every desktop `*Smoke.java` main class, excludes TUI paths, asserts the
  established checks still exist, and executes the whole discovered set under Linux Xvfb.
- Static Pages checks and the two dim-sum catalogs are validated before a draft release exists.
- A qualifying run receives a new tag derived from the monotonically increasing workflow run number
  and run attempt. An existing tag is a hard refusal; published releases are never refreshed.
- A hosted `windows-latest` runner builds one genuine Windows x64 EXE with `jpackage`, the bundled
  Java runtime and the project ICO.
- The installer uploads directly to the draft, without retained Actions artifacts.
- One project-bundled dim-sum photograph is selected deterministically, decoded during verification,
  attached to the release, and identified by English name, Cantonese name, and exact filename in the
  release notes.
- Publication requires exactly `JDownloader-Material-windows-x64.exe` plus the selected photograph,
  with uploaded state and minimum-size checks. A failed run removes only its incomplete draft.

## Configuration

The application and package versions derive from `github.run_number`. `jpackage` also embeds the
immutable prepare-release tag, the Windows runner's UTC release date and `GITHUB_SHA` as
`jdownloader.material.version`, `jdownloader.material.releaseDate` and
`jdownloader.material.commit`; the in-app changelog uses those values for the current build.
Authentication resolves only through
`secrets.RELEASE_TOKEN || secrets.ORG_TOKEN || secrets.GITHUB_TOKEN`. The image selection source is
`release-assets/dimsum/catalog.json`; runtime copies live in `src/main/resources/dimsum/`.

## Failure modes

- A missing or failed smoke, Pages guard, catalog mismatch, undecodable image, missing package icon,
  installer failure, missing/extra asset, incomplete upload or undersized artifact fails the run.
- No release is created when verification fails.
- After draft creation, later failure deletes that draft. If the matching release is already
  published, cleanup refuses to mutate it and fails loudly.
- A reused tag fails before creation rather than overwriting history.

## Security and privacy

The workflow runs only on branch pushes and explicit dispatches, never on tag pushes or untrusted
pull-request code.
Hosted runners are disposable. Tokens are passed only in `GH_TOKEN`; the workflow never prints
them. Release images are local, original project assets with no third-party runtime fetch.

## Verification

Inspect the completed Actions run, tag target, release draft flags, exact two asset names, sizes,
upload state and SHA-256 digests with `gh`. Exercise the workflow locally as described in
[verification.md](verification.md) before pushing.

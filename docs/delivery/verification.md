# Desktop delivery verification

## Status

The release workflow discovers every current non-TUI desktop `*Smoke.java` main by filename instead
of maintaining a silently stale count or allowlist. Remote evidence for this workflow remains
pending until it is pushed.

## Local commands

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress test-compile
$testRoot = (Resolve-Path "src/test/java").Path
$smokes = Get-ChildItem -LiteralPath $testRoot -Recurse -File -Filter "*Smoke.java" |
  Where-Object { $_.FullName -notmatch "[\\/]tui[\\/]" } |
  ForEach-Object {
    [IO.Path]::GetRelativePath($testRoot, $_.FullName) `
      -replace "\.java$", "" -replace "[\\/]", "."
  } | Sort-Object -Unique
foreach ($smoke in $smokes) {
  .\mvnw.cmd org.codehaus.mojo:exec-maven-plugin:3.5.0:java `
    "-Dexec.mainClass=$smoke" "-Dexec.classpathScope=test"
  if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}
node .\site\smoke-check.mjs
```

## Evidence contract

Record each command, exit code, smoke class and assertion count where available. For visible fixes,
capture the exact desktop surface from the real built application. After push, record the exact
commit, workflow run, installer asset metadata, release tag, dim-sum asset, Pages deployment and
wiki tip. A running check is `running`, not `verified`.

## Failure modes

`mvn test` alone is insufficient because the smoke classes are plain main programs. A compile-only
result, green installer build without smoke execution, unrelated screenshot, or predicted CI result
does not count as verification.

## Security

Smoke runs use test or temporary data. Do not submit real downloads, expose retained signed URLs,
or write test state into another person's normal profile.

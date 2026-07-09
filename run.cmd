@echo off
rem ---------------------------------------------------------------------------
rem JDownloader Material - zero-setup build & run for Windows.
rem Finds (or auto-downloads) a JDK 25+, then builds and runs via the bundled
rem Maven Wrapper. No pre-installed Java or Maven required.
rem ---------------------------------------------------------------------------
setlocal
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0run.ps1" %*
endlocal

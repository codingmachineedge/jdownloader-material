# -----------------------------------------------------------------------------
# JDownloader Material — zero-setup build & run (Windows).
#
# 1. Locates a JDK 21+ (JAVA_HOME, PATH, or a previously provisioned .jdk/).
# 2. If none is found, downloads Eclipse Temurin from the Adoptium API for this
#    machine's architecture and unpacks it into .jdk/ (project-local, no admin,
#    no system changes).
# 3. Builds and launches the app through the bundled Maven Wrapper (mvnw), which
#    likewise self-downloads Maven. Requires only Windows + internet on first run.
# -----------------------------------------------------------------------------
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$jdkDir = Join-Path $root '.jdk'
$required = 25   # minimum Java feature release (pom compiles with --release 25)
$temurin = 25    # release to provision when none is found

function Get-JavaMajor($javaExe) {
    try {
        $line = (& $javaExe -version 2>&1 | Select-Object -First 1) -join ''
        if ($line -match '"(\d+)[\.\d_]*"') { return [int]$Matches[1] }
    } catch { }
    return 0
}

function Find-Java {
    # 1) JAVA_HOME
    if ($env:JAVA_HOME) {
        $exe = Join-Path $env:JAVA_HOME 'bin\java.exe'
        if ((Test-Path $exe) -and (Get-JavaMajor $exe) -ge $required) { return $env:JAVA_HOME }
    }
    # 2) java on PATH
    $onPath = Get-Command java -ErrorAction SilentlyContinue
    if ($onPath -and (Get-JavaMajor $onPath.Source) -ge $required) {
        return (Split-Path -Parent (Split-Path -Parent $onPath.Source))
    }
    # 3) previously provisioned
    if (Test-Path $jdkDir) {
        $home = Get-ChildItem $jdkDir -Directory | Where-Object { Test-Path (Join-Path $_.FullName 'bin\java.exe') } | Select-Object -First 1
        if ($home -and (Get-JavaMajor (Join-Path $home.FullName 'bin\java.exe')) -ge $required) { return $home.FullName }
    }
    return $null
}

$javaHome = Find-Java
if (-not $javaHome) {
    $arch = if ([System.Runtime.InteropServices.RuntimeInformation]::OSArchitecture -eq 'Arm64') { 'aarch64' } else { 'x64' }
    Write-Host "No JDK $required+ found - downloading Eclipse Temurin $temurin ($arch) to .jdk\ ..." -ForegroundColor Yellow
    $url = "https://api.adoptium.net/v3/binary/latest/$temurin/ga/windows/$arch/jdk/hotspot/normal/eclipse"
    $zip = Join-Path $env:TEMP "temurin-$temurin.zip"
    Invoke-WebRequest -Uri $url -OutFile $zip -UseBasicParsing
    New-Item -ItemType Directory -Force $jdkDir | Out-Null
    Expand-Archive -Path $zip -DestinationPath $jdkDir -Force
    Remove-Item $zip -Force
    $javaHome = (Get-ChildItem $jdkDir -Directory | Where-Object { Test-Path (Join-Path $_.FullName 'bin\java.exe') } | Select-Object -First 1).FullName
    if (-not $javaHome) { throw 'JDK download or extraction failed.' }
    Write-Host "JDK provisioned at $javaHome" -ForegroundColor Green
}

$env:JAVA_HOME = $javaHome
$env:Path = "$javaHome\bin;$env:Path"
Write-Host "Using JDK: $javaHome"

& (Join-Path $root 'mvnw.cmd') -q javafx:run @args
exit $LASTEXITCODE

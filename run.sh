#!/usr/bin/env bash
# -----------------------------------------------------------------------------
# JDownloader Material — zero-setup build & run (Linux / macOS).
#
# 1. Locates a JDK 25+ (JAVA_HOME, PATH, or a previously provisioned .jdk/).
# 2. If none is found, downloads Eclipse Temurin from the Adoptium API for this
#    OS/architecture and unpacks it into .jdk/ (project-local, no sudo, no
#    system changes).
# 3. Builds and launches the app through the bundled Maven Wrapper (mvnw), which
#    likewise self-downloads Maven. Requires only bash + curl/wget + internet on
#    the first run.
# -----------------------------------------------------------------------------
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
JDK_DIR="$ROOT/.jdk"
REQUIRED=25   # minimum Java feature release (pom compiles with --release 25)
TEMURIN=25    # release to provision when none is found

java_major() {
    "$1" -version 2>&1 | head -n1 | sed -E 's/.*"([0-9]+).*/\1/' 2>/dev/null || echo 0
}

find_java_home() {
    # 1) JAVA_HOME
    if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ] \
       && [ "$(java_major "$JAVA_HOME/bin/java")" -ge "$REQUIRED" ]; then
        echo "$JAVA_HOME"; return
    fi
    # 2) java on PATH
    if command -v java >/dev/null 2>&1 \
       && [ "$(java_major "$(command -v java)")" -ge "$REQUIRED" ]; then
        # resolve …/bin/java -> home
        local exe; exe="$(command -v java)"
        exe="$(readlink -f "$exe" 2>/dev/null || echo "$exe")"
        echo "$(dirname "$(dirname "$exe")")"; return
    fi
    # 3) previously provisioned
    if [ -d "$JDK_DIR" ]; then
        local home
        for home in "$JDK_DIR"/*/ "$JDK_DIR"/*/Contents/Home/; do
            if [ -x "${home}bin/java" ] && [ "$(java_major "${home}bin/java")" -ge "$REQUIRED" ]; then
                echo "${home%/}"; return
            fi
        done
    fi
    echo ""
}

fetch() { # url -> file
    if command -v curl >/dev/null 2>&1; then curl -fsSL -o "$2" "$1"
    elif command -v wget >/dev/null 2>&1; then wget -qO "$2" "$1"
    else echo "Need curl or wget to download a JDK." >&2; exit 1
    fi
}

JAVA_HOME_RESOLVED="$(find_java_home)"
if [ -z "$JAVA_HOME_RESOLVED" ]; then
    case "$(uname -s)" in
        Linux)  OS=linux ;;
        Darwin) OS=mac ;;
        *) echo "Unsupported OS: $(uname -s). Install a JDK $REQUIRED+ manually." >&2; exit 1 ;;
    esac
    case "$(uname -m)" in
        x86_64|amd64)  ARCH=x64 ;;
        aarch64|arm64) ARCH=aarch64 ;;
        *) echo "Unsupported arch: $(uname -m). Install a JDK $REQUIRED+ manually." >&2; exit 1 ;;
    esac
    echo "No JDK $REQUIRED+ found — downloading Eclipse Temurin $TEMURIN ($OS/$ARCH) to .jdk/ ..."
    URL="https://api.adoptium.net/v3/binary/latest/$TEMURIN/ga/$OS/$ARCH/jdk/hotspot/normal/eclipse"
    TAR="$(mktemp -d 2>/dev/null || echo /tmp)/temurin-$$.tar.gz"
    fetch "$URL" "$TAR"
    mkdir -p "$JDK_DIR"
    tar -xzf "$TAR" -C "$JDK_DIR"
    rm -f "$TAR"
    JAVA_HOME_RESOLVED="$(find_java_home)"
    [ -n "$JAVA_HOME_RESOLVED" ] || { echo "JDK download or extraction failed." >&2; exit 1; }
    echo "JDK provisioned at $JAVA_HOME_RESOLVED"
fi

export JAVA_HOME="$JAVA_HOME_RESOLVED"
export PATH="$JAVA_HOME/bin:$PATH"
echo "Using JDK: $JAVA_HOME"

exec "$ROOT/mvnw" -q javafx:run "$@"

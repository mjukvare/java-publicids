#!/usr/bin/env bash
#
# Cleans, builds and tests every module with JDK 21.
#
# Usage: ./build.sh [phase] [extra Maven arguments]
#
# The phase defaults to verify (compile, test, and package the jars). Name a later phase to go further, e.g.
# ./build.sh install. Extra arguments go straight to Maven, e.g. ./build.sh -Dtest=PublicIdsTest

set -euo pipefail

cd "$(dirname "$0")"

fail() {
    echo "build: $*" >&2
    exit 1
}

phase=verify
if [[ $# -gt 0 && $1 != -* ]]; then
    phase=$1
    shift
fi

# Always build with JDK 21, whatever the shell defaults to. JAVA_HOME_21 (set by CI images such as GitHub Actions)
# wins; on macOS java_home finds an installed one.
if [[ -n "${JAVA_HOME_21:-}" ]]; then
    JAVA_HOME=$JAVA_HOME_21
elif [[ -x /usr/libexec/java_home ]]; then
    JAVA_HOME=$(/usr/libexec/java_home -v 21 2>/dev/null) || fail "no JDK 21 installed"
fi
[[ -n "${JAVA_HOME:-}" ]] || fail "JAVA_HOME isn't set; point it or JAVA_HOME_21 at a JDK 21"
export JAVA_HOME

java_version=$("$JAVA_HOME/bin/java" -version 2>&1 | head -n 1)
[[ "$java_version" =~ \"21[.\"] ]] || fail "JAVA_HOME ($JAVA_HOME) isn't JDK 21: $java_version"

echo "Building with $java_version"

./mvnw -B clean "$phase" "$@"

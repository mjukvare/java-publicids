#!/usr/bin/env bash
#
# Signs and uploads the release tagged on HEAD to Maven Central.
#
# Usage: ./release.sh [extra Maven arguments]
#
# HEAD must carry exactly one tag of the form vX.Y.Z, and the working tree must be clean. The version is taken from
# the tag. Extra arguments go straight to Maven, e.g. ./release.sh -Dgpg.keyname=<KEY_ID>

set -euo pipefail

cd "$(dirname "$0")"

fail() {
    echo "release: $*" >&2
    exit 1
}

git rev-parse --verify --quiet HEAD >/dev/null || fail "no commits yet"

# Staged, unstaged and untracked (non-ignored) files would all end up in, or differ from, what the tag describes
if [[ -n "$(git status --porcelain)" ]]; then
    git status --short >&2
    fail "working tree has uncommitted changes; commit or stash them first"
fi

tags=()
while IFS= read -r tag; do
    [[ -n "$tag" ]] && tags+=("$tag")
done < <(git tag --points-at HEAD --list 'v*')

case ${#tags[@]} in
    0) fail "HEAD has no v* tag; tag the release first, e.g. git tag v1.2.3" ;;
    1) tag=${tags[0]} ;;
    *) fail "HEAD has several v* tags (${tags[*]}); keep only one" ;;
esac

version=${tag#v}
[[ "$version" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || fail "tag $tag isn't a release version of the form vX.Y.Z"

echo "Releasing $version from tag $tag ($(git rev-parse --short HEAD))"

# Runs the same build and tests as ./build.sh, carried on through signing and upload in one pass
./build.sh deploy -P release -Drevision="$version" "$@"

cat <<EOF

Uploaded $version to the Central Portal. To finish the release:
  1. Check the deployment at https://central.sonatype.com/publishing/deployments and press Publish.
  2. Push the tag: git push origin $tag
EOF

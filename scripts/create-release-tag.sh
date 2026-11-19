#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 1 ]]; then
    echo "Usage: $0 <version>" >&2
    exit 2
fi

version="$1"
script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd -- "$script_dir/.." && pwd)"
cd "$repo_root"

fail() {
    echo "error: $*" >&2
    exit 1
}

[[ "$(tr -d '[:space:]' < version.txt)" == "$version" ]] || \
    fail "version.txt does not contain $version"
[[ -f CHANGELOG.md ]] || fail "CHANGELOG.md is missing; merge the release pull request first"
grep -Fq "## [$version]" CHANGELOG.md || \
    fail "CHANGELOG.md has no entry for $version"

tag="v$version"
git fetch --tags origin
head="$(git rev-parse HEAD)"

if git rev-parse -q --verify "refs/tags/$tag" >/dev/null; then
    tagged="$(git rev-list -n 1 "$tag")"
    [[ "$tagged" == "$head" ]] || \
        fail "tag $tag points to $tagged, but HEAD is $head"
    echo "tag $tag already points to HEAD"
    exit 0
fi

git -c user.name="${GIT_AUTHOR_NAME:-cinpo-release}" \
    -c user.email="${GIT_AUTHOR_EMAIL:-cinpo-release@users.noreply.github.com}" \
    tag -a "$tag" -m "CINPO $version"
git push origin "$tag"

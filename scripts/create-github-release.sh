#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 2 ]]; then
    echo "Usage: $0 <version> <checksums-file>" >&2
    exit 2
fi

version="$1"
checksums_file="$2"

fail() {
    echo "error: $*" >&2
    exit 1
}

[[ -f "$checksums_file" ]] || fail "$checksums_file is missing"

tag="v$version"
notes_file="$(mktemp)"
cleanup() {
    rm -f "$notes_file"
}
trap cleanup EXIT

if [[ -f CHANGELOG.md ]]; then
    awk -v ver="$version" '
        index($0, "## [" ver "]") == 1 { found = 1; next }
        found && index($0, "## [") == 1 { exit }
        found { print }
    ' CHANGELOG.md > "$notes_file"
fi

if [[ ! -s "$notes_file" ]]; then
    printf 'CINPO %s\n' "$version" > "$notes_file"
fi

extra_args=()
if [[ "$version" == *-* ]]; then
    extra_args+=(--prerelease)
fi

if gh release view "$tag" >/dev/null 2>&1; then
    gh release edit "$tag" --notes-file "$notes_file" "${extra_args[@]}"
else
    gh release create "$tag" \
        --title "CINPO $version" \
        --notes-file "$notes_file" \
        --verify-tag \
        "${extra_args[@]}"
fi

gh release upload "$tag" "$checksums_file" --clobber

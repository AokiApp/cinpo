#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 1 ]]; then
    echo "Usage: $0 <checksums-file>" >&2
    exit 2
fi

checksums_file="$1"
if [[ ! -f "$checksums_file" ]]; then
    echo "error: $checksums_file is missing" >&2
    exit 1
fi

: "${GITHUB_ACTOR:?GITHUB_ACTOR must be set}"
: "${GITHUB_TOKEN:?GITHUB_TOKEN must be set}"

repository_url="${GITHUB_PACKAGES_REPOSITORY_URL:-https://maven.pkg.github.com/AokiApp/cinpo}"
failed=0

while read -r expected path; do
    [[ -n "$expected" && -n "$path" ]] || continue

    if ! actual="$(curl \
        --silent \
        --show-error \
        --fail \
        --location \
        --user "${GITHUB_ACTOR}:${GITHUB_TOKEN}" \
        "${repository_url%/}/${path}" \
        | sha256sum \
        | awk '{print $1}')"; then
        echo "missing: ${path}" >&2
        failed=1
        continue
    fi

    if [[ "$actual" != "$expected" ]]; then
        echo "mismatch: ${path}" >&2
        failed=1
    fi
done < "$checksums_file"

if [[ $failed -ne 0 ]]; then
    exit 1
fi

echo "published checksums match"

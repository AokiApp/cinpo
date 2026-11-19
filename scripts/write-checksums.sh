#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 1 ]]; then
    echo "Usage: $0 <repository-directory>" >&2
    exit 2
fi

repository_dir="$1"
if [[ ! -d "$repository_dir" ]]; then
    echo "error: $repository_dir is not a directory" >&2
    exit 1
fi

cd "$repository_dir"
find . -type f \( -name '*.pom' -o -name '*.module' -o -name '*.jar' \) -printf '%P\0' \
    | sort -z \
    | xargs -0 sha256sum

#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 2 ]]; then
    echo "Usage: $0 <version> <staging-directory>" >&2
    exit 2
fi

version="$1"
staging_dir="$2"

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd -- "$script_dir/.." && pwd)"

fail() {
    echo "error: $*" >&2
    exit 1
}

[[ -d "$staging_dir" ]] || fail "$staging_dir is not a directory"

work_dir="$(mktemp -d)"
cleanup() {
    rm -rf "$work_dir"
}
trap cleanup EXIT

"$repo_root/scripts/bootstrap.sh" "$work_dir/project"

template_version="$(sed -n 's/^cinpo = "\([^"]*\)".*/\1/p' "$work_dir/project/gradle/libs.versions.toml")"
[[ "$template_version" == "$version" ]] || \
    fail "template pins $template_version, expected $version"

if [[ -d "$repo_root/vendor" ]]; then
    cp -a "$repo_root/vendor" "$work_dir/project/vendor"
fi

cd "$work_dir/project"
repository_uri="file://$(cd "$staging_dir" && pwd)"

./gradlew --no-daemon -PcinpoRepository="$repository_uri" \
    cinpoRun --args='--debug --test'
./gradlew --no-daemon -PcinpoRepository="$repository_uri" cinpoJar

java -jar build/libs/cinpo-appliance.jar write --test
java -p build/libs/cinpo-appliance.jar \
    -m app.aoki.cinpo/app.aoki.cinpo.cli.CinpoCli --help

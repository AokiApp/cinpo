#!/usr/bin/env bash
set -euo pipefail

# Packages every starter under template/ as a versioned archive.
# Output: <output-directory>/<version>/<template>/template.tar.gz

usage() {
    echo "Usage: $0 <output-directory>" >&2
}

fail() {
    echo "error: $*" >&2
    exit 1
}

if [[ $# -ne 1 ]]; then
    usage
    exit 2
fi

output_dir="$1"

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd -- "$script_dir/.." && pwd)"
cd "$repo_root"

[[ -f version.txt ]] || fail "version.txt is missing"
version="$(tr -d '[:space:]' < version.txt)"
[[ -n "$version" ]] || fail "version.txt is empty"

[[ -d template ]] || fail "template/ is missing"

if [[ -n "$(git status --porcelain -- template/)" ]]; then
    fail "template/ has uncommitted or untracked changes; commit them before packaging"
fi

required_files=(
    .gitignore
    AGENTS.md
    README.md
    build.gradle
    settings.gradle
    manifest.yaml
    gradlew
    gradlew.bat
    gradle/libs.versions.toml
    gradle/wrapper/gradle-wrapper.jar
    gradle/wrapper/gradle-wrapper.properties
)

reference_wrapper=""
template_count=0

for template_path in template/*/; do
    [[ -d "$template_path" ]] || continue
    template_dir="${template_path%/}"
    template_name="$(basename "$template_dir")"

    for required_file in "${required_files[@]}"; do
        [[ -f "$template_dir/$required_file" ]] || \
            fail "$template_name: missing $required_file"
    done

    grep -qx "rootProject.name = 'cinpo-template'" "$template_dir/settings.gradle" || \
        fail "$template_name: settings.gradle does not declare rootProject.name = 'cinpo-template'"

    tracked_forbidden="$(git ls-files -- \
        "$template_dir/vendor" \
        "$template_dir/vendor.zip" \
        "$template_dir/build" \
        "$template_dir/.gradle")"
    [[ -z "$tracked_forbidden" ]] || \
        fail "$template_name: tracked vendor/build paths must not be packaged: $tracked_forbidden"

    catalog_version="$(sed -n 's/^cinpo = "\([^"]*\)".*/\1/p' "$template_dir/gradle/libs.versions.toml")"
    [[ "$catalog_version" == "$version" ]] || \
        fail "$template_name: cinpo version is '$catalog_version', expected '$version'"

    gradlew_mode="$(git ls-files -s -- "$template_dir/gradlew" | awk '{print $1}')"
    [[ "$gradlew_mode" == "100755" ]] || \
        fail "$template_name: gradlew must be executable in the git index (mode 100755)"

    wrapper_properties="$template_dir/gradle/wrapper/gradle-wrapper.properties"
    if [[ -z "$reference_wrapper" ]]; then
        reference_wrapper="$wrapper_properties"
    elif ! cmp -s "$reference_wrapper" "$wrapper_properties"; then
        fail "gradle-wrapper.properties differs between templates: $reference_wrapper and $wrapper_properties"
    fi

    template_count=$((template_count + 1))
done

[[ "$template_count" -gt 0 ]] || fail "no templates found under template/"

mkdir -p "$output_dir"
output_dir="$(cd -- "$output_dir" && pwd)"
rm -rf -- "$output_dir/$version"

for template_path in template/*/; do
    [[ -d "$template_path" ]] || continue
    template_dir="${template_path%/}"
    template_name="$(basename "$template_dir")"

    target_dir="$output_dir/$version/$template_name"
    archive="$target_dir/template.tar.gz"
    mkdir -p "$target_dir"

    git archive --format=tar.gz --output="$archive" "HEAD:template/$template_name"

    entries="$(tar -tzf "$archive")"

    if printf '%s\n' "$entries" | grep -Eq '^template(/|$)'; then
        fail "$template_name: archive contains a template/ prefix"
    fi

    for required_file in "${required_files[@]}"; do
        printf '%s\n' "$entries" | grep -qxF "$required_file" || \
            fail "$template_name: archive is missing $required_file"
    done

    if printf '%s\n' "$entries" | \
        grep -Eq '^(vendor(/|$)|vendor\.zip$|build(/|$)|\.gradle(/|$))'; then
        fail "$template_name: archive contains a forbidden path"
    fi

    printf '%s\n' "$entries" | grep -Eq '^applet/' || \
        fail "$template_name: archive has no applet/ entries"
    printf '%s\n' "$entries" | grep -Eq '^host/' || \
        fail "$template_name: archive has no host/ entries"

    echo "$archive"
done

#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root"

fail() {
    echo "error: $*" >&2
    exit 1
}

for command_name in python3 jq; do
    command -v "$command_name" >/dev/null 2>&1 || fail "$command_name is required"
done

[[ -f version.txt ]] || fail "version.txt is missing"

version="$(tr -d '[:space:]' < version.txt)"
if [[ ! "$version" =~ ^[0-9]+\.[0-9]+\.[0-9]+([.-][0-9A-Za-z][0-9A-Za-z.-]*)?$ ]]; then
    fail "version.txt does not contain a semver version: $version"
fi

manifest_version="$(python3 - <<'PY'
import json

with open(".release-please-manifest.json", encoding="utf-8") as handle:
    print(json.load(handle)["."])
PY
)"
[[ "$manifest_version" == "$version" ]] || \
    fail ".release-please-manifest.json has $manifest_version, expected $version"

catalogs=("gradle/libs.versions.toml")
for template_dir in template/*/; do
    catalogs+=("${template_dir%/}/gradle/libs.versions.toml")
done

for catalog in "${catalogs[@]}"; do
    [[ -f "$catalog" ]] || fail "$catalog is missing"
    catalog_version="$(sed -n 's/^cinpo = "\([^"]*\)".*/\1/p' "$catalog")"
    [[ -n "$catalog_version" ]] || fail "$catalog does not declare a cinpo version"
    [[ "$catalog_version" == "$version" ]] || \
        fail "$catalog has $catalog_version, expected $version"
done

grep -q 'libs.versions.cinpo' build.gradle || \
    fail "build.gradle does not read the cinpo version from the version catalog"
if grep -Eq "^[[:space:]]*version[[:space:]]*=[[:space:]]*'[0-9]+\.[0-9]+\.[0-9]+" build.gradle; then
    fail "build.gradle still contains a hard-coded project version"
fi

if grep -Eq "id 'app\\.aoki\\.cinpo' version" template/*/build.gradle; then
    fail "a template pins the Gradle plugin version directly"
fi
if grep -Eq "app\\.aoki\\.cinpo:(cinpo|cinpo-runtime-[a-z0-9-]+):[0-9]+\\.[0-9]+\\.[0-9]+" template/*/build.gradle; then
    fail "a template pins a CINPO dependency version directly"
fi

if grep -Eq "app\\.aoki\\.cinpo:(cinpo|cinpo-runtime-[a-z0-9-]+):[0-9]+\\.[0-9]+\\.[0-9]+" README.md; then
    fail "README.md pins a CINPO dependency version"
fi
if grep -Eq "app\\.aoki\\.cinpo:cinpo:[0-9]+\\.[0-9]+\\.[0-9]+" \
    cinpo-gradle-plugin/src/main/java-gradle/app/aoki/cinpo/gradle/CinpoGradleSupport.java; then
    fail "CinpoGradleSupport.java pins a CINPO version in its error message"
fi

while IFS= read -r extra_file; do
    [[ -f "$extra_file" ]] || fail "release-please-config.json references missing $extra_file"
done < <(jq -r '.packages["."]["extra-files"][] | select(type == "object") | .path' release-please-config.json)

echo "version $version is consistent"

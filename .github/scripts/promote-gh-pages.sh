#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 4 ]]; then
    echo "Usage: $0 <version> <staging-directory> <checksums-file> <templates-directory>" >&2
    exit 2
fi

version="$1"
staging_dir="$2"
checksums_file="$3"
templates_dir="$4"

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd -- "$script_dir/../.." && pwd)"
cd "$repo_root"

fail() {
    echo "error: $*" >&2
    exit 1
}

[[ "$(tr -d '[:space:]' < version.txt)" == "$version" ]] || \
    fail "version.txt does not contain $version"
[[ -d "$staging_dir" ]] || fail "$staging_dir is not a directory"
[[ -f "$checksums_file" ]] || fail "$checksums_file is missing"
[[ -d "$templates_dir/$version" ]] || fail "$templates_dir/$version is not a directory"

pages_dir="$(mktemp -d)"
rmdir "$pages_dir"

cleanup() {
    git worktree remove --force "$pages_dir" >/dev/null 2>&1 || true
}
trap cleanup EXIT

if git ls-remote --exit-code --heads origin gh-pages >/dev/null 2>&1; then
    git fetch origin gh-pages
    git worktree add --detach "$pages_dir" refs/remotes/origin/gh-pages
    git -C "$pages_dir" switch -C gh-pages
else
    git worktree add --detach "$pages_dir" HEAD
    git -C "$pages_dir" branch -D gh-pages >/dev/null 2>&1 || true
    git -C "$pages_dir" switch --orphan gh-pages
    git -C "$pages_dir" rm -rf . >/dev/null 2>&1 || true
fi

maven_promoted=0
if [[ -e "$pages_dir/app/aoki/cinpo/cinpo/$version" ]]; then
    if [[ -f "$pages_dir/checksums-${version}.txt" ]] && \
        cmp -s "$checksums_file" "$pages_dir/checksums-${version}.txt"; then
        maven_promoted=1
    else
        fail "version $version already exists in gh-pages with different checksums"
    fi
fi

templates_promoted=0
if [[ -d "$pages_dir/templates/$version" ]]; then
    if diff -r "$templates_dir/$version" "$pages_dir/templates/$version" >/dev/null; then
        templates_promoted=1
    else
        fail "templates for version $version already exist in gh-pages with different contents"
    fi
fi

if [[ "$maven_promoted" -eq 1 && "$templates_promoted" -eq 1 ]]; then
    echo "version $version is already promoted"
    exit 0
fi

if [[ "$maven_promoted" -eq 0 ]]; then
    cp -a "$staging_dir"/. "$pages_dir"/
fi

if [[ "$templates_promoted" -eq 0 ]]; then
    mkdir -p "$pages_dir/templates"
    cp -a "$templates_dir/$version" "$pages_dir/templates/"
fi
install -m 755 "$repo_root/scripts/bootstrap.sh" "$pages_dir/bootstrap.sh"
install -m 644 "$repo_root/scripts/bootstrap.ps1" "$pages_dir/bootstrap.ps1"

sed -i "s|^default_ref=\"main\"$|default_ref=\"v${version}\"|" "$pages_dir/bootstrap.sh"
grep -q "^default_ref=\"v${version}\"$" "$pages_dir/bootstrap.sh" || \
    fail "failed to pin bootstrap.sh to v${version}"

sed -i "s|^\\\$DefaultRef = 'main'$|\$DefaultRef = 'v${version}'|" "$pages_dir/bootstrap.ps1"
grep -q "^\\\$DefaultRef = 'v${version}'$" "$pages_dir/bootstrap.ps1" || \
    fail "failed to pin bootstrap.ps1 to v${version}"

cp "$checksums_file" "$pages_dir/checksums.txt"
cp "$checksums_file" "$pages_dir/checksums-${version}.txt"

git -C "$pages_dir" add -A
if git -C "$pages_dir" diff --cached --quiet; then
    echo "no changes to gh-pages"
    exit 0
fi

git -C "$pages_dir" \
    -c user.name="${GIT_AUTHOR_NAME:-cinpo-release}" \
    -c user.email="${GIT_AUTHOR_EMAIL:-cinpo-release@users.noreply.github.com}" \
    commit -m "docs: publish ${version}"
git -C "$pages_dir" push origin HEAD:gh-pages

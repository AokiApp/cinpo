#!/usr/bin/env bash
set -euo pipefail

usage() {
    cat <<'USAGE' >&2
Usage: scripts/bump-version.sh [new-version]

Updates the CINPO version in:
  - build.gradle
  - every template/*/build.gradle file

If new-version is omitted, increments the current root project patch version.
After updating files, runs git add, git commit, and git push.

Examples:
  scripts/bump-version.sh
  scripts/bump-version.sh 0.3.0
USAGE
}

if [[ $# -gt 1 ]]; then
    usage
    exit 2
fi

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd -- "$script_dir/.." && pwd)"
cd "$repo_root"

new_version="${1:-}"

if [[ -n "$new_version" && ! "$new_version" =~ ^[0-9]+\.[0-9]+\.[0-9]+([.-][0-9A-Za-z][0-9A-Za-z.-]*)?$ ]]; then
    echo "error: version must look like 1.2.3, optionally followed by a suffix such as -SNAPSHOT" >&2
    exit 2
fi

python3 - "$new_version" <<'PY'
from pathlib import Path
import re
import sys

new_version = sys.argv[1]

root_build = Path("build.gradle")
root_text = root_build.read_text()
root_version_match = re.search(r"(?m)^version = '([0-9]+)\.([0-9]+)\.([0-9]+)'$", root_text)

if not new_version:
    if not root_version_match:
        raise SystemExit("error: failed to read current plain semver root project version from build.gradle")

    major, minor, patch = (int(part) for part in root_version_match.groups())
    new_version = f"{major}.{minor}.{patch + 1}"
    print(f"bumping patch version to {new_version}")

template_replacements = [
    (
        re.compile(r"(?m)^(\s*id 'app\.aoki\.cinpo\.gradle' version )'[^']+'$"),
        rf"\g<1>'{new_version}'",
        "template Gradle plugin version",
    ),
    (
        re.compile(r"(?m)^(\s*implementation 'app\.aoki\.cinpo:cinpo:)[^']+'$"),
        rf"\g<1>{new_version}'",
        "template runtime dependency version",
    ),
]

updates = {
    Path("build.gradle"): [
        (
            re.compile(r"(?m)^version = '[^']+'$"),
            f"version = '{new_version}'",
            "root project version",
        ),
    ],
}

for template_build in sorted(Path("template").glob("*/build.gradle")):
    updates[template_build] = template_replacements

changed_files = []

for path, replacements in updates.items():
    text = path.read_text()
    original_text = text

    for pattern, replacement, description in replacements:
        text, count = pattern.subn(replacement, text, count=1)
        if count != 1:
            raise SystemExit(f"error: failed to update {description} in {path}")

    if text != original_text:
        path.write_text(text)
        changed_files.append(path)

for path in changed_files:
    print(f"updated {path}")
PY

committed_version="$(python3 - <<'PY'
from pathlib import Path
import re

text = Path("build.gradle").read_text()
match = re.search(r"(?m)^version = '([^']+)'$", text)
if not match:
    raise SystemExit("error: failed to read updated root project version from build.gradle")
print(match.group(1))
PY
)"

git add -- build.gradle template/*/build.gradle

if git diff --cached --quiet; then
    echo "no version changes to commit"
    exit 0
fi

git commit -m "Bump version to ${committed_version}"
git push
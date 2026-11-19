#!/usr/bin/env python3
"""Regenerate maven-metadata.xml files for the CINPO Pages repository.

The release workflow appends one version at a time. Gradle writes metadata for
only the version it just published, so the promotion step rebuilds the metadata
from the version directories that are present on the gh-pages branch.
"""

from __future__ import annotations

import re
import sys
import xml.etree.ElementTree as ET
from datetime import datetime, timezone
from pathlib import Path

GROUP_ID = "app.aoki.cinpo"
VERSION_PATTERN = re.compile(r"^\d+\.\d+\.\d+(?:-[0-9A-Za-z][0-9A-Za-z.-]*)?$")


def version_key(version: str) -> tuple[int, int, int, int, str]:
    match = re.match(r"^(\d+)\.(\d+)\.(\d+)(?:-(.*))?$", version)
    if not match:
        return (0, 0, 0, 0, version)

    major, minor, patch, prerelease = match.groups()
    return (
        int(major),
        int(minor),
        int(patch),
        0 if prerelease else 1,
        prerelease or "",
    )


def write_metadata(artifact_dir: Path, versions: list[str]) -> None:
    latest = versions[-1]
    stable = [version for version in versions if "-" not in version]
    release = stable[-1] if stable else latest

    metadata = ET.Element("metadata")
    ET.SubElement(metadata, "groupId").text = GROUP_ID
    ET.SubElement(metadata, "artifactId").text = artifact_dir.name

    versioning = ET.SubElement(metadata, "versioning")
    ET.SubElement(versioning, "latest").text = latest
    ET.SubElement(versioning, "release").text = release

    versions_element = ET.SubElement(versioning, "versions")
    for version in versions:
        ET.SubElement(versions_element, "version").text = version

    ET.SubElement(versioning, "lastUpdated").text = datetime.now(
        timezone.utc
    ).strftime("%Y%m%d%H%M%S")

    tree = ET.ElementTree(metadata)
    ET.indent(tree, space="  ")
    output = artifact_dir / "maven-metadata.xml"
    tree.write(output, encoding="utf-8", xml_declaration=True)
    output.write_text(output.read_text(encoding="utf-8") + "\n", encoding="utf-8")


def main() -> int:
    if len(sys.argv) != 2:
        print(f"Usage: {sys.argv[0]} <repository-directory>", file=sys.stderr)
        return 2

    repository = Path(sys.argv[1])
    group_dir = repository / "app" / "aoki" / "cinpo"
    if not group_dir.is_dir():
        print(f"error: {group_dir} is missing", file=sys.stderr)
        return 1

    for artifact_dir in sorted(group_dir.iterdir()):
        if not artifact_dir.is_dir():
            continue

        versions = sorted(
            (
                child.name
                for child in artifact_dir.iterdir()
                if child.is_dir() and VERSION_PATTERN.match(child.name)
            ),
            key=version_key,
        )
        if not versions:
            continue

        write_metadata(artifact_dir, versions)
        print(f"updated {artifact_dir.name}: {', '.join(versions)}")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())

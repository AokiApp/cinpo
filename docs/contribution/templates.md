# Template distribution

Templates are published on Pages alongside the Maven repository. Each release
packages every directory under `template/` with `scripts/package-templates.sh`:

```
templates/<version>/<template>/template.tar.gz
```

The file name is always `template.tar.gz`; the version and the template name are
the directories. The script builds the archive with
`git archive HEAD:template/<name>`, so the archive matches the tagged tree, and
validates the required files, the version catalog, the absence of vendor and
build paths, and the Gradle wrapper.

`promote-gh-pages.sh` copies `build/templates/` to `gh-pages`. When
`templates/<version>/` already exists, it compares the staged and published
bytes with `diff -r`; an identical directory is already promoted, and any
difference fails. The Maven promotion and the template promotion are checked
separately, so a retry can complete the missing side.

The Pages copy of `bootstrap.sh` / `bootstrap.ps1` carries the release version in
`default_version`. The bootstrap scripts download the archive for that version,
treat HTTP 404 as "not published for this version", and extract it with `tar`.
They no longer fetch the repository or the Gradle wrapper with git. Generated
projects are still initialized as git repositories.

The bootstrap only downloads release archives; there is no local template mode.
To test a template change end to end, package it with
`scripts/package-templates.sh`, serve the output locally, and pass
`--version <version>` with `CINPO_TEMPLATE_BASE_URL` pointing at the server. A
template can also be built directly from a copy of its directory, because every
starter ships its own Gradle wrapper. The templates accept
`-PcinpoRepository=<uri>` to resolve CINPO artifacts from a local Maven
repository instead of the public one.

The template archives are published only on Pages; they are not attached to the
GitHub Release and do not have checksum files or attestations. Corrupted
transfers are detected by tar and gzip.

## Verification after a release

After a release, check the pinned bootstrap version and one archive:

```bash
git show origin/gh-pages:bootstrap.sh | grep '^default_version='
curl -I https://AokiApp.github.io/cinpo/templates/0.5.1/basic/template.tar.gz
```

The version in `default_version` must match the release, and the archive URL must
return 200.

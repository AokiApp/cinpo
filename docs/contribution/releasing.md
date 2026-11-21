# Releasing CINPO

## Quick checklist

1. Merge normal pull requests into `main`. Release Please opens or updates a
   release pull request.
2. Run CI manually on the release branch. Release Please creates the pull
   request with `GITHUB_TOKEN`, so GitHub does not start CI for it.
3. Merge the release pull request. Squash merge is recommended.
4. Run the Release workflow with the version from the release pull request.
5. Verify the tag, GitHub Release, GitHub Packages, and Pages.

## Prerequisites

- The `VENDOR_PASSWORD` repository secret, used to unpack the Oracle Java Card
  archives during the build.
- The `release` environment. It can have protection rules; without them the
  workflow starts immediately.
- The Release workflow declares the `GITHUB_TOKEN` permissions it needs. No
  personal access token is required.
- For automatic CI on release pull requests, replace the token in
  `.github/workflows/release-please.yml` with a fine-grained personal access
  token stored as `RELEASE_PLEASE_TOKEN`. It needs Contents and Pull requests
  write access. The default workflow uses `GITHUB_TOKEN` and manual CI.

## Version sources

`version.txt` is Release Please's record of the current version. The build reads
the version from `gradle/libs.versions.toml`, and each starter template has its
own `gradle/libs.versions.toml`. `scripts/check-version.sh` fails when these
files disagree, when a template pins a CINPO version directly, or when the
README or an error message contains a hard-coded CINPO version.

To change the version by hand, update `version.txt`, the four version catalogs,
and `.release-please-manifest.json` together. Release Please normally does this
in its release pull request.

## Release Please

`.github/workflows/release-please.yml` runs on every push to `main`. It opens or
updates a release pull request containing:

- the next version in `version.txt` and the four `gradle/libs.versions.toml` files
- the matching entry in `CHANGELOG.md`

The configuration and the action input both set `skip-github-release: true`, so
Release Please does not create a tag or a GitHub Release. The release workflow
tags the verified commit instead. This keeps tagging behind the build.

The workflow uses `GITHUB_TOKEN`. GitHub does not start CI for pull requests
created with `GITHUB_TOKEN`, so run the CI workflow manually for the release
branch:

```bash
gh workflow run ci.yml --ref release-please--branches--main--components--cinpo
```

The branch name is shown on the release pull request. Merge the release pull
request with a squash merge when possible. A merge commit and the original
commit can both appear in the changelog when their messages match.

The `bootstrap-sha` setting in `release-please-config.json` was only needed for
the first release pull request. It has been removed now that 0.5.1 is tagged.

## Version rules

CINPO uses Semantic Versioning. While the major version is `0`:

- a breaking change bumps the minor version (`bump-minor-pre-major`)
- a feature bumps the minor version
- a bug fix bumps the patch version

For a release candidate, set the version explicitly with a `Release-As:` footer
on a commit, for example `Release-As: 0.5.2-rc.1`, or configure Release Please's
prerelease settings. The Release workflow marks a version containing `-` as a
prerelease.

Tags start at `v0.5.1`. The 0.5.1 changelog links to `v0.5.0` for comparison, but
0.5.0 was not tagged, so that link does not resolve.

## Publishing

Merge the release pull request first. The workflow refuses to run when
`CHANGELOG.md` has no section for the requested version.

Run the Release workflow immediately after the merge. The tag points to the
current `main` commit, so a later commit would be included in the release.

```bash
gh workflow run release.yml -f version=0.5.1
```

The `release` job performs these steps:

1. verify `version.txt`, the `main` branch, `CHANGELOG.md`, and the tag if it
   already exists
2. run `./gradlew clean check`
3. build `build/staging-maven-repository` and `build/checksums.txt`
4. package `build/templates/<version>/<template>/template.tar.gz`
5. create the annotated tag `v0.5.1`
6. publish the six artifacts to GitHub Packages
7. compare the published checksums with the staged files
8. copy the staged files and the template archives to `gh-pages` without
   removing older versions
9. create the GitHub Release with `checksums.txt`

The `deploy-pages` job deploys the `gh-pages` branch. `gh-pages` is the
cumulative repository and was seeded with 0.5.0. Every version directory is
kept, but `maven-metadata.xml` is copied from the latest release. CINPO pins
exact versions, so version ranges are not supported.

The templates accept `-PcinpoRepository=<uri>` to resolve CINPO artifacts from
a local Maven repository instead of the public one.

### Failure and retry

- If a step before the tag fails, fix the problem and rerun the same version.
- If the tag exists and publishing fails, rerun the same version. The publish
  script skips artifacts that already exist, and the checksum step verifies the
  bytes that are already in GitHub Packages.
- If the tag points to a different commit, do not move it. Fix the code and
  release a new version.
- After publication, a fix goes into a new patch version. An existing version is
  never republished with different bytes.

### After the release

```bash
gh release view v0.5.1
git show origin/gh-pages:bootstrap.sh | grep '^default_version='
curl -I https://AokiApp.github.io/cinpo/app/aoki/cinpo/cinpo/0.5.1/cinpo-0.5.1.pom
curl -I https://AokiApp.github.io/cinpo/checksums-0.5.1.txt
curl -I https://AokiApp.github.io/cinpo/templates/0.5.1/basic/template.tar.gz
```

Check that GitHub Packages has the core, the three runtimes, the Gradle plugin,
and the plugin marker at the released version. The release workflow already
compares their checksums before it promotes Pages.

The release job attests the staged Maven artifacts. To verify a downloaded artifact:

```bash
gh attestation verify <file> --repo AokiApp/cinpo
```

### Pages recovery

`gh-pages` is a normal branch that stores the cumulative Maven repository, the
template archives, and the bootstrap scripts. If it is lost, recreate it from
the live Pages site or from a release artifact, then let the next release add
its version on top. Do not delete version directories to save space; older
templates rely on them.

## Template distribution

Templates are published on Pages alongside the Maven repository. Each release
packages every directory under `template/` with `scripts/package-templates.sh`:

```
templates/<version>/<template>/template.tar.gz
```

The file name is always `template.tar.gz`; the version and the template name are
the directories. The script builds the archive with
`git archive HEAD:template/<name>`, so the archive matches the tagged tree, and
validates the required files, the version catalog, and the absence of vendor and
build paths.

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

The bootstrap only downloads release archives. To test a template change end to
end, package it with `scripts/package-templates.sh`, serve the output locally,
and pass `--version <version>` with `CINPO_TEMPLATE_BASE_URL` pointing at the
server. A template can also be built directly from a copy of its directory,
because every starter ships its own Gradle wrapper.

The template archives are published only on Pages; they are not attached to the
GitHub Release and do not have checksum files or attestations. Corrupted
transfers are detected by tar and gzip.

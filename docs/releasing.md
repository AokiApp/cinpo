# Releasing CINPO

## Version sources

`version.txt` is Release Please's record of the next version. The build reads
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
tags the verified commit instead. This keeps tagging behind the build and
template checks.

The workflow uses `GITHUB_TOKEN`. GitHub does not start CI for pull requests
created with `GITHUB_TOKEN`, so run the CI workflow manually for the release
branch:

```bash
gh workflow run ci.yml --ref release-please--branches--main--components--cinpo
```

The branch name is shown on the release pull request. A fine-grained personal
access token with Contents and Pull requests write access can replace
`GITHUB_TOKEN` later if automatic checks are wanted.

## Version rules

CINPO uses Semantic Versioning. While the major version is `0`:

- a breaking change bumps the minor version (`bump-minor-pre-major`)
- a feature bumps the minor version
- a bug fix bumps the patch version

Use a suffix such as `-rc.1` for a release candidate.

## Publishing

Run the Release workflow after the release pull request is merged:

```bash
gh workflow run release.yml -f version=0.5.1
```

The workflow runs on `main` and performs these steps in one job:

1. verify `version.txt`, the `main` branch, and the tag if it already exists
2. run `./gradlew clean check`
3. build `build/staging-maven-repository` and `build/checksums.txt`
4. build the `basic` template against that local repository
5. create the annotated tag `v0.5.1`
6. publish the six artifacts to GitHub Packages
7. compare the published checksums with the staged files
8. copy the staged files to `gh-pages` without removing older versions
9. create the GitHub Release

The `deploy-pages` job deploys the `gh-pages` branch. `gh-pages` is the
cumulative repository and was seeded with 0.5.0. Every version directory is
kept, but `maven-metadata.xml` is copied from the latest release. CINPO pins
exact versions, so version ranges are not supported.

A failed release is retried with the same version before anything is published.
After publication, a fix goes into a new patch version; an existing version is
never republished with different bytes.

The templates accept `-PcinpoRepository=<uri>` so the release workflow can
verify against the local staging repository instead of the public one.

## Template distribution

The current `bootstrap.sh` and `bootstrap.ps1` fetch `template/<name>` from the
GitHub repository with a sparse checkout. That is useful during development, but
it is not a release artifact:

- the unversioned Pages bootstrap defaults to `main`, which can be ahead of the
  published framework version
- the generated project depends on `git` and on the repository layout
- the download is not checked against a checksum

The target is to package each template as a versioned archive, including the
Gradle wrapper and its version catalog, publish the archives with checksums on
the release and on Pages, and have the bootstrap scripts download and verify the
archive for a specific release. Until that change lands, the release workflow
pins the Pages copy of the bootstrap script to the release tag so that the
template and the framework version at least match.

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

The configuration sets `skip-github-release: true`, so Release Please does not
create a tag or a GitHub Release. The publish workflow tags the verified commit
instead. This keeps tagging behind the build and template checks.

The workflow uses `RELEASE_PLEASE_TOKEN` when it is configured and falls back to
`GITHUB_TOKEN` otherwise. Configure a fine-grained personal access token with
Contents and Pull requests write access. Without it, Release Please still opens
the release pull request, but GitHub does not start CI for pull requests created
with `GITHUB_TOKEN`, so the release pull request would not run the checks.

## Version rules

CINPO uses Semantic Versioning. While the major version is `0`:

- a breaking change bumps the minor version (`bump-minor-pre-major`)
- a feature bumps the minor version
- a bug fix bumps the patch version

Use a suffix such as `-rc.1` for a release candidate.

## Publishing

The publish workflow is not part of this change yet. It will run for one
explicit version, and it will:

1. verify that `version.txt`, the tag, and the checked-out commit agree
2. run `./gradlew clean check`
3. build a local Maven repository with
   `./gradlew publishAllPublicationsToStagingRepository`
4. verify the `basic` template against that local repository
5. create the annotated tag after the checks pass
6. publish the six artifacts to GitHub Packages in one Gradle run
7. compare the published checksums with the staged files
8. copy the same files to `gh-pages` without removing older versions
9. create the GitHub Release with the changelog and checksums

A failed release is retried with the same version before anything is published.
After publication, a fix goes into a new patch version; an existing version is
never republished with different bytes.

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

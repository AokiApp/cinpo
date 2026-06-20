# CINPO probe template

This is a CINPO starter project for probing card/runtime behavior.

To create a new project from the repository root, run [`bash ./scripts/bootstrap.sh --template probe <destination-directory>`](../../scripts/bootstrap.sh:1). The copied project automatically sets [`rootProject.name`](settings.gradle:27) from the destination directory name, while leaving the other sample identifiers unchanged so you can rename packages, classes, AIDs, and Gradle coordinates yourself.

It contains:

- `applet/MaguroApplet.java`: a passive Java Card applet used as a probe target. It accepts SELECT and rejects every other APDU with `SW_INS_NOT_SUPPORTED`.
- `manifest.yaml`: the probe applet package and AID declaration.
- `profile/jcdksim.yaml`: a local Oracle JCDK simulator profile using development-only test keys.

Run from this project directory after Oracle Java Card vendor files are prepared. If `GITHUB_ACTOR` and `GITHUB_TOKEN` or `gpr.user` and `gpr.key` are available, Gradle resolves CINPO from GitHub Packages first; otherwise it falls back to GitHub Pages:

```bash
../gradlew cinpoRun
../gradlew cinpoRun --args='--test'
../gradlew cinpoJar
```

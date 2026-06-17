# CINPO project template

This is the minimal CINPO starter project.

To create a new project from the repository root, run [`bash ./bootstrap.sh <destination-directory>`](bootstrap.sh:1). The copied project automatically sets [`rootProject.name`](template/settings.gradle:27) from the destination directory name, while leaving the other sample identifiers unchanged so you can rename packages, classes, AIDs, and Gradle coordinates yourself.

It contains:

- `applet/HelloApplet.java`: a tiny Java Card applet with `HELLO` and `ECHO` commands.
- `host/provision/HelloProvision.java`: the default `provision` task. It selects the applet and verifies the `HELLO` command.
- `host/test/HelloTest.java`: the conventional `test` task used by `--test`. It verifies the `ECHO` command.
- `manifest.yaml`: the applet package and AID declaration.
- `profile/jcdksim.yaml`: a local Oracle JCDK simulator profile using development-only test keys.

Run from this project directory after Oracle Java Card vendor files are prepared. If [`GITHUB_ACTOR`](template/README.md:14) and [`GITHUB_TOKEN`](template/README.md:14) or [`gpr.user`](template/README.md:14) and [`gpr.key`](template/README.md:14) are available, Gradle resolves CINPO from GitHub Packages first; otherwise it falls back to GitHub Pages:

```bash
../gradlew cinpoRun
../gradlew cinpoRun --args='--test'
../gradlew cinpoJar
```

# CINPO project template

This is the minimal CINPO starter project.

To create a new project from the repository root, run [`bash ./bootstrap.sh <destination-directory>`](bootstrap.sh:1). The copied project automatically sets [`rootProject.name`](template/settings.gradle:27) from the destination directory name, while leaving the other sample identifiers unchanged so you can rename packages, classes, AIDs, and Gradle coordinates yourself.

It contains:

- `applet/HelloApplet.java`: a tiny Java Card applet with `HELLO`, `ECHO`, and certificate storage commands.
- `host/provision/HelloProvision.java`: the default `provision` task. It issues a leaf X.509 certificate and writes it to the applet.
- `host/provision/CertificateIssuer.java`: a minimal Bouncy Castle-backed certificate issuer for the provision task.
- `host/test/HelloTest.java`: the conventional `test` task used by `--test`. It reads the stored certificate back and parses it as X.509.
- `manifest.yaml`: the applet package and AID declaration.
- `profile/jcdksim.yaml`: a local Oracle JCDK simulator profile using development-only test keys.

Run from this project directory after Oracle Java Card vendor files are prepared. If [`GITHUB_ACTOR`](template/README.md:14) and [`GITHUB_TOKEN`](template/README.md:14) or [`gpr.user`](template/README.md:14) and [`gpr.key`](template/README.md:14) are available, Gradle resolves CINPO from GitHub Packages first; otherwise it falls back to GitHub Pages:

```bash
../gradlew cinpoRun
../gradlew cinpoRun --args='--test'
../gradlew cinpoJar
```

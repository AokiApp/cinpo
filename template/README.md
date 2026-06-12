# CINPO project template

This is the minimal CINPO starter project.

It contains:

- `applet/HelloApplet.java`: a tiny Java Card applet with `HELLO`, `ECHO`, and certificate storage commands.
- `host/provision/HelloProvision.java`: the default `provision` task. It issues a leaf X.509 certificate and writes it to the applet.
- `host/provision/CertificateIssuer.java`: a minimal Bouncy Castle-backed certificate issuer for the provision task.
- `host/test/HelloTest.java`: the conventional `test` task used by `--test`. It reads the stored certificate back and parses it as X.509.
- `manifest.yaml`: the applet package and AID declaration.
- `profile/jcdksim.yaml`: a local Oracle JCDK simulator profile using development-only test keys.

Run from this project directory after GitHub Packages credentials and Oracle Java Card vendor files are prepared:

```bash
../gradlew cinpoRun
../gradlew cinpoRun --args='--test'
../gradlew cinpoJar
```

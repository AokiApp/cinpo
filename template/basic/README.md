# CINPO Application Project

This project was generated from the `basic` starter template.

**CINPO** (**C**ard **IN**stallation and **P**rovision **O**rchestrator), "Spring Boot for Java Card", is a framework for installing and provisioning Java Card applets. You write host-side tasks as ordinary Java classes, annotate them (`@CardTaskDef`), inject what you need (`@Inject`), and CINPO handles manifest validation, CAP compilation/conversion, installation on the JCDK simulator or a real card, APDU transport, and GlobalPlatform secure-channel authentication. No `main()`, build scripts, or ad-hoc Gradle tasks — `./gradlew cinpoRun` does it all. See the [CINPO repository](https://github.com/AokiApp/cinpo) and its [documentation](https://github.com/AokiApp/cinpo/tree/main/docs).

This template shows one flow: the host issues an X.509 leaf certificate, writes it into the applet, then reads it back and verifies it.

## What you get

- `applet/HelloApplet.java` — `ECHO`, `HELLO`, and certificate storage (up to 2048 bytes).
- `host/provision/` — the default `provision` task issues and writes a leaf certificate in 220-byte chunks.
- `host/test/` — the `test` task reads the certificate back and parses it as X.509.
- `profile/jcdksim.yaml` — simulator profile with development-only keys.

## Requirements

- JDK 21 and git.
- Oracle JCDK 26.0 (Tools and the Simulator for your OS) placed in `vendor/`; see [vendor setup](https://github.com/AokiApp/cinpo/blob/main/docs/vendor.md).

## Layout

```
├── applet/                # On-card code (Java Card subset)
├── host/provision/        # @CardTaskDef("provision") — default kind
├── host/test/             # @CardTaskDef("test") — runs with --test
├── manifest.yaml          # Package, applet, and AID declarations
├── profile/jcdksim.yaml   # Simulator profile
├── vendor/                # Oracle JCDK archives (not committed)
└── build.gradle
```

## Run it

```bash
./gradlew cinpoRun                   # install and run provision
./gradlew cinpoRun --args='--test'   # provision, then test
./gradlew cinpoJar                   # self-contained production JAR
```

On Windows use `.\gradlew.bat`. `--test` is not a unit-test runner: it installs the applet, provisions the certificate, then verifies the round-trip.

## Development loop

```bash
./gradlew cinpoRun --args='--test'                   # applet or manifest changed
./gradlew cinpoRun --args='--skip-install --test'    # host-only change
./gradlew cinpoRun --args='--debug --test'           # show APDU traffic
```

If it fails, check `vendor/` first, then the AID/SW of the failing command, then the certificate chunk size and profile.

## Where to change what

| Goal | Change |
|------|--------|
| APDU command | `applet/HelloApplet.java`, then the host constants in `host/` |
| Task kind | `host/<kind>/YourTask.java` with `@CardTaskDef("<kind>")` |
| Package or AIDs | `manifest.yaml` and the `package` declaration in `applet/` |
| Certificate flow | `host/provision/CertificateIssuer.java` and `host/test/HelloTest.java` |
| Real-card profile | `profile/<name>.yaml` with `runtime: pcsc` |

The directory under `host/` is a convention: CINPO indexes `@CardTaskDef` by annotation.

## Verify

`./gradlew cinpoRun --args='--test'` must pass: it installs the applet, provisions the certificate, and verifies the echo and certificate round-trip.

- Assert the status word before the data; assert the exact bytes.
- Change the producer and the test together when a contract changes.
- If the check cannot run here, say so. See `host/test/HelloTest.java` for the assertion shape.

## Make it yours

The sample values are placeholders. They are:

| Placeholder | Where it appears |
|---|---|
| `com.example.cinpo.hello` | applet package, `manifest.yaml` |
| `HelloApplet` | applet class, host comments, this README |
| `F00000000101` | `manifest.yaml` (`package.aid`, `classAid`, `instanceAid`) |
| `CN=CINPO Template Leaf` / `... Test CA` | `CertificateIssuer.java`, `HelloTest.java` |

1. Rename the applet package/class and the matching `manifest.yaml` fields.
2. Replace the AIDs; register your own RID instead of reusing `F00000000101`.
3. Replace the certificate flow and demo commands. For a real card, add `profile/<name>.yaml` with `runtime: pcsc`.
4. Run the verify command, then check for leftovers:

   ```bash
   grep -RIn --exclude-dir=.git --exclude-dir=build --exclude-dir=vendor \
     -e 'HelloApplet' -e 'com.example.cinpo.hello' -e 'F00000000101' .
   ```

## Documentation

- [Writing and testing tasks](https://github.com/AokiApp/cinpo/blob/main/docs/tasks.md)
- [manifest.yaml reference](https://github.com/AokiApp/cinpo/blob/main/docs/manifest.md)
- [Profiles](https://github.com/AokiApp/cinpo/blob/main/docs/profiles.md) — simulator, PC/SC, SCP03
- [Vendor setup](https://github.com/AokiApp/cinpo/blob/main/docs/vendor.md) — JCDK and companion bundles

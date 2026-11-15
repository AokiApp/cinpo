# CINPO Application Project

This project was generated from the `basic` starter template.

**CINPO** (**C**ard **IN**stallation and **P**rovision **O**rchestrator), "Spring Boot for Java Card", is a framework for installing and provisioning Java Card applets. You write host-side tasks as ordinary Java classes, annotate them (`@CardTaskDef`), inject what you need (`@Inject`), and CINPO handles manifest validation, CAP compilation/conversion, installation on the JCDK simulator or a real card, APDU transport, and GlobalPlatform secure-channel authentication. No `main()`, build scripts, or ad-hoc Gradle tasks — `./gradlew cinpoRun` does it all. See the [CINPO repository](https://github.com/AokiApp/cinpo) and its [documentation](https://github.com/AokiApp/cinpo/tree/main/docs).

This template shows one flow: the host issues an X.509 leaf certificate, writes it into the applet, then reads it back and verifies it.

What you get:

- `applet/HelloApplet.java` — a Java Card applet with `ECHO`, `HELLO`, and certificate storage commands (up to 2048 bytes).
- `host/provision/HelloProvision.java` — the default `provision` task. Issues a leaf certificate and writes it to the applet in 220-byte chunks.
- `host/provision/CertificateIssuer.java` — a minimal test-CA certificate issuer (`CN=CINPO Template Test CA` → `CN=CINPO Template Leaf`). Development only.
- `host/test/HelloTest.java` — the conventional `test` task. Reads the certificate back and parses it as X.509.
- `profile/jcdksim.yaml` — simulator profile with development-only test keys.

## Requirements

- **JDK 21** and **git**
- **Oracle Java Card Development Kit (JCDK) 26.0**, which is not redistributed. Download it from [Oracle's Java Card downloads page](https://www.oracle.com/java/technologies/javacard-downloads.html) and place the archives in `vendor/`:

  - Tools: `java_card_devkit_tools-bin-v26.0-b_705-04-MAY-2026.zip`
  - Simulator (Linux): `java_card_devkit_simulator-linux-bin-v26.0-b_788-05-MAY-2026.tar.gz`
  - Simulator (Windows): `java_card_devkit_simulator-win-bin-v26.0-b_788-05-MAY-2026.zip`

  See [vendor setup](https://github.com/AokiApp/cinpo/blob/main/docs/vendor.md). Without these files, `cinpoRun` and `cinpoJar` fail.

## Layout

```
├── applet/                # On-card code (Java Card subset)
├── host/
│   ├── provision/         # @CardTaskDef("provision") — the default kind
│   └── test/              # @CardTaskDef("test") — runs with --test
├── manifest.yaml          # Package, applet and AID declarations
├── profile/jcdksim.yaml   # Simulator profile (development-only test keys)
├── vendor/                # Oracle JCDK archives (not committed)
└── build.gradle
```

## Run it

From this directory:

```bash
./gradlew cinpoRun                   # install the applet, then run provision tasks
./gradlew cinpoRun --args='--test'   # also run the test tasks
./gradlew cinpoJar                   # build the self-contained production JAR
```

On Windows use `.\gradlew.bat` instead of `./gradlew`.

## Development loop

The loop is the same for every change: edit, run, read the first failure, repeat.

```bash
# Applet or manifest changed: rebuild, reinstall, provision, test
./gradlew cinpoRun --args='--test'

# Only host/ changed: keep the installed applet and rerun the tasks
./gradlew cinpoRun --args='--skip-install --test'

# See task selection and APDU traffic
./gradlew cinpoRun --args='--debug --test'
```

`--test` is not a unit-test runner. It installs the applet, runs `provision`, then runs `test` against the simulator (or the card in the active profile). A green run means the whole chain worked.

If it fails, check in this order:

1. `vendor/` is ready — without the Oracle archives (and any companion bundle this template needs) nothing compiles.
2. `manifest.yaml` and the AID the host selects agree.
3. The status word of the failing command.
4. The command bytes and the applet's `switch` on `INS`.
5. Chunk size and offset — the certificate is written in 220-byte chunks.
6. The profile and secure-channel keys, if you are on a real card.

| SW | Meaning | First thing to check |
|----|---------|----------------------|
| `6D00` | INS not supported | applet `case` and host `INS_*` constant |
| `6E00` | CLA not supported | applet `CLA` and host `CLA` constant |
| `6700` | wrong length | Lc/Le, chunk size, offset |
| `6982` | security status not satisfied | profile, secure channel, card state |
| `6A82` | file or application not found | AID in `manifest.yaml` and the SELECT |

## Where to change what

| Goal | Change | Verify |
|------|--------|--------|
| Add or change an APDU command | `applet/HelloApplet.java` (`INS_*`, `switch`, handler), then mirror the constant in `host/provision/HelloProvision.java` and `host/test/HelloTest.java` | `./gradlew cinpoRun --args='--test'` |
| Add a task kind | `host/<kind>/YourTask.java` with `package <kind>;` and `@CardTaskDef("<kind>")` | `./gradlew cinpoRun --args='--task=<kind>'` |
| Add an applet to the package | `applet/SecondApplet.java`, then an entry under `applets:` in `manifest.yaml` | `./gradlew cinpoRun --args='--test'` |
| Rename the package or AIDs | `manifest.yaml` and the `package` declaration in `applet/` | `./gradlew cinpoRun --args='--test'` |
| Change the certificate | `host/provision/CertificateIssuer.java` and the assertions in `host/test/HelloTest.java` | `./gradlew cinpoRun --args='--test'` |
| Add a dependency | `build.gradle` → `dependencies { }` | `./gradlew cinpoRun` |
| Bundle a data file | Put it under `host/` and read it with `getClass().getClassLoader().getResourceAsStream(...)` | `./gradlew cinpoRun --args='--test'` |
| Add a real-card profile | `profile/<name>.yaml` with `runtime: pcsc`, then run with `--profile <name>` | Run on the card |

The directory under `host/` is a convention, not a compiler rule: CINPO indexes `@CardTaskDef` by annotation.

## Testing and debugging

A test task is ordinary Java. The shape that catches real mistakes is: check the status word, assert the exact bytes, and put both the expected and actual value in the failure message.

```java
// imports omitted; see host/test/HelloTest.java for the full task.
ResponseApdu response = channel.transmit(
        new CommandApdu(CLA, INS_ECHO, 0x00, 0x00, payload, 256));
if (response.sw() != 0x9000) {
    throw new IllegalStateException("ECHO failed: SW=%04X".formatted(response.sw()));
}
if (!Arrays.equals(payload, response.data())) {
    throw new IllegalStateException(
            "ECHO returned %s, expected %s".formatted(
                    Util.toHex(response.data()), Util.toHex(payload)));
}

// A negative test is worth as much as a positive one: an unknown INS must fail.
ResponseApdu unknown = channel.transmit(
        new CommandApdu(CLA, 0x7F, 0x00, 0x00, null, 256));
if (unknown.sw() != 0x6D00) {
    throw new IllegalStateException(
            "unknown INS: expected SW=6D00, got SW=%04X".formatted(unknown.sw()));
}
```

Rules that keep the check honest:

- A check that can pass without running the task is not a check. Assert something the card actually returned.
- Assert the status word before the data; an unexpected SW with an empty body is still a failure.
- Keep fixtures deterministic. Do not depend on card state left by a previous run unless you also assert it.
- When you change a contract (CLA, INS, AID, certificate subject, chunk size), change the test and the producer in the same edit.
- `--skip-install` is for host-only changes. If `applet/` or `manifest.yaml` changed, run the full flow.
- If a check cannot run here (no `vendor/`, no card, no network), say so. Do not call it passing.

## Make it yours

The sample values are placeholders — replace them before any real use. They are:

| Placeholder | Where it appears |
|---|---|
| `com.example.cinpo.hello` | `applet/HelloApplet.java` (`package`), `manifest.yaml` (`basePackage`, `package.name`), `applets[0].className` |
| `HelloApplet` | `applet/HelloApplet.java`, host task comments, this README |
| `F00000000101` | `manifest.yaml` (`package.aid`, `classAid`, `instanceAid`) |
| `CN=CINPO Template Leaf` | `host/provision/CertificateIssuer.java`, `host/test/HelloTest.java` |
| `CN=CINPO Template Test CA` | `host/provision/CertificateIssuer.java` |

In order:

1. **Rename the applet and its package** — change the `package` declaration in `applet/HelloApplet.java`, rename the class if you like, then make `manifest.yaml` agree: `basePackage`, `package.name`, and `applets[0].className`.
2. **Replace the AIDs** in `manifest.yaml`. Register your own RID; do not reuse `F00000000101`. Host tasks read the instance AID from the manifest.
3. **Replace the sample contract** — update the certificate issuer/test assertions, or replace the demo commands and their handlers. If you change CLA/INS or the certificate subject/validity, change the host constants, the test, and the docs in the same edit.
4. **Real-card profile** — `profile/jcdksim.yaml` carries public simulator keys. For a physical card, create `profile/<name>.yaml` with `runtime: pcsc` and your own SCP03 keys, run with `--profile <name>`, and keep that file out of git.
5. **Verify** — `./gradlew cinpoRun --args='--test'` must pass.
6. **Check for leftovers**:

   ```bash
   grep -RIn --exclude-dir=.git --exclude-dir=build --exclude-dir=vendor \
     -e 'HelloApplet' -e 'com.example.cinpo.hello' -e 'F00000000101' .
   ```

## Documentation

- [Writing and testing tasks](https://github.com/AokiApp/cinpo/blob/main/docs/tasks.md) — task lifecycle, injection, `TaskArguments`, and the API reference
- [manifest.yaml reference](https://github.com/AokiApp/cinpo/blob/main/docs/manifest.md)
- [Profiles](https://github.com/AokiApp/cinpo/blob/main/docs/profiles.md) — simulator vs physical card, PC/SC reader selection, SCP03
- [Vendor setup](https://github.com/AokiApp/cinpo/blob/main/docs/vendor.md) — Oracle JCDK and companion bundles

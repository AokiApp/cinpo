# CINPO Applet with SCP03 Secure Messaging

This project was generated from the `authenticated` starter template.

**CINPO** (**C**ard **IN**stallation and **P**rovision **O**rchestrator), "Spring Boot for Java Card", is a framework for installing and provisioning Java Card applets. You write host-side tasks as ordinary Java classes, annotate them (`@CardTaskDef`), inject what you need (`@Inject`), and CINPO handles manifest validation, CAP compilation/conversion, installation on the JCDK simulator or a real card, APDU transport, and GlobalPlatform secure-channel authentication. No `main()`, build scripts, or ad-hoc Gradle tasks — `./gradlew cinpoRun` does it all. See the [CINPO repository](https://github.com/AokiApp/cinpo) and its [documentation](https://github.com/AokiApp/cinpo/tree/main/docs).

This template shows SCP03 secure messaging: the applet handles GlobalPlatform security commands, and the host test proves that an unauthenticated request is refused (`6982`) while authenticated commands succeed.

What you get:

- `applet/HelloApplet.java` — a Java Card applet with `ECHO`, `HELLO`, and `SHLO`, plus the GlobalPlatform security-command path.
- `host/provision/HelloProvision.java` — the default `provision` task. SELECTs the applet and checks `HELLO`.
- `host/test/HelloTest.java` — the conventional `test` task. Rejects unauthenticated `SHLO`, authenticates SCP03, then exercises `ECHO` and `SHLO`.
- `profile/jcdksim.yaml` — simulator profile with public SCP03 test keys. Development only.

## Requirements

- **JDK 21** and **git**
- **Oracle Java Card Development Kit (JCDK) 26.0**, which is not redistributed. Download it from [Oracle's Java Card downloads page](https://www.oracle.com/java/technologies/javacard-downloads.html) and place the archives in `vendor/`:

  - Tools: `java_card_devkit_tools-bin-v26.0-b_705-04-MAY-2026.zip`
  - Simulator (Linux): `java_card_devkit_simulator-linux-bin-v26.0-b_788-05-MAY-2026.tar.gz`
  - Simulator (Windows): `java_card_devkit_simulator-win-bin-v26.0-b_788-05-MAY-2026.zip`

  See [vendor setup](https://github.com/AokiApp/cinpo/blob/main/docs/vendor.md). Without these files, `cinpoRun` and `cinpoJar` fail.

- **GlobalPlatform Card API 1.5**, a companion bundle that is not part of the Oracle JCDK. Download [`javacard-1.5.jar`](https://mvn.javacard.pro/maven/org/globalplatform/javacard/1.5/javacard-1.5.jar) and extract the `.exp` without flattening its package path:

  ```bash
  mkdir -p vendor/globalplatform/org.globalplatform-1.5/exports
  curl -fL -o vendor/globalplatform/org.globalplatform-1.5/gpapi-globalplatform.jar \
    https://mvn.javacard.pro/maven/org/globalplatform/javacard/1.5/javacard-1.5.jar
  unzip -o vendor/globalplatform/org.globalplatform-1.5/gpapi-globalplatform.jar \
    'org/globalplatform/javacard/globalplatform.exp' \
    -d vendor/globalplatform/org.globalplatform-1.5/exports
  ```

  The result must match `manifest.yaml` (`vendor/globalplatform/org.globalplatform-1.5/gpapi-globalplatform.jar` and `exports/org/globalplatform/javacard/globalplatform.exp`). See [companion bundles](https://github.com/AokiApp/cinpo/blob/main/docs/vendor.md#companion-bundles).

## Layout

```
├── applet/                # On-card code (Java Card subset + GlobalPlatform API)
├── host/
│   ├── provision/         # @CardTaskDef("provision") — the default kind
│   └── test/              # @CardTaskDef("test") — runs with --test
├── manifest.yaml          # Package, applet, AID, and companion-bundle declarations
├── profile/jcdksim.yaml   # Simulator profile (development-only test keys)
├── vendor/                # Oracle JCDK archives and the GP API (not committed)
└── build.gradle
```

## Run it

From this directory, after staging `vendor/` and the companion bundle:

```bash
./gradlew cinpoRun                   # install the applet, then run provision tasks
./gradlew cinpoRun --args='--test'   # also run the test tasks
./gradlew cinpoJar                   # build the self-contained production JAR
```

On Windows use `.\gradlew.bat` instead of `./gradlew`.

`--test` runs `provision` then `test`. The test expects the applet to be selected by provision.

## Development loop

The loop is the same for every change: edit, run, read the first failure, repeat.

```bash
# Applet, manifest, profile, or companion bundle changed: rebuild, reinstall, provision, test
./gradlew cinpoRun --args='--test'

# Only host/ changed: keep the installed applet and rerun the tasks
./gradlew cinpoRun --args='--skip-install --test'

# See task selection and APDU traffic
./gradlew cinpoRun --args='--debug --test'
```

`--test` is not a unit-test runner. It installs the applet, runs `provision`, then runs `test` against the simulator (or the card in the active profile). A green run means the whole chain worked, including the secure channel.

If it fails, check in this order:

1. `vendor/` is ready — without the Oracle archives (and any companion bundle this template needs) nothing compiles.
2. `manifest.yaml` and the AID the host selects agree.
3. The status word of the failing command.
4. The profile's ISD AID, key version, key identifier, and SCP03 keys/security level.
5. Secure-messaging wrapping: the applet unwraps wrapped commands and wraps responses including the status word.

| SW | Meaning | First thing to check |
|----|---------|----------------------|
| `6D00` | INS not supported | applet `case` and host `INS_*` constant |
| `6E00` | CLA not supported | applet `CLA` and host `CLA` constant |
| `6700` | wrong length | Lc/Le, wrapped vs unwrapped length |
| `6982` | security status not satisfied | expected for unauthenticated `SHLO`; otherwise keys or security level |
| `6985` | conditions of use not satisfied | applet/card state or install state |
| `6A82` | file or application not found | AID in `manifest.yaml` and the SELECT |

## Where to change what

| Goal | Change | Verify |
|------|--------|--------|
| Add or change an APDU command | `applet/HelloApplet.java` (`INS_*`, `switch`, handler), then mirror the constant in host tasks | `./gradlew cinpoRun --args='--test'` |
| Add or change an authenticated command | Check `AUTHENTICATED` in the applet; add one unauthenticated and one authenticated test | `./gradlew cinpoRun --args='--test'` |
| Change SCP03 keys or security level | `profile/jcdksim.yaml` for the simulator; a separate profile for a real card | `./gradlew cinpoRun --args='--test'` |
| Upgrade the GlobalPlatform API | `manifest.yaml` and the staged `vendor/globalplatform/...` path — both to the same existing version | `./gradlew cinpoRun --args='--test'` |
| Add a task kind | `host/<kind>/YourTask.java` with `package <kind>;` and `@CardTaskDef("<kind>")` | `./gradlew cinpoRun --args='--task=<kind>'` |
| Rename the package or AIDs | `manifest.yaml` and the `package` declaration in `applet/` | `./gradlew cinpoRun --args='--test'` |
| Add a dependency | `build.gradle` → `dependencies { }` | `./gradlew cinpoRun` |
| Add a real-card profile | `profile/<name>.yaml` with `runtime: pcsc`, then run with `--profile <name>` | Run on the card |

The directory under `host/` is a convention, not a compiler rule: CINPO indexes `@CardTaskDef` by annotation. The companion-bundle `id`, `jar`, and `exports` must name the same staged version.

## Testing and debugging

Keep the pair: one unauthenticated refusal and one authenticated success. Run the negative test before opening a session, on the raw channel.

```java
// imports omitted; see host/test/HelloTest.java for the full task.
ResponseApdu rejected = channel.transmit(
        new CommandApdu(CLA_CINPO, INS_SHLO, 0x00, 0x00, null, 256));
if (rejected.sw() != 0x6982) {
    throw new IllegalStateException(
            "unauthenticated SHLO: expected SW=6982, got SW=%04X".formatted(rejected.sw()));
}

try (SecureChannelSession session =
        SecureChannelSession.create(channel, secureChannelProfile)) {
    session.authenticate(); // the applet must already be selected
    ResponseApdu response = session.transmit(
            new CommandApdu(CLA_CINPO, INS_SHLO, 0x00, 0x00, null, 256));
    if (response.sw() != 0x9000 || !Arrays.equals(SECURE_HELLO, response.data())) {
        throw new IllegalStateException(
                "SHLO failed: SW=%04X".formatted(response.sw()));
    }
}
```

Rules that keep the check honest:

- Do not delete the negative test because it returns `6982`. That status word is the assertion.
- Use `session.transmit()` for authenticated commands and `channel.transmit()` only before authentication or for negative tests.
- Assert the status word before the data; an unexpected SW with a plausible body is still a failure. Keep fixtures deterministic.
- When you change a contract (CLA, INS, AID, key version, security level, bundle version), change the test and the producer in the same edit.
- `--skip-install` is for host-only changes. If `applet/`, `manifest.yaml`, the profile, or the bundle changed, run the full flow.
- If a check cannot run here (no `vendor/`, no bundle, no card, no network), say so. Do not call it passing.

## Make it yours

The sample values are placeholders — replace them before any real use. They are:

| Placeholder | Where it appears |
|---|---|
| `com.example.cinpo.hello` | `applet/HelloApplet.java` (`package`), `manifest.yaml` (`basePackage`, `package.name`), `applets[0].className` |
| `HelloApplet` | `applet/HelloApplet.java`, host task comments, this README |
| `F00000000101` | `manifest.yaml` (`package.aid`, `classAid`, `instanceAid`) |
| `globalplatform-1.5` | `manifest.yaml` (`companionBundles[0].id`, paths), fetch script, staged `vendor/` path |
| simulator SCP03 keys | `profile/jcdksim.yaml` only; public test keys, never for a real card |

In order:

1. **Rename the applet and its package** — change the `package` declaration in `applet/HelloApplet.java`, rename the class if you like, then make `manifest.yaml` agree.
2. **Replace the AIDs** in `manifest.yaml`. Register your own RID; do not reuse `F00000000101`. Host tasks read the instance AID from the manifest.
3. **Replace keys and profile** — create `profile/<name>.yaml` with `runtime: pcsc` and the card's SCP03 keys/ISD/key version for a physical card; keep it out of git.
4. **Keep the companion bundle consistent** — manifest and staged path must name the same existing version.
5. **Replace the demo commands** and keep at least one authenticated command with its negative test.
6. **Verify** — `./gradlew cinpoRun --args='--test'` must pass.
7. **Check for leftovers**:

   ```bash
   grep -RIn --exclude-dir=.git --exclude-dir=build --exclude-dir=vendor \
     -e 'HelloApplet' -e 'com.example.cinpo.hello' -e 'F00000000101' .
   ```

## Documentation

- [Writing and testing tasks](https://github.com/AokiApp/cinpo/blob/main/docs/tasks.md) — task lifecycle, injection, `TaskArguments`, and the API reference
- [manifest.yaml reference](https://github.com/AokiApp/cinpo/blob/main/docs/manifest.md)
- [Profiles](https://github.com/AokiApp/cinpo/blob/main/docs/profiles.md) — simulator vs physical card, PC/SC reader selection, SCP03
- [Vendor setup](https://github.com/AokiApp/cinpo/blob/main/docs/vendor.md) — Oracle JCDK and companion bundles

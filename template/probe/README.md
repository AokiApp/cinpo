# CINPO Card Probe

This project was generated from the `probe` starter template.

**CINPO** (**C**ard **IN**stallation and **P**rovision **O**rchestrator), "Spring Boot for Java Card", is a framework for installing and provisioning Java Card applets. You write host-side tasks as ordinary Java classes, annotate them (`@CardTaskDef`), inject what you need (`@Inject`), and CINPO handles manifest validation, CAP compilation/conversion, installation on the JCDK simulator or a real card, APDU transport, and GlobalPlatform secure-channel authentication. No `main()`, build scripts, or ad-hoc Gradle tasks — `./gradlew cinpoRun` does it all. See the [CINPO repository](https://github.com/AokiApp/cinpo) and its [documentation](https://github.com/AokiApp/cinpo/tree/main/docs).

This template is a read-only diagnostic project. The applet is a placeholder that accepts SELECT and rejects everything else; the host-side `probe` task selects an application, reads card data, and prints it. It does not write card state.

What you get:

- `applet/MaguroApplet.java` — a minimal placeholder applet (SELECT only).
- `host/probe/ProbeTask.java` — the `probe` task kind. SELECTs a target, sends `GET DATA` for card data (tag `0x66`), and prints the result.
- `manifest.yaml` — package and applet declaration.
- `profile/jcdksim.yaml` — simulator profile with development-only test keys.

**The default task kind is `provision`, and this template does not define it.** A bare `./gradlew cinpoRun` fails with `No CINPO tasks found for kind: provision`. Run the probe explicitly with `--task=probe`.

## Requirements

- **JDK 21** and **git**
- **Oracle Java Card Development Kit (JCDK) 26.0**, which is not redistributed. Download it from [Oracle's Java Card downloads page](https://www.oracle.com/java/technologies/javacard-downloads.html) and place the archives in `vendor/`:

  - Tools: `java_card_devkit_tools-bin-v26.0-b_705-04-MAY-2026.zip`
  - Simulator (Linux): `java_card_devkit_simulator-linux-bin-v26.0-b_788-05-MAY-2026.tar.gz`
  - Simulator (Windows): `java_card_devkit_simulator-win-bin-v26.0-b_788-05-MAY-2026.zip`

  See [vendor setup](https://github.com/AokiApp/cinpo/blob/main/docs/vendor.md). Without these files, `cinpoRun` and `cinpoJar` fail.

- **PC/SC reader and card** for real-card probing. The simulator works without a reader. See [Profiles](https://github.com/AokiApp/cinpo/blob/main/docs/profiles.md).

## Layout

```
├── applet/                # Placeholder applet (Java Card subset)
├── host/probe/            # @CardTaskDef("probe") — the read-only diagnostic
├── manifest.yaml          # Package and applet declaration
├── profile/jcdksim.yaml   # Simulator profile (development-only test keys)
├── vendor/                # Oracle JCDK archives (not committed)
└── build.gradle
```

## Run it

From this directory:

```bash
./gradlew cinpoRun --args='--task=probe'                     # install placeholder, then probe
./gradlew cinpoRun --args='--skip-install --task=probe'      # probe only (safe on a real card)
./gradlew cinpoRun --args='--task=probe -- --aid=F000000001010001'  # select a specific AID
./gradlew cinpoRun --args='--debug --task=probe'             # see APDU traffic
./gradlew cinpoJar                                           # build the self-contained production JAR
```

On Windows use `.\gradlew.bat` instead of `./gradlew`.

`--skip-install` avoids installing the placeholder applet; the probe task itself only sends SELECT and `GET DATA`. `--aid` takes 5 to 16 bytes of hex after `--` (`--aid VALUE` or `--aid=VALUE`). Without it, the probe selects the default application.

## Development loop

The loop is the same for every change: edit, run, read the first failure, repeat.

```bash
# Edit ProbeTask, rerun without reinstalling
./gradlew cinpoRun --args='--skip-install --task=probe'

# Full install + probe
./gradlew cinpoRun --args='--task=probe'

# See task selection and APDU traffic
./gradlew cinpoRun --args='--debug --task=probe'
```

This template has no `--test` kind. A diagnostic run is successful when the output is readable and the card answered (including a refusal status word).

If it fails, check in this order:

1. `vendor/` is ready — without the Oracle archives (and any companion bundle this template needs) nothing compiles.
2. The active profile, reader, and card are available.
3. The SELECT status word (`6A82` means the AID is not on the card).
4. The command bytes and the card's expected CLA/INS.
5. For PC/SC, the reader is not claimed by another process.

| SW | Meaning | First thing to check |
|----|---------|----------------------|
| `6D00` | INS not supported | the card does not implement this command |
| `6E00` | CLA not supported | try the other CLA (`0x00` vs `0x80`) |
| `6700` | wrong length | Lc/Le |
| `6A82` | file or application not found | the AID passed to `--aid` |
| `6A88` | referenced data not found | `GET DATA` tag is not supported by this card |
| `6982` | security status not satisfied | the command needs an authenticated session |

## Where to change what

| Goal | Change | Verify |
|------|--------|--------|
| Read another tag or command | Add a `channel.transmit(...)` call in `host/probe/ProbeTask.java` and print SW/data | `./gradlew cinpoRun --args='--skip-install --task=probe'` |
| Add a task argument | `arguments.firstValue("name")` with validation in `ProbeTask.java` | run with `-- --name=value` |
| Add a pass/fail check | New `host/test/YourTest.java` with `@CardTaskDef("test")` and assertions | `./gradlew cinpoRun --args='--task=probe --task=test'` |
| Add a task kind | `host/<kind>/YourTask.java` with `package <kind>;` and `@CardTaskDef("<kind>")` | `./gradlew cinpoRun --args='--task=<kind>'` |
| Rename the package or AIDs | `manifest.yaml` and the `package` declaration in `applet/` | `./gradlew cinpoRun --args='--task=probe'` |
| Add a real-card profile | `profile/<name>.yaml` with `runtime: pcsc`, then run with `--profile <name>` | Run on the card |

The directory under `host/` is a convention, not a compiler rule: CINPO indexes `@CardTaskDef` by annotation.

## Testing and debugging

A probe reports what the card said, including "no". Do not throw on a status word the card is allowed to return; reserve exceptions for transport and programming errors.

```java
// imports omitted; see host/probe/ProbeTask.java for the full task.
ResponseApdu response = channel.transmit(
        new CommandApdu(0x80, Iso7816Commands.INS_GET_DATA, 0x9F, 0x7F, null, 256));
if (response.sw() == 0x9000) {
    System.out.println("CPLC: " + Util.toHex(response.data()));
} else {
    System.out.println("CPLC unavailable: SW=%04X".formatted(response.sw()));
}
```

Rules that keep the check useful:

- Print the status word, not just the data. "Empty" and "refused" are different answers.
- Do not write to the card. No STORE DATA, no INSTALL, no key change. A write makes it a provisioning task, not a probe.
- On a real card, prefer `--skip-install`; without it CINPO installs the placeholder applet before probing.
- Validate `--aid` before sending it (5 to 16 bytes, valid hex) and fail with a clear message.
- If you need a pass/fail assertion, put it in a `test` task. A probe that exits non-zero on an unsupported tag cannot be used to discover what a card supports.
- If a check cannot run here (no `vendor/`, no reader, no card, no network), say so. Do not call it passing.

## Make it yours

The sample values are placeholders — replace them before any real use. They are:

| Placeholder | Where it appears |
|---|---|
| `com.example.cinpo.probe` | `applet/MaguroApplet.java` (`package`), `manifest.yaml` (`basePackage`, `package.name`), `applets[0].className` |
| `MaguroApplet` | `applet/MaguroApplet.java`, `manifest.yaml`, this README |
| `F00000000101` | `manifest.yaml` (`package.aid`, `classAid`, `instanceAid`) |
| `probe` | `host/probe/ProbeTask.java` (`package`, `@CardTaskDef`), the command line |

In order:

1. **Rename the applet and its package** — change the `package` declaration in `applet/MaguroApplet.java`, rename the class if you like, then make `manifest.yaml` agree.
2. **Replace the AIDs** in `manifest.yaml`. Register your own RID; do not reuse `F00000000101`.
3. **Set the profile** — `profile/jcdksim.yaml` for the simulator. For a physical card, create `profile/<name>.yaml` with `runtime: pcsc` and run with `--profile <name>`, keeping real keys out of git.
4. **Adjust the probe target/commands** as needed. Keep the task read-only.
5. **Verify** — `./gradlew cinpoRun --args='--skip-install --task=probe'` must print a SELECT result and a card-data result (data or a status word).
6. **Check for leftovers**:

   ```bash
   grep -RIn --exclude-dir=.git --exclude-dir=build --exclude-dir=vendor \
     -e 'MaguroApplet' -e 'com.example.cinpo.probe' -e 'F00000000101' .
   ```

## Documentation

- [Writing and testing tasks](https://github.com/AokiApp/cinpo/blob/main/docs/tasks.md) — task lifecycle, injection, `TaskArguments`, and the API reference
- [manifest.yaml reference](https://github.com/AokiApp/cinpo/blob/main/docs/manifest.md)
- [Profiles](https://github.com/AokiApp/cinpo/blob/main/docs/profiles.md) — simulator vs physical card, PC/SC reader selection, SCP03
- [Vendor setup](https://github.com/AokiApp/cinpo/blob/main/docs/vendor.md) — Oracle JCDK and companion bundles

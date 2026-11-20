# CINPO Card Probe

This project was generated from the `probe` starter template.

**CINPO** (**C**ard **IN**stallation and **P**rovision **O**rchestrator), "Spring Boot for Java Card", is a framework for installing and provisioning Java Card applets. You write host-side tasks as ordinary Java classes, annotate them (`@CardTaskDef`), extend `CardTask`, and CINPO handles manifest validation, CAP compilation/conversion, installation on the JCDK simulator or a real card, APDU transport, and GlobalPlatform secure-channel authentication. No `main()`, build scripts, or ad-hoc Gradle tasks — `./gradlew cinpoRun` does it all. See the [CINPO repository](https://github.com/AokiApp/cinpo) and its [documentation](https://github.com/AokiApp/cinpo/tree/main/docs).

This template is a read-only diagnostic: the applet is a placeholder, and `ProbeTask` selects an application or the default one, reads card data with `GET DATA`, and prints it. It does not write card state.

## What you get

- `applet/MaguroApplet.java` — a placeholder applet that accepts SELECT only.
- `host/probe/ProbeTask.java` — the `probe` task: SELECT, `GET DATA` (tag `0x66`), and print.
- `profile/jcdksim.yaml` — Oracle JCDK simulator profile with development-only keys.

**The default kind is `provision`, which this template does not define.** Run `--task=probe` explicitly.

## Requirements

- JDK 21 and git.
- Oracle JCDK 26.0 (Tools and the Simulator for your OS) in `vendor/`; see [vendor setup](https://github.com/AokiApp/cinpo/blob/main/docs/vendor.md).
- A PC/SC reader and card for real-card probing; the simulator needs neither.

## Layout

```
├── applet/                # Placeholder applet (Java Card subset)
├── host/probe/            # @CardTaskDef("probe") — read-only diagnostic
├── manifest.yaml          # Package and applet declaration
├── profile/jcdksim.yaml   # Simulator profile
├── vendor/                # Oracle JCDK archives (not committed)
└── build.gradle
```

## Run it

```bash
./gradlew cinpoRun --args='--task=probe'                     # install, then probe
./gradlew cinpoRun --args='--skip-install --task=probe'      # probe only (safe on a real card)
./gradlew cinpoRun --args='--task=probe -- --aid=F000000001010001'
./gradlew cinpoRun --args='--debug --task=probe'
```

On Windows use `.\gradlew.bat`. `--skip-install` avoids installing the placeholder applet. `--aid` takes 5–16 bytes of hex after `--`; without it, the probe selects the default application.

## Development loop

```bash
./gradlew cinpoRun --args='--skip-install --task=probe'   # rerun without installing
./gradlew cinpoRun --args='--task=probe'                  # full install + probe
./gradlew cinpoRun --args='--debug --task=probe'          # show APDU traffic
```

This template has no `--test` kind. A run is successful when the output is readable and the card answered, including a refusal status word. `./gradlew build` runs the same probe through the Gradle `test` lifecycle.

If it fails, check `vendor/`, then the profile/reader, then the SELECT or command SW.

## Where to change what

| Goal | Change |
|------|--------|
| Read another tag | Add a `channel.transmit(...)` call in `host/probe/ProbeTask.java` |
| Add an argument | `arguments.firstValue("name")` with validation |
| Add a pass/fail check | New `host/test/YourTest.java` with `@CardTaskDef("test")` |
| Package or AIDs | `manifest.yaml` and the `package` declaration in `applet/` |
| Real-card profile | `profile/<name>.yaml` with `runtime: pcsc` |

The directory under `host/` is a convention: CINPO indexes `@CardTaskDef` by annotation.

## Verify

`./gradlew cinpoRun --args='--skip-install --task=probe'` must print a SELECT result plus a card-data result (data or a status word). There is no automated pass/fail check.

- A card refusal (`6A82`, `6A81`, `6A88`) is a result, not a crash; report the SW.
- Do not write to the card. If you need a gate, add a `test` kind with assertions.
- If the check cannot run here, say so. See `host/probe/ProbeTask.java` for the shape.

## Make it yours

The sample values are placeholders. They are:

| Placeholder | Where it appears |
|---|---|
| `com.example.cinpo.probe` | applet package, `manifest.yaml` |
| `MaguroApplet` | applet class, `manifest.yaml`, this README |
| `F00000000101` | `manifest.yaml` (`package.aid`, `classAid`, `instanceAid`) |
| `probe` | `host/probe/ProbeTask.java` and the command line |

1. Rename the applet package/class and the matching `manifest.yaml` fields.
2. Replace the AIDs; register your own RID instead of reusing `F00000000101`.
3. Keep the probe read-only; adjust its target or commands as needed.
4. Run the verify command, then check for leftovers:

   ```bash
   grep -RIn --exclude-dir=.git --exclude-dir=build --exclude-dir=vendor \
     -e 'MaguroApplet' -e 'com.example.cinpo.probe' -e 'F00000000101' .
   ```

## Documentation

- [Writing and testing tasks](https://github.com/AokiApp/cinpo/blob/main/docs/tasks.md)
- [manifest.yaml reference](https://github.com/AokiApp/cinpo/blob/main/docs/manifest.md)
- [Profiles](https://github.com/AokiApp/cinpo/blob/main/docs/profiles.md) — simulator, PC/SC, SCP03
- [Vendor setup](https://github.com/AokiApp/cinpo/blob/main/docs/vendor.md) — JCDK and companion bundles

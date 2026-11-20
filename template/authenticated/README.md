# CINPO Applet with SCP03 Secure Messaging

This project was generated from the `authenticated` starter template.

**CINPO** (**C**ard **IN**stallation and **P**rovision **O**rchestrator), "Spring Boot for Java Card", is a framework for installing and provisioning Java Card applets. You write host-side tasks as ordinary Java classes, annotate them (`@CardTaskDef`), inject what you need (`@Inject`), and CINPO handles manifest validation, CAP compilation/conversion, installation on the JCDK simulator or a real card, APDU transport, and GlobalPlatform secure-channel authentication. No `main()`, build scripts, or ad-hoc Gradle tasks — `./gradlew cinpoRun` does it all. See the [CINPO repository](https://github.com/AokiApp/cinpo) and its [documentation](https://github.com/AokiApp/cinpo/tree/main/docs).

This template shows SCP03 secure messaging: the applet handles GlobalPlatform security commands, and the host test proves that an unauthenticated request is refused (`6982`) while authenticated commands succeed.

## What you get

- `applet/HelloApplet.java` — `ECHO`, `HELLO`, `SHLO`, plus the GlobalPlatform security-command path.
- `host/provision/` — `provision` selects the applet and checks `HELLO`.
- `host/test/` — `test` rejects unauthenticated `SHLO`, authenticates SCP03, then checks `ECHO` and `SHLO`.
- `profile/jcdksim.yaml` — Oracle JCDK simulator profile with public SCP03 test keys.

## Requirements

- JDK 21 and git.
- Oracle JCDK 26.0 (Tools and the Simulator for your OS) in `vendor/`; see [vendor setup](https://github.com/AokiApp/cinpo/blob/main/docs/vendor.md).
- GlobalPlatform Card API 1.5 in `vendor/globalplatform/org.globalplatform-1.5/`; see [companion bundles](https://github.com/AokiApp/cinpo/blob/main/docs/vendor.md#companion-bundles).

## Layout

```
├── applet/                # On-card code (Java Card subset + GlobalPlatform API)
├── host/provision/        # @CardTaskDef("provision") — default kind
├── host/test/             # @CardTaskDef("test") — runs with --test
├── manifest.yaml          # Package, applet, AID, and companion-bundle declarations
├── profile/jcdksim.yaml   # Simulator profile
├── vendor/                # Oracle JCDK and the GP API (not committed)
└── build.gradle
```

## Run it

```bash
./gradlew cinpoRun                   # install and run provision
./gradlew cinpoRun --args='--test'   # provision, then test
./gradlew cinpoJar                   # self-contained production JAR
```

On Windows use `.\gradlew.bat`. `--test` runs `provision` then `test`; the test expects the applet to be selected by provision.

## Development loop

```bash
./gradlew cinpoRun --args='--test'                   # applet, manifest, or profile changed
./gradlew cinpoRun --args='--skip-install --test'    # host-only change
./gradlew cinpoRun --args='--debug --test'           # show APDU traffic
```

If it fails, check `vendor/` and the companion bundle first, then the profile keys/security level, then the failing SW.

## Where to change what

| Goal | Change |
|------|--------|
| APDU command | `applet/HelloApplet.java`, then the host constants in `host/` |
| Authenticated command | Check `AUTHENTICATED` in the applet; add one refusal and one success test |
| SCP03 keys or level | `profile/jcdksim.yaml` for the simulator; a card profile for hardware |
| GlobalPlatform API | `manifest.yaml` and the staged `vendor/globalplatform/...` path |
| Package or AIDs | `manifest.yaml` and the `package` declaration in `applet/` |

The directory under `host/` is a convention: CINPO indexes `@CardTaskDef` by annotation. The bundle `id`, `jar`, and `exports` must name the same staged version.

## Verify

`./gradlew cinpoRun --args='--test'` must pass: it installs the applet, rejects unauthenticated `SHLO`, authenticates SCP03, and verifies `ECHO` and `SHLO` through the session.

- Keep the negative test: `6982` is the assertion, not a failure.
- Use `session.transmit()` for authenticated commands; use the raw channel only before authentication or for negative tests.
- Assert the status word before the data.
- If the check cannot run here, say so. See `host/test/HelloTest.java` for the assertion shape.

## Make it yours

The sample values are placeholders. They are:

| Placeholder | Where it appears |
|---|---|
| `com.example.cinpo.hello` | applet package, `manifest.yaml` |
| `HelloApplet` | applet class, host comments, this README |
| `F00000000101` | `manifest.yaml` (`package.aid`, `classAid`, `instanceAid`) |
| `globalplatform-1.5` | `manifest.yaml`, staged `vendor/` path |
| simulator SCP03 keys | `profile/jcdksim.yaml` only |

1. Rename the applet package/class and the matching `manifest.yaml` fields.
2. Replace the AIDs; register your own RID instead of reusing `F00000000101`.
3. Replace the keys/profile for a real card; keep the GP bundle version and path consistent.
4. Keep at least one authenticated command with its negative test, then verify:

   ```bash
   ./gradlew cinpoRun --args='--test'
   ```

## Documentation

- [Writing and testing tasks](https://github.com/AokiApp/cinpo/blob/main/docs/tasks.md)
- [manifest.yaml reference](https://github.com/AokiApp/cinpo/blob/main/docs/manifest.md)
- [Profiles](https://github.com/AokiApp/cinpo/blob/main/docs/profiles.md) — simulator, PC/SC, SCP03
- [Vendor setup](https://github.com/AokiApp/cinpo/blob/main/docs/vendor.md) — JCDK and companion bundles

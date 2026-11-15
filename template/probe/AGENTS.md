# AGENTS.md

Rules for agents (and humans) working in this project. Follow them literally.

## What this project is

**CINPO** (**C**ard **IN**stallation and **P**rovision **O**rchestrator), "Spring Boot for Java Card", is a framework for installing and provisioning Java Card applets. You write host-side tasks as ordinary Java classes, annotate them (`@CardTaskDef`), inject what you need (`@Inject`), and CINPO handles manifest validation, CAP compilation/conversion, installation on the JCDK simulator or a real card, APDU transport, and GlobalPlatform secure-channel authentication. No `main()`, build scripts, or ad-hoc Gradle tasks — `./gradlew cinpoRun` does it all. Repository: [github.com/AokiApp/cinpo](https://github.com/AokiApp/cinpo). Documentation: [docs/](https://github.com/AokiApp/cinpo/tree/main/docs).

This project, generated from the `probe` starter template, is a host-side diagnostic, not an applet project. `ProbeTask` selects an application (or the default application), reads card data with `GET DATA`, and prints the result. `MaguroApplet` is a placeholder that accepts SELECT and rejects everything else. The probe task never writes to the card and reports what the card says, including refusals.

## The two worlds

| | Card world (`applet/`) | Host world (`host/`) |
|---|---|---|
| Runs on | Java Card chip / JCDK simulator | JVM (development machine / production) |
| Language | Java Card subset (3.0.4 API) | Java 21 |
| Types | `byte`, `short`, `boolean`, `byte[]` | the full Java type system |
| Memory | persistent EEPROM; transient APDU buffer per command | ordinary heap |
| Role here | Placeholder target for installation | The diagnostic that does the work |

The only boundary is the APDU protocol. The probe does not import the applet, and the applet does not know about the probe.

## Before you change

1. Read `manifest.yaml`: package name, AIDs, `toolSdkVersion`, `targetApiVersion` (and companion bundles, if any). It is the source of truth.
2. Find both sides of the contract: the applet handler and the host task that sends the command. Change both in one edit.
3. Make the smallest change that proves the behavior, then run `./gradlew cinpoRun --args='--skip-install --task=probe'`.
4. If the change alters a contract (CLA, INS, AID, security level, chunk size, certificate subject), update the test/probe check and the docs in the same change.

## Commands and how a run works

- `./gradlew cinpoRun --args='--task=probe'` — installs the placeholder applet, then runs the probe.
- `./gradlew cinpoRun --args='--skip-install --task=probe'` — probes without installing (preferred on a real card).
- `./gradlew cinpoRun --args='--task=probe -- --aid=F000000001010001'` — selects a specific AID (5–16 bytes hex, after `--`).
- `./gradlew cinpoRun --args='--debug --task=probe'` — verbose APDU/task logging.
- `./gradlew cinpoJar` — build the self-contained production JAR.
- The default task kind is `provision`, which this template does not define; a bare `cinpoRun` fails. `--test` is not available unless you add `provision`/`test` kinds.
- Without the Oracle archives in `vendor/`, `cinpoRun` and `cinpoJar` fail immediately — a setup failure, not a code failure.

At compile time an annotation processor indexes `@CardTaskDef` classes into `META-INF/cinpo/<kind>`. At runtime: discover → instantiate → inject `@Inject` fields → call `run()`. An unchecked exception aborts the pipeline and exits non-zero. See [Writing tasks](https://github.com/AokiApp/cinpo/blob/main/docs/tasks.md#how-it-works).

## The probe task at a glance (`host/probe/ProbeTask.java`)

`@CardTaskDef("probe")`. It reads `--aid` from `TaskArguments` (validates 5–16 bytes), SELECTs that AID or the default application, sends `GET DATA` for tag `0x66`, and prints results. A card refusal SW is reported, not thrown; the task is read-only.

```java
Optional<byte[]> aid = arguments.firstValue("aid").map(Util::parseHex);
ResponseApdu response = channel.transmit(/* SELECT ... */);
if (response.sw() != 0x9000) {
    System.out.println("SELECT failed: SW=%04X".formatted(response.sw()));
}
```

## The applet at a glance (`applet/MaguroApplet.java`)

A placeholder: `install()` registers the AID, `process()` accepts SELECT and throws `SW_INS_NOT_SUPPORTED` for everything else. Do not remove the last applet from `manifest.yaml`.

## How to change things

### Add a read-only probe command

1. Build a `CommandApdu`, call `channel.transmit()`, branch on `response.sw()`.
2. Print a label, the SW on failure, and `Util.toHex(response.data())` on success.
3. Run `./gradlew cinpoRun --args='--skip-install --task=probe'`.

### Add a task argument

Use `arguments.firstValue("name")`, validate it, and pass it after `--` (`--name=value` or `--name value`).

### Add a task kind or a pass/fail gate

Create `host/<kind>/YourTask.java` with `@CardTaskDef("<kind>")`. If a check must fail the build, put assertions in a `test` kind and run `--task=probe --task=test`; keep `ProbeTask` diagnostic (do not exit non-zero on an unsupported tag).

## Hard rules

1. **The probe task is read-only.** SELECT and `GET DATA` only. No STORE DATA, no INSTALL, no key change.
2. **On a real card, prefer `--skip-install`.** Without it, CINPO installs the placeholder applet before probing.
3. **Report status words; do not throw on a card refusal.** `6A82`, `6A81`, `6A88` are answers. Throw only on transport or programming errors.
4. **AIDs come from the manifest or `--aid`, never from literals.** Validate `--aid` (5–16 bytes, valid hex).
5. **Task classes** must be `public final`, have a `public` no-argument constructor, implement `app.aoki.cinpo.task.CardTask`, and carry `@CardTaskDef("<kind>")`. The package conventionally matches the directory under `host/`, but discovery is by annotation.
6. **`manifest.yaml` is the source of truth.** After editing it, package/AID fields and the applet declaration must agree.
7. **If verification cannot run here** (no `vendor/`, no reader, no card), say so — do not report success.

## Do not

- Do not add writes to `ProbeTask` (STORE DATA, INSTALL, PUT KEY, state-changing commands).
- Do not use `profile/jcdksim.yaml`'s well-known test keys on a real card.
- Do not swallow a transport exception as a card refusal.
- Do not claim `--test` works; this template has no `provision` or `test` kind until you add them.
- Do not add a `main()` method or a shell script to run tasks — CINPO runs tasks via `@CardTaskDef`.

## Definition of done

```bash
./gradlew cinpoRun --args='--skip-install --task=probe'
```

must run and print a SELECT result plus a card-data result (data or a status word). This template has no automated pass/fail check: success means the output is readable and the card answered. If you add a `test` kind, run it and require the expected status words.

If the check cannot run here, say so. If the change alters a command, an argument, or the output format, update the docs in the same change.

## Where new knowledge goes

Do not append every new fact here. Put it where it can be maintained:

| Kind of knowledge | Home | Shape |
|---|---|---|
| Invariant for every project from this template | this file | one rule, linked to a check |
| Procedure or workflow | `README.md` | numbered steps with commands |
| API or specification detail | CINPO docs (linked above) | reference plus example |
| Fact specific to this generated project | end of this file | `## Project-specific notes` |
| A mistake that can be reproduced | a test or CI check | assertion and failure message |
| A one-off incident | commit message or issue | not a rule |

Promotion order: reproduce it, encode it as a check, write the smallest example, and only then add a rule if it is universal. When you add a rule, remove or merge one. `must` means enforced by code or a test; `conventionally` means recommended, not enforced. If you need an exception, it was not a `must`.

## Project-specific notes

<!-- Rules that apply only to this generated project. Keep the template sections above unchanged. -->

# AGENTS.md

Rules for agents (and humans) working in this project. Follow them literally.

## What this project is

**CINPO** (**C**ard **IN**stallation and **P**rovision **O**rchestrator), "Spring Boot for Java Card", is a framework for installing and provisioning Java Card applets. You write host-side tasks, annotate them with `@CardTaskDef`, and CINPO handles manifest validation, CAP compilation/conversion, installation on the JCDK simulator or a real card, APDU transport, and GlobalPlatform secure-channel authentication. No `main()`, build scripts, or ad-hoc Gradle tasks — `./gradlew cinpoRun` does it all. Repository: [github.com/AokiApp/cinpo](https://github.com/AokiApp/cinpo). Documentation: [docs/](https://github.com/AokiApp/cinpo/tree/main/docs).

This project, generated from the `authenticated` starter template, is a Java Card applet (`applet/HelloApplet.java`) plus host-side tasks (`host/`); `provision` runs by default, `test` with `--test`. Its contract has two halves: the applet handles GlobalPlatform SCP03 security commands, and `HelloTest` proves that an unauthenticated `SHLO` is refused (`6982`) while authenticated `ECHO` and `SHLO` succeed. Keep both halves.

## The two worlds

| | Card world (`applet/`) | Host world (`host/`) |
|---|---|---|
| Runs on | Java Card chip / JCDK simulator | JVM (development machine / production) |
| Language | Java Card subset (3.0.4 API) plus the GlobalPlatform Card API | Java 21 |
| Types | `byte`, `short`, `boolean`, `byte[]` | the full Java type system |
| Memory | persistent EEPROM (survives power loss); transient APDU buffer per command | ordinary heap |
| Libraries | `javacard.framework.*`, `org.globalplatform.*` | anything |

The only boundary between them is the APDU protocol: do not import applet classes from host code or vice versa. Shared knowledge (CLA `0x80`, the `INS_*` bytes, the security level) is duplicated on purpose; change both sides in the same edit.

## Before you change

1. Read `manifest.yaml`: package name, AIDs, `toolSdkVersion`, `targetApiVersion` (and companion bundles, if any). It is the source of truth.
2. Find both sides of the contract: the applet handler and the host task that sends the command. Change both in one edit.
3. Make the smallest change that proves the behavior, then run the verification command for this template.
4. If the change alters a contract (CLA, INS, AID, security level, chunk size, certificate subject), update the test/probe check and the docs in the same change.

## Commands and how a run works

- Stage the GlobalPlatform API 1.5 into `vendor/globalplatform/org.globalplatform-1.5/` once, as described in the [README](README.md#requirements).
- `./gradlew cinpoRun --args='--test'` — compiles, installs, runs `provision` then `test`.
- `./gradlew cinpoRun --args='--skip-install --test'` — rerun tasks without reinstalling (host-only changes).
- `./gradlew cinpoRun --args='--debug --test'` — verbose APDU/task logging.
- `./gradlew cinpoJar` — build the self-contained production JAR.
- Without the Oracle archives in `vendor/`, or without the GP bundle, `cinpoRun` and `cinpoJar` fail immediately — a setup failure, not a code failure. See [vendor setup](https://github.com/AokiApp/cinpo/blob/main/docs/vendor.md) and [companion bundles](https://github.com/AokiApp/cinpo/blob/main/docs/vendor.md#companion-bundles).

At compile time an annotation processor validates and indexes `@CardTaskDef` classes into `META-INF/cinpo/<kind>`. At runtime, for each selected kind: discover → instantiate (no-arg constructor) → populate inherited dependency fields → call `run()`. An unchecked exception aborts the pipeline and exits non-zero. See [Writing tasks](https://github.com/AokiApp/cinpo/blob/main/docs/tasks.md#how-it-works).

## The secure channel model

`profile/jcdksim.yaml` declares SCP03. The host authenticates a `SecureChannelSession`; the applet is the selected security endpoint and forwards `INITIALIZE UPDATE` (`0x50`) and `EXTERNAL AUTHENTICATE` (`0x82`) to `GPSystem.getSecureChannel().processSecurity()`. For wrapped commands the applet unwraps before reading and wraps responses including the status word (`90 00` inside the cryptogram). `SHLO` requires `AUTHENTICATED` and returns `6982` otherwise. Use `session.transmit()` for authenticated commands; use the raw `channel.transmit()` only before authentication or for a negative test.

## The applet at a glance (`applet/HelloApplet.java`)

CLA `0x80`. The applet must return `SW_CLA_NOT_SUPPORTED` / `SW_INS_NOT_SUPPORTED` for wrong CLA/unknown INS.

| Command | INS | P1/P2 | Behavior |
|---------|-----|-------|----------|
| ECHO | `0x01` | — | Returns the received data; wrapped when the command was wrapped |
| HELLO | `0x02` | — | Returns `"Hello, CINPO!"` plus the GP card content state |
| SHLO | `0x03` | — | Returns `"Hello, SCP!"`; requires `AUTHENTICATED`, otherwise `6982` |
| INITIALIZE UPDATE | `0x50` | GP | Handled by `secureChannel.processSecurity()` |
| EXTERNAL AUTHENTICATE | `0x82` | GP | Handled by `secureChannel.processSecurity()` |

Instance fields and `new byte[]`/objects in `install()` or the constructor live in persistent EEPROM and survive power loss. The APDU buffer is transient and is valid only for the current command. Do not allocate arrays or objects in `process()`: EEPROM is small and cannot be freed, so repeated allocation in command handling can exhaust it in ways that are easy to miss. For scratch space, use a transient array such as `JCSystem.makeTransientByteArray(..., JCSystem.CLEAR_ON_DESELECT)`. `process()` must not throw uncaught exceptions.

## How to change things

### Add an APDU command

1. Define `INS_FOO`, add a `case` in `process()`, and keep CLA/INS/SW handling consistent on both sides. Forward GlobalPlatform SCP commands to `secureChannel.processSecurity()` as in the existing code.
2. Read incoming data, write the response into the APDU buffer, and send it. For commands that may arrive in chunks, keep receiving until all data has been read.
3. Mirror the constant in the host task. Use `session.transmit()` for authenticated commands and the raw channel only before authentication or for negative tests.
4. Exercise it from the verification task for this template.

```java
import javacard.framework.APDU;
import javacard.framework.Applet;
import javacard.framework.ISO7816;
import javacard.framework.ISOException;
import javacard.framework.JCSystem;
import javacard.framework.Util;
import org.globalplatform.GPSystem;
import org.globalplatform.SecureChannel;

public final class HelloApplet extends Applet {
    // Constants: use `static final`; Java Card does not support a general static initializer block.
    private static final byte CLA_CINPO = (byte) 0x80;
    private static final byte INS_FOO = (byte) 0x20;

    private SecureChannel secureChannel;
    private final byte[] scratch;

    private HelloApplet() {
        secureChannel = GPSystem.getSecureChannel();
        // Scratch RAM: use JCSystem.makeTransientByteArray, not `new byte[]` in process().
        scratch = JCSystem.makeTransientByteArray((short) 64, JCSystem.CLEAR_ON_DESELECT);
    }

    public static void install(byte[] bArray, short bOffset, byte bLength) {
        new HelloApplet().register(bArray, (short) (bOffset + 1), bArray[bOffset]);
    }

    public void process(APDU apdu) {
        if (selectingApplet()) {
            return;
        }

        byte[] buffer = apdu.getBuffer();
        if (buffer[ISO7816.OFFSET_CLA] != CLA_CINPO) {
            ISOException.throwIt(ISO7816.SW_CLA_NOT_SUPPORTED);
        }

        byte ins = buffer[ISO7816.OFFSET_INS];
        if (ins == SecureChannel.INS_INITIALIZE_UPDATE || ins == SecureChannel.INS_EXTERNAL_AUTHENTICATE) {
            secureChannel.processSecurity(apdu);
            return;
        }

        switch (ins) {
            case INS_FOO:
                handleFoo(apdu);
                return;
            default:
                ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
        }
    }

    private void handleFoo(APDU apdu) {
        byte[] buffer = apdu.getBuffer();
        short bytesRead = apdu.setIncomingAndReceive();
        short offsetCData = apdu.getOffsetCdata();
        short total = 0;

        // For extended/segmented input, loop until all incoming data has been read.
        while (bytesRead > 0) {
            Util.arrayCopyNonAtomic(buffer, offsetCData, scratch, total, bytesRead);
            // Alternative APIs: Util.arrayCopyNonAtomic / arrayFillNonAtomic / arrayCompare.
            total += bytesRead;
            bytesRead = apdu.receiveBytes(offsetCData);
        }

        Util.arrayCopyNonAtomic(scratch, (short) 0, buffer, (short) 0, total);
        apdu.setOutgoingAndSend((short) 0, total);
        // Errors: use ISOException.throwIt(sw). Do not throw `new Exception()` or use try-with-resources.
    }
}
```

### Add a task kind

1. `host/<kind>/YourTask.java` with `package <kind>;`.
2. `@CardTaskDef("<kind>") public final class YourTask extends CardTask`, with a public no-argument constructor.
3. Run it: `./gradlew cinpoRun --args='--task=<kind>'`.

Task API details are in [Writing tasks](https://github.com/AokiApp/cinpo/blob/main/docs/tasks.md).

### Add or change an authenticated command

Check `(secureChannel.getSecurityLevel() & SecureChannel.AUTHENTICATED) != 0` and throw `SW_SECURITY_STATUS_NOT_SATISFIED` if not met. Add two tests: unauthenticated on the raw channel must return `6982`, authenticated through `session.transmit()` must succeed. A command with only the positive test is half-tested.

## The host tasks at a glance

- **`host/provision/HelloProvision.java`** (`@CardTaskDef(value = "provision", order = 100)`): SELECTs the instance AID from the manifest and verifies `HELLO`.
- **`host/test/HelloTest.java`** (`@CardTaskDef(value = "test", order = 100)`): rejects unauthenticated `SHLO` (`6982`), authenticates SCP03, then verifies `ECHO` and `SHLO` through the session.

`HelloTest` assumes `provision` ran first (applet selected). Use `--test`.

## Hard rules

1. **AIDs come from the manifest, never from literals.** Use the inherited `manifest` field and read the AID from `manifest.applets()`.
2. **Task classes** must be `public final` classes, have a `public` no-argument constructor, extend `app.aoki.cinpo.task.CardTask`, and carry `@CardTaskDef("<kind>")`. The package conventionally matches the directory under `host/`, but discovery is by annotation.
3. **Check status words.** After every APDU, require the expected SW; otherwise throw `IllegalStateException` with the SW (or, in a read-only probe, report it explicitly).
4. **`manifest.yaml` is the source of truth.** After editing it, `basePackage`, the applet `package`, `package.name`, `applets[0].className`, and the AIDs must agree.
5. **If verification cannot run here** (no `vendor/`, no bundle, no card, no network), say so — do not report success. A check that could not run is not a passing check.
6. **Do not guess.** Read `manifest.yaml` → `applet/` → `host/` → the [docs](https://github.com/AokiApp/cinpo/tree/main/docs); if the answer is not there, say so.

- **Use `SecureChannelSession` for authenticated traffic.** Do not hand-roll SCP03 wrapping, MAC chaining, or key derivation.
- **Keep the negative test.** Unauthenticated `SHLO` returning `6982` is required.
- **Keep the companion bundle consistent.** Manifest `id`, `jar`, and `exports` must name the same staged version.

## Do not

- Do not commit anything under `vendor/` or any real SCP03 keys.
- Do not bypass the secure session for commands the applet marks authenticated.
- Do not delete the unauthenticated `6982` test or weaken `HelloTest`.
- Do not set `toolSdkVersion` to anything other than `26.0`.
- Do not add a `main()` method or a shell script to run tasks — CINPO runs tasks via `@CardTaskDef`.

## Definition of done

```bash
./gradlew cinpoRun --args='--test'
```

must pass: it installs the applet, runs `provision`, rejects unauthenticated `SHLO`, authenticates SCP03, and verifies `ECHO` and `SHLO` through the session. On failure, check `vendor/` and the companion bundle first, then profile keys/security level, then the failing SW; add `--debug` for APDU traffic.

If the check cannot run here, say so. If the change alters a contract (CLA, INS, AID, security level, key version, bundle version), update the test and the docs in the same change.

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

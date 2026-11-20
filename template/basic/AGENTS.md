# AGENTS.md

Rules for agents (and humans) working in this project. Follow them literally.

## What this project is

**CINPO** (**C**ard **IN**stallation and **P**rovision **O**rchestrator), "Spring Boot for Java Card", is a framework for installing and provisioning Java Card applets. You write host-side tasks as ordinary Java classes, annotate them (`@CardTaskDef`), extend `CardTask`, and CINPO handles manifest validation, CAP compilation/conversion, installation on the JCDK simulator or a real card, APDU transport, and GlobalPlatform secure-channel authentication. No `main()`, build scripts, or ad-hoc Gradle tasks — `./gradlew cinpoRun` does it all. Repository: [github.com/AokiApp/cinpo](https://github.com/AokiApp/cinpo). Documentation: [docs/](https://github.com/AokiApp/cinpo/tree/main/docs).

This project, generated from the `basic` starter template, is a Java Card applet (`applet/HelloApplet.java`) plus host-side tasks (`host/`); `provision` runs by default, `test` with `--test`. It shows one flow: issue an X.509 leaf certificate on the host, write it into the applet's storage, read it back and verify it. Treat that flow as the contract and keep both ends consistent.

## The two worlds

| | Card world (`applet/`) | Host world (`host/`) |
|---|---|---|
| Runs on | Java Card chip / JCDK simulator | JVM (development machine / production) |
| Language | Java Card subset (3.0.4 API) | Java 21 |
| Types | `byte`, `short`, `boolean`, `byte[]` | the full Java type system |
| Memory | persistent EEPROM (survives power loss); transient APDU buffer per command | ordinary heap |
| Libraries | `javacard.framework.*` only | anything (here: Bouncy Castle) |

The only boundary between them is the APDU protocol: do not import applet classes from host code or vice versa. Shared knowledge (CLA `0x80`, the `INS_*` bytes) is duplicated on purpose; if you change one side, change the other in the same change.

## Before you change

1. Read `manifest.yaml`: package name, AIDs, `toolSdkVersion`, `targetApiVersion` (and companion bundles, if any). It is the source of truth.
2. Find both sides of the contract: the applet handler and the host task that sends the command. Change both in one edit.
3. Make the smallest change that proves the behavior, then run the verification command for this template.
4. If the change alters a contract (CLA, INS, AID, security level, chunk size, certificate subject), update the test/probe check and the docs in the same change.

## Commands and how a run works

- `./gradlew cinpoRun` — validates `manifest.yaml`, compiles and converts the applet, (re)installs it, then runs resolved task kinds in order (default: `provision`).
- `./gradlew cinpoRun --args='--test'` — runs `provision` then `test`.
- `./gradlew cinpoRun --args='--skip-install --test'` — rerun tasks without reinstalling (host-only changes).
- `./gradlew cinpoRun --args='--debug --test'` — verbose APDU/task logging.
- `./gradlew cinpoJar` — build the self-contained production JAR.
- On Windows use `.\gradlew.bat`. Without the Oracle archives in `vendor/`, `cinpoRun` and `cinpoJar` fail immediately — a setup failure, not a code failure (see [vendor setup](https://github.com/AokiApp/cinpo/blob/main/docs/vendor.md)).

At compile time an annotation processor validates and indexes `@CardTaskDef` classes into `META-INF/cinpo/<kind>`. At runtime, for each selected kind: discover → instantiate (no-arg constructor) → populate inherited dependency fields → call `run()`. An unchecked exception aborts the pipeline and exits non-zero. See [Writing tasks](https://github.com/AokiApp/cinpo/blob/main/docs/tasks.md#how-it-works).

## The applet at a glance (`applet/HelloApplet.java`)

CLA `0x80`, selected by `instanceAid`. The applet must return `SW_CLA_NOT_SUPPORTED` / `SW_INS_NOT_SUPPORTED` for wrong CLA/unknown INS.

| Command | INS | P1/P2 | Behavior |
|---------|-----|-------|----------|
| ECHO | `0x01` | — | Receives data, sends it back unchanged |
| HELLO | `0x02` | — | Returns `"Hello, CINPO!"` |
| PUT_CERTIFICATE | `0x10` | offset | Appends a chunk at `offset`; `offset == 0` resets the stored length (220-byte chunks from host) |
| GET_CERTIFICATE | `0x11` | offset | Returns up to 240 bytes starting at `offset` |
| GET_CERTIFICATE_LENGTH | `0x12` | — | Returns the stored length as 2 bytes |

Certificate storage is one persistent `byte[2048]`. Instance fields and `new byte[]`/objects in `install()` or the constructor live in persistent EEPROM and survive power loss. The APDU buffer is transient and is valid only for the current command. Do not allocate arrays or objects in `process()`: EEPROM is small and cannot be freed, so repeated allocation in command handling can exhaust it in ways that are easy to miss. For scratch space, use a transient array such as `JCSystem.makeTransientByteArray(..., JCSystem.CLEAR_ON_DESELECT)`. `process()` must not throw uncaught exceptions.

## How to change things

### Add an APDU command

1. Define `INS_FOO`, add a `case` in `process()`, and keep CLA/INS/SW handling consistent on both sides.
2. Read incoming data, write the response into the APDU buffer, and send it. For commands that may arrive in chunks, keep receiving until all data has been read.
3. Mirror the constant in the host task: `channel.transmit(new CommandApdu(CLA, INS_FOO, p1, p2, data, le))`.
4. Exercise it from the verification task for this template.

```java
import javacard.framework.APDU;
import javacard.framework.Applet;
import javacard.framework.ISO7816;
import javacard.framework.ISOException;
import javacard.framework.JCSystem;
import javacard.framework.Util;

public final class HelloApplet extends Applet {
    // Constants: use `static final`; Java Card does not support a general static initializer block.
    private static final byte CLA_CINPO = (byte) 0x80;
    private static final byte INS_FOO = (byte) 0x20;

    private final byte[] persistentData;
    private final byte[] scratch;

    private HelloApplet() {
        persistentData = new byte[64]; // Persistent EEPROM: allocate in install()/constructor only.
        // Scratch RAM: use JCSystem.makeTransientByteArray, not `new byte[]` in process().
        scratch = JCSystem.makeTransientByteArray((short) 64, JCSystem.CLEAR_ON_DESELECT);
    }

    public static void install(byte[] bArray, short bOffset, byte bLength) {
        new HelloApplet().register();
    }

    public void process(APDU apdu) {
        if (selectingApplet()) {
            return;
        }

        byte[] buffer = apdu.getBuffer();
        if (buffer[ISO7816.OFFSET_CLA] != CLA_CINPO) {
            ISOException.throwIt(ISO7816.SW_CLA_NOT_SUPPORTED);
        }

        switch (buffer[ISO7816.OFFSET_INS]) {
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

## The host tasks at a glance

- **`host/provision/HelloProvision.java`** (`@CardTaskDef(value = "provision", order = 100)`): SELECTs the instance AID from the manifest, verifies `HELLO`, issues a leaf with `CertificateIssuer.issueLeafCertificate()`, and writes it in 220-byte chunks.
- **`host/test/HelloTest.java`** (`@CardTaskDef(value = "test", order = 100)`): SELECTs the applet, verifies `ECHO`, reads the certificate length and bytes, parses it as X.509, and asserts subject `CN=CINPO Template Leaf`.

The issuer's subject/validity and the test's assertions are coupled; change both together.

## Hard rules

1. **AIDs come from the manifest, never from literals.** Use the inherited `manifest` field and read the AID from `manifest.applets()`.
2. **Task classes** must be `public final` classes, have a `public` no-argument constructor, extend `app.aoki.cinpo.task.CardTask`, and carry `@CardTaskDef("<kind>")`. The package conventionally matches the directory under `host/`, but discovery is by annotation.
3. **Check status words.** After every APDU, require the expected SW; otherwise throw `IllegalStateException` with the SW (or, in a read-only probe, report it explicitly).
4. **`manifest.yaml` is the source of truth.** After editing it, `basePackage`, the applet `package`, `package.name`, `applets[0].className`, and the AIDs must agree.
5. **If verification cannot run here** (no `vendor/`, no bundle, no card, no network), say so — do not report success. A check that could not run is not a passing check.
6. **Do not guess.** Read `manifest.yaml` → `applet/` → `host/` → the [docs](https://github.com/AokiApp/cinpo/tree/main/docs); if the answer is not there, say so.

## Do not

- Do not commit anything under `vendor/`.
- Do not use `profile/jcdksim.yaml`'s well-known test keys on a real card.
- Do not set `toolSdkVersion` to anything other than `26.0`.
- Do not add a `main()` method or a shell script to run tasks — CINPO runs tasks via `@CardTaskDef`.
- Do not weaken `HelloTest` or the certificate/protocol assertions.

## Definition of done

```bash
./gradlew cinpoRun --args='--test'
```

must pass: it installs the applet, provisions the certificate, and verifies the echo and certificate round-trip. On failure, check `vendor/` first, then the first failing task's exception/SW; add `--debug` for APDU traffic. See the [failure model](https://github.com/AokiApp/cinpo/blob/main/docs/tasks.md#failure-model).

If the check cannot run here, say so. If the change alters a contract, update the test and the docs in the same change.

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

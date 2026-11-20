# Writing host-side tasks

A task is a small Java program that CINPO runs on the host side of the APDU link — against the simulator during development, or against a physical card in production. Tasks are how you turn "the applet is installed" into actual card state.

## What you can build with tasks

- **Provision credentials at install time** — set a PIN, store keys, issue a certificate and write it to the applet. The default `provision` kind runs after installation.
- **Factory-provision production cards** — the appliance JAR runs the same task unattended, on any machine, even inside an air gap. No Gradle, no simulator, no network.
- **Verify the card after provisioning** — write a `test` task that SELECTs the applet and checks status words. `--test` runs provisioning and verification in one shot.
- **Maintain cards already in the field** — read inputs after `--` and update keys or configuration. Open your own secure channel session when the card demands authenticated commands.
- **Probe unknown cards** — write a custom kind (`@CardTaskDef("probe")`) that selects an application and dumps its card data, AIDs, and GlobalPlatform state.
- **Multi-applet orchestration** — iterate `manifest.applets()` and provision every applet in one package in a single run.
- **Template-specific workflows** — pass your own arguments after `--` (e.g. `--db ./cards.db --record sample01`) and interpret them however your template wants.

## How it works

### Declaring a task

A task class must:

1. be `public final`
2. have a `public` no-argument constructor
3. extend `app.aoki.cinpo.task.CardTask`
4. be annotated with `@CardTaskDef`

```java
@CardTaskDef("provision")
public final class SetupPin extends CardTask {

    @Override
    public void run() {
        // your logic
    }
}
```

The package name conventionally matches the directory under `host/` (e.g. a task in `host/provision/` is `package provision;`). This is not enforced: the annotation processor indexes the class by `@CardTaskDef`, not by its source path, so a class in `host/probe/` can declare `package test;` if you have a reason.

### Discovery and indexing

At compile time, an annotation processor validates every `@CardTaskDef` class and indexes it into `META-INF/cinpo/<kind>` — one index file per kind. At runtime, the loader reads those files from the classpath, instantiates each class, populates its inherited dependency fields, and runs it. Kind names are arbitrary strings; `provision` and `test` are only conventions.

### Selection and execution order

- The default kind is `provision`. It runs when no `--task` or `--test` is given.
- `--task=<kind>` is repeatable and preserves command-line order. Duplicate kinds are rejected.
- `--test` expands to `provision` followed by `test`. `--task` and `--test` cannot be combined.
- `--skip-install` skips the install phase and runs only the task phases.
- Within a kind, tasks are sorted by `order()` (ascending; default `0`), then by class name.
- The pipeline is: init → install (unless `--skip-install`) → one phase per resolved kind, in the resolved order.

```bash
./gradlew cinpoRun --args='--test'                          # provision then test
./gradlew cinpoRun --args='--task=provision --task=test'    # explicit, same order
./gradlew cinpoRun --args='--task=probe'                    # only the probe kind
./gradlew cinpoRun --args='--skip-install --task=provision' # re-run tasks without reinstalling
```

### Task dependencies

`CardTask` exposes the runtime dependencies as inherited `protected` fields. The loader populates them before `run()` is called:

| Field | Type | What it is | Typical use |
|-------|------|-----------|-------------|
| `channel` | `ApduChannel` | Live channel to the card/simulator | Talking to the applet |
| `manifest` | `AppletManifest` | Parsed `manifest.yaml` | Looping over applets, reading AIDs |
| `profile` | `Profile` | Active runtime profile | Conditional logic per environment |
| `secureChannelProfile` | `SecureChannelProfile` | SCP key material from the profile | Opening your own secure sessions |
| `taskArguments` | `TaskArguments` | Raw arguments after `--` | Template-specific selectors |

### Passing arguments

Everything after `--` is forwarded verbatim to tasks and exposed as `TaskArguments`:

```bash
./gradlew cinpoRun --args='write -- --record sample01 --db ./cards.db'
```

```java
@Override
public void run() {
    String recordId = taskArguments.firstValue("record").orElse("sample01");   // Optional<String>
    String dbPath = taskArguments.firstValue("db").orElse("host/data/cards.db");
    boolean audit = taskArguments.hasFlag("audit");                              // boolean
    List<String> tags = taskArguments.values("tag");                             // List<String>
}
```

`--record` without a following value throws `IllegalArgumentException`. Both forms are accepted: `--db path` and `--db=path`. Argument names are matched as-is (no normalization beyond trimming).

### Failure model

An exception from `run()` aborts the pipeline and the command exits with a non-zero status. Make your checks explicit — fail fast with `IllegalStateException`:

```java
private void transmitOk(CommandApdu command) {
    ResponseApdu response = channel.transmit(command);
    if (response.sw() != 0x9000) {
        throw new IllegalStateException("APDU failed: SW=%04X".formatted(response.sw()));
    }
}
```

Retrying is usually wrong for APDU commands — a failing status word means the card rejected the command, not that the transport hiccuped.

### Bundling data with `host/`

Non-Java files under `host/` are packaged as classpath resources (useful for fixture databases, certificates, templates). Some drivers read resource streams directly; others require copying the resource to a temporary file first. If a bundled file is read-only, treat it as such.

## API reference

### `app.aoki.cinpo.apdu.ApduChannel`

Interface. The live connection to the card/simulator. Never instantiate; use the inherited `channel` field.

| Method | Description |
|--------|-------------|
| `ResponseApdu transmit(CommandApdu capdu)` | Send an APDU command; returns the full response (data + SW1/SW2). |
| `void reset()` | Reset the transport/session state ("cold restart"). Persistent card data is preserved. |
| `void close()` | Release runtime resources. |

### `app.aoki.cinpo.apdu.CommandApdu`

Value type for an outgoing APDU.

- Constructors: `CommandApdu(int cla, int ins, int p1, int p2)`, `(int cla, int ins, int p1, int p2, byte[] data)`, `(int cla, int ins, int p1, int p2, byte[] data, Integer le)`
- Accessors: `cla()`, `ins()`, `p1()`, `p2()` (each `int`), `data()` (`byte[]`), `le()` (`Integer`)

### `app.aoki.cinpo.apdu.ResponseApdu`

Value type for an incoming APDU response.

| Member | Description |
|--------|-------------|
| `data()` | Response data, `byte[]` |
| `sw()` | Status word `SW1 << 8 \| SW2`, as an `int` (e.g. `0x9000`) |
| `sw1()` / `sw2()` | Split status bytes |
| `toBytes()` | Raw response bytes |
| `toHexString()` | Hex representation |
| `ResponseApdu.fromBytes(byte[])` / `fromHexString(String)` | Static constructors |

### `app.aoki.cinpo.apdu.Iso7816Commands`

Static factories for ISO 7816-4 commands and constants.

| Constant | Value | Constant | Value |
|----------|-------|----------|-------|
| `INS_VERIFY` | `0x20` | `INS_PUT_DATA` | `0xDA` |
| `INS_CHANGE_REFERENCE_DATA` | `0x24` | `INS_READ_BINARY` | `0xB0` |
| `INS_GET_CHALLENGE` | `0x84` | `INS_SELECT` | `0xA4` |
| `INS_GET_DATA` | `0xCA` | `INS_UPDATE_BINARY` | `0xD6` |

`SELECT_OPTION_RETURN_NO_DATA = 0x0C`, `SELECT_OPTION_RETURN_FCI_TEMPLATE = 0x04`, `SELECT_OPTION_FIRST_OR_ONLY_OCCURRENCE = 0x00`, `SELECT_METHOD_BY_DF_NAME = 0x04`.

| Method | Builds |
|--------|--------|
| `selectDf(byte[] dfName)` / `selectDf(byte[] dfName, int selectOptions[, Integer le])` | SELECT by DF name |
| `selectEf(int fileId[, boolean requestFci[, Integer le]])` | SELECT element file |
| `readBinary(int offset, int le)` | INS `0xB0` |
| `readBinaryBySfi(int sfi, int offset, int le)` | INS `0xB0` via SFI |
| `updateBinary(int offset, byte[] data)` | INS `0xD6` |
| `updateBinaryBySfi(int sfi, int offset, byte[] data)` | INS `0xD6` via SFI |
| `verify(int referenceDataNumber, byte[] verificationData)` | INS `0x20` |
| `changeReferenceData(int referenceDataNumber, byte[] newReferenceData)` | INS `0x24` |
| `getChallenge(int le)` | INS `0x84` |
| `getData(int tag[, Integer le])` | INS `0xCA` |
| `putData(int tag, byte[] value)` | INS `0xDA` |

### `app.aoki.cinpo.config.AppletManifest`

Record describing `manifest.yaml`.

| Accessor | Type | Notes |
|----------|------|-------|
| `basePackage()` | `String` | declared package |
| `toolSdkVersion()` | `String` | JCDK version |
| `targetApiVersion()` | `String` | target Java Card API |
| `packageName()` | `String` | CAP package name |
| `loadFileAid()` | `byte[]` | the `package.aid` |
| `version()` | `String` | package version |
| `applets()` | `List<AppletEntry>` | declared applets |
| `companionBundles()` | `List<CompanionBundle>` | declared bundles |

`AppletEntry` records: `id()` (`String`), `className()` (`String`), `classAid()` (`byte[]`), `instanceAid()` (`byte[]`), `privileges()` (`byte[]`).

`CompanionBundle` records: `id()`, `jar()`, `exports()` (all `String`).

### `app.aoki.cinpo.config.Profile`

Record describing a profile (`profile/<name>.yaml`).

| Accessor | Type | Notes |
|----------|------|-------|
| `name()` | `String` | the profile name, e.g. `jcdksim`, `mycard` |
| `runtime()` | `String` | `jcdksim` (simulator) or `pcsc` (physical card) |
| `secureChannel()` | `ScpConfig` | SCP configuration |

### `app.aoki.cinpo.config.ScpConfig`

Record; carries the profile-declared SCP configuration. `toSecureChannelProfile()` turns it into a runtime `SecureChannelProfile`.

| Component | Type | Notes |
|-----------|------|-------|
| `protocol` | `String` | `scp02` or `scp03` |
| `isdAid` | `byte[]` | ISD AID |
| `keyVersionNumber` | `int` (0x00–0xFF) | Key Version Number |
| `keyIdentifier` | `int` (0x00–0xFF) | Key Identifier |
| `securityLevel` | `int` | security level bits |
| `encKey` | `byte[]` | 16, 24 or 32 bytes |
| `macKey` | `byte[]` | matches `encKey` length |
| `dekKey` | `byte[]` | matches `encKey` length |

### `app.aoki.cinpo.gp.scp.SecureChannelProfile`

Record; the runtime SCP key material. The loader derives it from the active `Profile` and assigns it to `CardTask.secureChannelProfile`.

- Variant: `SecureChannelProfile.Scp.SCP02` / `SecureChannelProfile.Scp.SCP03`
- Static factories: `scp03(securityDomainAid, keyVersionNumber, keyIdentifier, securityLevel, encKey, macKey, dekKey)`, `scp02(securityDomainAid, keyVersionNumber, keyIdentifier, securityLevel, encKey, macKey, dekKey, hostChallenge, scp02SequenceCounter)`

| Accessor | Type |
|----------|------|
| `securityDomainAid()` / `encKey()` / `macKey()` / `hostChallenge()` / `scp02SequenceCounter()` | `byte[]` |
| `dekKey()` | `byte[]` or `null` |

### `app.aoki.cinpo.gp.scp.SecureChannelSession`

Task-scoped secure channel; created from a channel and profile; short-lived.

| Member | Description |
|--------|-------------|
| `SecureChannelSession.create(ApduChannel, SecureChannelProfile)` | Static factory |
| `authenticate()` | Run the mutual authentication handshake |
| `transmit(CommandApdu)` | Send a command over the open channel (unwrapped/protected) |
| `isAuthenticated()` / `isAborted()` / `isClosed()` | State queries |
| `close()` | Terminate the session |

### `app.aoki.cinpo.task.TaskArguments`

Record; the arguments after `--`, verbatim.

| Member | Description |
|--------|-------------|
| `firstValue(String optionName)` | `Optional<String>` — first occurrence's value |
| `values(String optionName)` | `List<String>` — all matching values |
| `hasFlag(String optionName)` | `boolean` — flag present? |
| `isEmpty()` / `empty()` | size query / empty instance |
| `raw()` (record component) | `List<String>` |

`--name value` and `--name=value` are both accepted. Missing value throws `IllegalArgumentException`.

### `app.aoki.cinpo.task.CardTask`, `app.aoki.cinpo.task.CardTaskDef`

- `CardTask` — abstract base class; provides the `channel`, `manifest`, `profile`, `secureChannelProfile`, and `taskArguments` fields; declares `void run()`.
- `@CardTaskDef(String value, int order = 0)` — declares a kind and an ordering weight.

## Worked examples

All examples assume:

```java
import app.aoki.cinpo.apdu.CommandApdu;
import app.aoki.cinpo.apdu.Iso7816Commands;
import app.aoki.cinpo.apdu.ResponseApdu;
import app.aoki.cinpo.task.CardTask;
import app.aoki.cinpo.task.CardTaskDef;
import java.nio.charset.StandardCharsets;
```

### A provision task that SELECTs and writes the PIN

```java
package provision;

@CardTaskDef("provision")
public final class SetupPin extends CardTask {

    @Override
    public void run() {
        transmitOk(Iso7816Commands.selectDf(manifest.applets().get(0).instanceAid()));
        transmitOk(Iso7816Commands.changeReferenceData(0x01, "1234".getBytes(StandardCharsets.US_ASCII)));
    }

    private void transmitOk(CommandApdu command) {
        ResponseApdu response = channel.transmit(command);
        if (response.sw() != 0x9000) {
            throw new IllegalStateException("APDU failed: SW=%04X".formatted(response.sw()));
        }
    }
}
```

### A test task

```java
package test;

@CardTaskDef("test")
public final class VerifyPinWorks extends CardTask {

    @Override
    public void run() {
        byte[] aid = manifest.applets().get(0).instanceAid();
        channel.transmit(Iso7816Commands.selectDf(aid));

        ResponseApdu response = channel.transmit(
            Iso7816Commands.verify(0x01, "1234".getBytes(StandardCharsets.US_ASCII)));
        if (response.sw() != 0x9000) {
            throw new IllegalStateException("PIN verify failed: SW=%04X".formatted(response.sw()));
        }
    }
}
```

### Multi-applet provisioning

```java
package provision;

@CardTaskDef("provision")
public final class ProvisionAll extends CardTask {

    @Override
    public void run() {
        for (var applet : manifest.applets()) {
            ResponseApdu response = channel.transmit(Iso7816Commands.selectDf(applet.instanceAid()));
            if (response.sw() != 0x9000) {
                throw new IllegalStateException("SELECT failed for applet " + applet.id() + ": SW=%04X".formatted(response.sw()));
            }
            // provision the selected applet
        }
    }
}
```

### A task inside its own secure channel session

```java
package provision;

import app.aoki.cinpo.gp.scp.SecureChannelSession;

@CardTaskDef(value = "provision", order = 200)
public final class WriteKeys extends CardTask {

    @Override
    public void run() {
        SecureChannelSession session = SecureChannelSession.create(channel, secureChannelProfile);
        session.authenticate();
        try {
            session.transmit(new CommandApdu(0x80, 0xD4, 0x00, 0x00, new byte[] { /* key material */ }));
        } finally {
            session.close();
        }
    }
}
```

The `order = 200` ensures this task runs after the default-order provision tasks (default `0`).

### A custom kind

```java
package probe;

@CardTaskDef("probe")
public final class CardProbe extends CardTask {

    @Override
    public void run() {
        ResponseApdu response = channel.transmit(new CommandApdu(0x80, 0xA0, 0x00, 0x00));
        System.out.printf("SW=%04X, data=%02X bytes%n", response.sw(), response.data().length);
    }
}
```

Run it with:

```bash
./gradlew cinpoRun --args='--task=probe'
./gradlew cinpoRun --args='--skip-install --task=probe'   # re-run without reinstalling
```

## Where to look next

- Compilable examples in the templates: `template/basic/host/provision/HelloProvision.java`, `template/basic/host/test/HelloTest.java`, `template/probe/host/probe/ProbeTask.java`.
- [Profiles](profiles.md) — `jcdksim` (simulator), `jcardengine` (in-process engine), and `pcsc` (real card reader), `scp02` vs `scp03`.
- Framework changes are verified before publication by the `Release` workflow, which builds the `basic` template against the staged repository.

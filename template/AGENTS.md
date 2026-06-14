# AGENTS.md

> AI agent instructions for CINPO application projects generated from this template.

## Project Overview

This is a **CINPO** (Card INstallation and Provision Orchestrator) application project — a JavaCard applet with annotation-driven host-side provisioning and testing tasks. CINPO acts like "Spring Boot for JavaCard": you declare tasks with annotations, inject dependencies, and the framework handles GlobalPlatform authentication, CAP compilation, simulator lifecycle, and APDU transport.

**What this project produces**: A smart card applet (runs on a physical JavaCard or simulator) plus host-side Java programs that communicate with the applet via APDU commands.

## Directory Structure

```
.
├── AGENTS.md              # This file — AI agent instructions
├── manifest.yaml          # Applet package declaration (AIDs, SDK version)
├── build.gradle           # Gradle build (CINPO plugin + dependencies)
├── settings.gradle        # Gradle settings (project name)
├── applet/                # JavaCard applet source (runs ON the card)
│   └── *.java             # JavaCard API only — no java.util, no java.io
├── host/                  # Host-side tasks (runs on the PC/server)
│   ├── provision/         # Default provisioning tasks (selected by `write` or `--task=provision`)
│   ├── test/              # Verification tasks (selected by `--test` or `--task=test`)
│   └── <custom>/          # Any additional task kind directory
├── profile/               # Runtime profile overrides
│   └── jcdksim.yaml       # Oracle JCDK simulator profile (dev-only keys)
└── vendor/                # Oracle JavaCard SDK files (gitignored, required at runtime)
```

## Prerequisites

### Vendor SDK Files

The Oracle JavaCard Development Kit is **required** to compile applets and run the simulator. These are proprietary and not distributed with this project.

- The parent repository contains an encrypted `vendor.zip`
- Extract with: `VENDOR_PASSWORD=<password> ../extract_vendor.sh` (from parent), then `cp -a ../vendor ./vendor`
- Or place extracted vendor files directly in `./vendor/`
- **Without vendor files, `cinpoRun` and `cinpoJar` will fail**

### Java 21

The host-side code requires JDK 21 (configured via Gradle toolchain). The applet code is compiled separately by the JavaCard SDK tools.

### Gradle Credentials

CINPO framework artifacts resolve from GitHub Packages or GitHub Pages:

```properties
# In ~/.gradle/gradle.properties or as environment variables
gpr.user=<github-username>
gpr.key=<github-token-with-packages:read>
```

Or via environment: `GITHUB_ACTOR` and `GITHUB_TOKEN`. If neither is set, resolution falls back to GitHub Pages (public, no auth needed).

## Build & Run Commands

All commands are executed from this project directory. The Gradle wrapper lives one level up (`../gradlew`) when working inside a monorepo checkout; standalone projects use `./gradlew`.

```bash
# Run default provisioning (install applet + run provision tasks)
../gradlew cinpoRun

# Run provisioning then test verification
../gradlew cinpoRun --args='--test'

# Run specific task kinds
../gradlew cinpoRun --args='--task=provision --task=test'

# Skip applet installation (re-run tasks only against already-installed applet)
../gradlew cinpoRun --args='--skip-install --task=provision'

# Pass template-specific arguments after --
../gradlew cinpoRun --args='write -- --record sample01'

# Build self-contained fat JAR
../gradlew cinpoJar

# Run fat JAR
java -jar build/libs/cinpo-appliance.jar write --test

# Debug mode (verbose APDU logging)
../gradlew cinpoRun --args='--debug --test'
```

## Architecture & Dual-World Model

### The Two Worlds

This project spans **two execution environments** that communicate via APDU byte-level protocol:

| | Card World (`applet/`) | Host World (`host/`) |
|---|---|---|
| **Runs on** | JavaCard chip / simulator | JVM on PC/server |
| **Language** | JavaCard 3.0.4 (subset of Java) | Java 21 (full JVM) |
| **Types** | `byte`, `short`, `byte[]` only | Full Java type system |
| **Memory** | Persistent EEPROM, ~32KB | Unlimited heap |
| **Communication** | Receives APDUs, sends responses | Sends APDUs via `ApduChannel` |
| **Libraries** | `javacard.framework.*` only | Any Maven dependency |

### The Contract Between Worlds

The applet and host tasks share an implicit **APDU protocol contract**:

```
┌──────────────┐    APDU Command     ┌──────────────┐
│  Host Task   │ ──────────────────► │   Applet     │
│  (Java 21)   │                     │  (JavaCard)  │
│              │ ◄────────────────── │              │
└──────────────┘    APDU Response    └──────────────┘
```

**This contract is defined by matching constants on both sides:**

- **CLA** (Class byte): identifies your applet's command set (e.g., `0x80`)
- **INS** (Instruction byte): identifies each command (e.g., `0x01` = ECHO, `0x02` = HELLO)
- **P1, P2**: parameter bytes (often used as 16-bit offset)
- **Data format**: the byte layout of command data and response data

When you add a new command, you **must** update both sides to match.

## How To: Common Development Tasks

### Add a New APDU Command

1. **Define the INS constant in the applet** (`applet/YourApplet.java`):
   ```java
   private static final byte INS_MY_COMMAND = (byte) 0x20;
   ```

2. **Add case to the `process()` switch**:
   ```java
   case INS_MY_COMMAND:
       handleMyCommand(apdu, buffer);
       return;
   ```

3. **Implement the applet handler**:
   ```java
   private void handleMyCommand(APDU apdu, byte[] buffer) {
       short received = apdu.setIncomingAndReceive();
       // Process buffer[ISO7816.OFFSET_CDATA] through buffer[OFFSET_CDATA + received - 1]
       // Write response into buffer[0..N]
       apdu.setOutgoingAndSend((short) 0, responseLength);
   }
   ```

4. **Mirror the INS constant in host task** (`host/provision/` or `host/test/`):
   ```java
   private static final int INS_MY_COMMAND = 0x20;
   ```

5. **Call from host**:
   ```java
   ResponseApdu response = channel.transmit(
       new CommandApdu(CLA_MY, INS_MY_COMMAND, p1, p2, inputData, expectedResponseLength));
   ```

### Add a New Task Kind

1. Create a new directory: `host/mytask/`
2. Create the task class:
   ```java
   package mytask;

   import app.aoki.cinpo.task.CardTask;
   import app.aoki.cinpo.task.CardTaskDef;
   import app.aoki.cinpo.task.Inject;
   import app.aoki.cinpo.apdu.ApduChannel;
   import app.aoki.cinpo.config.AppletManifest;

   @CardTaskDef(value = "mytask", order = 100)
   public final class MyCustomTask implements CardTask {
       @Inject private ApduChannel channel;
       @Inject private AppletManifest manifest;

       public MyCustomTask() {}

       @Override
       public void run() {
           // Your logic here
       }
   }
   ```
3. Run with: `../gradlew cinpoRun --args='--task=mytask'`

### Add a New Applet to the Package

1. Create the applet class in `applet/`:
   ```java
   package com.example.mypackage;
   import javacard.framework.*;

   public final class SecondApplet extends Applet {
       public static void install(byte[] buffer, short offset, byte length) {
           byte aidLength = buffer[offset];
           new SecondApplet().register(buffer, (short)(offset + 1), aidLength);
       }
       @Override
       public void process(APDU apdu) { /* ... */ }
   }
   ```

2. Register in `manifest.yaml`:
   ```yaml
   applets:
     - id: main
       className: com.example.mypackage.MainApplet
       classAid: D27600008501010001
       instanceAid: D2760000850101000001
       privilege: "00"
     - id: second
       className: com.example.mypackage.SecondApplet
       classAid: D27600008501010002
       instanceAid: D2760000850101000002
       privilege: "00"
   ```

3. Access from host tasks:
   ```java
   byte[] secondAid = manifest.applets().get(1).instanceAid();
   // or find by id if API supports it
   ```

### Accept Runtime Arguments in Tasks

```java
import app.aoki.cinpo.task.TaskArguments;

@CardTaskDef("provision")
public final class ConfigurableTask implements CardTask {
    @Inject private TaskArguments taskArguments;

    @Override
    public void run() {
        String recordId = taskArguments.firstValue("record").orElse("default");
        String dbPath = taskArguments.firstValue("db").orElse("host/data/cards.db");
        // Use these values in your logic
    }
}
```

Invoke: `../gradlew cinpoRun --args='write -- --record myrecord --db ./mydata.db'`

### Bundle Data Files as Resources

Place non-Java files anywhere under `host/`. They are packaged as classpath resources:

```
host/
├── provision/
│   └── MyTask.java
└── data/
    └── default-records.db
```

Access from task code:
```java
InputStream is = getClass().getClassLoader().getResourceAsStream("data/default-records.db");
```

## Applet Code (`applet/`) — Detailed Rules

### JavaCard Language Restrictions

| Allowed | NOT Allowed |
|---------|-------------|
| `byte`, `short`, `boolean` | `int`, `long`, `float`, `double` |
| `byte[]`, `short[]` | `String`, `Object[]`, generics |
| `javacard.framework.*` | `java.util.*`, `java.io.*`, `java.lang.String` |
| `javacardx.apdu.ExtendedLength` | `java.nio.*`, reflection, threads |
| Static final constants | Dynamic class loading |
| `Util.arrayCopyNonAtomic()` | `System.arraycopy()` |
| `ISOException.throwIt(SW)` | `new Exception()`, try-with-resources |

### Memory Model

- **All instance fields are persistent** (stored in EEPROM, survive power loss)
- **APDU buffer is transient RAM** (only valid during one command)
- **Allocate arrays in constructor only** — `new byte[N]` in `process()` will leak EEPROM on every call
- Use `JCSystem.makeTransientByteArray()` for scratch buffers that don't persist

### APDU Processing Pattern

```java
public void process(APDU apdu) {
    if (selectingApplet()) return;  // Always first

    byte[] buffer = apdu.getBuffer();

    // 1. Validate CLA
    if (buffer[ISO7816.OFFSET_CLA] != MY_CLA) {
        ISOException.throwIt(ISO7816.SW_CLA_NOT_SUPPORTED);
    }

    // 2. Dispatch on INS
    switch (buffer[ISO7816.OFFSET_INS]) {
        case INS_X: handleX(apdu, buffer); return;
        default: ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
    }
}

// Receiving data from host:
private void handleWrite(APDU apdu, byte[] buffer) {
    short len = apdu.setIncomingAndReceive();
    // Data is at buffer[ISO7816.OFFSET_CDATA .. OFFSET_CDATA+len-1]
}

// Sending data to host:
private void handleRead(APDU apdu, byte[] buffer) {
    Util.arrayCopyNonAtomic(myData, (short)0, buffer, (short)0, myDataLen);
    apdu.setOutgoingAndSend((short)0, myDataLen);
}
```

### Status Words

| SW | Meaning | When to use |
|----|---------|-------------|
| `0x9000` | Success | Implicit on normal return |
| `ISO7816.SW_CLA_NOT_SUPPORTED` | Wrong CLA | CLA byte doesn't match |
| `ISO7816.SW_INS_NOT_SUPPORTED` | Unknown INS | INS not in switch |
| `ISO7816.SW_WRONG_LENGTH` | Bad data length | Incoming data too long/short |
| `ISO7816.SW_WRONG_P1P2` | Bad parameters | P1/P2 out of range |
| `ISO7816.SW_CONDITIONS_NOT_SATISFIED` | State error | Operation not allowed now |
| `ISO7816.SW_SECURITY_STATUS_NOT_SATISFIED` | Auth needed | PIN not verified |

## Host-Side Tasks (`host/`) — Detailed Rules

### Task Class Requirements

1. **Must be `public final`**
2. **Must have a public no-argument constructor**
3. **Must implement `CardTask`**
4. **Must have `@CardTaskDef` annotation** with at minimum a `value`
5. **Package name must match the directory** (e.g., files in `host/provision/` → `package provision;`)
6. **No `main()` method** — the framework is the entry point

### Task Execution Lifecycle

```
1. Framework discovers annotated task classes (compile-time index)
2. Tasks are instantiated (no-arg constructor)
3. @Inject fields are populated
4. run() is called
5. Exceptions propagate and abort the pipeline
```

### Error Handling

Throw `IllegalStateException` (or any `RuntimeException`) to abort. The framework prints the error and exits non-zero.

```java
private ResponseApdu transmitOk(CommandApdu command) {
    ResponseApdu response = channel.transmit(command);
    if (response.sw() != 0x9000) {
        throw new IllegalStateException("APDU failed: SW=%04X".formatted(response.sw()));
    }
    return response;
}
```

### Injectable Types Reference

| Type | Description | Typical Use |
|------|-------------|-------------|
| `ApduChannel` | Send/receive APDUs to the card | Every task that talks to the card |
| `AppletManifest` | Parsed `manifest.yaml` (AIDs, package info) | Getting applet AIDs for SELECT |
| `Profile` | Active runtime profile config | Conditional logic per environment |
| `SecureChannelProfile` | SCP key material | Opening additional secure sessions |
| `TaskArguments` | Arguments passed after `--` | Template-specific runtime config |

## Manifest (`manifest.yaml`)

```yaml
schema: cinpo.applet.v1

basePackage: com.example.myapplet        # Java package for applet sources
toolSdkVersion: "26.0"                   # Oracle JavaCard SDK version
targetApiVersion: "3.0.4"                # JavaCard API version to target

package:
  name: com.example.myapplet             # CAP file package identifier
  aid: D276000085010100                  # Package AID (hex, even length)
  version: "1.0"                         # Package version

applets:
  - id: main                             # Logical identifier (for reference)
    className: com.example.myapplet.MainApplet  # Fully qualified class name
    classAid: D27600008501010001         # Class AID (hex, must be under package AID)
    instanceAid: D2760000850101000001    # Instance AID (hex, used in SELECT)
    privilege: "00"                      # Installation privilege byte
```

**AID rules:**
- All AIDs are hex strings with even length
- Class AIDs must be prefixed by the package AID
- Instance AIDs are what you SELECT from host tasks
- AIDs must be globally unique on the card

## Profiles (`profile/`)

```yaml
schema: cinpo.profile.v1
name: jcdksim                    # Profile name (matches filename without .yaml)
runtime: jcresim                 # jcresim = simulator, pcsc = physical reader

secureChannel:
  protocol: scp03                # scp03 (AES, recommended) or scp02 (3DES, legacy)
  isdAid: A000000151000000       # Issuer Security Domain AID
  keyVersionNumber: 16           # Key version on the card
  keyIdentifier: 0               # Key identifier
  securityLevel: 1               # 1=C-MAC, 3=C-MAC+C-ENC
  encKey: "1111..."              # 32-byte hex (64 chars) for SCP03
  macKey: "2222..."              # 32-byte hex (64 chars) for SCP03
  dekKey: "3333..."              # 32-byte hex (64 chars) for SCP03
```

**⚠️ The `jcdksim.yaml` keys are development-only test keys. Never use them on production cards.**

For physical card readers, use `runtime: pcsc` and optionally specify:
```yaml
pcsc:
  reader: "Specific Reader Name"
  excludedReaders:
    - "Built-in Contactless Reader"
```

## Key Imports Quick Reference

### Applet (JavaCard API)

```java
import javacard.framework.APDU;
import javacard.framework.Applet;
import javacard.framework.ISO7816;
import javacard.framework.ISOException;
import javacard.framework.Util;
import javacard.framework.JCSystem;
import javacard.framework.OwnerPIN;
import javacardx.apdu.ExtendedLength;
```

### Host tasks (CINPO API)

```java
import app.aoki.cinpo.apdu.ApduChannel;
import app.aoki.cinpo.apdu.CommandApdu;
import app.aoki.cinpo.apdu.Iso7816Commands;
import app.aoki.cinpo.apdu.ResponseApdu;
import app.aoki.cinpo.config.AppletManifest;
import app.aoki.cinpo.task.CardTask;
import app.aoki.cinpo.task.CardTaskDef;
import app.aoki.cinpo.task.Inject;
import app.aoki.cinpo.task.TaskArguments;
```

## Common Pitfalls & Troubleshooting

| Problem | Cause | Fix |
|---------|-------|-----|
| Build fails with "vendor not found" | Missing Oracle SDK files | Extract vendor.zip or copy vendor/ directory |
| `SW=6D00` at runtime | INS byte not recognized by applet | Check INS constant matches between host and applet |
| `SW=6E00` at runtime | CLA byte wrong | Check CLA constant matches between host and applet |
| `SW=6700` at runtime | Wrong data length sent | Check APDU data size vs what applet expects |
| `SW=6982` at runtime | Security condition not satisfied | PIN not verified or authentication required |
| `SW=6A86` at runtime | P1/P2 out of range | Check offset calculation logic |
| Task not discovered | Missing annotation or wrong package | Ensure `@CardTaskDef`, correct package name matching directory |
| "No tasks found for kind X" | Directory doesn't exist or class not annotated | Create `host/X/` directory with properly annotated class |
| Applet throws `6F00` | Uncaught exception on card | Bug in applet code — check array bounds, null refs |
| EEPROM exhaustion | Allocating `new byte[]` in `process()` | Move all allocations to constructor |
| Response data corrupt | Writing response before copying incoming data | Copy incoming data to instance field first, then write response |

## Testing Workflow

```
1. Edit applet code in applet/
2. Edit/add host provision task in host/provision/
3. Edit/add host test task in host/test/
4. Run: ../gradlew cinpoRun --args='--test'
   → Compiles applet → Starts simulator → Installs applet
   → Runs provision tasks → Runs test tasks
5. Iterate. Use --skip-install to avoid reinstalling when only host tasks changed:
   ../gradlew cinpoRun --args='--skip-install --test'
```

**Note**: `--skip-install` only works if the applet is already installed from a previous run. If you changed applet code, you must reinstall.

## Dependencies

Managed in `build.gradle`:

| Dependency | Purpose |
|-----------|---------|
| `app.aoki.cinpo:cinpo:0.1.2` | Core CINPO framework (tasks, APDU, GP) |
| `org.yaml:snakeyaml:2.2` | YAML parsing for manifest/profiles |
| `org.bouncycastle:bcprov-jdk18on:1.78.1` | Cryptography provider |
| `org.bouncycastle:bcpkix-jdk18on:1.78.1` | X.509/PKIX certificate support |

Add additional dependencies in `build.gradle` → `dependencies {}` for host-side tasks. Applet code has no external dependencies (JavaCard API is provided by the SDK at compile time).

## Source Layout Rules

The `build.gradle` configures:
```groovy
sourceSets {
    main {
        java.srcDirs = ['host']            // All Java under host/ is compiled
        resources {
            srcDirs = ['host']              // Non-Java files become classpath resources
            exclude '**/*.java'
        }
    }
}
```

This means:
- Java package roots start at `host/` (so `host/provision/Foo.java` → `package provision;`)
- Non-Java files in `host/` are accessible as classpath resources at runtime
- Applet code is compiled separately by the CINPO Gradle plugin (not via standard `javac`)

# CINPO

> Spring Boot for JavaCard — annotation-driven applet installation and provisioning.

## Overview

CINPO (**C**ard **IN**stallation and **P**rovision **O**rchestrator) is a framework that lets you install and provision JavaCard applets with zero boilerplate. You declare your tasks with annotations, declare your dependencies with `@Inject`, and the framework handles everything else — GlobalPlatform secure channel authentication, CAP file loading, APDU transport, simulator lifecycle.

```java
@CardTaskDef("provision")
public final class MyProvision implements CardTask {
    @Inject private ApduChannel channel;

    @Override
    public void run() {
        channel.transmit(Iso7816Commands.selectDf(MY_AID));
        // your provisioning logic
    }
}
```

No manual wiring. No scripts. Just annotate, inject, run.

## Philosophy

CINPO defines a developer experience for developing Java Card Applets. It provides a complete workflows and frameworks for building, provisioning, testing and deploying in the wild. 

It is designed just like "Spring Boot for Java Card".

- We want to make Java Card Applet development easier and more modern. 
- We want Spring Boot or Quarkus-like opinionated experience for Java Card Applet development.
- We want out-of-box experience for Java Card Applet development, with batteries included.
- We aim the world where we can achieve applet development with the following simple steps:
  - Create a project from the template
  - Write Card Applet
  - Add annotations to provision code
  - Run in one simple gradle command
  - Do without entrypoint Main class, without build shellscripts, ad-hoc gradle tasks.
- We want to help developers make secure and robust applets in fast and iterative way.

## Getting Started

### 1. Create a project from the minimal template

You can create a starter project without cloning this repository by piping [`scripts/bootstrap.sh`](scripts/bootstrap.sh) from GitHub Pages with `curl`:

```bash
curl -fsSL https://AokiApp.github.io/cinpo/bootstrap.sh | bash -s -- my-project
```

This downloads and runs [`scripts/bootstrap.sh`](scripts/bootstrap.sh), which fetches [`template/`](template) into a fresh project directory. It requires `git`, `awk`, and `mktemp` on your machine.

If you already have a checkout of this repository, you can run the script locally instead:

```bash
bash ./scripts/bootstrap.sh my-project
```

This creates a standalone starter project that you can customize manually. If you prefer, you can still copy [`template/`](template) yourself. The starter is intentionally small: a single Hello applet plus one provision task that issues and writes a leaf certificate, and one test task that reads it back.

```
my-project/
├── manifest.yaml          # What to install (package AID, applets, SDK version)
├── applet/                # JavaCard applet source files
├── host/
│   ├── provision/         # Default task kind for plain `write`
│   ├── test/              # Conventional verification tasks for `--test`
│   └── foobar/            # Any additional annotation-defined task kind
└── profile/
    └── jcdksim.yaml       # Optional simulator profile override
```

The copy script automatically sets [`rootProject.name`](template/settings.gradle:27) in the generated project's [`settings.gradle`](template/settings.gradle) to the destination directory name. Other sample identifiers remain unchanged for manual customization.

Non-Java files placed under `host/` are packaged as runtime resources. Template projects can use this to bundle default fixture data or a read-only database file alongside host-side task code.

### 2. Define your manifest

Edit `manifest.yaml` to describe your applet package:

```yaml
schema: cinpo.applet.v1

basePackage: com.example.myapplet
toolSdkVersion: "26.0"
targetApiVersion: "3.0.4"

package:
  name: com.example.myapplet
  aid: D276000085010100
  version: "1.0"

companionBundles:
  - id: globalplatform-1.7
    jar: vendor/globalplatform/org.globalplatform-1.7/gpapi-globalplatform.jar
    exports: vendor/globalplatform/org.globalplatform-1.7/exports

applets:
  - id: main
    className: com.example.myapplet.MainApplet
    classAid: D27600008501010001
    instanceAid: D2760000850101000001
    privilege: "00"
```

### 3. Write your applet

Place JavaCard applet source files in `applet/`. Standard JavaCard API — nothing CINPO-specific here.

If your applet depends on vendor-supplied on-card APIs, declare them in `companionBundles`. Each bundle contributes:

- a stub `jar` on the [`compileAppletJava`](src/main/groovy/app.aoki.cinpo.gradle.gradle:35) classpath
- an `exports` directory in the converter export search path used by [`CinpoConverterLogic.profileFor()`](src/main/java-gradle/com/sun/javacard/converter/CinpoConverterLogic.java:38)

This lets CINPO compile and convert applets that import companion packages such as GlobalPlatform, provided the vendor supplies both artifacts.

### 4. Write a default provision task

Create a class in `host/provision/`:

```java
@CardTaskDef("provision")
public final class SetupPins implements CardTask {
    @Inject private ApduChannel channel;

    @Override
    public void run() {
        channel.transmit(Iso7816Commands.selectDf(AID));
        channel.transmit(Iso7816Commands.changeReferenceData(0x01, "1234".getBytes()));
    }
}
```

The annotation processor indexes this class at compile time. At runtime, the framework discovers it, injects dependencies, and calls `run()`.

Any task kind can be declared with `@CardTaskDef("<kind>")`. `provision` is simply the default selected kind when no explicit task selector is provided.

### 5. Build and run

Configure GitHub Packages credentials as Gradle properties or environment variables so the standalone template/example projects can resolve CINPO artifacts:

```properties
gpr.user=<github-user>
gpr.key=<github-token-with-packages-read>
```

Then run an application project from its own directory:

```bash
cd template
../gradlew cinpoRun
```

That's it. SDK download, CAP compilation, simulator setup, GP authentication, installation, and provisioning — all handled.

To run the conventional test flow after provisioning:

```bash
../gradlew cinpoRun --args='--test'
```

To select explicit task kinds or skip installation:

```bash
../gradlew cinpoRun --args='--task=foobar'
../gradlew cinpoRun --args='--task=provision --task=test'
../gradlew cinpoRun --args='--skip-install --task=foobar'
```

To pass template-specific runtime arguments to host-side tasks, put them after `--`:

```bash
../gradlew cinpoRun --args='write -- --record sample01'
../gradlew cinpoRun --args='write --profile mycard -- --record sample01 --db ./cards.db'
```

CINPO still parses its own options strictly. Only the arguments after `--` are forwarded verbatim to tasks.

### Production JAR

To build a self-contained fat JAR with embedded simulator:

```bash
../gradlew cinpoJar
```

Run it anywhere:

```bash
java -jar build/libs/cinpo-appliance.jar write --profile jcdksim
java -jar build/libs/cinpo-appliance.jar write --profile mycard -- --record customer-42
```

## Writing Tasks

### Task kinds

CINPO task kinds are annotation-defined and loaded from `META-INF/cinpo/<kind>`.

Common conventions are:

| Kind | Annotation | Selected by |
|------|-----------|-------------|
| Provision | `@CardTaskDef("provision")` | Default `write`; or `--task=provision` |
| Test | `@CardTaskDef("test")` | `--test`; or `--task=test` |
| Custom | `@CardTaskDef("foobar")` | `--task=foobar` |

`--task` is repeatable and preserves command-line order. `--test` is shorthand for `--task=provision --task=test`.

### Ordering

Tasks within the same kind are sorted by `order()` (ascending), then by class name:

```java
@CardTaskDef(value = "provision", order = 100)
public final class FirstTask implements CardTask { ... }

@CardTaskDef(value = "provision", order = 200)
public final class SecondTask implements CardTask { ... }
```

### Injectable types

Fields annotated with `@Inject` are populated before `run()` is called:

| Type | What you get |
|------|-------------|
| `ApduChannel` | Live channel to the card/simulator |
| `AppletManifest` | Parsed manifest (AIDs, package info) |
| `Profile` | Active runtime profile |
| `SecureChannelProfile` | SCP key material for opening your own secure sessions |
| `TaskArguments` | Raw template-specific arguments forwarded after `--` |

### Template-specific runtime arguments

Arguments after `--` are not interpreted by CINPO. They are forwarded verbatim to host-side tasks and exposed as `TaskArguments`.

```java
@CardTaskDef("provision")
public final class LoadSelectedRecord implements CardTask {
    @Inject private TaskArguments taskArguments;

    @Override
    public void run() {
        String recordId = taskArguments.firstValue("record").orElse("sample01");
        String dbPath = taskArguments.firstValue("db").orElse("host/data/cards.db");
        // interpret recordId/dbPath however your template wants
    }
}
```

This keeps CINPO generic while letting each template define its own selectors such as `record`, `db`, `customer`, or `input`.

If you bundle a file-based database under `host/`, remember that it is packaged as a classpath resource. Some drivers can read directly from a resource stream; others may require copying the resource to a temporary file before opening it.

### Example: multi-applet provisioning

```java
@CardTaskDef("provision")
public final class FullProvision implements CardTask {
    @Inject private ApduChannel channel;
    @Inject private AppletManifest manifest;

    @Override
    public void run() {
        for (var applet : manifest.applets()) {
            channel.transmit(Iso7816Commands.selectDf(applet.instanceAid()));
            // provision each applet
        }
    }
}
```

### Example: provisioning with secure channel

When your applet requires authenticated commands (e.g., writing keys or sensitive data), open your own SCP session within the task:

```java
@CardTaskDef(value = "provision", order = 200)
public final class WriteKeys implements CardTask {
    @Inject private ApduChannel channel;
    @Inject private SecureChannelProfile scpProfile;

    @Override
    public void run() {
        SecureChannelSession session = SecureChannelSession.create(channel, scpProfile);
        session.authenticate();
        try {
            session.transmit(new CommandApdu(0x80, 0xD4, 0x00, 0x00, keyData));
        } finally {
            session.close();
        }
    }
}
```

### Example: test task

Test tasks run after provisioning and verify that the card state is correct:

```java
@CardTaskDef("test")
public final class VerifyPinWorks implements CardTask {
    @Inject private ApduChannel channel;
    @Inject private AppletManifest manifest;

    @Override
    public void run() {
        byte[] aid = manifest.applets().get(0).instanceAid();
        channel.transmit(Iso7816Commands.selectDf(aid));

        ResponseApdu response = channel.transmit(
            Iso7816Commands.verify(0x01, "1234".getBytes()));
        if (response.sw() != 0x9000) {
            throw new RuntimeException("PIN verify failed: SW=%04X".formatted(response.sw()));
        }
    }
}
```

## Profiles

A profile defines **where** APDUs go and **how** the secure channel authenticates.

### Builtin: `jcdksim`

The default profile targets the Oracle JCDK simulator with SCP03 and well-known test keys. No configuration needed — just `./gradlew cinpoRun`.

### Custom profiles

Create `profile/<name>.yaml` in the application project directory. Named profiles resolve only from `profile/`. Path-like values such as `--profile ./secrets/card.yaml` are resolved relative to the application project directory; absolute paths are used as-is.


```yaml
schema: cinpo.profile.v1
name: mycard
runtime: pcsc
pcsc:
  # Optional. When set, CINPO uses this reader only and does not fall back.
  reader: My Smart Card Reader
  # Optional. Used only for automatic reader selection.
  excludedReaders:
    - Built-in Contactless Reader

secureChannel:
  protocol: scp03
  isdAid: A000000151000000
  keyVersionNumber: 16
  keyIdentifier: 0
  securityLevel: 1
  encKey: <your 32-byte hex ENC key>
  macKey: <your 32-byte hex MAC key>
  dekKey: <your 32-byte hex DEK key>
```

Use it:

```bash
./gradlew cinpoRun --args='--profile mycard'
# or
java -jar cinpo-appliance.jar write --profile mycard
```

## End-to-end verification

Framework changes should pass unit tests, artifact publication, and simulator-backed application flows without adding project-specific verification tasks:

```bash
./gradlew test publishAllPublicationsToStagingRepository
(cd template && ../gradlew cinpoRun --args='--test')
```

The application flows consume the published CINPO artifacts, generate CAP resources, start the managed Oracle JCDK simulator, install the applets, run provisioning, and execute `--test` tasks. This catches schema drift, missing generated resources, profile resolution mistakes, and simulator/runtime classpath regressions that unit tests cannot see.

### Runtime adapters

| `runtime` value | Transport |
|----------------|-----------|
| `jcresim` | Oracle JCDK simulator (auto-managed) |
| `pcsc` | PC/SC card reader (javax.smartcardio) |

### Supported protocols

| `protocol` | Key size | Notes |
|-----------|----------|-------|
| `scp03` | 16 or 32 bytes | Recommended. AES-based. |
| `scp02` | 16 bytes | Legacy 3DES-based. C-MAC only. |


## License

CINPO is source-available, not open source. It is provided under the
[AokiApp Normative Application License - Tight](LICENSE.md). Review that license before using,
modifying, or distributing the software. Third-party components remain subject to their own terms;
see [Third-Party Notices](THIRD_PARTY_NOTICES.md).
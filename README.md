<div align="center">

# CINPO

**Spring Boot for JavaCard — annotation-driven applet installation and provisioning.**

[![Publish](https://github.com/AokiApp/cinpo/actions/workflows/publish.yml/badge.svg)](https://github.com/AokiApp/cinpo/actions/workflows/publish.yml)
[![License](https://img.shields.io/badge/License-ANAL--T-4c1?labelColor=30363d)](LICENSE.md)

[Getting Started](#getting-started) · [Templates](#templates) · [Writing Tasks](#writing-tasks)

</div>

---

## Overview

CINPO (**C**ard **IN**stallation and **P**rovision **O**rchestrator) is a framework that lets you install and provision JavaCard applets with zero boilerplate. Annotate your tasks with `@CardTaskDef`, inject dependencies with `@Inject`, and the framework handles the rest — GlobalPlatform secure channel authentication, CAP file loading, APDU transport, simulator lifecycle.

```java
@CardTaskDef("provision")
public final class MyProvision implements CardTask {
    @Inject private ApduChannel channel;
    @Inject private AppletManifest manifest;

    @Override
    public void run() {
        channel.transmit(
            Iso7816Commands.selectDf(manifest.applets().get(0).instanceAid()));
        // your provisioning logic
    }
}
```

No manual wiring. No scripts. Just annotate, inject, run.

## Philosophy

CINPO defines a streamlined developer experience for Java Card applet development. It provides complete workflows and frameworks for building, provisioning, testing, and deploying in the wild, from creating the template project to packaging for production. It is designed just like "Spring Boot for Java Card."

The goal is to put the developer experience first and make applet development easier and more modern, so developers can make secure and robust applets fast and iteratively. It is opinionated like Spring Boot or Quarkus, and out-of-the-box, with batteries included.

So applet development is a few simple steps: create a project from the template, write your applet, add annotations to your provisioning code, and run everything with one command. No entrypoint `Main` class, no build shell scripts, no ad-hoc Gradle tasks.

And for production, everything ships as a single self-contained appliance JAR with minimal dependencies. That means a smaller attack surface, and the same artifact runs on an air-gapped network. Portability is a core design value.

## Getting Started

### Prerequisites

- **JDK 21**
- **git**
- The **Oracle Java Card Development Kit (JCDK)**; see Step 2.

### 1. Create a project from a template

```bash
curl -fsSL https://AokiApp.github.io/cinpo/bootstrap.sh | bash -s -- my-project
```

On Windows:

```powershell
irm https://AokiApp.github.io/cinpo/bootstrap.ps1 -OutFile bootstrap.ps1
powershell -ExecutionPolicy Bypass -File .\bootstrap.ps1 my-project
```

Pass `--name`/`-Name` and `--template`/`-Template` to choose the destination and starter (see [Templates](#templates)).

```
my-project/
├── manifest.yaml          # What to install (package AID, applets, SDK version)
├── applet/                # JavaCard applet source files
├── host/
│   ├── provision/         # Default task kind for plain `write`
│   └── test/              # Conventional verification tasks for `--test`
├── profile/
│   └── jcdksim.yaml       # jcdksim simulator profile (development-only keys)
└── vendor/                # Oracle JCDK archives (you provide these)
```

### 2. Prepare the vendor directory

CINPO does not redistribute the Oracle JCDK; you must download it and place the Tools and Simulator archives in `my-project/vendor/` (see [docs/vendor.md](docs/vendor.md)). Without them, `cinpoRun` and `cinpoJar` fail.

After placement, `my-project/vendor/` should look like this:

```
vendor/
├── java_card_devkit_tools-bin-v26.0-b_705-04-MAY-2026.zip
└── java_card_devkit_simulator-linux-bin-v26.0-b_788-05-MAY-2026.tar.gz   # Linux
    (or java_card_devkit_simulator-win-bin-v26.0-b_788-05-MAY-2026.zip)   # Windows
```

### 3. Define your manifest

Edit `manifest.yaml` to describe your applet package (see [docs/manifest.md](docs/manifest.md) for the full schema):

```yaml
schema: cinpo.applet.v1            # manifest schema version

basePackage: com.example.myapplet  # Java package your applet sources use
toolSdkVersion: "26.0"             # JCDK version
targetApiVersion: "3.0.4"          # Java Card API version the applet targets

package:
  name: com.example.myapplet       # package identifier; should match basePackage
  aid: D276000085010100            # package AID (hex)
  version: "1.0"                   # package version

companionBundles: []               # see docs/manifest.md

applets:
  - id: main                       # logical identifier, referenced by host tasks
    className: com.example.myapplet.MainApplet   # fully qualified applet class name
    classAid: D27600008501010001   # class AID; must start with the package AID
    instanceAid: D2760000850101000001  # instance AID; what the host SELECTs
    privilege: "00"                # installation privilege byte; see the GlobalPlatform Card Specification
```

### 4. Write your applet

Place JavaCard applet source files in `applet/`. Standard JavaCard API — nothing CINPO-specific here.

If your applet imports vendor-supplied on-card APIs such as GlobalPlatform, declare them in `companionBundles` — see [docs/manifest.md](docs/manifest.md).

### 5. Write a default provision task

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

Any task kind can be declared with `@CardTaskDef("<kind>")`. `provision` is simply the default selected kind when no explicit task selector is provided.

### 6. Simulate

From the project directory:

```bash
cd my-project
./gradlew cinpoRun                                      # install applet + run provision tasks
./gradlew cinpoRun --args='--test'                      # also run the test tasks
./gradlew cinpoRun --args='--task=foobar'               # select explicit task kinds
./gradlew cinpoRun --args='--task=provision --task=test'
./gradlew cinpoRun --args='--skip-install --task=foobar'  # skip installation
```

CAP compilation, simulator setup, GP authentication, installation, and provisioning are all handled.

To run on Windows, use `.\gradlew.bat` instead of `./gradlew`.

To pass template-specific runtime arguments to host-side tasks, put them after `--`:

```bash
./gradlew cinpoRun --args='write -- --record sample01'
```

Only the arguments after `--` are forwarded verbatim to your tasks.

### 7. Run on a real card

The simulator is a starting point; the same commands work against a physical card.

#### Profiles

The default is `jcdksim` (simulator, well-known test keys). For a physical card, create `profile/<name>.yaml` with `runtime: pcsc` and your own keys — see [docs/profiles.md](docs/profiles.md).

```bash
./gradlew cinpoRun --args='--profile mycard --test'
```

Production machines typically run the appliance instead of Gradle. Build a self-contained fat JAR with the embedded simulator:

```bash
./gradlew cinpoJar
```

Run it anywhere, no Gradle required:

```bash
java -jar build/libs/cinpo-appliance.jar write --profile jcdksim
java -jar build/libs/cinpo-appliance.jar write --profile mycard --test
java -jar build/libs/cinpo-appliance.jar write --profile mycard -- --record customer-42
```

## Templates

`scripts/bootstrap.sh` and `scripts/bootstrap.ps1` copy one of three starter templates into the destination directory. Select one with `--template` (`-Template` on PowerShell); the default is `basic`.

| Template | What it provides |
|----------|------------------|
| `basic` | The default starter: a Hello applet, one `provision` task that issues and writes a leaf certificate, and one `test` task that reads it back. |
| `authenticated` | An SCP03 secure-messaging example: the applet handles GlobalPlatform security commands through a companion bundle, and the test proves that an unauthenticated command is refused and the authenticated one succeeds. |
| `probe` | A read-only card probe: the `probe` task kind selects an application or the default application and reads `GET DATA` without writing to the card. |

## Writing Tasks

A task is a small Java program that runs on the host side of the APDU link, against the simulator or a physical card. Tasks turn "the applet is installed" into actual card state.

Some of the jobs tasks can do:

- **Provision credentials** — set a PIN, store keys, write a certificate.
- **Run test cases** — write tests as tasks and run them with `--test`.
- **Factory-provision production cards** — unattended, even on an air-gapped line.

```java
@CardTaskDef("provision")
public final class LoadSelectedRecord implements CardTask {
    @Inject private TaskArguments args;

    @Override
    public void run() {
        String recordId = args.firstValue("record").orElse("sample01");
        String dbPath = args.firstValue("db").orElse("host/data/cards.db");
        // the template decides what these mean
    }
}
```

For the full guide — discovery internals, injection semantics, resources, complete examples — see [docs/tasks.md](docs/tasks.md).

## License

CINPO is source-available, not open source. It is provided under the
[AokiApp Normative Application License - Tight](LICENSE.md). Review that license before using,
modifying, or distributing the software. Third-party components remain subject to their own terms;
see [Third-Party Notices](THIRD_PARTY_NOTICES.md).
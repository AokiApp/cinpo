# Profiles

A profile tells CINPO two things: **where APDUs go** (the simulator or a physical card) and **how the secure channel authenticates** (which protocol, which keys).

## The default profile: `jcdksim`

`jcdksim` is just a profile YAML file, shipped in `profile/jcdksim.yaml` by the starter templates. It targets the Oracle JCDK simulator (`jcresim`) with SCP03 and **well-known test keys** — no extra configuration is needed:

```yaml
schema: cinpo.profile.v1
name: jcdksim
runtime: jcresim
secureChannel:
  protocol: scp03
  isdAid: A000000151000000         # default JCDK simulator ISD AID
  keyVersionNumber: 16             # 0x10
  keyIdentifier: 0
  securityLevel: 1                 # C-MAC
  encKey: "1111..."                # 32 bytes of 0x11
  macKey: "2222..."                # 32 bytes of 0x22
  dekKey: "3333..."                # 32 bytes of 0x33
```

These keys are public. Never use them on a real card — they exist so that a fresh project works out of the box for development.

The CLI defaults `--profile` to `jcdksim`. CINPO has no builtin profiles and no registry: it is resolved like any other name, from `profile/jcdksim.yaml` in the project or from the same path on the classpath. An appliance JAR carries the project's profiles, so a bare `java -jar cinpo-appliance.jar write` works even from a directory with no `profile/` folder. If you don't use the simulator, name your own profile; leaving the default in place fails at profile or adapter lookup.

## Custom profiles

Create `profile/<name>.yaml` in the application project directory:

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

How the `--profile` argument is resolved:

- **Named profile** (`--profile mycard`) — resolved from `profile/mycard.yaml` inside the application project directory, then from `profile/mycard.yaml` on the classpath inside the appliance JAR. Other directories inside the project are not searched.
- **Path-like value** (`--profile ./secrets/card.yaml`) — values containing `/`, `\`, or ending in `.yaml`/`.yml` are resolved relative to the application project directory. Absolute paths are used as-is.
- **Missing profile** — the command fails with a message listing the paths that were searched, including the classpath path `profile/<name>.yaml`.
- **Omitted `--profile`** — defaults to `jcdksim`, resolved from `profile/jcdksim.yaml` in the project or on the classpath. If you don't use the simulator, name your profile explicitly.
- **`reader` set in the `pcsc` block** — that reader is used exclusively; no automatic fallback or selection happens. `excludedReaders` is only consulted for automatic reader selection.

Notes on the `secureChannel` block:

- `protocol` — `scp03` (AES, recommended) or `scp02` (3DES, legacy, deprecated; C-MAC only).
- `encKey`, `macKey`, `dekKey` — hex strings of 16, 24, or 32 bytes for SCP03. `macKey` and `dekKey` must be the same length as `encKey`. SCP02 keys are 16 bytes (3DES).
- `isdAid` — the Issuer Security Domain AID (the default above matches the JCDK simulator).
- `securityLevel` — `1` (C-MAC) or `3` (C-MAC + C-ENC).
- `keyVersionNumber` / `keyIdentifier` — as provisioned on the card. The JCDK simulator default is `16` (0x10) / `0`.

Use it:

```bash
./gradlew cinpoRun --args='--profile mycard'
# or, with the appliance JAR
java -jar cinpo-appliance.jar write --profile mycard
```

## Managing secrets

The templates ship `profile/jcdksim.yaml` with public test keys, which is safe to commit. Real SCP keys are not. The templates do not gitignore `profile/`, so if you add a profile with real keys (for example `mycard.yaml`), gitignore that file yourself or keep it outside the repository and pass its path: `--profile /path/to/mycard.yaml` (absolute paths are used as-is).

## Using a physical card

`runtime: pcsc` uses the host's PC/SC stack (`javax.smartcardio`). On Linux this is usually `pcscd` with a supported reader driver; on Windows the built-in Smart Card service provides PC/SC. A reader, a card, and a driver must all be available before `cinpoRun` starts.

- **Reader selection.** Set `pcsc.reader` to pin an exact reader name. Without it, CINPO selects a reader automatically and skips names listed in `pcsc.excludedReaders` (for example a contactless reader you do not want to use). When the wrong reader is picked, pin it.
- **Profile paths on Windows.** `--profile` accepts a path-like value, including a Windows path passed through Gradle: `.\gradlew.bat cinpoRun --args="--debug --profile=C:\path\to\card.yaml --test"`.
- **Keys and access control.** The `secureChannel` block must match the card's Issuer Security Domain: `isdAid`, `keyVersionNumber`, `keyIdentifier`, `protocol`, and the three static keys. A mismatch typically fails during secure-channel authentication (`6982`, `6985`, or an authentication error), not during installation.
- **Read-only diagnostics.** The `probe` template's task does not write to the card, but a normal run still installs the placeholder applet unless you pass `--skip-install`. On a card you do not want to modify, run `--skip-install --task=probe`.

Real SCP keys are not test keys. Keep them out of git and do not reuse `profile/jcdksim.yaml` values on hardware. If a command or a reader setup cannot be verified here, say so instead of assuming it passed.

## Runtime adapters

| `runtime` value | Transport |
|----------------|-----------|
| `jcresim` | Oracle JCDK simulator (auto-managed). Requires the Oracle JCDK vendor archives in `vendor/`. |
| `jcardengine` | In-process Java Card engine. No native simulator; runs the applet as compiled `classes`. |
| `pcsc` | PC/SC card reader (javax.smartcardio) |

## Supported protocols

| `protocol` | Key size | Notes |
|-----------|----------|-------|
| `scp03` | 16, 24, or 32 bytes | Recommended. AES-based. |
| `scp02` | 16 bytes | Deprecated in the GlobalPlatform specifications. Legacy 3DES-based. C-MAC only. |

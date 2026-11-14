# Profiles

A profile tells CINPO two things: **where APDUs go** (the simulator or a physical card) and **how the secure channel authenticates** (which protocol, which keys).

## The builtin: `jcdksim`

`jcdksim` is the default profile, used when `--profile` is omitted. It targets the Oracle JCDK simulator (`jcresim`) with SCP03 and **well-known test keys** — no configuration is needed:

```yaml
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

If `profile/jcdksim.yaml` does not exist, the builtin `jcdksim` is used, so the default works even without any profile files in the project.

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

- **Named profile** (`--profile mycard`) — resolved from `profile/mycard.yaml` inside the application project directory. Only `profile/` is searched; there is no other directory fallback.
- **Path-like value** (`--profile ./secrets/card.yaml`) — values containing `/`, `\`, or ending in `.yaml`/`.yml` are resolved relative to the application project directory. Absolute paths are used as-is.
- **Missing profile** — the command fails with a message listing the paths that were searched. Only `jcdksim` falls back to a builtin.
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

## Runtime adapters

| `runtime` value | Transport |
|----------------|-----------|
| `jcresim` | Oracle JCDK simulator (auto-managed) |
| `pcsc` | PC/SC card reader (javax.smartcardio) |

## Supported protocols

| `protocol` | Key size | Notes |
|-----------|----------|-------|
| `scp03` | 16, 24, or 32 bytes | Recommended. AES-based. |
| `scp02` | 16 bytes | Deprecated in the GlobalPlatform specifications. Legacy 3DES-based. C-MAC only. |

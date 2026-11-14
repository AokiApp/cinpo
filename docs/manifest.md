# Manifest reference

Reference for `manifest.yaml`, the CINPO applet manifest. This document is normative for the format and describes the load-time behavior of the parser. For a guide to writing a manifest from scratch, see the "Define your manifest" step in [the README](../README.md#3-define-your-manifest).

## File location and lifecycle

- Location: the application project root (`my-project/manifest.yaml`).
- Format: YAML. Parsed once when the application project is loaded.
- On parse, the whole document is validated before anything proceeds. Failures abort loading and raise `IllegalArgumentException`.

## Example

The example shows a GlobalPlatform companion bundle and two applets installed from one package.

```yaml
schema: cinpo.applet.v1

basePackage: com.example.cinpo.hello
toolSdkVersion: "26.0"
targetApiVersion: "3.0.4"

package:
  name: com.example.cinpo.hello
  aid: F00000000101
  version: "1.0"

companionBundles:
  - id: globalplatform-1.6
    jar: vendor/globalplatform/org.globalplatform-1.6/gpapi-globalplatform.jar
    exports: vendor/globalplatform/org.globalplatform-1.6/exports

applets:
  - id: hello
    className: com.example.cinpo.hello.HelloApplet
    classAid: F0000000010101
    instanceAid: F000000001010001
    privilege: "00"
  - id: second
    className: com.example.cinpo.hello.SecondApplet
    classAid: F0000000010102
    instanceAid: F000000001010002
    privilege: "00"
```

## Top-level keys

| Key | Type | Notes |
|-----|------|-------|
| `schema` | **string** | Must be `cinpo.applet.v1`. Any other value keeps loading and only logs a warning. |
| `basePackage` | **string** | Java package of the applet sources. Non-blank. |
| `toolSdkVersion` | **string** | The JCDK version the applet is compiled and converted with. Only `26.0` is supported today. |
| `targetApiVersion` | **string** | Target Java Card API version: `3.0.4`, `3.0.5`, `3.1.0`, `3.2.0`. |
| `package` | **map** | Package (CAP file) declaration, see [`package`](#package). |
| `companionBundles` | **list** | Possibly empty list of companion bundle declarations, see [`companionBundles`](#companionbundles). |
| `applets` | **list** | At least one applet, see [`applets`](#applets). |

`toolSdkVersion` and `targetApiVersion` are validated together: every supported `targetApiVersion` currently requires `toolSdkVersion` starting with `24.`, `25.` or `26.`.

`companionBundles` may be `[]`. `applets` may not be empty.

## `package`

Describes the installer-visible container (the Global Platform specification calls the package an *Executable Load File* (ELF); the `package.aid` below is its AID).

| Key | Type | Notes |
|-----|------|-------|
| `name` | **string** | CAP package name. Non-blank. Conventionally equal to `basePackage`. Not enforced. |
| `aid` | hex string | Hex string (whitespace/`-`/`_` allowed inside). Decoded to the Executable Load File AID. Must decode to a non-empty byte array. |
| `version` | **string** | Package version. Non-blank. |

## `companionBundles`

One entry per vendor-supplied on-card API the applet imports. All other applets use only `javacard.framework.*` and need `companionBundles: []`.

Each entry:

| Key | Type | Notes |
|-----|------|-------|
| `id` | **string** | Bundle identifier; used in diagnostics and duplicate detection. Non-blank. |
| `jar` | **string** | Path to the stub jar. Resolved against the project directory unless absolute. Non-blank. |
| `exports` | **string** | Path to the exports directory. Resolved against the project directory unless absolute. Non-blank. |

Rules:

- Duplicate `id`s are rejected: `Duplicate companion bundle id: <id>`.
- The `jar` lands on the applet compile classpath; `exports` lands on the JCDK converter's export search path.
- Nothing else about the bundle (existence, readability, API version) is checked at manifest load time — failures surface later, at compile or convert time.

## `applets`

One entry per installable applet in the package. `applets` must contain at least one entry.

| Key | Type | Notes |
|-----|------|-------|
| `id` | **string** | Logical name referenced from code and diagnostics. Non-blank. |
| `className` | **string** | Fully-qualified applet class within `basePackage`. Non-blank. |
| `classAid` | hex string | Module AID. Decoded bytes must not be empty. Conventionally prefixed with `package.aid`. Not enforced. |
| `instanceAid` | hex string | Application Instance AID — the value host tasks SELECT. Decoded bytes must not be empty. |
| `privilege` | hex string | Installation privileges byte(s). Decoded bytes must not be empty — `"00"` means no privileges. |

## AID layout and hierarchy

```
package AID (Executable Load File AID)
├── class AID (Module AID) - HelloApplet
│   └── Application Instance AID
└── class AID (Module AID) - OtherApplet
    └── Application Instance AID
```

- Class AIDs are conventionally prefixed with the package AID (e.g. `F00000000101` → `F0000000010101` → `F000000001010001`). Not enforced.
- The instance AID is what host-side tasks SELECT. The package AID / load file AID is consumed at install time.
- AIDs are hex strings: `0A-1F…` style with separators allowed, or plain `F0000000010101`.

## Where to look next

- Guide walkthrough: [`../README.md#3-define-your-manifest`](../README.md#3-define-your-manifest)
- Compilable manifests: `template/basic/manifest.yaml` (no bundle) and `template/authenticated/manifest.yaml` (`id: globalplatform-1.6` companion bundle)
- [tasks.md](tasks.md) — host-side tasks that consume the manifest
- [vendor.md](vendor.md) — JCDK and companion bundle artifact sources
- [profiles.md](profiles.md) — simulator and physical card profiles

# Preparing the `vendor/` directory

CINPO does not redistribute Oracle's proprietary toolkit and does not download it for you. You need two archives from [Oracle's Java Card downloads page](https://www.oracle.com/java/technologies/javacard-downloads.html) (linked from [Java Card](https://www.oracle.com/java/java-card/) as "Java Card Downloads"):

1. **JCDK Tools 26.0**: click `java_card_devkit_tools-bin-v26.0-b_705-04-MAY-2026.zip` and accept the license (a license lightbox is shown). This is the JCDK Tools 26.0 archive.
2. **JCDK Simulator 26.0** for your OS, accepted the same way:
   - Linux: `java_card_devkit_simulator-linux-bin-v26.0-b_788-05-MAY-2026.tar.gz`
   - Windows: `java_card_devkit_simulator-win-bin-v26.0-b_788-05-MAY-2026.zip`

Copy both into your project's `vendor/` directory. For example:

```bash
mkdir -p vendor
mv ~/Downloads/java_card_devkit_tools-bin-v26.0-b_705-04-MAY-2026.zip vendor/
mv ~/Downloads/java_card_devkit_simulator-linux-bin-v26.0-b_788-05-MAY-2026.tar.gz vendor/
```

After placement, `vendor/` should look like this:

```
vendor/
├── java_card_devkit_tools-bin-v26.0-b_705-04-MAY-2026.zip
└── java_card_devkit_simulator-linux-bin-v26.0-b_788-05-MAY-2026.tar.gz   # Linux
    (or java_card_devkit_simulator-win-bin-v26.0-b_788-05-MAY-2026.zip)   # Windows
```

The Tools archive is always required. You only need the Simulator archive matching the OS you build on. Their versions must match the `toolSdkVersion` in your `manifest.yaml` (26.0 today).

Notes:

- `vendor/` is gitignored. The archives are proprietary; do not commit them.
- Checksums for the expected files are pinned in `scripts/extract_vendor.sh` if you want to verify integrity.
- Without these files, `cinpoRun` and `cinpoJar` fail.

## Companion bundles

Some applets import an on-card API that the Oracle JCDK does not provide, such as the GlobalPlatform Card API. Declare it under `companionBundles` in `manifest.yaml`; CINPO puts the jar on the applet compile classpath and the exports directory on the converter's export search path. Both paths are resolved against the project directory.

```yaml
companionBundles:
  - id: globalplatform-1.5
    jar: vendor/globalplatform/org.globalplatform-1.5/gpapi-globalplatform.jar
    exports: vendor/globalplatform/org.globalplatform-1.5/exports
```

Download the jar and extract the `.exp` without flattening its package path:

```bash
mkdir -p vendor/globalplatform/org.globalplatform-1.5/exports
curl -fL -o vendor/globalplatform/org.globalplatform-1.5/gpapi-globalplatform.jar \
  https://mvn.javacard.pro/maven/org/globalplatform/javacard/1.5/javacard-1.5.jar
unzip -o vendor/globalplatform/org.globalplatform-1.5/gpapi-globalplatform.jar \
  'org/globalplatform/javacard/globalplatform.exp' \
  -d vendor/globalplatform/org.globalplatform-1.5/exports
```

The result is:

```
vendor/globalplatform/org.globalplatform-1.5/
├── gpapi-globalplatform.jar          # classes for the applet compiler
└── exports/
    └── org/globalplatform/javacard/globalplatform.exp
```

Notes:

- This template uses GlobalPlatform API 1.5, which was the newest release on `mvn.javacard.pro` when the template was written. The Oracle JCDK does not contain it, and it is not in `vendor.zip`.
- The `id`, `jar`, and `exports` values must all name the same staged version. If you upgrade, change the manifest and the staging path together, and check that the version exists before writing the path.
- The converter may warn that the GlobalPlatform 1.5 `.exp` uses an older export file format. That warning is expected; a missing `.exp` is not.
- A missing bundle fails the build with `Companion bundle '<id>' jar file does not exist` or `... exports directory does not exist`. Treat that as a staging error, not an applet error.

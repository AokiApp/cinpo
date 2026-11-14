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

# LensDrop H5 GitHub build package r1

Prepared 19 November 2026. This is a documentation-only repack of the actual H5 source archive, not H6 and not a build-fix release.

## Exact preservation

All 341 original H5 file payloads are retained unchanged, including both workflows, build helpers, source, deployment ZIP, old guides and old manifests. New files are additive. Old manifests describe their original release subset; the new manifest covers this expanded archive. No native binary is included or claimed.

Original archive SHA-256: `fe5ffd9cbef222d0d17f3fae6f9f31e105dd73201f2bbc7eb543b24a653b5019`.

## Additions

`START_GITHUB_H5.html` and `.md` provide a complete H5-only upload/build guide. `docs/github-h5-r1/` records the actual checks and rerun JVM/SQLite outputs. `GITHUB_R1_MANIFEST_SHA256.txt` hashes every packaged file except itself. The original Hostinger deployment ZIP is byte-identical to the standalone H5 cloud package.

## Verification

The original ZIP CRC check passed. Both workflow documents were parsed and expected names, manual-only triggers, source/helper paths, artifact names, app metadata and signing-secret names checked. Build-helper Python syntax was parsed. The existing 10 policy, 15 print-policy and 24 exact queue/ledger JVM/SQLite bridge cases were executed and passed. These tests do not compile the Android application or the Windows application.

No GitHub run, Android SDK compile/lint, Windows build, live deployment or physical-device test was performed. A correctly structured source package does not guarantee successful native compilation. The original H5 Android workflow assembles debug and runs JVM logic tests, but does not run Android lint or emulator/instrumentation tests. This documentation refresh does not change that scope.

## Use

Extract the project and open `START_GITHUB_H5.html`. Copy its contents, including hidden `.github`, into the separate private LensDrop repository. Keep the existing `.git` directory and signing identity. Run the two manual workflows on a new commit. Do not overwrite the Printer Lab repository or change Hostinger to fix a compiler error.

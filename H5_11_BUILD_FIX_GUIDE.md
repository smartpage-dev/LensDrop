# LensDrop H5.11 GitHub build repair — 2026-11-24

This is a **source/test consistency repair**, not an APK, EXE, or Hostinger deployment.

## Verified against the uploaded GitHub logs

- `logs_122824438340(1).zip` (Android): `RecoveryReliabilityTest.java:38` expects SQLite `PRAGMA user_version = 8`, while production `QueueDb` opens schema **9**. Existing 24 watch-ledger, 27 ticket-protocol, 28 ticket-ledger, 21 JPEG/retry and preceding recovery tests passed before this assertion. Native `assembleDebug` and `lintDebug` were **not reached**.
- `logs_122824485684(1).zip` (Windows prerequisite): H5.11 source gate rejects `tools/github/test_windows_packaging.py` with actual hash `c165dba7...` vs expected `6ac395f9...`. The old file hard-codes the H5.7 manifest; H5.11 version uses the current verifier's `source.MANIFEST`. The Windows native build was **not reached**.

## Apply this overlay

Copy the files inside this ZIP to the **root of the existing H5.11 LensDrop repository** (preserving paths), replacing exactly:

1. `clients/android/reliability-tests/RecoveryReliabilityTest.java` — expect current schema 9 and update the test description.
2. `ANDROID_SOURCE_SNAPSHOT_H5_11.json` — matching hash for the corrected test.
3. `tools/github/test_windows_packaging.py` — restore the H5.11 test file already present in the original complete H5.11 release, not the older H5.7/H5.9 version.

Do not copy into Printer Lab. Do not overwrite local runtime data or signing secrets. This patch does **not** alter production Android Java, Cloud runtime, Windows station, database migrations, printer drivers or pairing logic. No Hostinger redeploy is needed.

## Run on GitHub

1. Commit and push the three files. Ensure the repository contains the **H5.11** source tree; the patch is not a substitute for the full H5.11 baseline.
2. Start a **NEW** `LensDrop Android APK` workflow on the new commit; do not use `Re-run jobs` on the old SHA. Keep the existing signing identity.
3. Start a **NEW** `LensDrop Windows Print Station` workflow on the same commit.
4. Confirm the source guards, Android recovery suite, all remaining tests, `assembleDebug`, `lintDebug`, Windows native publish and artifact upload. If anything else fails, provide the new complete logs and the source-integrity report.

## Local verification performed

- H5.11 Android 105-file snapshot: PASS after updating the expected digest.
- H5.11 Cloud/Station 114-file snapshot: PASS with restored H5.11 packaging test.
- Windows packaging tests: 8/8 PASS.
- Android recovery/identity/ledger suite: 25/25 PASS, including frozen H5.3 schema4 to current schema9 migration test.
- Android build integrity: 23 PASS; policy 10 PASS; print policy 15 PASS; CP9550 geometry 31 PASS; watch-ledger 24 PASS; gallery UI 26 PASS; slideshow ticket 9 PASS; Java syntax parser 42 PASS.
- A longer batched rerun was interrupted during subsequent tests; do not count uncompleted runners. The uploaded Android log itself shows the ticket and JPEG tests passed before the schema assertion.

No native APK build, Android lint, Windows EXE build, live Hostinger upgrade or physical printer test was performed for this patch.

# LensDrop H5.1: fix both GitHub build failures

**19 November 2026 | Main LensDrop project, NOT Printer Lab 58**

Your Android log contains two Java compiler errors. Your Windows workflow stopped in a cloud regression test before the Windows build started. This repair changes the relevant Android code and the cloud test fixture. It does not change your live website or printer configuration.

**No APK or Windows executable is included. The next new GitHub runs must compile them.**

## 1. Choose one package

**Already uploaded LensDrop H5 or H5 GitHub r1? Use `LensDrop_H5_1_BuildFix_Patch_From_H5.zip`.** It contains only new/changed files. Merge its contents into the existing LensDrop repository root. Keep every other file.

For a fresh repository or complete archive, use `LensDrop_H5_1_BuildFix_GitHub_Ready.zip`. Open its inner `LensDrop_H5` folder and upload the contents, including `.github`. Do not put another `LensDrop_H5` folder inside your repository.

Do not upload either package to `Luce-Printer-Lab-58`. These are different programs. Do not upload this complete source ZIP to Hostinger.

## 2. Update the existing LensDrop repository

### GitHub Desktop

Extract the patch. Open the inner `LensDrop_H5_1_PATCH` folder. Copy its **contents** into the local LensDrop repository. Merge folders and replace matching files; do not delete the existing `clients` or `cloud` folder, and do not touch `.git`.

Review the changes, commit and **Push origin**. Suggested commit message:

```text
Fix LensDrop H5 Android compile errors and release-test dependencies
```

### GitHub website only

Open your LensDrop repository on its default branch (usually `main`). Choose **Add file > Upload files**. Drag the files and folders inside `LensDrop_H5_1_PATCH` into the repository root, retaining their relative paths. Commit the changes. Do not upload only the ZIP or the enclosing patch folder.

The patch has no changes to `.github`, signing secrets or toolchain choices. Include every supplied file, particularly the new Java helper and cloud test helper.

After updating, verify these paths exist:

```text
clients/android/app/src/main/java/app/lensdrop/transfer/DirectorySync.java
clients/android/app/src/main/java/app/lensdrop/transfer/StaffSetupActivity.java
cloud/tests/helpers/release-fixture.cjs
cloud/tests/release-fixture.test.cjs
```

In `clients/android/app/build.gradle`, verify:

```gradle
versionCode 6
versionName '0.2.0-alpha.6-h5.1'
```

## 3. Start NEW runs for both workflows

Go to **Actions > LensDrop Android APK > Run workflow**. Select the updated branch, accept the SDK licences and keep your chosen signing mode.

Then run **Actions > LensDrop Windows Print Station > Run workflow** on the same updated commit.

**Do not use Re-run jobs on the old failures.** GitHub reruns use the old run's original commit; they do not automatically pick up your new patch [1]. Confirm the new run's commit differs from the failed `377bea07...`.

## 4. Download outputs only after full success

| Workflow | Artifact / contents |
|---|---|
| LensDrop Android APK | `LensDrop-H5-APK-<run>-<signing-mode>`; contains `LensDrop-H5-debug.apk`, BUILD_INFO.json and checksums |
| LensDrop Windows Print Station | `LensDrop-H5-Windows-x64-<run>`; contains `LensDrop-PrintStation-H5-win-x64.zip` |

Android BUILD_INFO.json should show `0.2.0-alpha.6-h5.1`, code `6`, and the new commit. Artifact names deliberately retain H5.

Extract the Windows inner ZIP completely before launching `LensDropStation.exe`. Keep the included runtime and other files together. Start paused in simulation; a successful build is not a physical printer test.

## 5. Preserve signing and existing data

The LensDrop secret remains **LENSDROP_TEST_KEYSTORE_BASE64**. Do not substitute Printer Lab's secret or generate another LensDrop key when you already have one.

Temporary signing is only for disposable trials. For ongoing phone testing use the same persistent key. A different key can block an in-place update; never uninstall an app holding important queue data just to bypass that error.

Android application ID and queue schemas are unchanged. H5 cloud runtime, station runtime, private data path, storage identity, admin key and initialisation setting are unchanged. **You do not need to redeploy Hostinger for these build fixes.**

## 6. What the repair actually does

| Failure in your upload | Correction |
|---|---|
| `finish()` conflicts with Activity.finish | Rename the staff approval-completion method and its callback to completePairing |
| `OsConstants.O_DIRECTORY` cannot be found | Use public OS APIs, verify the opened descriptor is a directory, retain fsync and propagate errors |
| Temporary new-release cannot load sharp | Supply the test release with its existing installed dependency tree; keep all resume/persistence assertions |

The Windows error is a test-fixture omission, not evidence that your Windows printer or Hostinger website has lost a library. Installing sharp in the original checkout already succeeded in the uploaded log.

## 7. Local verification and remaining gates

Local checks passed: **117 cloud tests, 24 station-protocol tests, 23 desktop-client tests, 24 exact-ledger JVM/SQLite cases, 10 session-policy and 15 print-policy assertions, and 15 new build-contract tests**. Twenty-two Java files were syntax-parsed only.

Both original Android compiler mistakes have expected-failure contract tests. The original cloud dependency failure was reproduced and then passed with the corrected fixture. Details and local logs are under `docs/h5_1/`.

**Full Android SDK build/lint, Windows native compilation, new GitHub runs, and real phone/printer operation were not performed here.** Local SDK/Gradle/.NET are absent; download requests failed DNS resolution. Test dependencies used preinstalled sharp 0.34.1; no fresh npm installation is claimed. The Android workflow still does not run lint or instrumentation.

If a new run fails, download its new logs. The first substantive compiler/test error is more useful than the final exit-code line. Never disable the failing test or clear event data just to get a green checkmark.

## 8. Historical documents and manifests

`docs/h5_1/BUILD_FIX_REPORT.md` and `H5_1_MANIFEST_SHA256.txt` describe this repaired snapshot. Older H4/H5/r1 reports and manifests are retained as historical evidence; their original counts and versions are not assertions about this repaired package. The original H5 deployment ZIP remains byte-for-byte unchanged.

## Reference

[1] GitHub: re-runs retain the original GITHUB_SHA and GITHUB_REF. https://docs.github.com/en/actions/how-tos/manage-workflow-runs/re-run-workflows-and-jobs

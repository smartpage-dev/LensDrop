# LensDrop H5.6.1 - Android build and source-coherence repair

**23 November 2026. Source/build repair only. No APK, cloud deployment, Windows rebuild or device test is claimed.**

## 1. Exact evidence

The uploaded `logs_122260118809.zip` (SHA-256 `0866281ee0a8ec8bf51ed6e32a19d243eac8577da1175a688481c685a1cfccc2`) contains 17 log files. GitHub checked out commit `f55156174f287fffd67efd54997115d505a9c7cb` in the LensDrop repository. The Gradle output identifies run 5. Its signing mode was `temporary-test`.

SDK preparation and the old three-script logic stage completed. The failure is `:app:compileDebugJavaWithJavac`, with **17 compiler errors**. No successful assembly, lint, APK verification or install is established by this run. The deprecated-API note is not the failure. The compiler error excerpt is in `Error_Excerpt.txt`.

Two different issues are visible:

| Finding | Evidence and scope |
|---|---|
| Missing Intent import in the supplied UI update | Three `new Intent(...)` navigation expressions in DeliveryActivity have no `android.content.Intent` import in the exact original H5.6 full archive. This is a defect in the delivered source, independent of how the repository was uploaded. |
| Compiled support files do not match the new screens | RecoveryActivity, PrintProfilePolicy, JpegStructure and ArchiveStaging are unavailable in the compiler's source set. CloudApi lacks thumbnail(); QueueDb lacks archivePolicy()/retryAssignments(); the compiled PrintQueue.add accepts four arguments rather than the five used by the new screen. These classes/methods are present in the exact H5.6 full archive. |
| Old workflow still running | The uploaded step runs only RUN_POLICY_TESTS, RUN_PRINT_POLICY_TESTS and RUN_LEDGER_TESTS; its Gradle command calls assembleDebug alone. The delivered H5.6 workflow has eight logic runners and both assembleDebug and lintDebug. |

The error pattern proves an inconsistent compiled source set and a workflow mismatch, **not who caused it or which upload operation occurred**. An incomplete incremental merge, omitted files/hidden workflow or other checkout changes could explain it. The user's current repository tree was not directly accessed. Do not solve this by deleting the archive/recovery calls or replacing them with empty methods.

## 2. Correction

Baseline: exact `LensDrop_H5_6_Android_Setup_UI_GitHub_Ready.zip`, SHA-256 `d05ce6a35d09e59f8c9a9da55b450b5c5ba41b6a7c0cf54cf17addb12b4e7570` (586 original files).

The **only production Java change** is `import android.content.Intent;` in DeliveryActivity. Android is version `0.2.0-alpha.6-h5.6.1`, versionCode **12**, application ID `app.lensdrop.transfer`. Existing theme, scanner, queue and upload/printing algorithms are unchanged. Photo queue schema remains 6, print queue 3. No new migration or permission is introduced by this repair.

The complete Android repair bundle includes all Android source, resources, assets, tests and build inputs, the current `.github/workflows/android-debug.yml`, build helpers and a source manifest. It is **not another small delta requiring the missing H5.2/H5.4/H5.5 files to be present**.

A new early `Verify complete Android source snapshot (no mixed versions)` step compares 91 required reference files before SDK preparation or signing restoration. It reports missing/stale files and unexpected runtime source/resource/library files, without dumping their contents. CRLF/LF normalization is permitted for UTF-8 text. No source, device data or remote service is changed by the verifier. The snapshot is checked again before artifact collection, and the result plus manifest hash are included with the APK metadata.

This is an **accidental-mix guard**, not a cryptographic distribution signature or proof of Android compatibility. Intentional custom Android modifications require a reviewed new manifest; bypassing the guard is not a repair. Native assembly and lint still run afterward and remain mandatory. Source-based test expectations were updated to code12, not suppressed.

## 3. Unchanged components

Byte comparison against the original H5.6 archive confirms that all 212 cloud files, 26 Windows Print Station files, 6 desktop-uploader files and 4 deployment archives are unchanged. The Windows workflow, signing-key generator, Android manifest and database/transport implementations remain unchanged. Existing signing restoration and cleanup functions in android_ci.py are unchanged; only source verification/provenance and the current backend note were added to its collection path.

Cloud remains `0.2.0-alpha.8-hostinger.5.5` / schema7. Windows remains `0.1.3-alpha.h5.5`. This Android-only repair does not require a live cloud redeploy or station rebuild when those components are already H5.5. A deliberately older backend still needs the separately documented H5.5 upgrade; no compatibility with old H3/H4/H5 APIs is newly promised.

The source includes historical documents and manifests. `CURRENT_RELEASE.json`, `START_BUILD_REPAIR_H5_6_1.html` and `H561_FILE_MANIFEST.json` describe this repair. Older versioned reports retain their original evidence boundaries.

## 4. Executed checks

| Check | Result | Boundary |
|---|---:|---|
| New snapshot and Intent regression tests | 23 passed | 21 inventory/CLI/negative cases plus two source-extracted javac name-resolution cases; tiny Android test doubles, not Android SDK. |
| H5.6 invitation/readiness tests | 60 assertions passed | Actual pure-Java parser/policy. |
| H5.6 UI/source contracts | 52 passed | Static source/resource checks; code12 expectation updated. |
| Actual scanner controller branch tests | 39 assertions passed | Existing minimal Android/decoder fakes; no real camera. |
| Production Java grammar checks | 36 parsed | Grammar only, not type/API checking. |
| Session rules | 10 assertions passed | Pure Java. |
| Print rules / previous build contracts | 15 + 15 passed | Existing JVM/targeted public-API stubs. |
| CP9550 profile/geometry | 31 assertions passed | No paper/driver. |
| Watch ledger | 24 passed | Actual QueueDb/WatchLedger on JVM/SQLite bridge. |
| Ticket packet / ledger | 27 + 28 passed | No Bluetooth transmission or paper scan. |
| JPEG / recovery | 21 + 25 passed | Existing framing and ledger tests. |
| Archive/workflow | 28 passed | Actual ledger/staging with bridge, not Android ContentResolver. |
| H5.5 cross-component contracts | 37 passed | Source/configuration checks with current Android version expectation. |

The missing-import negative control compiles the navigation expressions extracted from the actual DeliveryActivity, using its real content import declarations. Removing the new import recreates `cannot find symbol: class Intent`. The corrected expression set compiles and executes three fake navigation calls. This **does not compile the complete Activity against the Android framework**.

Inventory negative controls delete each missing helper, replace QueueDb/PrintQueue/CloudApi with mismatched contents, remove the Intent import, remove lint from the workflow, change versionCode and introduce an extra class/library. Each is rejected. Private local files and build outputs are not read or included in the source manifest.

An early rerun of the historical cross-component contract failed because it still expected versionCode11. Only the version expectation was corrected to12, then all37 checks passed. That initial result is retained in the execution history. Native compile/lint errors are not suppressed or relabeled as warnings.

Cloud/station application suites were not rerun for this Android-only correction; byte identity is recorded instead. Historical pass counts are not new execution.

## 5. Still required

The current environment has Java but no Gradle, Android SDK, sdkmanager or adb. A new request to Google's SDK repository failed DNS resolution. Therefore **no complete H5.6.1 Gradle/SDK build, lint, APK signing check, installation, scanner camera session, printer test, resource/endurance or Hostinger action was performed here**.

GitHub must run the exact updated snapshot and finish assembly, lint, APK verification and artifact upload. Additional SDK/lint issues may still appear. The previous camera-exit report remains a hardware/runtime gate; fixing compilation does not prove the scanner works on the phone.

## 6. Apply safely

Use the complete Android repair overlay OR the full project; no need to use both. Back up the working source checkout/custom changes. Merge at the existing LensDrop repository root, including hidden `.github`; keep `.git`, private signing secrets and runtime data. Do not use the Printer Lab repository. Do not upload only the ZIP or wrap it in an extra folder.

Confirm code12, the source manifest at repository root, all36 production Java files, and the new workflow preflight. Commit/push and start a **new Run workflow** on that commit. The failed SHA `f5515617...` must not be the new run's commit. Re-running an old job uses its original commit.

The uploaded run used temporary signing. For a disposable never-installed trial this is acceptable. For an existing installation with valuable queues/groups, preserve its signing identity; do not select a newly generated persistent key and assume it will match. Do not uninstall/clear data just to force an update. The secret name remains `LENSDROP_TEST_KEYSTORE_BASE64`.

After complete success download the `LensDrop-H5-APK-...` program artifact, not only logs. The filename remains `LensDrop-H5-debug.apk`; BUILD_INFO must show H5.6.1/code12 and your new commit. First test with disposable JPEGs and the current H5.5 backend.

## References

- Uploaded log and exact H5.6 archive: primary evidence for this repair.
- GitHub: https://docs.github.com/en/actions/how-tos/manage-workflow-runs/re-run-workflows-and-jobs
- Android lint: https://developer.android.com/studio/write/lint

These platform references explain the workflow/lint gate, not a successful LensDrop build or runtime result.

# LensDrop H5.6.1 - repair the complete Android checkout

**This is for LensDrop, not Printer Lab. No APK is included.**

Your uploaded run has a real missing Intent import and a mixed/incomplete Android support tree. It also ran an older workflow. Do not keep applying small deltas to an uncertain baseline. The repair bundle supplies the full Android build inputs and hidden workflow together.

## 1. Choose ONE package

**Android repair bundle (recommended for this build failure):** merge ALL contents of `LensDrop_H5_6_1_ANDROID_REPAIR` into the root of your existing LensDrop repository. It is a full Android replacement overlay, not a patch that assumes H5.5 support files are already present. It does not include or change the cloud/Windows application.

**Full GitHub project:** merge the contents of `LensDrop_H5_6_1` at repository root after reviewing/backing up custom changes. Cloud/Windows/deploy files are the unchanged H5.5 versions. Merely uploading this source does not deploy Hostinger.

In both cases preserve `.git`, all signing secrets and runtime data. Do not delete the entire repository, create a second nested LensDrop folder or upload just the ZIP. Never add live databases, photos or keys. Prefer GitHub Desktop to preserve all nested/hidden files. On macOS, Command + Shift + . shows hidden items.

## 2. Check the repository layout

```text
LensDrop/
  .github/workflows/android-debug.yml
  ANDROID_SOURCE_SNAPSHOT_H5_6_1.json
  clients/android/app/build.gradle
  clients/android/app/src/main/java/app/lensdrop/transfer/
    DeliveryActivity.java
    RecoveryActivity.java
    PrintProfilePolicy.java
    JpegStructure.java
    ArchiveStaging.java
    QueueDb.java
    PrintQueue.java
    CloudApi.java
    ... the remaining production files ...
  tools/github/verify_android_snapshot.py
  tools/github/android_ci.py
```

`clients/android/app/build.gradle` must show:

```gradle
versionCode 12
versionName '0.2.0-alpha.6-h5.6.1'
```

`DeliveryActivity.java` must import `android.content.Intent`. Do not remove the archive or recovery buttons to silence compiler errors. All 36 production Java files belong in this source version.

## 3. Commit and start a NEW Android run

Commit/push, then Actions > LensDrop Android APK > Run workflow, on the updated branch (normally main). Accept SDK licenses and retain your established signing choice.

Do NOT choose Re-run jobs on the old failure. That repeats the old SHA. The old uploaded commit starts `f5515617`; the new run must use your new commit.

The workflow now shows:

1. Prepare output and validate choices.
2. Verify complete Android source snapshot (no mixed versions).
3. SDK/signing preparation.
4. All policy, profile, queue, ticket, recovery, archive, UI and new integrity tests.
5. Compile APK and run Android lint (required).
6. Verify APK signature and prepare download.

If the snapshot step names a missing or stale file, restore that exact file from this bundle. Do not disable the check, regenerate its manifest merely to accept an incomplete upload, or change SDK/signing values as a workaround. Intentional custom source needs a reviewed new snapshot.

## 4. Download the program artifact

Only after the whole run succeeds, download `LensDrop-H5-APK-<new run>-<signing mode>`. Extract `LensDrop-H5-debug.apk`. The filename alone does not indicate the version.

Check BUILD_INFO.json:

```text
version_name: 0.2.0-alpha.6-h5.6.1
version_code: 12
application_id: app.lensdrop.transfer
source_snapshot: lensdrop-android-h5.6.1-code12
git_commit: YOUR NEW COMMIT
```

The artifact also includes `android-source-integrity.json` and signature/checksum information. A log artifact is not an application installer.

## 5. Preserve the installed app and event data

The failed run used `temporary-test`. That does not establish a compatible key for an existing app. Keep the original signing identity when updating in place. A different permanent key does not magically match an earlier temporary APK. Do not uninstall or clear queues to bypass a signature error.

No cloud redeploy or Windows rebuild is required solely for this Android correction when both already run H5.5. Keep cloud credentials, data directory and initialization=0 unchanged. This repair keeps Android database schemas6/3 unchanged.

After installing code12, verify Event / Setup / Support and retry the staff QR flow with a disposable invitation. Permission denial, Cancel and a wrong QR should be visible outcomes. Success must still be followed by the intended device's code approval in web Admin. The scanner hardware behavior remains unverified here.

## Evidence and scope

See `BUILD_REPAIR_REPORT.md`, `ERROR_EXCERPT.txt` and `ANDROID_SOURCE_SNAPSHOT_H5_6_1.json` in the repair overlay; full-project copies are also under `docs/h5.6.1/`. This delivery changes a missing import and build-coherence checks, not cloud/print algorithms. Local checks are not an Android SDK build, lint result or phone test.

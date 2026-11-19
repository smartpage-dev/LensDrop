# H5 update - read the current guide first

This component is part of H5. Read the root docs/h5/LensDrop_H5_Setup_Guide.html and RELEASE_STATUS.json before the retained earlier instructions below. Cloud schema5; Android native compilation and Windows native compilation remain unverified. H5 supersedes earlier statements that no print source or staff-QR source exists. No compiled APK/EXE is bundled.

---

# LensDrop Android H4 - source-only engineering alpha

**No APK. No Android SDK build or physical-phone validation.** This is source code, not a ready-to-install photographer app. Cloud H4 is required for personal delivery. Read `../../cloud/docs/h4/LensDrop_H4_WatchFolder_and_PersonalQR_Guide.html` first.

## Build

Application ID/namespace `app.lensdrop.transfer`; versionCode 4; versionName `0.2.0-alpha.5-h4`; minSdk 29; compileSdk/targetSdk 35. JDK 17, AGP 8.9.1 and Gradle 8.11.1 required. No Gradle wrapper binary/Android SDK is bundled. No new Android runtime package dependency was added; the existing QR encoder and license are bundled as assets.

```sh
gradle wrapper --gradle-version 8.11.1
./gradlew :app:assembleDebug
```

Use `gradlew.bat` on Windows. A successful build would produce `app/build/outputs/apk/debug/app-debug.apk`; this file was not produced here. The build scripts only invoke an installed toolchain. Do not ship an unreviewed debug build. Signing, store declarations and security review are unfinished.

## Changed workflow

Pair event/device, choose an exact **primary shared MediaStore JPEG folder**, then Start watching. Pairing alone does not start discovery. A transactional catalogue baseline excludes existing identities persistently. It replaces the previous timestamp/10-second-overlap policy. Restart resumes the same baseline and reconciles missed arrivals as candidates. No arbitrary SAF/SD-card/private-app/recursive/RAW/HEIC source support is claimed.

JPEG outputs must be accessible, finished and uniquely written. In-place edits of excluded files stay excluded; changes to imported identities are flagged. Late-indexed physical files can still appear as new catalogue candidates. Source-folder metadata is reconciled in pages; this is not an incremental-only scanner or a measured 10,000-photo phone benchmark.

New candidates require confirmation by default. Group mode binds confirmed photos to one permanent QR; Single mode binds one selected photo to one permanent QR. Prepare a bounded pool of three unused links per mode while online. Show history does not change receiving; explicit Resume does. Undo restores the prior receiver and retires empty accidental QRs; nonempty/observed assignments never move. Trusted-order automatic assignment is opt-in for each group and resets on group/resume/mode changes. Recovery candidates are never guessed into the current group.

Skip retains originals, does not upload, and counts separately. Existing selected/blocked jobs must be resolved before switching event. A different event still needs a different dedicated source folder. Queue v3 migration preserves old jobs as event-only uploads. Keep application ID, signing identity and app data on upgrade; never clear storage/uninstall to recover work.

## Resource and lifecycle boundaries

One active upload, 1 MiB chunks, streaming hashes, 256 MiB staging budget and at least 1 GiB free-space reserve. Small previews, local QR rendering, no native AI processing. Battery/thermal gates are provisional values, not measured promises. Other apps can keep filling their own download folders after LensDrop pauses.

The dataSync foreground service and deferred JobScheduler recovery remain. UIDT, guaranteed all-day screen-off work and immediate force-stop/reboot recovery are not implemented. Full folder/provider, Wi-Fi/cellular, permission and eight-hour device testing is required. The QR WebView is offline with no JS/native bridge; a physical WebView test and full SDK build remain outstanding.

## Test commands

`sh RUN_LEDGER_TESTS.sh` compiles the actual QueueDb/WatchLedger/WatchRules/SessionRules using small test doubles and executes SQL through Python's SQLite library. Requires JDK 17+ and Python 3. **24 cases passed**. This is NOT Android SQLite, ContentResolver, UI, instrumentation or APK validation.

`sh RUN_POLICY_TESTS.sh` runs the existing **10 pure-Java assertions**. Java syntax parsing also completed; it is not full application type checking.

## Not implemented

FTP receiver, direct camera USB/PTP/SDK, camera-card reconciliation, Pixelcake/LUMIX private-library access or guaranteed auto-export, floating QR overlay, remote printing/Print Station, PIN guest access, iOS app and signed release APK. Guest pages and cloud approval controls are in the cloud package. The small Node personal-QR rehearsal tool is a desktop API aid, not a mobile substitute.

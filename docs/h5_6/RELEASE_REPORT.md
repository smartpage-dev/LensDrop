# LensDrop H5.6 - Android setup, scanner recovery and workspace UI

23 November 2026. Engineering SOURCE pilot. No compiled APK, Android lint result or phone-camera validation is included. The exact H5.5 source archive is the baseline: SHA-256 `7962034c3baac42eebe11aaf68927ce3bd96eef171cae0312abf4c68a0226457`.

## 1. What the new evidence supports

The exact WhatsApp ZIP contains four screenshots, not a crash report. The main screen shows the earlier `H5 ALPHA` interface and lacks H5.5 private-archive controls. The pasted invitation reached a server claim/code state. The setup screenshot uses older approval wording. This strongly indicates the installed screen does not match the delivered H5.5 interface, but does not identify its exact versionCode or prove whether the scanner crashed, cancelled or returned a camera error. Input hashes and bounded observations are in INPUT_EVIDENCE.json. Raw invitation/code screenshots are NOT redistributed.

The exact H5.5 StaffSetupActivity passes scanning to ZXing's default capture activity. Its result handler silently returns when a parsed scan contains no contents. That absence of user feedback is source-confirmed. It is not a diagnosis of the phone's original camera failure. CAMERA was already in the manifest and the default ZXing capture manager has permission handling; this release does not claim a missing manifest permission was the cause. Printer Lab's old Bluetooth logs are unrelated to this camera issue.

## 2. Implemented Android changes

* Main screen: Event / Setup / Support. Installed PackageInfo version is visible, not a fixed H5 label. Event groups everyday actions and compact truthful counts; Setup has join/source/optional printers; Support retains recovery, diagnostics and controlled tests. Manual token/URL entry and specialist controls are collapsed, not removed.
* Guided source setup: picker before manual path entry, explicit saved archive policy, source-readiness action, permissions on demand, no automatic queue clearing. Only nonsecret folder/settings drafts are retained across rotation. Status refresh allows one database snapshot task at a time and stops when the main screen leaves the foreground.
* Staff setup: scan or paste, show the phone's code/context, require authenticated Admin approval, finish setup on the phone, then continue to the photo source. Pairing state is still saved encrypted before network operations. An in-process guard prevents overlapping enrollment operations across activity recreation. Replacing a saved pending claim requires confirmation; it is not silently discarded.
* New private StaffQrScannerActivity owns a QR-only BarcodeView using the existing ZXing 4.3.0 dependency. Camera permission is checked before guarded view creation/opening. Camera denial/open errors, invalid guest QRs, cancellation and empty returns receive explanations. Retry, app-permission settings and paste remain available. Failed state is retained across recreation; failure does not automatically keep reopening the camera. The preview uses SurfaceView rather than TextureView; this is an implementation choice, not proof of the original cause.
* A valid staff URL returns to the parent for an explicit server-origin review and Claim invitation. Scanning itself never calls enrollment APIs or prints. No scanned image is saved. Redacted diagnostics record only installed build, result category/time and camera exception class when available, never the invitation URL, code, token or camera frame.
* Guest QR, photo-print and thermal-ticket screens use grouped native cards and clearer primary/secondary actions. Guest-selection/Undo/History, print staging/confirmation and thermal ticket ledger/transport behavior are preserved. Recovery remains the existing specialist screen behind Support.
* Native lightweight widgets only: no WebView, new image assets or additional runtime libraries. Density-based spacing/touch targets and system/keyboard insets are handled in LuceUi. Actual native layout remains a device-test gate.

## 3. Versions and unchanged components

| Component | Version / action |
|---|---|
| Android | `0.2.0-alpha.6-h5.6`, versionCode **11**, app ID `app.lensdrop.transfer` unchanged |
| Android data | Photo schema **6**, print schema **3** unchanged; no data reset or new migration |
| Cloud | Retains H5.5 `0.2.0-alpha.8-hostinger.5.5`, schema **7**, byte-identical |
| Windows station | Retains H5.5 `0.1.3-alpha.h5.5`, byte-identical |
| Photo printing | Method 1 / Method 2, immutable job protocol, CP9550 profile/cutter adapter unchanged |
| QR tickets / Printer Lab | Ticket protocol, transport and ledger unchanged; separate Lab app unchanged |
| Build tools | Same min29/target35/compile35, AGP8.9.1, Gradle8.11.1, Java17 and ZXing4.3.0 |

The production cloud, Windows station, desktop uploader, deployment ZIPs and signing helpers are compared byte-for-byte to H5.5. QueueDb, PrintQueue, Config, WatchLedger, upload/print engines and staging are unchanged. DeliveryActivity and TicketActivity business-method tails are identical; PrintActivity's prepare/preview/submission methods are identical. Main-screen operation guards and onboarding orchestration did change and require lifecycle testing. This is not a claim that all Android code is unchanged.

Only the Android workflow needs a new run for an already-H5.5 installation. The full archive includes the existing H5.5 cloud/station for completeness, not a new H5.6 server. Do not redeploy the server just for this scanner/UI update. A substantially older server/client installation still needs the H5.5 upgrade procedure and complete stopped backups.

## 4. Executed verification

| Suite | Result | Scope |
|---|---:|---|
| New invitation/readiness logic | 60 assertions | Actual production pure-Java parser and setup policy |
| New source/resource contracts | 52 checks | Includes a deliberately weakened parser rejected by the behavioral suite |
| New scanner activity branches | 39 assertions | Actual StaffQrScannerActivity compiled against deliberately minimal Android/decoder fakes; not SDK or instrumentation |
| Production Java grammar | 36 source files | Javac parse-only, not Android API/type resolution |
| Existing session policy | 10 assertions | Pure Java |
| Existing print policy | 15 assertions | Pure Java |
| Existing H5.1 build contracts | 15 cases | Existing callback/API source contracts; version expectation updated |
| Existing CP9550 geometry | 31 assertions | Pure Java, not paper/cutter output |
| Existing watch ledger | 24 cases | Actual ledger through JVM/SQLite bridge |
| Existing thermal ticket | 27 protocol + 28 ledger | No Bluetooth or paper |
| Existing JPEG/recovery | 21 + 25 | Pure Java/SQLite |
| Existing archive/workflow | 28 | Actual ledger/staging harness |
| Existing H5.5 source contracts | 37 | Source/configuration checks; Android version expected H5.6 |
| Browser layout reference | 40 checks | Nine preview states plus navigation at 360/390/480/1280px; no native UI |

The new scanner branch tests exercise denied permission before construction, no-camera fallback, permission grant, duplicate resume, pause and late callbacks, one successful delivery, wrong-QR recovery/recreation, camera construction/open errors, explicit cancel/paste/settings, and no credential or network side effects. LuceUi and platform/decoder behavior are mocked in that suite; tests cannot establish real camera behavior or Android symbol compatibility.

Initial authoring commands caught a source-test typo and a manifest-path mistake; those were corrected before the final passing runs. Failed/interrupted commands are not counted as passes. Cloud/station suites were NOT rerun for this increment; unchanged-component hashes are used rather than claiming the previous 156/57 results as new tests. The earlier intermittent station-suite stall and all existing field qualification limits remain open.

Native `assembleDebug` and `lintDebug` stay mandatory in GitHub after the local logic checks. No suppression/baseline or continue-on-error was introduced. The environment has no Gradle/SDK/adb/.NET; official SDK/dependency download attempts failed DNS. A green native build, installation and phone tests are still required. No new APK/EXE is supplied.

## 5. Required phone acceptance

Keep the existing Android signer and app data. Do not uninstall, clear data, reset the watch baseline, change the Storage ID or regenerate credentials to fix an upgrade. If the old phone is already paired, the updated UI must retain that event and queue; do not pair again merely to test the look.

Test a fresh disposable staff QR. Camera permission denial must show a useful explanation and leave paste usable. Grant permission and explicitly retry. Cancel/back must return without changing pairing. A guest/projector QR must not enroll. A valid staff QR must first show the server confirmation, then the actual phone code; Admin must approve and the phone must finish. Rotate/background during scan and during a pending network claim, then reconcile the saved claim rather than create another identity. Close other camera apps only as a troubleshooting step, not an assumption about cause.

Verify new-only baseline and private archive, displayed-history versus receiving group, single-photo assignment, one simulated Method1/Method2 print, and manual ticket preview/cancel. Old jobs, receipts, identities and originals must remain. Test full-length paper QR, photo printer and camera/transfer coexistence separately before production.

## 6. Preview status and handoff

ANDROID_UI_PREVIEW.html and the labelled PNGs are a self-contained **browser layout reference** for the implemented native hierarchy. They are not captured Android screens or a compiled app. All data is synthetic; scan/print controls in that preview do not access hardware or the cloud. Native fonts, camera surface and keyboard behavior must be inspected after building.

Use START_ANDROID_H5_6.html for the current labels. Historical H5/H5.5 guides and manifests remain historical in the full source archive. H56_FILE_MANIFEST.json is the current source inventory. Final extracted-package checks and hashes are recorded separately to avoid circular manifests.

## 7. Primary platform references

These establish API semantics, not proof of the device failure or hardware success.

* JourneyApps ZXing Android Embedded v4.3.0 README: https://github.com/journeyapps/zxing-android-embedded/blob/v4.3.0/README.md
* Exact CameraPreview lifecycle and StateListener: https://raw.githubusercontent.com/journeyapps/zxing-android-embedded/v4.3.0/zxing-android-embedded/src/com/journeyapps/barcodescanner/CameraPreview.java
* Exact BarcodeView: https://raw.githubusercontent.com/journeyapps/zxing-android-embedded/v4.3.0/zxing-android-embedded/src/com/journeyapps/barcodescanner/BarcodeView.java
* Exact CaptureManager (old default path): https://raw.githubusercontent.com/journeyapps/zxing-android-embedded/v4.3.0/zxing-android-embedded/src/com/journeyapps/barcodescanner/CaptureManager.java
* Android runtime permissions: https://developer.android.com/training/permissions/requesting
* Android edge-to-edge/insets: https://developer.android.com/develop/ui/views/layout/edge-to-edge

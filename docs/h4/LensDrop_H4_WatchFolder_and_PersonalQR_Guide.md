# LensDrop H4
## New-photos-only watch sessions and personal QR delivery

**Engineering alpha / 16 November 2026**  
Cloud: `0.2.0-alpha.5-hostinger.4` | Cloud schema: **4** | Android source: `0.2.0-alpha.5-h4`, versionCode **4**, queue schema **3**.

**The cloud program and Android source have changed. There is no APK in this package.** The Android SDK build could not run because the required toolchain is unavailable. JVM ledger tests and Java syntax parsing are not an Android build or a phone test. The new web pages can be rehearsed with the supplied desktop command-line tool before a phone build exists.

This guide takes precedence for H4. Earlier PDF, Word and HTML guides are preserved as historical setup and feature references; their previous Android timestamp scanner and queue details do not describe H4.

### What is ready, and what is not

| Component | Delivery status |
| --- | --- |
| Cloud personal group/single links, approval controls and guest pages | Implemented; tested locally, not deployed to your Hostinger account by this update. |
| Android persistent folder baseline, selection, QR history and safe Undo | Implemented in source; exact database/ledger logic tested in a JVM/SQLite harness. No Android SDK compilation or physical validation. |
| Desktop personal-QR rehearsal | Runnable Node script; tested against the actual local backend. Not a camera bridge or folder watcher. |
| Existing Python desktop uploader | Retained unchanged. It does **not** gain Android's new-only baseline or personal-QR modes. Use a dedicated empty test folder with it. |
| FTP receiver, USB/PTP, camera SDK, editor overlay, remote printing | Not implemented in H4. |

## 01 / Upgrade your existing Hostinger installation safely

Your H3.1 cloud was already running. Do not create a new data directory or replace its storage identity for this upgrade.

1. Pause all upload clients. Stop the old server and back up its **complete private persistent data directory**, including SQLite files, uploads, branding and storage identity. Retain the previous program ZIP and your private environment settings. Do not copy a live SQLite database alone and call that a complete backup.
2. Keep `LENSDROP_ADMIN_KEY`, `LENSDROP_DATA_DIR` and `LENSDROP_STORAGE_ID` unchanged. Confirm `LENSDROP_INITIALIZE_STORAGE=0`. Do not enable initialization to work around a missing database or wrong path.
3. Upload **only** `LensDrop_Hostinger_Cloud_H4_NewPhotos_PersonalQR.zip` through the existing Node.js Web App redeploy screen. Do not upload the complete outer bundle to Hostinger or extract it manually into `public_html`.
4. Retain: framework `Other`; Node `22.x` at least `22.13`; root and output directory `.`; entry `server.js`; build `npm run build`; package manager `npm`; start `npm start` where offered. Keep a single active process using the data directory. The package respects the platform-assigned port.
5. After startup, open `/health` and check the H4 version. Open `/admin`, connect with your unchanged Admin Key, and verify the old event, photos, branding and links remain. Use the deployment check to verify initialization is disabled.
6. Start with a separate rehearsal event and a separate device token. Do not use real guests for initial validation.
7. Verify restart/redeploy retention with rehearsal photos before event use. The host persistence and backup flags are not automatically certified by this local release.

**Migration:** Cloud schema 3 becomes schema 4 on startup. H4 keeps existing public-gallery and slideshow behavior. To roll back, restore the **pre-upgrade full data backup together with its matching previous code**. Do not point H3.1 at an H4 database and expect a supported downgrade.

No dependency was added; `sharp` remains pinned to `0.34.1`. Executed checks used a preinstalled dependency, not a successful clean internet installation. Hostinger must still complete its own install/build.

## 02 / Try the personal QR pages now, without an Android APK

The new rehearsal script uploads an explicitly chosen JPEG (single mode), or 1–20 selected JPEGs (group mode). It creates and saves the QR **before uploading**, then streams chunks and verifies the final receipt. Repeating the same command with the same saved state resumes rather than creating a new QR.

**Prerequisites:** H4 cloud running; Node 22.13+ on your computer; a new device token created in Admin for the rehearsal event; one or more completed JPEGs no larger than 64 MiB each. Use ordinary JPEGs with different content from isolated preflight test photos.

In a private local folder, create a plain-text file named `device-token.txt` containing **only the device token**, not your Admin Key. Do not save it inside the cloud deployment directory or share it. Open Terminal in the extracted complete package's `cloud` folder.

For a single selected image, adapt the three local paths below to your computer:

```sh
node tools/personal-qr-rehearsal.cjs \
  --server https://honeydew-crocodile-457025.hostingersite.com \
  --token-file "/absolute/path/to/device-token.txt" \
  --state "/absolute/path/to/rehearsal-single-001" \
  --mode single \
  --photo "/absolute/path/to/finished-photo.jpg"
```

For a multi-photo group, use a **different new state directory**, `--mode group`, and repeat `--photo`:

```sh
node tools/personal-qr-rehearsal.cjs \
  --server https://honeydew-crocodile-457025.hostingersite.com \
  --token-file "/absolute/path/to/device-token.txt" \
  --state "/absolute/path/to/rehearsal-group-001" \
  --mode group \
  --photo "/absolute/path/to/photo-a.jpg" \
  --photo "/absolute/path/to/photo-b.jpg"
```

On Windows, put the command on one line or use the correct continuation syntax for your shell. Paths above are examples, not actual folders created by LensDrop. macOS Terminal accepts a dragged file/folder path.

The script prints the private personal URL and writes `qr.svg` plus `session.json` into the chosen state directory. Open `qr.svg` in a browser. Scan it before approval to see the waiting page. Preserve the state directory after interruptions; run the **same** command to resume. A different photo selection requires a new state directory and a new link. The script refuses to silently replace a saved selection.

The cloud requires approval by default. In Admin, choose the rehearsal event, find **Personal delivery / Group QR / Single photo**, refresh history, open **QR / review personal delivery**, and use **Release to this personal QR only**. The guest page then shows the approved photo. This does not publish it to the whole-event gallery or slideshow.

The rehearsal tool is deliberately limited. It does not watch folders, group photographs by camera capture, assign delayed editor outputs, or prove Android operation. It has no automatic retry loop: after a transient error, rerun the same command. Never delete state as the first recovery step.

## 03 / Android build gate

The complete package includes `clients/android`, **not an installable APK**. The app is not ready for a photographer to install directly from these ZIPs.

Build inputs are JDK 17, Gradle 8.11.1, Android Gradle Plugin 8.9.1, and Android SDK 35. Minimum Android API is 29; target is 35. The existing build scripts require those tools; they do not download a verified SDK or contain an APK. No Gradle wrapper binary is bundled.

From `clients/android` after installing the correct toolchain:

```sh
gradle wrapper --gradle-version 8.11.1
./gradlew :app:assembleDebug
```

On Windows, use `gradlew.bat :app:assembleDebug` after wrapper generation. A successful build would create `app/build/outputs/apk/debug/app-debug.apk`; that file was **not** produced here. Resolve compilation and platform errors before distributing it. Store policy review, signing/release builds and mobile security review remain outstanding.

A test-phone update must keep the same application ID (`app.lensdrop.transfer`), signing identity and app data. H4 adds queue schema 3 and preserves existing queue jobs. Do not uninstall, clear app data, change signing keys or downgrade to recover pending work. Older event-only jobs retain their old destination and are not automatically assigned a personal QR.

## 04 / Android setup after a successful build

1. Make the camera app or editor save one separate test JPEG into **shared primary phone storage**. A picture visible only inside another app's private library is not sufficient.
2. Open LensDrop. Grant the full image access required for future shared JPEG discovery; selected-photos-only permission is not enough for this implementation.
3. Enter the HTTPS cloud origin and event-specific **device token**, find shared photo folders and choose the exact dedicated output folder. Do not choose a general mixed personal album. Decide whether mobile data is permitted.
4. Tap **Pair event / verify destination**. Pairing checks the destination but does not import a folder.
5. Run **Pre-shoot test: choose ONE separate JPEG** if required. It remains isolated and cannot be publicly or personally released. Use other images for production tests.
6. Tap **Start watching / resume NEW PHOTOS ONLY**. Wait for the saved-baseline/active-watching status before producing new images. Existing accessible JPEG identities become excluded records; their photo contents are not imported or decoded for the baseline.
7. Open **Guest QR / Groups / Select one photo**. While online, tap **Prepare 3 group + 3 single QR links**. The small unused pool is stored locally for QR display during upload interruptions. Used links persist in history; offline availability is finite.
8. Select Group or Single mode and follow the matching flow below. Resume the transfer service when paused. Opening a QR alone does not start camera reception or guarantee cloud upload.

The exact source folder is bound to its event/device. Switching events still requires unresolved selected/blocked work to be handled and a different dedicated folder. Explicitly skipped candidates do not block that transition; source originals are never removed by Skip.

## 05 / What “new photos only” means

| Situation | H4 source behavior |
| --- | --- |
| 500 accessible JPEG identities already present at first Start Watching | Record as excluded; do not create upload jobs or QR deliveries. |
| New completed JPEG appears after the saved baseline | Discover it as a candidate, or apply the explicit trusted-order group policy described below. |
| Old capture date, newly added identity in the selected folder | It is a new arrival; EXIF shooting time is not the admission criterion. |
| Repeated notifications for the same identity | Reconcile the existing record; do not create duplicate jobs. |
| App restart, network interruption or missed notifications | Preserve baseline and assigned destinations; reconcile newly accessible items as candidates. |
| Existing excluded photo is edited in place | Keep it excluded. Editing an old file is not an automatic import request. |
| Previously imported photo changes | Flag the source change rather than silently replacing a guest's image. |

H4 uses the **primary shared MediaStore JPEG collection and an exact relative folder**. It does not implement arbitrary filesystem/SAF picking, recursive subfolders, removable-card sources, app-private folders, RAW, HEIC or video. Folder choices may require at least one indexed sample JPEG to appear.

“New” means a newly accessible catalogue identity after the baseline, not a guaranteed creation-time snapshot of every physical file. A pre-existing file indexed late can appear as a new candidate. Use a dedicated folder and allow indexing to settle before the first baseline. A rebuilt MediaStore identity/version stops the watcher for review instead of silently resetting it.

The scanner performs bounded metadata pages and reconciliation; it is **not yet an optimized incremental-only large-library index**. It does not decode all photos, but a very large folder still incurs database and provider work. The 10,000-record test is a ledger test, not a measured 10,000-photo phone benchmark.

A pending export must settle before queueing; staging then checks readability, source size/modified metadata and JPEG boundaries. Source apps must write complete, uniquely named output files. Do not rely on overwrite-in-place exports or a short period of unchanged file size as a universal proof of another app's write completion. The staged upload is checksummed; preview-to-source mutation handling still needs physical validation.

## 06 / Group QR, history and safe Undo

Use **Next Group** to activate a prepared group link. Its QR can be scanned before a photo is uploaded. The guest sees a waiting page until at least one photo is uploaded and approved. Several confirmed photos can belong to the same permanent group QR.

**Safe default: new arrivals are candidates, not automatically assigned.** Open Candidates, preview a photo and confirm assignment to the current receiving group. This extra confirmation is intentional for delayed official-app or editor output.

For a source whose ordering has been proven, an operator may explicitly enable **Auto-assign new arrivals to current group (TRUSTED ORDER ONLY)**. It captures the active group at first observation. The switch resets at Next Group, Resume and mode changes. Restart/recovery arrivals still require confirmation. It does not identify the person in the image or infer which guest an unobserved late camera transfer belongs to.

**History / Show QR** changes the displayed QR only. The receiving group stays unchanged, and both labels remain visible. **Resume this group** explicitly changes the receiver; existing queued jobs keep their previous destinations. **Show current receiving group** restores the current QR without making another group.

**Undo Next Group** restores the previous receiving group. An empty accidental group is retired and never repurposed. If it already has an assigned or still-settling file, that group remains in history and the photo stays assigned there. Undo does not silently move photos. H4 does not include a manual reassignment tool; a wrong personal link can be revoked by Admin. Resolve ambiguous files before assigning them.

QR history is device/event scoped, loaded in pages of 50. A saved QR is a bearer link: anyone with it can open the approved delivery. PIN protection and guest accounts are not implemented. Repeated visits do not change the destination.

## 07 / Single selected photo QR

Choose **Single selected photo QR**. Newly arrived images remain candidates. Open a candidate, inspect the small preview and confirm. One prepared single-photo link is bound to that photo. Other candidates stay unuploaded until selected separately or explicitly skipped.

The displayed QR remains fixed as other images arrive. The server refuses a second different photo on that single-photo delivery. Existing upload retries keep the same link and destination. Switching mode never converts old links.

**Skip** and **Skip all remaining candidates** retain source originals and remember the excluded choice; they do not upload, delete or mark files as cloud verified. Counts distinguish selected uploads, candidates and skips. An accounted-for selection is not a claim that every received photo was uploaded or every camera-card photo was backed up.

External-editor integration still depends on the editor actually exporting accessible finished JPEGs. Automatic preset application does not establish automatic export. No Pixelcake, LUMIX Lab or camera compatibility is certified by H4. A late, unidentifiable edited photo must remain a candidate rather than being guessed into the current guest's group.

## 08 / Personal approval versus public gallery and slideshow

Personal deliveries are separate from the event's public publication status. New personal uploads start in public **Review**, even in an event configured for immediate public publication. Personal approval alone never makes them visible to the public gallery or slideshow.

Admin can release or withdraw a photo for its personal QR, inspect history and revoke a complete personal link. The optional **auto-release future personal photos** setting is off by default. Enabling it affects future personal upload completion; it does not retroactively release old images or publish them to the public gallery.

Setting a photo **Hidden** also clears personal approval. Unhiding does not silently grant personal access again; explicit reapproval is required. Revoked links cannot be reused. An already downloaded guest copy cannot be recalled.

Guest pages use the event's name/logo, show waiting/ready states, support preview and download of the delivery JPEG, and refresh the approved feed. They do not expose camera originals. A single group is paginated at 100 photos per page. The Admin personal detail panel shows the most recent 200 photos; larger personal groups require further admin paging work and are not a complete per-group review UI in this alpha.

On failed feed verification, the page clears stale content after 30 seconds. This is not a permanent offline gallery. Revocation/hiding is enforced server-side for future requests and reflected after successful refresh; it cannot erase a guest's saved copy.

## 09 / Resource, recovery and privacy boundaries

Android source keeps one upload active, streams 1 MiB chunks, stages at most a bounded duplicate budget, and uses a small preview rather than full-resolution decoding for the candidate screen. It retains low-space, low-battery and thermal gates. The 256 MiB staging budget does not limit another app's downloads; that source folder can keep growing.

The source has a foreground-service timeout path and deferred recovery scheduling. It does not implement Android user-initiated data transfer jobs (UIDT), unlimited background execution, or a guarantee of immediate screen-off upload. Force stop, reboot, permission revocation, network transitions and eight-hour thermal/battery behavior require real-phone tests.

Private queues, source photos and server data must be preserved after failures. Do not reset the baseline, delete app data or create a new storage identity as routine recovery. Existing QR availability is limited by the prepared pool when offline. A guest still needs internet to open a cloud link, and unuploaded photos remain unavailable until transfer succeeds.

No camera FTP receiver, native SDK/USB/PTP input, camera-card completeness reconciliation, editor control, floating QR overlay, Print Station, print preview/remote printing, iOS app, PIN page or background push notifications are included in H4. Those remain separate future work.

## 10 / Tests completed and tests still required

| Executed check | Result and limitation |
| --- | --- |
| Cloud regression suite | **92 passed**, including 21 H4 tests; actual local Node/SQLite/sharp backend. |
| Exact Android ledger/queue logic | **24 passed** with actual Java source and a JVM-to-SQLite test bridge. Includes 10,000 pre-existing identities, restart, migration, undo and skips. Not Android instrumentation. |
| Existing desktop client regression | **23 passed** on Linux against the updated local backend. Windows/macOS runtime behavior not verified here. |
| Java policy checks | **10 assertions passed**; selected pure logic only. |
| Web UI / independent QR decoding | **14 checks passed** using Chromium DOM plus an explicit fetch/image bridge to the local backend. Not native browser navigation, HTTPS/CSP or a live Hostinger test. |
| Android platform build | **Not completed**. SDK/Gradle unavailable; no APK. Java syntax parsing does not close this gap. |

The web screenshots included here use **synthetic test images and local test links**. They show the implemented cloud UI, not the Android application or a real event. Native browser navigation was blocked by the environment. Use the supplied logs to distinguish bridge checks from deployment proof.

Before event use, build/install Android, test exact shared-storage permissions and source export behavior, then verify existing photos stay excluded; new/recovered photos appear once; history never redirects receiving; Undo never moves queued photos; single QR never changes to another photo; 503/network/app interruption resumes; and full Hostinger restart/redeploy keeps photos, assignments and links. Run sustained multi-photographer and eight-hour device tests with measured memory, heat, battery, storage and bandwidth.

No completed tests authorize deleting originals or claim end-to-end camera-card completeness. Do not replace a working event workflow with this engineering alpha before qualification.

## 11 / Source references and retained guides

Official platform references used during implementation:

- Android shared media: https://developer.android.com/training/data-storage/shared/media
- MediaStore identity/version APIs: https://developer.android.com/reference/android/provider/MediaStore
- Android foreground data-sync time limits: https://developer.android.com/develop/background-work/services/fgs/timeout
- AGP 8.9 / Gradle compatibility: https://developer.android.com/build/releases/agp-8-9-0-release-notes

The preceding operational behavior is specific to the supplied H4 code and tests; these platform references are not proof of phone compatibility. Read the retained H3 slideshow, H3.1 recovery and full setup guides for unchanged cloud functions. H4 release notes and this guide override earlier Android watch-window descriptions.

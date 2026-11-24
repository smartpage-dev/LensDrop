# LensDrop H5.11 — Dual Single/Group QR + Staff Group Desk

**Engineering source candidate — NOT a compiled APK, production deployment or hardware certification.**

## Exact versions
- Cloud: `0.2.0-alpha.11-hostinger.5.11`, additive SQLite schema **10**.
- Android: `0.2.0-alpha.6-h5.11`, `versionCode 17`, local photo QueueDb schema **9**; print queue schema unchanged.
- Windows Station: `0.1.4-alpha.h5.11`, **Staff Desk UI/proxy only**; native print engine, CP9550DW profiles and 58B3 protocol unchanged.

## New operator workflow
1. In Cloud Admin, select an active event and confirm the existing phone/station pairing.
2. On Android Event, use **Single Photo QR** for automatic Single links. Every new eligible photo is archived through the existing Watch Folder upload queue; after a verified cloud receipt, the phone reconciles one stable Single identity per photo. Failed QR reconciliation is retried independently. The latest verified QR is shown unless **Hold** is active.
3. Use **Group Photo QR** to prepare/start a group, explicitly assign photos, and tap **Next Group** when moving to the next guest. **Previous QR only changes the display**, never the receiving group. Group thumbnail section uses three columns and can refresh membership from the cloud when online.
4. In Cloud Admin or the authorized Windows Staff Desk, use **Groups / manage** to inspect groups, add a verified photo, or remove a photo from that group only. Group membership changes are audited and do not alter the Single link or original JPEG.
5. Staff may still look up a QR, select a photo, confirm one print request, and separately record collection. QR lookup alone never prints.

## Key behavior
- Each photo has one immutable Cloud Photo ID, one Single collection, and zero or one active Group collection. Both collections reference the same stored photo.
- `S0001`, `G0001` etc. are event-local labels, not secret URLs. Actual private links use unguessable capability tokens.
- Cloud allocates labels for **new** Single and Group collections. Old legacy labels and links remain unchanged.
- Upload retries, app restarts and delayed receipts must never reassign a photo based on completion order. Auto-assign to a receiving group remains a separate **trusted-order** choice; it is OFF by default.
- The cloud controls **personal photo release** independently of public Gallery/Slideshow. A QR can be ready while its photo is still awaiting release. Enable the event's existing Personal Delivery auto-release policy only when appropriate.
- Single QR tickets print through the already selected 58B3 printer by an explicit action. No automatic paper output or print on QR lookup.
- An existing printed Group QR continues to point to the same collection, so staff removing/moving photos changes what the guest sees. Confirm changes carefully.

## Migration and upgrade (do not skip)
1. Commit the H5.11 source to a **new Git branch**. Run the GitHub Android and Windows workflows; require Android `assembleDebug` **and** `lintDebug`, source checks, cloud and station tests, and artifact provenance.
2. Before changing Hostinger, pause new uploads and printing, reconcile uncertain physical print jobs, and take a **consistent stopped-server backup** of Cloud DB + WAL/SHM, originals, thumbnails, private assets, event storage identity, and station settings. Back up Android app data/queue where possible without clearing it.
3. Deploy the H5.11 Hostinger root ZIP **only after the backup**. Preserve `LENSDROP_ADMIN_KEY`, `LENSDROP_STORAGE_ID`, `LENSDROP_DATA_DIR`, and set `LENSDROP_INITIALIZE_STORAGE=0`. Verify schema10, old guest links, old photos, event branding, staff pairing and cloud health.
4. Update the Windows station (if using the new local Staff Group Desk) and Android code17 with the **same existing Android signing key**. Never uninstall/clear storage to bypass a signing mismatch.
5. Test on a disposable event: upload 3 photos, verify S0001–S0003, add B/C to G0002, remove C from G0002 without changing S0003, print one QR, and check the paper's exact URL. Test a 10-photo offline batch and app restart before field use.
6. **Rollback requires a matching pre-upgrade data backup.** Do not install older Cloud/schema9 or Android/schema8 code against upgraded databases. Reconcile print outcomes before restoring a ledger.

## Known limits / release gates
- Android native Gradle build/lint, actual 4G/5G upload, 58B3 paper QR, CP9550DW printing and multi-photographer endurance are **not executed here**.
- Group attribution is explicit or trusted-order only. An external editor's delayed output cannot be attributed to a guest automatically with certainty.
- Group staff operations are online only; no offline staff edit reconciliation, no station-as-uploader Method 3, no guest-operated self-service print.
- Staff Group Desk supports **add/remove**. Moving between groups is a deliberate remove-then-add operation with separate audit; an atomic move UI is not in this build.
- Only up to 100 recent Single QR records are shown on Android; historical records remain in the local DB. Group remote thumbnail list is capped to 60, display to 24.
- Do not use this source candidate in a live event until the new GitHub builds, migration rehearsal and real-device tests pass.

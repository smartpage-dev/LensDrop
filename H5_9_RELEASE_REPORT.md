# LensDrop H5.9 — Event lifecycle, independent projector visibility and Android slideshow QR

Engineering source pilot, 2026-11-24. Based on the delivered H5.8 source snapshot; no live Hostinger or installed Android app was changed.

## Versions
- Cloud `0.2.0-alpha.9-hostinger.5.9`, SQLite schema 8.
- Android `0.2.0-alpha.6-h5.9`, versionCode 15, existing app ID, photo QueueDb schema 8, print queue unchanged.
- Windows Print Station H5.5/H5.7 UI source unchanged. Printer Lab unchanged.

## New behavior
- Event lifecycle active/closed/archived, explicit name confirmation, no destructive deletion of populated events. Empty archived event deletion requires full ID+name and zero related records/logo.
- Closed events reject NEW cloud photo upload sessions, new photographer invitations, new staff orders and new guest collections; in-flight work can still complete. Offline LAN print grants are not remotely recalled. Archived state additionally checks unresolved print jobs.
- `projector_visible` per photo, independent from public gallery `status=live`. Existing live photos migrate to projector-visible to preserve previously published links; newly uploaded photos default projector-off unless the Admin explicitly enables auto projector for future photos. Hidden/test photos cannot be projected.
- Event-specific Slideshow link, shared event name/logo, explicit projector/photo counts. The old token survives migration; rotation revokes the old link.
- Android Support → Event Slideshow QR: staff-authenticated event-scoped fetch, on-screen QR/large QR/browser open and manual 58B3 print. Rechecks current link before storing and before sending; persistent ticket attempts; no auto-retry, no guest photo-print entitlement.

## Local verification
- Cloud build passed with existing pinned sharp 0.34.1 (not fresh npm install).
- 162 Cloud API/SQLite/regression tests passed, including six new event/projector/migration tests.
- 9 pure-Java slideshow ticket protocol tests compiled and passed; 17 Android slideshow UI source contracts passed.
- 102-file Android source snapshot and 109-file Cloud/Station snapshot passed; 23 Android source guard negative tests passed.
- Existing 28 ticket-ledger, 5 gallery-metadata, 23 gallery-UI, 15 print-policy checks passed; 40 Java production files parsed, not compiled against Android SDK.
- A repeated ArchiveWorkflow JVM/SQLite bridge run stalled and was interrupted; it is NOT counted as a pass or claimed fixed.

## Compatibility and limitations
- Existing guest links, cloud original images, print-job identities, Windows profiles and Bluetooth guest-ticket protocol unchanged.
- Event close/archive is NOT a remote cancellation of in-flight uploads or offline-authorized local printing.
- Projector links are bearer links; slideshow-only approval intentionally exposes the image to that audience even while private `review` remains.
- Android QR is a public slideshow URL, not personal group QR. Printed paper QR may remain physically readable after rotation but should open a revoked link.
- Real Android APK assembly/lint, phone runtime, Bluetooth paper scan, CP9550DW and live Hostinger deployment have not been executed here.
- Prior intermittent SQLite/JVM bridge test stalls are not claimed resolved.

## Deployment
Build Android on GitHub from a new commit, back up stopped Cloud and Windows data, deploy only the H5.9 Hostinger root ZIP with original keys/storage ID and initialization=0, verify migration and links, then update Android in place with the same signer. Rollback requires matching code and complete data backups and print reconciliation.

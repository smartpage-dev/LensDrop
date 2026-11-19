# LensDrop H4 release report

Date: 16 November 2026. Baseline inspected: `LensDrop_Programs_and_Docs_v0.2-alpha_H3_1_StartupFix.zip`. Cloud version `0.2.0-alpha.5-hostinger.4`, schema 4. Android source version `0.2.0-alpha.5-h4` / versionCode 4 / local queue schema 3.

## Delivery status

This is an actual cloud and Android-source update, not only a specification. **No Android APK is supplied, and an Android SDK build has not completed.** The current environment lacks Android SDK/Gradle; attempts to reach dependency/toolchain hosts failed DNS resolution. The included build-attempt log stops at the missing Gradle prerequisite. Java source parsing and the database harness are not a substitute for compiling/installing the app.

No H4 deployment was performed on Brian's Hostinger account. His previously working H3.1 site is not changed by generating these files. No user keys or account access were used in local tests.

## Implemented changes

- Cloud creates permanent personal delivery identities and random bearer QR links independently of upload completion. Group links can receive multiple files; single links bind one file at upload initiation. Retries cannot silently change the destination.
- New personal release policy is separate from public gallery/slideshow status. Approval defaults off. Releasing a personal image does not publish it publicly. Hidden clears personal permission, and unhiding does not grant it back. Event/device ownership, link revocation and cross-group asset boundaries are enforced.
- Android source replaces the timestamp-based watcher with a durable baseline of accessible shared JPEG identities. Existing files remain excluded after reopen; missed arrivals reconcile as candidates. Source changes, pending/stable checks and idempotent enqueueing avoid assuming every provider notification is a new photo.
- Android source includes Group/Single modes, bounded prepared QR pools, candidate previews, history display distinct from active receiving, explicit Resume, safe Undo, and Skip without deleting originals. Known destinations persist through interrupted uploads.
- Default candidate confirmation avoids guessing delayed editor results. Trusted-order automatic group assignment is explicit and resets when changing groups/resuming/modes. Recovered arrivals remain candidates. This is a conservative implementation, not a claim that the watcher can infer who is in a photograph.
- Cloud operation counts distinguish candidates and skips from uploaded/verified work. Selected-work accounting is separate from complete-source reconciliation.
- New Node rehearsal utility creates a group/single QR before streaming selected JPEGs, persists offsets/selection identity and verifies receipts. It is independently tested against the actual local backend. It is not a phone watcher or a replacement APK.
- Legacy Python desktop uploader is byte-for-byte unchanged (6 retained desktop files). Its behavior is not changed to new-only by H4. Existing cloud gallery/slideshow/startup fixes remain in regression coverage.

Code change inventory: **42 added/modified program/test files**, listed with hashes in `CHANGED_PROGRAM_FILES_FROM_H3_1.json`. The new HTML/Markdown guide supersedes the previous Android scanner description; old PDF/Word/HTML guides remain historical references.

## Executed verification

| Check | Result | Scope / caveat |
| --- | --- | --- |
| Cloud regression | 92 passed | Real local Node22.16, SQLite and preinstalled sharp0.34.1; 21 added H4 cases. Includes rehearsal CLI repeated with same state, with stable QR and no duplicate commit. |
| Exact Android queue/ledger harness | 24 passed | Actual Java QueueDb/WatchLedger/WatchRules/SessionRules compiled with test doubles; SQL executed via Python SQLite. Not Android SDK, ContentResolver or physical-device instrumentation. |
| Pure Java policy checks | 10 assertions passed | Existing selected policy logic only. |
| Legacy desktop regression | 23 passed | Linux runtime against H4; not Windows/macOS field validation. |
| Cloud web UI and QR | 14 passed | Chromium DOM plus explicit fetch/image bridge to actual backend; independent OpenCV QR decode. Native navigation was blocked, so HTTPS/CSP/browser transport are not certified. |
| Build/preflight | Passed | Startup syntax, in-memory SQLite, native JPEG encode/decode, web assets and QR encoder; no event storage opened. |
| Android Java syntax | Parsed | Not full API/type checking. |
| Android assembleDebug | Not completed | Missing toolchain; no APK. |

The 10,000-record case proves persistence/exclusion of metadata identities in the ledger harness, not a 10,000-photo upload soak, phone memory bound or battery claim. Screenshots are actual cloud UI using synthetic fixture photos and local test links, not promotional Android images or real guests.

## Known constraints and open validation

Android source supports primary shared MediaStore JPEGs in one exact folder. It does not support arbitrary app-private storage, recursive subfolders, removable-card/SAF sources, RAW/HEIC/video, or a certified camera/editor combination. Late indexing of existing physical files can introduce new catalogue candidates. Finished unique output files are required; overwritten files are not a reliable new-photo signal. Large-folder metadata reconciliation and UI/service resource behavior require profiling.

QR reservations are bounded to three unused links per mode and need internet to replenish. Local cancellation syncs to the server when connected; cancelled identities are never repurposed. Personal links are bearer links without PIN/login. Revocation cannot erase an image already downloaded. Admin personal photo detail currently shows the latest 200; guest feed pages are 100; phone candidate/history pages are 50. Manual assignment correction is not implemented; revoke a mistaken link and resolve before assignment.

No FTP receiver, USB/PTP/manufacturer SDK integration, Pixelcake automation, LUMIX private-library access, QR overlay, print station/remote printing, iOS app, UIDT orchestration or signed store release is included. No native Android UI, OS lifecycle, network switching, physical camera, printer, eight-hour resource, live hosting or production backup/durability qualification occurred.

## Upgrade / rollback

Pause clients, stop server and back up the entire existing persistent data directory first. Preserve Admin Key, DATA_DIR and STORAGE_ID. Keep INITIALIZE_STORAGE=0 for the existing installation. H4 migrates cloud schema3 to4; rollback requires the matching old program **and** complete preupgrade data backup. Do not downgrade only the code. Keep a single active storage writer.

Use only the root-ready cloud ZIP in Hostinger Node.js Web App. Root/output `.`, entry `server.js`, build `npm run build`, Node22.x >=22.13. No new cloud npm dependency. Clean internet npm installation was not verified in this environment; packaged checks reuse an available installed sharp binary.

A real Android build, device permission/provider tests and controlled camera-to-phone-to-cloud qualification remain the next release gates. This update must not be described as an installable or production-ready phone release.

# LensDrop H2 Enhanced - release and validation report

Release date: 14 November 2026. Cloud: **0.2.0-alpha.3-hostinger.2**. Android source: **0.2.0-alpha.3-h2**, versionCode 3.

## Delivery and readiness

This release changes the application, desktop transfer client and Android source. It is not a documentation-only repack. Use for controlled rehearsal only. There is no APK, iOS app, Windows EXE, installer or production certification. No real camera/phone combination or Hostinger account was tested.

The complete ZIP includes programs, original full guide, H1 Hostinger guide, new eight-page H2 guide (Chinese operations and upgrade sections, English technical sections), validation logs and the inner Hostinger ZIP. Upload only the inner Hostinger ZIP to a Node.js Web App after storage checks and backup.

## Implemented features

### 1. Separate source, verified and public counts

The cloud now distinguishes client-reported accessible-file jobs from completed upload sessions, deduplicated photo objects and Live/Review/Hidden publication counts. Isolated tests are counted separately. Missing legacy counts and heartbeats older than 90 seconds cannot produce a reconciled result. Ordinary API activity does not refresh source telemetry freshness. Revoked devices cannot be marked reconciled. Source receipt is not evidence that the camera remains connected.

The web console has core English/Traditional Chinese labels, three stage cards, device status, explicit refresh, paginated 200-photo admin lists, report buttons and protected test rows. Client/native status details are still partly or fully English. Admin intentionally does not constantly replace controls while reviewing images.

### 2. Isolated pre-shoot test

The upload protocol persists an immutable boolean is_test. Server authorization and public asset/gallery queries exclude test images even in a Live-by-default event. The public status endpoint rejects publication of a test. Clients check the H2 capability before sending a test to an older server. A test runs only the selected JPEG, without normal folder discovery.

This proves accessible-file-to-cloud receipt verification only; it does not automatically prove camera download or browser visibility. For the complete rehearsal, use DIFFERENT non-test JPEGs, review/publish one and inspect the actual guest gallery. Identical test bytes cannot be promoted to production for the same event/device, even under another filename. Test files consume storage/quota; cleanup UI is not included.

### 3. Conservative safe event transition

Desktop queues bind both the destination scope and source folder. Reusing an existing queue with another event/device or source is rejected. A failed pairing no longer replaces the saved offline identity before checking the scope. Each desktop event requires a different folder AND state directory; there is no machine-global registry across unrelated state directories.

Android source adds persistent source-folder bindings and refuses a new destination when old work remains, including blocked work. Changing an established scope's source folder is refused. H2 does NOT support simultaneous draining of an old event while starting a new event on the same phone. It does not infer event identity from late-arriving photos. A blocked permanent-error job can therefore hold the transition; an audited skip/abandon workflow is not provided. Do not bypass this by clearing app data.

### 4. Wrap-up and diagnostics

Desktop --drain / Finish observed queue stops discovery and processes already known queued work. Android source includes an equivalent mode. Let camera downloads finish and confirm the last file has been discovered BEFORE draining. Files arriving after discovery is stopped are not included in that snapshot.

Reconciliation never certifies camera-card completeness or authorizes deleting originals. Verified upload jobs and unique photos are distinct counters. Receipts refer to a local commit on one server, not independently replicated backup.

Cloud and client diagnostic exports use explicit field/error allowlists. Tokens, credential hashes, private labels/names, filenames, source paths, photo bytes and raw server errors are omitted as applicable. These are diagnostic SNAPSHOTS, not historical crash dumps. The richer administrator-only reconciliation report contains event/device identities and should remain confidential.

The desktop console uses a read-only database connection for diagnostics so exporting while uploading cannot accidentally reset an active row. On exit it writes status, diagnostics and wrap-up snapshots without deleting source files.

### 5. Operator and guest usability

A small Python/Tk desktop console provides test/resume/drain/pause and report export, with masked device-token entry. Tokens are not saved by the GUI. It requires Python 3.10+ and Tcl/Tk, and is not an installer. Windows/macOS source launchers are included but were not executed on those operating systems.

The guest page shows a fixed new-photo notice without automatically replacing current cards or moving the scroll position. Explicit action loads newest photos. Published-photo visibility is reconciled for the current cards (maximum 500 IDs per request). Withdrawn content is removed after the next successful check; previously downloaded/cached copies cannot be recalled. This remains a public-by-link gallery, not a password-protected client portal.

### Android source details

The updated source separates local counts and cached cloud publication counts, supports selected isolated tests, current-event blocked-job retry, drain mode and allowlisted JSON exports. Pairing no longer automatically starts the scanner. The foreground notification avoids unchanged frequent updates, and the recovery job defers when the foreground service is running. Scope checks prevent an old network client from handling a newly paired scope. Preflight/drain completion forces a final heartbeat attempt; failed reporting remains unknown/stale, not an invented success.

The original one-at-a-time transfer, streaming checksum, 1 MiB chunks, private staging cap, reserve-space and thermal/battery gates remain. Actual RAM, CPU, heat, battery and all-day background behavior are NOT measured. MediaStore reconciliation is not a fully optimized incremental-index implementation. No new camera SDK/CCAPI/PTP/FTP-receiver adapter or iOS app is included.

## Executed validation

| Check | Executed result | Boundary |
|---|---|---|
| Cloud automated suite | 35 passed, zero failed/skipped | Node 22.16.0, sharp 0.34.1 available locally |
| Desktop automated suite | 23 passed, zero failed/skipped | Linux/Python; includes real local HTTP backend CLI tests |
| Pure-Java policy | 10 assertions passed | Session rules/error allowlist only, not Android build |
| Android source parse | 10 Java files parsed | No Android symbols/resources/manifest/APK validation |
| Static/mocked web UI | 8 checks passed | set_content and synthetic fetch, not live browser/server |
| Tk UI smoke | Passed launch/idle rendering | Linux/Xvfb only, not full GUI upload E2E |
| Hostinger build preflight | Passed | Syntax, in-memory SQLite, native JPEG encode/decode; no event storage opened |

The 58 cloud/desktop tests include H1 regressions: crash recovery, lost acknowledgement, persistent queue, duplicate filenames, single-writer/storage guards and code-directory-replacement resume. New coverage includes Live-event test isolation, immutable test purpose, same-content conflicts, stale/missing telemetry, redaction, additive migration, admin pagination, gallery visibility and read-only diagnostic export.

The retained 10,000-record queue and 1,000-session tests establish metadata/session persistence, not 10,000 real-photo phone throughput or 1,000 parallel photographers. Current test logs are in docs/validation-h2. Old reports are retained as historical records and are not the H2 evidence.

## Blocked or unexecuted

Live browser navigation was blocked by ERR_BLOCKED_BY_ADMINISTRATOR. It was not bypassed; static/mocked rendering is separately labeled. Google SDK download DNS failed; Android SDK and Gradle were absent, so no APK was built. The npm registry check returned EAI_AGAIN; no clean dependency installation, lockfile or dependency audit was completed. Tests used an already available sharp 0.34.1 installation. Deployments must install and verify dependencies themselves; do not run npm ci without a valid lockfile.

No live Hostinger deployment, Windows/macOS execution, physical camera/phone test, long-duration field soak, store-policy qualification or security audit was performed. Native build scripts are supplied for JDK 17, Gradle 8.11.1, AGP 8.9.1 and SDK 35, with prerequisites and limitations in the guide.

## Upgrade and rollback

Pause uploaders, stop the single server process, and take a consistent backup of the ENTIRE private persistent data directory and saved configuration. Preserve LENSDROP_DATA_DIR, LENSDROP_STORAGE_ID, LENSDROP_ADMIN_KEY and existing device credentials. For an upgrade, LENSDROP_INITIALIZE_STORAGE must stay 0. Do not reset storage or force-delete locks to get past a startup error.

H2 adds schema-2 fields to the cloud, desktop queue and Android source schema. Existing cloud photos default to non-test. Cloud and desktop migration regressions ran; actual Android SQLite migration remains untested. H1 cannot enforce the H2 test-isolation semantics. Never start H1 on an H2 database. Roll back using the preupgrade WHOLE-DIRECTORY backup with matching H1 code; plan separately for uploads received after that backup.

Hostinger remains conditional on a confirmed private persistent LOCAL directory outside the deployment tree, SQLite/file-lock semantics, one active writer and a successful real restart/redeployment test. Node support alone does not establish those requirements. MySQL/S3 settings do not convert this implementation.

## First rehearsal

1. Back up and deploy the smaller H2 cloud ZIP to the confirmed setup. Run Check deployment; confirm old records and credentials.
2. Create a Review-mode rehearsal event and one device token. Use the desktop console with a distinct source folder and queue directory.
3. Run one separate JPEG preflight, confirm the receipt and its never-public status.
4. Upload three different rehearsal JPEGs normally. Publish one; verify public viewing/download and withdrawal in a real browser.
5. Interrupt connectivity, reopen with the same queue, drain known work and compare reports. Keep source originals.
6. Only after a real Android build, prove shared-file delivery with one exact camera/firmware/official-app/phone/OS combination; then expand to multi-photographer and eight-hour tests.

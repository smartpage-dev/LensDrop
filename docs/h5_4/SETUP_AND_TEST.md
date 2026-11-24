# LensDrop H5.4 - Reliability repair pilot

**23 November 2026 | Source release for controlled testing.** This is the first repair increment following the H5.3 independent review. It is not production certification and does not deploy your live system.

## 1. What to install, and in what order

| Component | H5.4 requirement |
|---|---|
| Hostinger cloud | `0.2.0-alpha.7-hostinger.5.4`, database schema **6** |
| Windows Print Station | `0.1.2-alpha.h5.4`, native assembly `0.1.2` |
| Android LensDrop | `0.2.0-alpha.6-h5.4`, versionCode **9**, application ID `app.lensdrop.transfer` |
| Phone storage | Photo queue schema **5**, print queue schema **2**; additive records plus transactional jobs-table rebuild |
| Printer Lab | Unchanged; separate application/repository |

**Upgrade cloud first, then Windows, then Android.** The cloud must understand the new station state and retention fields before the new station begins reporting. Historical H5.2/H5.3 instructions saying “no cloud deployment needed” do not apply to this release.

The complete GitHub archive contains all applications, tests, historical documentation and the new deployment ZIP. The separate Cloud ZIP is the only archive to upload to Hostinger. A patch is supplied only for the exact delivered H5.3 source, not an arbitrary modified fork. There is no new APK/EXE in these archives.

## 2. Back up before changing anything

Pause photo intake, phone uploads and print execution. Stop the cloud and Windows station, and take consistent backups of their **complete persistent data directories**, including databases, WAL files if present, originals, unfinished files, driver snapshots, private configuration, storage identity and `print-signing-key.pem`. Retain all camera/phone originals. Export relevant phone diagnostics and the new recovery audit after it becomes available. A diagnostics export is not a full restore backup.

Record and reconcile every uncertain physical print before replacing or restoring a ledger. Preserve your existing Android signing key, device/station credentials and printer mappings. Do not clear phone data, uninstall to overcome a signing mismatch, regenerate storage identity or reinitialize an existing cloud directory.

For the existing Hostinger installation retain:

```text
LENSDROP_ADMIN_KEY=<your existing private value>
LENSDROP_DATA_DIR=<your existing persistent private directory>
LENSDROP_STORAGE_ID=<your existing storage identity>
LENSDROP_INITIALIZE_STORAGE=0
```

No real key or invented server path is included here. Backups must be kept privately outside the source repository.

## 3. Build and deploy

1. Merge the patch contents into the root of the **LensDrop** repository, or use the full source tree. Include `.github`, preserve `.git`, private signing secrets and unrelated work. Do not use the Printer Lab repository.
2. Commit and push. Start new runs of **LensDrop Android APK** and **LensDrop Windows Print Station** on that commit. Do not rerun an old job expecting a new commit.
3. Android now runs the repair tests and **`assembleDebug` plus mandatory `lintDebug`**. No lint suppression or baseline has been added. A lint failure is a gate to fix, not an invitation to disable the check. Signing mode remains unchanged: preserve `LENSDROP_TEST_KEYSTORE_BASE64` for ongoing installations.
4. Download the program artifacts, not only the logs. Android's filename still says `LensDrop-H5-debug.apk`; inspect `BUILD_INFO.json` for H5.4/code9. Extract the complete Windows portable archive and keep its runtime files together. It is not an installer.
5. After successful native builds, deploy `LensDrop_Hostinger_Cloud_H5_4_Reliability.zip` through your existing Node.js Web App. Build remains `npm run build`; entry file `server.js`; root/output `.`; keep the previously validated Node 22 version family and storage arrangement.
6. Verify `/health`, the H5.4 cloud version, existing activities, guest links, photographs and settings. Then update Windows from the full portable output while it is stopped. Update Android in place with the same signer.
7. Keep the Windows queue paused and in Simulation during setup. Resume only the controlled simulation tests below.

The older `deploy/LensDrop_Hostinger_Cloud_H5_QR_Pairing_Print.zip` filename is retained as an **identical H5.4 alias**, not an old schema-5 deployment. The H5.4 manifest is authoritative; old manifests are historical.

## 4. Print queue recovery

An expired ready job is now shown as **`authorization_expired`** rather than remaining at the front of the executable queue. Later eligible jobs can continue. Permission/profile failures are isolated as `failed_before_submit` with a reason.

Refreshing the station online or receiving a new authorized grant may refresh permission evidence, but an expired job does **not** automatically return to printing. Inspect it and explicitly choose Retry after the station confirms current permission, matching profile revision and unexpired authority. Cancellation is allowed only before submission. The destination and original job ID do not change.

A failed individual cloud receipt is retained with its own retry delay, and does not prevent retrieval of unrelated cloud jobs. Invalid station credentials or a changed cloud identity still stop/pause the station; they are not ignored as ordinary per-job errors.

The native print call has a **180-second watchdog**. A timeout requests termination of its local helper process, records `outcome_unknown`, and pauses the queue. This does not recall paper or cancel an accepted Windows spooler job. Late callback completion cannot overwrite the unknown decision. Inspect paper and the Windows queue; never blindly retry uncertain output.

## 5. Private print-cache accounting and cleanup

Cloud image quota now counts **reserved or retained cloud print bytes**, including partial/unuploaded reservations. A LAN-only metadata receipt costs zero image bytes. Limits are 2 GiB per event, 512 MiB per device and 8 GiB globally for this private print cache. These limits do not describe your total event-gallery or hosting storage allowance.

Only `simulated`, `operator_confirmed` and `cancelled` job caches become eligible, after **24 hours**. `receiving`, `ready`, `authorization_expired`, `submitting`, `submitted_unconfirmed`, `outcome_unknown` and other unfinished/uncertain assets are retained. Job identity, specification, revision and dedup history remain after image removal.

Cleanup is request-driven, not an always-running Hostinger background worker. Station configuration refreshes and new cloud print requests trigger a small bounded batch. Admin also provides **Clean eligible completed-job cache (24h+)**. Each invocation handles at most eight eligible records; further requests can continue. A durable pending marker precedes file deletion; reserved bytes are released only after unlink and directory sync. A restart safely resumes an interrupted cleanup.

This is cleanup of **private print-cache copies**, not event originals, guest gallery images, phone photos or ticket history. It does not delete uncertain work to bypass a quota. Abandoned incomplete reservations still need an authorized, explicit resolution. Retained local phone files may continue to consume staging space after retirement; no automatic destructive reclamation was added.

## 6. Android JPEG handling and per-file retry

The photo uploader no longer requires the last two bytes of the whole file to be JPEG EOI. A bounded marker/framing validator accepts supported 8-bit baseline/extended/progressive DCT JPEGs with trailing bytes, while preserving the **complete original byte stream and checksum**. It skips segment payloads rather than looking for any `FFD9` pair, and enforces 64 MiB file and approximately 60-million-pixel bounds.

This is not a full entropy decoder or universal image/container support. Unsupported frames, malformed markers and oversized dimensions are actionable file errors. Cloud decoding and exact receipts remain required. Real camera/editor and Motion Photo samples still need qualification; only desktop-generated JPEG/trailer cases were exercised here.

Transient source/JPEG readiness errors receive per-file backoff and block after the fifth failure instead of indefinitely imposing a global network cooldown. Unrelated good jobs remain eligible. Shared network/server and storage-pressure conditions retain shared backpressure. Retry blocked files remains an explicit operator action after fixing the cause.

## 7. Recovery / previous events on Android

Open **Recovery / previous events** from main setup or the guest QR screen.

First choose **Pause photo and print workers** and wait for the current transfer checkpoint. Every mutation checks pause state and the shared transfer lane. Active uploads cannot be retired mid-checkpoint.

### Stop local retries / polling

Review the relevant upload or print. Choose **Retire locally** only after reading its status. This writes an audit decision and stops local retries/polling. It keeps the original file, staged bytes, job ID, remote identifier/outcome and guest assignment.

**Local retirement does not cancel a remote print, revoke a cloud upload, refund a print allowance or prove no photograph was printed.** A station may still finish an already accepted job. Reconcile there. Late local worker writes cannot silently reactivate a retired row.

Once remaining candidates are selected or skipped and unresolved assigned work is deliberately retired, the old event no longer blocks pairing to a new event. Choose a new source folder for the new event; existing folder bindings are not silently reassigned. The old event's credentials are archived using the existing encrypted secret storage, not exported in plaintext. Previous-event records are available for inspection; automatic restoration of old pairing or remote polling is not implemented.

### Review an overwritten source as a new candidate

For the currently paired event, retire the old unresolved work first (uploaded/skipped work already has a local outcome). Choose **Review changed source as NEW unassigned candidate**. The app copies the accessible completed JPEG into its own bounded staging area, checks source metadata, reads/hashes it twice, checks provider/scope stability, validates framing, and stores a new content revision. If any check fails, it creates no new candidate.

The old record and its issued QR never change. The new version inherits **no guest destination**. Open Candidates, inspect the staged preview, and explicitly select the intended guest. Identical previously recorded content is rejected as a duplicate revision. This cannot recover a source that the camera/editor has already deleted. Provider-epoch rebuild reconciliation remains a separate unresolved feature, not a filename-based guess.

### Audit export

Export PRIVATE recovery audit creates a JSON decision log with scope/job identifiers, prior states, timestamps and actions. It intentionally includes private identifiers for reconciliation, but no tokens, paths, filenames, guest URLs or image bytes. Store it with the event backup, not in a public repository. The normal redacted diagnostics are a separate export.

## 8. Guest identity and pairing

Quick Undo is limited to the most recent unused group within **120 seconds**. A group with assigned/observed photos or any saved ticket attempt is not quick-undoable. Use **History -> Resume this group** intentionally; the confirmation names the target. Existing assignments and issued links stay unchanged, and trusted-order automatic assignment stays off.

When Group B is receiving while History shows Group A's QR, candidate confirmation explicitly says **Assign to B** and preserves the displayed A QR. Single-photo confirmation deliberately creates and displays its new single link. A changed destination during preview requires reconfirmation.

Admin no longer displays the six-digit pairing code. Read it from the actual intended phone or station and enter it into authenticated Admin. Five wrong attempts permanently lock that invitation, including across server restart. Revoke it and generate a new invitation; do not reuse a screenshot or approve from a code copied from an Admin list. Existing paired identities remain valid unless explicitly revoked.

## 9. Controlled acceptance sequence

| Test | Required result |
|---|---|
| Upgrade | Existing event, photos, group links, station profile snapshots and job IDs remain; cloud schema 6; phone schemas 5/2. |
| Expired head + valid job | Expired request is visible and not submitted; valid request simulates once; expired work needs explicit permitted Retry. |
| Failed receipt | One recorded receipt error does not prevent another cloud job reaching the station; wrong station credentials still pause. |
| Retention | Completed cache under 24h retained; eligible older cache removed; ledger kept; unknown/submitted cache not deleted. |
| Source errors | Invalid/incomplete source blocks only itself after its retry budget; a supported trailer JPEG is preserved byte-for-byte. |
| Event turnover | Pause, inspect, retire locally, export audit, then pair event B without clearing data; inspect event A afterward. |
| Edited revision | Same URI can become a new unassigned staged candidate after explicit review; old single-photo QR unchanged. |
| History / Undo | Showing A while receiving B stays stable; used/expired quick Undo refused; intentional Resume remains. |
| Pairing | Admin has no code to copy; intended device code approves; wrong-code budget survives restart. |
| Hanging native call | In simulation/test callback, timeout records unknown and pauses; late success cannot mark completion. |
| Native / hardware | New Android build including lint, new Windows build, real phone lifecycle, CP9550 calibration and full-length 58B3 paper QR scan are still gates. |

Start with one test event, one phone, one station and synthetic JPEGs. Camera intake and real printing come only after the controlled flow works. New photos arriving during initial baseline are still excluded: pause capture/export while baselining and start only after Watching is confirmed. This wording is not a generation-boundary fix.

## 10. What remains deliberately outside this repair

No new camera FTP/SDK adapter, QR floating overlay, automated ticket printing, station ticket-scan selection/collection, payment/quota redemption or self-service kiosk. Gallery and slideshow still share `live` eligibility; personal delivery approval is separate. There is no independent per-photo projector permission yet.

Provider-epoch recovery, baseline-generation races, measured large-folder optimization, phone thermal/background longevity, ticket rotation behavior, robust stale-lock ownership and clean dependency lockfile validation remain open. The persisted recovery job still needs `RECEIVE_BOOT_COMPLETED`; this permission was not removed. Same-PID process locks are not automatically discarded.

## 11. Rollback and evidence

Schema compatibility changed. Do not replace only an APK, server ZIP or executable with an older version and expect safe downgrade. Restore matching pre-upgrade code plus complete data **only after accounting for physical output since the snapshot**, otherwise a restored old ledger could print a completed job again. Preserve signing identity. Phone application data needs a tested platform-specific backup/restore process; diagnostics alone cannot reconstruct it.

See `RELEASE_REPORT.md`, `FINDING_STATUS.json`, `verification/`, and the H5.4 source manifest. Historical tests/build logs remain labeled by their original versions. H5.1 build success is not an H5.4 build. No live system was accessed or changed for this release.

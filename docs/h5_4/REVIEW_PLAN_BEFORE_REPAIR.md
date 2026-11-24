# Corrected implementation and acceptance plan

Status: recommendation after review reconciliation, not implemented work or a promised delivery schedule. No new source version is assigned here. Preserve H5.3 baseline hashes. No participants or responsibilities have been assigned.

## A. First repair increment: availability and safe event turnover

1. **Station authorization and reporting (001/002):** expire/quarantine individual jobs visibly, keep valid jobs moving, isolate receipt failures, and preserve all uncertain submission records. Add cloud support for any new reported state before the station uses it. Keep the original job ID across direct and relay routes.
2. **Cloud private-asset lifecycle (002):** distinguish metadata-only LAN jobs, byte reservations, partial files and retained assets. Bound total storage without charging files not held by the cloud. Add race-safe terminal retention cleanup and retain identity/dedup metadata. No destructive cleanup of unfinished or uncertain jobs. A database transition plus filesystem unlink is not atomic; design and test recovery of either order explicitly.
3. **Per-file validation/retry (003):** test the complete staged object using a bounded validator that understands relevant JPEG structure. Do not merely search for FFD9 (which may occur in metadata/embedded images), strip a trailer silently or decode unlimited pixels into memory. File errors receive per-file backoff and eventual actionable blocking; real shared network/server backpressure can remain global.
4. **Unresolved-event recovery (004/005):** expose candidates, changed sources, blocked uploads and pending/uncertain photo prints. Add explicit local retirement with an audit export, retaining original files and remote outcome uncertainty. A changed source becomes a distinct, unassigned candidate only after confirmation. Coordinate changes with current workers and retain old-event inspection. Do not use clear-data or reinstall as recovery.

Acceptance: invert the seven reproduction assertions where behavior should change; retain positive invariants and add multi-job, restart, concurrent-request and boundary tests. The original reproductions must fail against a correct repair where they assert defects. Tests for intended old behavior such as explicit Resume must be rewritten with the new UX semantics rather than removed silently.

## B. Guest-identity and trust hardening

- **006/008:** bounded quick Undo, clear previous/current guest labels, explicit Resume for used groups, and stable shown QR during another group's candidate confirmation. Ticket-issued links and existing image assignments remain immutable.
- **007:** authenticated blind code entry from the actual claiming device, context display, expiry and durable attempt/rate limits. A wrong-claim race is denied until the operator intentionally verifies the device; do not allow pairings based on a QR screenshot alone.
- **010/014/017:** explicit provider-epoch reconciliation; per-link errors cannot starve QR preparation; recoverable provider failures are distinguished from identity/scope violations. Name/size/mtime is not a content-identity proof.
- **019:** a native print watchdog marks uncertain work and pauses, but never retries physical submission automatically. Verify late native exits cannot overwrite the uncertain terminal decision.
- **021:** fix stale diagnostics and misleading baseline wording; keep RECEIVE_BOOT_COMPLETED for the persisted recovery job.

## C. Measured/mobile qualification and build integrity

- **009/011:** define and test the watch-start boundary; instrument folder scans at 0/1,000/10,000 files. Exercise pending exports and files moved into the watched folder. Optimize only with before/after correctness and resource evidence.
- **012/016:** test background timeout and user resume, camera-Wi-Fi/cloud cellular routing, thermal/battery behavior and overlapping transfer/print use. No claimed universal six-hour failure; no service-type substitution simply to evade restrictions.
- **018:** test rotation separately from deliberate background navigation for manual tickets. Keep immutable attempts and no automatic resend.
- **020:** preserve signing identities; add/review lockfiles for pinned dependencies, verify action commit references and add lint evidence. A temporary key should be an explicit disposable-test choice; do not generate a replacement for an installed persistent app.

## D. Publication policy / future feature increment

- **015:** correct current documentation immediately. Independent slideshow eligibility needs an explicit policy for existing photos, future defaults and direct asset protection. Do not choose a privacy-affecting migration default without documenting it for the owner.
- Ticket scan-to-select/collection, kiosk entitlement redemption, automatic tickets, FTP/native camera intake and expanded editing are not part of the reliability repair. They remain separate projects/gates.

## Migration and rollback requirements

Cloud first if new station-state reports or API response rules are introduced, then station, then compatible Android. Plan Android schema 4 upgrades additively if needed; old code does not necessarily interpret new terminal states consistently. A no-column state change is still a behavioral migration, not proof of rollback compatibility.

Stop and back up the complete data directory (database/WAL together under a consistent stopped snapshot, originals, pending files, identity marker and signing key), and the station ledger/settings/driver snapshots before upgrades. Never regenerate credentials, relabel old jobs, change target queues, or renew expired rights without authority. Restore matching pre-upgrade data and code only after accounting for physical output since the backup; rolling back old ledgers can otherwise resurrect a job that already printed.

Local retirement is an audit decision, not deletion. Image-cache cleanup must never erase the last required evidence for an unresolved job. Clear UI language must distinguish Cancel request, Stop local retry, Printer submission and Operator-confirmed collection.

## Release gates

| Gate | Evidence required |
|---|---|
| Source and dependencies | Exact commit/archive hashes; unchanged unrelated source; fresh pinned dependency install and lock verification. |
| Logic and protocol | Existing suites plus corrected multi-job/retention/recovery/approval regressions; no falsely counted expected-failure tests. |
| Native build | New Android build with lint evidence; new Windows build/layout checks; signing fingerprints and output hashes. |
| Phone recovery | New-only baseline, source overwrite, provider epoch change, event turnover, process death, timeout and explicit resume without clearing data. |
| Print simulation | Local/cloud/hybrid same job, expired head followed by eligible work, one failed receipt, quota cleanup and a hanging callback. |
| Real photo output | Supervised CP9550 4x6/auto-pair/manual-pair calibration; profile alternation; quantities; interrupted driver outcome review. |
| Real QR output | Actual full-length disposable guest link decoded completely from paper; same guest after history/Undo; missing paper, sleep and cancelled/partial send. |
| Endurance | Camera intake, upload, QR ticket and station use together; measure CPU/RAM/battery/temperature and reconciled counts. |
| Restore | Test restoration retaining signing identity and pending work, with no double writer or resurrected physical print. |

These are gates to execute, not completed tests. Production promotion requires their evidence; unit/JVM results alone cannot satisfy them.

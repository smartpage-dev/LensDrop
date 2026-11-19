> Historical pre-H2 reference. Consult the H2 guide and release report for current behavior and executed validation.

> Baseline 0.2.0-alpha.1 record retained for context. For current Hostinger startup, test status and limitations, read docs/hostinger/RELEASE_NOTES.md and the Hostinger setup guide first.

# LensDrop 0.2-alpha test report

Executed in the current Linux environment on 10 November 2026. Runtime: Node.js v22.16.0, Python 3.13.5, sharp 0.34.1 supplied in the environment. Node's built-in SQLite emitted its experimental warning. Tests ran against temporary local data directories and generated image fixtures; no real event/customer cloud was used.

## Executed checks: 23 passing tests

Cloud suite: 12/12 passing (`cloud/tests/test-results.txt`).

- Exact uploaded-byte preservation, review-first policy and public access checks.
- Chunk/full-file checksums, replay validation, invalid offsets and normal restart recovery.
- Same filenames across devices; content deduplication within device scope.
- Event/device authorization, token revocation and reused-client-ID rejection.
- 1,000 pending server upload sessions retained across a restart.
- Invalid JPEG, incorrect full hash and incomplete upload never published.
- Single-process data-directory lock.
- Three real subprocess SIGKILL cases: after chunk rename, after originals/derivatives rename, and after committed photo row. Restart produces a resumable session or the same single completed photo.
- Legacy prototype cap fix, including 10,000 dummy queued items.
- Legacy returned-edit review/hidden policy.

Desktop suite: 11/11 passing (`clients/tests/test-results.txt`).

- Lost chunk acknowledgement followed by client reopening and successful resume against the real local cloud service, with no duplicate committed photo.
- Revoked token stops/blocking behavior.
- Three distinct real JPEG fixtures all accounted for with source files retained.
- 10,000 pending SQLite job records retained after reopening.
- Duplicate observation deduplication and uploading-state recovery.
- Partial-source guard, staging hash equality, source preservation, and rebuilding crash-leftover staging files.
- Staging-budget backpressure retains jobs.
- Scope binding and rejecting unsafe remote HTTP/embedded credentials.

JavaScript syntax checks and Python compilation/import checks also executed. Source tests and their raw output are supplied so these assertions can be reproduced.

## What these numbers do NOT prove

The 10,000-job result is a metadata-queue persistence test, NOT 10,000 camera JPEGs uploaded through a phone. The 1,000-session test is not 1,000 concurrent transfers. Three SIGKILL cases cover selected critical commit boundaries, not every possible filesystem/hardware failure. No comparative Piufoto benchmark was performed.

Not executed: Android SDK build, APK installation, Android instrumented tests, iOS build, physical camera/phone transfers, eight-hour soak, battery/thermal/RAM benchmarks, actual multi-photographer event, Windows/macOS OS-specific launcher runs, browser end-to-end UI test, clean dependency installation/audit, public hosting deployment, independent security review, replicated backup/restore.

The Android source shares the protocol design but is a separate implementation. Passing Python/Node tests do NOT mean the Android app passes those tests. A production-ready mobile app is not claimed.

## Reproduction

Run `npm install && npm test` in the cloud directory. From the clients directory run `LENSDROP_CLOUD_DIR=/absolute/path/to/cloud python tests/test_client.py`. Both packages are needed for the desktop integration suite. Without the cloud environment variable, those integration tests are skipped.

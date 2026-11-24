# LensDrop H5.4 release and verification report

Date: 2026-11-23. Baseline: exact LensDrop H5.3 source archive SHA-256 `24497103ce96360b0efd21249e24c0681a3fa7d3939ab439d9cf94dd68033c9c` (426 original files). Engineering source repair release, not production certification.

## Shipped scope

- **001:** visibly quarantine expired/unauthorized/profile-mismatched print jobs; continue other eligible jobs; authenticated refresh plus explicit Retry, not silent printing.
- **002:** independent receipt retry/backoff, reservations for actual cloud print-cache responsibility, metadata-only LAN records consume zero image bytes; bounded 24h terminal cache cleanup with durable tombstones and retained identity.
- **003:** structurally bounded JPEG framing validator, original-byte/trailer preservation and separate file-local retry budget.
- **004/005:** Android pause/checkpoint guarded local retirement, previous-event audit/history, encrypted credential archive, explicit staged changed-source revisions without inherited delivery. This is not remote cancellation.
- **006/008:** 120-second unused-group quick Undo, explicit Resume, named receiving destination, preserve historical shown QR during group assignment.
- **007:** blind device-code approval in authenticated Admin; five durable wrong-code attempts per invitation.
- **019:** 180-second native call watchdog, unknown outcome plus pause, no late-state overwrite or auto reprint.
- **020/021 partial:** mandatory Android lint added to GitHub, new tests wired, accurate diagnostics and baseline wording; persisted-job permission retained. No fresh dependency lock install claim.

## Versions and migration

Cloud `0.2.0-alpha.7-hostinger.5.4`, schema6; station `0.1.2-alpha.h5.4`, native assembly0.1.2; Android `0.2.0-alpha.6-h5.4` / code9 / app ID unchanged, transfer queue5 and print queue2. The station ledger gains per-receipt backoff/attention columns transactionally; this is a behavioral migration even without a station user_version label. Upgrade cloud, station, Android after consistent stopped backups.

Core photo-print request remains the same nine-field immutable protocol. CP9550 driver capture, strip composition and native adapter source are unchanged (native version marker updated). Ticket packet/transport/ledger logic is unchanged; Undo surrounding it is deliberately stricter. Printer Lab, desktop uploader, signing helpers and camera integration are unchanged.

## Executed tests (this increment)

| Suite | Executed result | Boundary |
|---|---:|---|
| Cloud build | Pass | Existing local sharp0.34.1; not fresh install or Hostinger |
| Cloud tests | 131 pass | 117 existing +14 new reservation/retention/migration/pairing cases; real local APIs/SQLite |
| Station tests | 52 pass | Existing43 +9 new queue/report/watchdog/migration cases; local HTTP/HTTPS and simulated driver/callback |
| Android JPEG/retry | 21 pass | Real desktop-generated baseline/progressive JPEGs and synthetic trailer/invalid cases; pure Java framing |
| Android recovery/identity | 25 pass | Exact production ledger through JVM/SQLite bridge; schema4 migration from frozen H5.3 QueueDb included |
| Existing Android watch | 24 pass | Actual QueueDb/WatchLedger bridge; 10,000 baseline records; not ContentResolver |
| Existing ticket | 27 packet +28 ledger pass | No Bluetooth/paper |
| Existing policy | 10 session +15 print pass | Pure Java; existing H5.1 contracts also run by print script |
| Existing build contracts | 15 pass | Targeted stub/public-API callback checks, not Android SDK |
| Existing CP9550 layout | 31 pass | Pure Java geometry, not actual cut/profile output |
| Existing desktop | 23 pass | Includes local cloud integration; source unchanged |
| Web UI harness | 14 pass | Exact HTML/JS with explicit local API bridge; 390px/desktop, no native Windows or HTTPS browser test |
| Java syntax | Parse pass | All current production Java files; not type/API/resource compilation |

The new defect assertions were changed into desired-behavior regressions rather than silently skipping them. Existing tests for previously unbounded Undo and expiry were intentionally updated; their invariants remain. The external reviewer reproduction files are retained in the prior review-response package, not presented as passing repair tests.

## Test limitations and intermediate failures

Early cloud tests caught an unupdated schema guard and old schema expectations; those were fixed before final131 pass. Two old station tests expected expired jobs to stay ready/auto-resume; new source semantics required explicit assertions. A test initially referenced the pairing response as an object instead of its actual array and was corrected. The audit-export test used a minimal JSONObject/JSONArray stub without array serialization; it was changed to inspect actual fields rather than pretend the stub is a full JSON implementation.

Two combined repeated station runs stalled without an assertion failure (one after progress24 and one after43); the second occurred after the test-client socket/timeout hardening, so that change is NOT established as the cause or cure. Three diagnostic reruns and then three uninstrumented reruns of the exact extracted source each passed all52 tests. The intermittent stall remains unexplained and is an explicit repeatability/soak-test gate, not dismissed as a proven environment problem. The first extracted cloud run passed all131 tests. Extracted-source JPEG/recovery21+25 and ticket27+28 checks also passed. Another batched JVM/parse command reached successful test summaries but timed out before the parser ran; syntax parsing was run separately and passed. Interrupted commands are not counted as completed passes; repeated passes do not increase the number of distinct test cases.

## What has NOT been executed here

No new APK/EXE/native Windows build, Android SDK lint/instrumentation, clean dependency installation, actual provider/lifecycle recovery, real camera/editor input, native printer process termination, CP9550 paper/cutter, integrated58B3 paper scan, phone endurance, live Hostinger deployment or production rollback. Gradle/Android SDK/.NET are unavailable; the official SDK request failed DNS. Adding lint to CI is not claiming lint passed.

Storage retention tests simulate crashes at the tombstone/unlink boundaries; they are not power-failure tests on the target filesystem. Native watchdog tests use promises/callbacks, not a Windows driver. Local retirement leaves originals/staged data intact, so storage pressure can persist until separately authorized reconciliation. Provider rebuild still fails closed and needs a later recovery flow.

## Review coverage

`FINDING_STATUS.json` preserves all22 original finding IDs/titles/severities and records what changed versus deferred. This is not “22 issues fixed.” Source-level implementation and local regressions cannot close hardware qualification gates. Separate public gallery/projector eligibility, self-service/ticket-scanning and automatic tickets remain future work.

## Delivery

Use the complete source ZIP or the patch over exactH5.3, not both. Deploy the root Cloud ZIP only. Existing keys/storage identities/printer targets stay unchanged; initialization remains0. The guide explains upgrade ordering, explicit actions and rollback hazards. No real guest data, credentials, native binaries, printer drivers or fonts are redistributed.

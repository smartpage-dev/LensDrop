# LensDrop H5.11 Engineering Release Report

## Scope delivered in source
- Additive Cloud schema10 `collection_memberships`, unique active Single/Group membership per photo, audit, event sequences, idempotent staff membership operations.
- Legacy `deliveries` and `delivery_items` retained for compatibility/backfill; no old bearer links regenerated.
- Device Single ensure endpoint and Group member/thumbnail read endpoints. New Group labels allocated by Cloud for H5.11 phone requests.
- Staff Photo Desk on both Cloud and Windows: Single/Group labels and filters, Group list, add/remove membership with revision and explicit confirmation; no print on lookup.
- Android independent Single page, Group page routing, QR viewport fix, thumbnails, Single cloud-receipt reconciliation with persisted retry and history hold. Single ticket printing uses its own selected collection ID, not the shown Group.
- No changes to CP9550DW native driver, photo print protocol or 58B3 native packet generator.

## Tests executed in this environment
- Cloud Node/SQLite: 175/175 passed, pinned globally installed sharp 0.34.1 (not a fresh npm install).
- Station Node: 57/57 passed.
- Windows packaging unit tests: 8/8 passed (simulated native tool calls; not a Windows EXE build).
- Android source snapshot: 105 exact files passed; Cloud/Station source snapshot: 114 exact files passed.
- Android UI source/parser checks: 42 Java syntax parses passed, not Android SDK typechecking.
- Android ticket-ledger JVM/SQLite: 28 cases passed; existing workflow/gallery suites intermittently stalled in this environment and were **not** counted as passed in the final run.
- Android source snapshot negative tests: 23 passed.
- No actual Android APK assembly/lint, Windows native publish, hardware printing, live Hostinger deployment, real-phone endurance or migration of a live database performed.

## Important safety notes
- Cloud schema9 → 10 and Android queue8 → 9 are forward migrations. Back up first; no code-only rollback.
- Auto Single QR is generated after **verified** Cloud receipt, never just a file arrival. Guest photo visibility still follows the independent personal-release policy.
- Group assignment never infers a guest from delayed upload order.
- Old phone builds retain legacy binding behavior; use H5.11 clients for dual association. Mixed-version deployment needs a controlled rehearsal.
- The old H5.10 tests asserting a photo cannot have both a Single and a Group were updated to reflect the approved new business rule; other regression checks remain enabled.

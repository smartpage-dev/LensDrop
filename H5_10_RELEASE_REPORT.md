# LensDrop H5.10 — Event photo visibility, diagnostics and gallery synchronization

**Status:** Engineering source pilot. No live Hostinger deployment, Android native build/lint, Windows native build, or device endurance certification was performed in this release.

## Basis and scope

Based on the complete H5.9 GitHub source after the Windows test-manifest correction. Cloud schema 8 -> **9**; Android version **0.2.0-alpha.6-h5.10 / code 16**, Android photo queue schema remains **8** and print queue remains unchanged. Windows Print Station code is unchanged.

### New behavior

1. **Independent archive and publication.** Eligible new watch-folder arrivals continue to archive privately. Admin 03 Photos & Guests -> Event photo publishing selects whether future verified photos are auto-approved for the public Event Gallery and/or Slideshow. Defaults remain legacy behavior until the event explicitly saves this new policy. Existing photos are not retroactively published. Isolated test uploads remain hidden. Personal guest QR release is a separate policy.
2. **Staff Photo Desk** can browse verified private event photos, including unassigned/review photos. Server-side filters: All, Starred, Printed, Not printed, Review, Public, Slideshow, Globally hidden. **Printed** means `operator_confirmed`; a submitted/unconfirmed spooler result does not count as physically printed. Staff may explicitly star and toggle public channels. A hidden photo is not previewable or printable through this desk.
3. **Cloud-synchronized stars and visibility.** Cloud `photos.starred` and `projector_visible` plus existing `status` are the authoritative flags. Android Gallery syncs its current phone's recent cloud flags with a bounded GET every ~15 seconds while the gallery is foreground; its local list checks for new photos every 5 seconds. Three-column rows have blank equal-width placeholders until three photos exist. Cloud changes require verified photo IDs and active credentials.
4. **Guest personal QR:** A guest may request **hide only** from public Event Gallery or public Slideshow for a photo released to their own QR. This is **disabled by default** until Admin enables the event-level permission. No guest can publish a photo, change stars, delete cloud originals, or change print entitlements. Their personal photo remains available.
5. **Photographer management:** Existing revoke/rotate controls remain. Added reversible Admin-list hide and **Archive & Revoke**. Archive preserves photos and event history, blocks token rotation, and requires a fresh identity for future re-enrollment. No destructive deletion of photographer records with photos is provided.
6. **Event diagnostic export:** Admin event export includes event configuration, photo/queue counts, bounded photo action audit, device/station statuses and up to 500 sanitized server error categories. It does **not** contain passwords, tokens, full request paths, filenames, original photos, raw heartbeats or complete server stack traces. A server restart retains bounded errors in the SQLite database. This is not a complete host-level logcat/server log archive.
7. **Auto refresh:** existing Cloud 15/30 second status poll and Slideshow 5/10/15 second checks remain; Staff Desk adds a 10-second foreground refresh when no ticket collection/preview is active, without replacing an active print confirmation.

## Critical distinctions

- A photo can be cloud verified but private; its absence from a public gallery is not an upload failure.
- Personal QR release is independent of public gallery and slideshow visibility.
- A slideshow link is a public event capability, not a personal guest QR or print authorization.
- Printing a thermal QR ticket does not initiate a CP9550DW photo print.
- `operator_confirmed` reflects station operator confirmation; do not treat `submitted_unconfirmed` as printed.
- Existing legacy `Publish after verification` event policy may behave differently from newly saved explicit policy. Review per-event settings before enabling auto-publication.
- Admin's old global `hidden` state also hides personal delivery; new channel-specific hide controls preserve the personal photo. Do not use global Hide when only removing from slideshow.

## Testing

- Cloud Node build passed with pinned sharp 0.34.1 already available locally; **170/170 Cloud regression tests passed**, including 8 new end-to-end H5.10 API tests.
- Windows Station **57/57** existing protocol tests passed; Windows packaging **8/8** tests passed. No Windows native build was performed.
- Android 103-file source snapshot passed; 23 source-integrity tests, 26 gallery UI contracts, 5 gallery ledger tests, 10 session assertions, 31 print geometry assertions, 40 Java grammar parses and other selected policy checks passed. **No Gradle assembleDebug/lintDebug, phone UI or camera test** was performed.
- Browser DOM layout inspection at 390px and 1280px confirmed the policy panel is within 03 Photos & Guests, with no page JavaScript errors in static mode. This was not a live authenticated Hostinger session.
- One pre-existing Android workflow/SQLite bridge rerun stalled; do not count it as passed.

## Upgrade and rollback

1. Build Android code16 from the updated GitHub source using the **same persistent signing key**; keep existing installed app data and queued work. Do not install yet.
2. Stop activity; reconcile any physical prints marked unknown. Take consistent backups of the cloud database, WAL/SHM, originals, delivery images, thumbnails, branding, configuration and storage identity; back up station data and Android data according to your established procedure.
3. Deploy only `LensDrop_Hostinger_Cloud_H5_10.zip` through Hostinger Node.js Web App. Preserve `LENSDROP_ADMIN_KEY`, `LENSDROP_STORAGE_ID`, `LENSDROP_DATA_DIR`, and `LENSDROP_INITIALIZE_STORAGE=0`. Verify schema 9 and existing event/photo counts.
4. In Admin 03, select a disposable event. Save explicit auto-approval flags, verify an unassigned archive appears privately in Staff Desk, then test public gallery and slideshow separately. Verify stars, guest hide opt-in, photographer archive and redacted diagnostic export.
5. Install Android code16 with the existing signer. Confirm the 3-column layout with 1 and 2 photos, cloud-star sync, and per-photo channel hide. Keep print station in Simulation for first print tests.
6. **Rollback is not code-only.** Schema9 must not be opened by H5.9; restore a consistent stopped pre-upgrade database/files backup with matching code, and reconcile any prints or uploads made since backup. Never clear phone data or blindly replay unknown print jobs.

## Open items / constraints

- No camera/phone heat, memory, 58B3, CP9550DW, Windows 11 driver, Hostinger persistence or high-volume upload validation here.
- Historical Android Gallery items without a verified cloud ID cannot be remotely starred until upload completes.
- Android cloud-flag sync covers the newest 200 photos belonging to the phone, not an unbounded event library.
- Staff Desk is online and uses authenticated cloud APIs; offline full event-library browsing is not provided.
- Photographer removal with associated records is deliberately not supported; archive/revoke preserves the audit trail.
- Export contains structured bounded diagnostics, not arbitrary host filesystem logs or guest photos.

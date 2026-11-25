# LensDrop H5.12.1 — QR Before Upload / Guest Preparing / Manage

Engineering source candidate built on the EXACT `LensDrop-main(1).zip` H5.11 repository provided by the user (2026-11-25), incorporating the H5.12 collection-membership JPEG fix before this feature update. This is a STAGING candidate, not a deployed or hardware-certified release.

## Versions
- Android: `0.2.0-alpha.7-h5.12.1`, `versionCode 18`, local photo QueueDb schema **10**; print queue schema retained.
- Cloud: `0.2.0-alpha.12-hostinger.5.12.1`, additive Cloud SQLite schema **11**.
- Windows: `0.1.4-alpha.h5.11` unchanged; no QR-ticket native generator, driver, CP9550DW profile or print-queue change.

## Included
- Local per-photo cryptographically random Single guest tokens and local Group guest tokens, durable and recoverable before upload. Exact uploaded identity is reconciled against Cloud photo identity; QR never derived from filename, sequence or photographer code.
- Authenticated device `POST /v1/device/single-preissue` registers the private link without waiting for JPEG upload. Cloud binds same Single delivery after the verified upload completes. Original H5.12 JPEG/thumbnail membership fix retained.
- Guest personal page shows Preparing (including not-yet-known private URLs), Awaiting Release, Ready and identity exception states with automatic 3-second polling. Photo visibility and personal-release rules remain enforced.
- Group Photo page defaults to most recent *populated* group while receiving group remains distinct; existing history/undo/printing semantics stay explicit.
- Android Support includes Event Gallery QR and retains Slideshow QR, preserving event visibility policy.
- Cloud Admin can re-pair an existing photographer/device using short-lived admin-approved invitation, rename, archive/revoke and delete **only if no dependent history**. Not a bulk destructive deletion.
- Manual QR-ticket printing may be initiated before upload. No automated print and no assertion that a physical receipt printed.
- H5.12.1 GitHub source snapshots + build gates updated for Android versionCode18/schema10 and Cloud schema11.

## Safety limits / not yet field-tested
- Cloud cannot identify a device-local QR until the pre-registration request arrives. Unknown-token guest page retries without exposing private data; it may briefly say Preparing on invalid links.
- If network, upload or release never succeeds, guest page cannot conjure the photograph; photographer intervention may be required.
- Group membership remains explicit or a separately configured trusted-order workflow. File arrival order does **not** identify a guest reliably.
- Forward migrations: do not roll back code alone after Android schema10 or Cloud schema11; restore matching stopped-server + phone backups.
- No GitHub native Android APK/lint, Windows native EXE, live Hostinger migration, actual QR-ticket print, CP9550DW photo-print or multi-phone field endurance certification in this local test environment.

See companion `LD5121_GUIDE.md` and `LD5121_TESTS.md` for installation, test matrix and full caveats.

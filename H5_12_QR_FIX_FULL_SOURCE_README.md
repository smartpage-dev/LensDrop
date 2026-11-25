# LensDrop H5.12 QR Fix — FULL SOURCE RECOVERY / STAGING ONLY

Date: 2026-11-25.

## Origin and scope
- Full base: user-uploaded `LensDrop-main(1).zip`, SHA-256 `98a0f7a1894efdce4913bdb42d1c309689586e38cefa6ee09ce49568f325bc62`.
- Source baseline: H5.11 (Android code17, QueueDb schema9; Cloud schema10; Windows station 0.1.4-alpha.h5.11).
- Included: H5.11 Build Repair Overlay is already byte-identical in user-supplied GitHub archive.
- Added H5.12 QR guest media fix to `cloud/src/deliveries.cjs` and its source regression in `cloud/tests/h511-dual-collections.test.cjs`.
- Rebuilt new exact H5.12 Cloud/Station source snapshot + verifier and Windows workflow label; historical H5.11 snapshot retained unaltered.
- Android source and Android verifier unchanged (H5.11 105-file snapshot). Windows print engine unchanged. Database schema unchanged.

## Why QR image fix matters
Previously a new Single/Group collection could list a released photo from `collection_memberships`, but the JPEG/thumbnail endpoint still used legacy `delivery_items`. The new query uses `collection_memberships` for both listing and file access, retains `active=1`, `released=1`, hidden/test exclusions and existing capability checks. The personal auto-release setting is still independent; a QR without release will correctly show no downloadable photo.

## Not implemented
Pre-generated QR before upload, pending/preparing guest page, latest Group UI restoration, photographer delete/re-pair/manage, support Gallery QR, native builds, field/hardware tests. These belong to subsequent H5.12.x development, not this recovery checkpoint.

## How to build
1. Extract this ZIP and place its *contents* at the root of the GitHub repository on a fresh branch. Do not merge with a different Android source revision or clear any device/database state.
2. Verify: `python3 tools/github/verify_android_snapshot.py` (H5.11 105 files), `python3 tools/github/verify_h57_cloud_station_snapshot.py` (H5.12 114 files), `python3 tools/github/test_windows_packaging.py` (8 tests).
3. Install Cloud dependency normally on CI: `npm install --prefix cloud --no-audit --no-fund` (`sharp` is pinned at 0.34.1, subject to suitable runtime).
4. Run: `npm --prefix cloud run build`, `npm --prefix cloud test`, `node --test --test-concurrency=1 clients/print-station/tests/*.test.cjs`.
5. For Android manual workflow choose the same persistent TEST signing key for an update install; never uninstall existing app merely to change signing identity.
6. Run a new GitHub Android APK and Windows Print Station workflow on this new commit. Inspect Build Logs; native APK/EXE are NOT packaged here.
7. **Staging only:** use the paired H5.12 Hostinger Cloud Root ZIP in a disposable Hostinger app after a stopped-server backup of DB + WAL/SHM + media + storage identity + credentials. Keep LENSDROP_INITIALIZE_STORAGE=0. Do not deploy this checkpoint to a live event.

## Tested boundaries
- Source snapshots and no-hardware tests are necessary but not equivalent to successful Android SDK compile/lint, Windows native publish or printer/phone tests.
- This archive is a full **source tree** suitable for GitHub Actions, not a compiled installer or complete production release.
- H5.11 historical report and original release metadata remain historical; this is an additive QR fix checkpoint without native versionCode / Cloud schema bumps.

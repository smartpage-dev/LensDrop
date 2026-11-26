# Current Cloud source — H5.12.2 Ops UI + Admin Lite staging

Cloud runtime version `0.2.0-alpha.12-hostinger.5.12.1`, SQLite schema11 unchanged. The H5.12.2 Ops update provides admin cards, authenticated previews and QR pairing web UI, and `/admin-lite` backed by existing Admin Key authorisation. See `RELEASE_STATUS.json`. Preserve existing event storage, environment secrets and credentials. Build/package is not a live deployment.

Historical documentation follows.

---

# CURRENT RELEASE: H5.11 engineering candidate (schema 10)

Read `START_H5_11.md` and `RELEASE_STATUS.json` first. Older instructions below are historical and must not override H5.11 upgrade requirements. Do not deploy without a stopped-data backup and verified client builds.

---

# H5 update - read the current guide first

This component is part of H5. Read the root docs/h5/LensDrop_H5_Setup_Guide.html and RELEASE_STATUS.json before the retained earlier instructions below. Cloud schema5; Android native compilation and Windows native compilation remain unverified. H5 supersedes earlier statements that no print source or staff-QR source exists. No compiled APK/EXE is bundled.

---

# LensDrop Cloud H4 / New Photos and Personal QR

Version `0.2.0-alpha.5-hostinger.4`, SQLite schema **4**. Open `START_HERE.html` or `docs/h4/LensDrop_H4_WatchFolder_and_PersonalQR_Guide.html`.

This is a single-instance engineering alpha. It retains the Hostinger H3.1 startup fix, public gallery and slideshow, and adds independent personal group/single links, release/revocation, stable delivery binding, guest pages and candidate/skip-aware operation counts. Personal delivery does not automatically publish to the event gallery or slideshow.

For an existing deployment: pause/stop, back up the full persistent data directory, keep all existing environment settings and `LENSDROP_INITIALIZE_STORAGE=0`, then deploy this root ZIP. Migration to schema4 is automatic; rollback needs the matching preupgrade backup and old code. Do not point data at public_html/hbuilds or a new empty directory. No new secret is supplied here.

Node22.x >=22.13, entry server.js, build `npm run build`, package manager npm, root/output `.`. Existing dependency sharp0.34.1 unchanged. One process, private persistent local disk. No live Hostinger H4 deployment or clean internet dependency install is certified.

`npm test` runs the 92 cloud cases. `tools/personal-qr-rehearsal.cjs` is the new explicit-photo desktop rehearsal. See the guide for token-file, immutable state and approval instructions. It is not a folder watcher. Android source is in the complete outer bundle, not this cloud ZIP; NO APK is supplied anywhere.

Private bearer QR links are secrets. Keep them out of public logs, support screenshots and analytics. Sample config files are reference only; they are not automatically loaded. Retained older docs are historical; H4 is authoritative for changed behavior.

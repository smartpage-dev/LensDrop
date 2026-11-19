# LensDrop Cloud Core - H2 Enhanced

Version **0.2.0-alpha.3-hostinger.2**, 14 November 2026. Engineering alpha; no live Hostinger or camera/phone certification.

Start with `docs/enhanced/LensDrop_H2_Enhanced_User_and_Upgrade_Guide.html` (PDF/DOCX also included). H2 instructions take precedence over the retained H1 Hostinger guide, then the original full guide. The release report explains exact feature and test boundaries.

Upload only `LensDrop_Hostinger_Cloud_v0.2-alpha_H2.zip` to a Node.js Web App. Its root contains package.json and server.js. Framework Other; Node 22.x >=22.13; root/output `.`; build `npm run build`; entry `server.js`; start `npm start`. Tests used Node 22.16.0. Node 24 is declared but not tested here.

## Mandatory upgrade precautions

Pause clients and stop the single process. Back up the ENTIRE private persistent data directory consistently, plus saved configuration. Preserve DATA_DIR, STORAGE_ID, ADMIN_KEY and device credentials. Keep LENSDROP_INITIALIZE_STORAGE=0 for upgrades. H2 performs additive schema-2 migration. Never point H1 at an H2 database; rollback requires matching H1 code and the entire preupgrade backup.

Obtain provider confirmation of private persistent local storage outside app/public_html/hbuilds/nodejs, valid SQLite WAL/file locking and exactly one active writer. Live restart/redeployment tests remain required. Do not invent a storage path, delete locks or reinitialize old events to hide an error. First-time empty-directory setup remains documented in the H1 guide and sample.hostinger.env.

## H2 additions

Three-stage operational counts, isolated never-public preflight uploads, per-device summary, reconciliation/diagnostic exports, paginated admin photos, core bilingual web labels and non-disruptive guest new-photo notices. Tests consume quota and cannot be promoted. Counts do not prove camera-card completeness or replicated backup. The public gallery remains public-by-link.

## Build / tests

`npm run build` checks syntax, built-in SQLite and native JPEG processing without opening event storage. `npm test` runs 35 cloud tests. `docs/validation-h2` records the run and the separate 23 desktop tests. Native/mobile test boundaries are explicit in RELEASE_STATUS.json.

The npm registry was unavailable (EAI_AGAIN). No clean install, lockfile or dependency audit was completed. Tests used the already available pinned sharp 0.34.1. Install dependencies on the target and pass build; do not use npm ci without a lockfile.

`npm run start:local` is an unguarded local evaluation entry, not a way to bypass hosting storage requirements. See the original guide for local loopback evaluation. JPEG-only, <=64 MiB per upload, one server process/local filesystem, no independent background-worker service or object-store replacement.

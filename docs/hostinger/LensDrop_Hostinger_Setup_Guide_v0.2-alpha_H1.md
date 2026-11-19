# LensDrop - Hostinger setup guide

Cloud Core 0.2.0-alpha.2-hostinger.1 | 11 November 2026

## 01 / Start here

Hostinger deployment candidate | 11 November 2026

LensDrop Cloud Core can be evaluated on Hostinger through its Node.js Web App service, subject to the storage and single-process checks in this guide. Business Web Hosting is an eligible plan. Ordinary static/PHP file upload is not the correct deployment method. [H1, H2]

> This package is prepared for Hostinger testing, not certified on your account. No live Hostinger deployment, clean dependency installation, Android APK or iOS application is claimed.

### What to open

| Item | Purpose |
| --- | --- |
| START_HERE.html | Open this in the extracted complete bundle. It links to programs, both guides and the upload archive. |
| deploy/ upload ZIP | Upload this inner ZIP to Hostinger. The complete outer bundle is not the deployment ZIP. |
| cloud/ | Updated Cloud Core: server.js, package.json, src, public, tools, tests and documentation. |
| clients/ | Desktop Python uploader and original Android source. There is no mobile installer. |
| cloud/docs/user-guide/ | The original 26-page full user guide, kept as baseline documentation. |
| cloud/docs/hostinger/ | This supplement, release notes and current validation logs. |

### Read this supplement before the older guide

The Cloud Core version is 0.2.0-alpha.2-hostinger.1. Unlike the earlier documentation-only repack, this release changes startup safety, deployment diagnostics and packaging. Its upload protocol remains compatible with the supplied clients. The Android and desktop application source files are unchanged.

For LOCAL testing, use npm run start:local wherever the older guide says npm start. For the Hostinger deployment use npm start, which now runs the guarded root entry point. This distinction is deliberate.

## 02 / Confirm the storage first

Do this before uploading any event photographs

The current backend keeps SQLite, interrupted-upload chunks, originals and delivery images together on local disk. A new deployment must continue using exactly the same private data directory. This is an application requirement, not something that enabling Node.js alone guarantees.

Hostinger documents that deployment-managed hbuilds and public_html folders are overwritten during redeployment. Never place LENSDROP_DATA_DIR in either location, the application folder, or the older nodejs tree. [H3]

### Ask Hostinger support to confirm these requirements

> I am deploying one Node.js photo-upload service on Business Web Hosting. It needs a private writable LOCAL directory outside public_html, hbuilds and the application tree that persists across restart, redeploy and code rollback. Please confirm its exact absolute path, SQLite WAL/file locking and fsync support, disk quota and backup coverage, and whether old and new Node processes overlap during deployments. The app permits only one writer. Can this configuration be supported on my account?

Use the actual path returned for your account. Do not copy an invented /home/u123456 path or assume that a sibling folder is writable. The startup probe checks access and basic file operations; it cannot prove host-level durability or filesystem locking semantics.

### Choose the correct hosting path

| Hostinger option | Decision |
| --- | --- |
| Business / managed Cloud Node.js | Conditional evaluation path: persistent private storage, single-writer behavior, native dependency installation and live tests must all pass. |
| Hostinger VPS | Alternative when managed hosting cannot meet these requirements. You control the service and persistent local disk, but must configure TLS, updates, monitoring and backups. |
| Static / PHP-only site | Not suitable for this backend. A working HTML gallery alone is not a working upload API. |

A future managed database plus private object storage would remove this local-disk dependency. That architecture is not implemented here; setting MySQL or S3 variables will not convert this alpha. A VPS gives more control, not automatic production approval. [H1, H4]

## 03 / Create the Node.js Web App

Use the dedicated upload archive, not the whole bundle

1. Extract the complete LensDrop bundle on your computer. Open START_HERE.html, then read this guide and confirm the storage path from section 02.
2. In hPanel, open Websites and create a new Web App / Node.js application. Keep it separate from your existing unrelated website. Choose file upload. Hostinger also supports Git deployment, but this package uses the ZIP route. [H2]
3. Upload deploy/LensDrop_Hostinger_Cloud_v0.2-alpha_H1.zip. In this inner archive, server.js and package.json are at the top level; there is no extra cloud folder.
4. Enter the settings below. Screen labels can vary; configure a running backend rather than a static export. Do not select Vite, WordPress or a PHP-only site.

| Setting | Value for this package |
| --- | --- |
| Framework | Other |
| Node.js | 22.x, at least 22.13. Local tests used 22.16.0. Node 24.x is declared compatible but was not executed here. |
| Root directory | . |
| Output directory | . |
| Entry file | server.js |
| Build command | npm run build |
| Start command | npm start |
| Package manager | npm |
| Application instances | Exactly one active writer for this data directory |

Hostinger supports selecting Node 22.x and 24.x. This package requires built-in node:sqlite and the sharp native image dependency. The build command checks SQLite, JPEG encode/decode and required assets; it does not start the server or open event storage. [H5, H7, H8]

> A fresh registry installation could not be completed in this environment because npm DNS resolution failed. No package-lock.json or successful dependency audit is claimed. Hostinger must successfully install the pinned dependency and pass the build check. Do not use npm ci without a valid lockfile.

## 04 / Enter the environment values

Secrets belong in hPanel, never in public files

Open cloud/tools/Hostinger_Setup_Helper.html locally. Enter the confirmed private data path, generate new installation values and save them in a password manager. The helper generates values in your browser, does not transmit them and does not save them for you.

Alternatively, run npm run generate:secrets from cloud on a computer with Node installed. This prints an administrator key and storage ID. Generate them once; retain the same values for normal restarts and upgrades.

```text
NODE_ENV=production
HOST=0.0.0.0
LENSDROP_ADMIN_KEY=<your private random key>
LENSDROP_DATA_DIR=<confirmed absolute private path>
LENSDROP_STORAGE_ID=<your saved 32-character hex ID>
LENSDROP_INITIALIZE_STORAGE=1
LENSDROP_EVENT_QUOTA_BYTES=10737418240
LENSDROP_FREE_RESERVE_BYTES=536870912
```

Replace every placeholder. Paste each variable name and value separately into hPanel Environment Variables. The program does not automatically read .env files. Leave PORT unset unless the hosting platform explicitly supplies or requires it; the entry point respects the assigned port.

| Value | Meaning |
| --- | --- |
| Administrator key | Controls all events. Never give it to a photographer or paste it into a public gallery link. |
| Storage ID | Identifies this installation's data directory. It is not a password and must match the saved on-disk marker. |
| INITIALIZE_STORAGE=1 | Permits first initialization of a new EMPTY directory. Never use this to hide missing data. |
| INITIALIZE_STORAGE=0 | Normal operating mode. Set this immediately after first startup; missing storage or database then stops startup. |
| Quota / free reserve | 10 GiB event reservation quota and 512 MiB server free-space reserve. These are alpha defaults, not a promise of event capacity. |

> A directory containing existing data but no LensDrop marker is deliberately rejected. Do not add a marker by hand. Keep the old service stopped, back up the entire directory and plan a checked migration instead of treating existing events as a new installation.

## 05 / Verify the first startup

Health, configuration and access checks

1. Deploy the application. Check build and runtime logs. Successful startup emits lensdrop_ready with the release version. startup_rejected means a safety check stopped the server; use section 08 instead of deleting data.
2. Open /health on the assigned HTTPS origin. Expect ok: true and version 0.2.0-alpha.2-hostinger.1. A health response establishes reachability, not data persistence or event readiness.
3. Open /admin on the same origin. Enter your administrator key and select Check deployment. Save the storage_id, data_dir, counts and boot_id privately for comparison.
4. Create a Review-before-publishing test event. Create one device credential for the desktop pilot. Save the device token separately from the administrator key.
5. Complete the three-JPEG test in section 06. Confirm that Review photos remain absent from the guest gallery, and publishing/hiding them changes guest access correctly.
6. Change LENSDROP_INITIALIZE_STORAGE to 0 in hPanel. Apply the change and follow the restart/redeploy proof in section 07. Do not leave initialization enabled for normal use.

### Check the boundary

Use one canonical HTTPS hostname for all uploaders and visitors. Hostinger notes that domain.tld and www.domain.tld can run separate processes. Confirm aliases redirect before starting another backend. The existing server lock intentionally rejects a second writer; never remove it while a process might be alive. [H4]

Verify that requests to /sample.hostinger.env, /package.json, /src/store.cjs and /.lensdrop-storage.json are not exposed by the hosting layer. The application rejects those routes, but host-level static routing must be checked too. Use no sensitive images until these checks pass.

> The deployment panel always reports persistence_verified_by_host: false and backup_verified: false. These are deliberate reminders: a local write probe cannot certify redeployment survival or backups. Record your live test evidence separately rather than changing these fields to make the panel look green.

## 06 / Run the photographer pilot

First prove the desktop-to-cloud path

1. On Windows, open clients/desktop/START_WINDOWS.cmd after installing a supported Python version. On macOS, use the terminal command below. The launcher is not an installer.
2. Use the actual HTTPS server origin, without /admin or /g/... at the end. Select a folder containing only three distinct test JPEGs. Choose a separate queue-state folder outside the watched folder.
3. When prompted, enter the event-scoped DEVICE token, not the administrator key. The agent considers existing eligible JPEGs in that watch folder, so do not point it at your whole photo library.
4. Wait for three verified uploads. Check the event and publishing controls in the console. Repeat with twenty distinct JPEGs, then test interruption and resume using the same token, origin and queue-state directory.

```text
cd clients/desktop
python3 lensdrop_agent.py \
  --server https://YOUR-LENSDROP-HOST \
  --folder "$HOME/LensDrop/incoming-test" \
  --state "$HOME/LensDrop/queue-test"
```

Create the incoming-test folder before running the command, and replace the host placeholder. On Windows the equivalent Python launcher is py -3. The full baseline guide contains more detailed Windows/macOS instructions and token-recovery procedures.

### What each photographer eventually carries

The intended kit is a camera plus smartphone, with its own device credential and durable upload queue. This package contains Android source only. It does not yet provide an installable Android app, iOS app, or validated cross-brand camera connection.

For Panasonic, first prove that automatically received photographs become visible in accessible shared phone storage without manual export. A picture shown only inside an official app is not sufficient. The original full guide explains that test and the native-build prerequisites.

> Keep camera-card and phone originals. LensDrop can retry files it has received; a folder watcher cannot recover an image that never reached accessible phone storage. Never clear a queue, uninstall the app or format a camera card as an upload-recovery shortcut.

## 07 / Prove persistence and safe updates

Restart and redeploy are separate tests

### The required persistence test

1. With initialization now set to 0, record the test event, device identity, photo count, original-file checksum and deployment storage_id. Save the guest URL.
2. Restart the Node application without replacing code. Verify the same event, credentials, gallery, files and storage_id remain. The boot_id should change.
3. Redeploy the SAME inner ZIP with the SAME storage path, storage ID and administrator key. Hostinger exposes redeploy settings for environment and startup changes. [H6]
4. After redeployment, repeat the checks. Interrupt a test upload before completion, restart/redeploy, then verify it resumes to one correct result rather than restarting as a new event or producing duplicates.
5. Check that only one backend owns the directory. Overlapping old/new processes may cause startup rejection; the host must support the one-writer lifecycle. Do not work around it by deleting server.lock.

### Back up before changing application files

Pause uploaders and stop the backend cleanly. Confirm the writer has exited, then copy the ENTIRE data directory to a separate backup location, including its hidden storage marker, SQLite files, originals, delivery images, thumbnails and unfinished chunks. Retain configuration secrets separately. An uploaded program ZIP is not a backup.

Do not copy only lensdrop.sqlite while the server is running. The alpha uses WAL and local file commits; a partial or inconsistent copy can omit work. Verify the host backup scope and perform a restore into a separate test environment with only one writer.

### When a restart shows an empty gallery or missing files

Stop uploading. Compare the saved storage path and ID before making changes. Restore the correct complete directory or backup; do not enable initialization to create a substitute empty database. Preserve the client queue and source photographs so reconciliation remains possible.

> Do not upgrade during an event. This release uses the same database schema as the earlier core, but no automatic migration of unmarked old data is supplied. Do not point the older entry point at a live guarded directory as an improvised rollback.

## 08 / Troubleshooting

Treat safety failures as evidence, not obstacles to bypass

| Symptom / log code | Action |
| --- | --- |
| 404 or static files only | Confirm a Node.js Web App, framework Other, root entry server.js and the inner ZIP. Uploading only public/ cannot run the API. |
| 503 at startup | Read runtime logs first. Check Node version, native sharp installation, environment values, one-writer lock and account resource usage. Do not guess by deleting data. |
| absolute_data_dir_required / deployment_tree_forbidden | Use the provider-confirmed absolute private persistent path, outside the application and deployment trees. |
| storage_not_initialized | For a genuinely new empty installation, use initialization=1 once. For an existing installation, find the correct directory or restore it. |
| storage_identity_mismatch | Restore the saved storage ID/path pairing. Do not rewrite the marker or generate a new ID. |
| unmarked_nonempty_storage / database_missing | Stop. Preserve all files and investigate or restore a complete backup. This is not a signal to clear the folder. |
| Data directory locked / already using | Another process may still own the database. Arrange stop-before-start with the host. Never delete the lock while any writer may be alive. |
| 401 on uploader | Use the correct device token for that event/device. Keep the existing queue; token rotation/retry steps are in the full guide. |
| 429 / 507 | Backpressure or low storage. Pause intake if necessary; inspect quota, physical free space and backup/retention policy. Do not erase pending work. |
| Uploads work, guests see nothing | Review-first is intentional. Publish a selected photo to Live and use the correct guest link. Hidden and Review assets should be denied. |
| App is healthy but redeploy loses data | Stop the pilot. The storage requirement was not met. Use supported persistent hosting or redesign the storage backend. |

Hostinger managed Node requests are limited to 120 seconds. This core uses small resumable chunks and a 90-second request-body timeout, but JPEG finalization must also finish within the platform limit under actual load. A successful small-file test is not a large-event throughput result. [H4]

## 09 / What changed and what passed

Executed evidence versus remaining release gates

| This release adds | Scope |
| --- | --- |
| Root CommonJS server.js | Starts both directly and when loaded by a managed-host require() wrapper; honors PORT and a configurable bind address. |
| Guarded storage initialization | Rejects obvious unsafe paths, wrong identities, missing database and accidental adoption of nonempty folders. |
| Build preflight | Checks native JPEG operations, SQLite and web assets without opening event storage. |
| Admin deployment check | Authenticated runtime details for comparing storage and startup across test deployments. |
| Combined packaging | Application, clients, original full guide, new Hostinger supplement, helper and validation logs. |

Local Linux validation passed 22 cloud/prototype/hosting tests and 11 desktop tests: 33 in total. The new tests include loading the root entry with require(), rejecting unsafe storage, protecting diagnostics and continuing a partially uploaded JPEG after replacing the code directory. They use the locally available sharp 0.34.1 package.

The npm registry installation attempt failed with EAI_AGAIN (DNS resolution). No clean install, transitive dependency audit or Node 24 execution was completed. Live Hostinger deployment, host backup restore, browser end-to-end interaction, Android compilation, phone lifecycle behavior and camera field tests remain unverified.

### Event-use limits remain

JPEG only, maximum 64 MiB per uploaded file. One Node process, one cloud finalizer at a time and local SQLite/files. Receipts confirm the local instance commit, not independent replication. Guest links are unlisted public-by-link galleries, not confidential authenticated albums. The admin console lacks production account roles and comprehensive abuse protection.

Managed Hostinger does not provide always-running independent workers or inbound WebSockets. The current core uses request-driven work and browser polling; future heavy AI/video workers need a different deployment design. Moving to a larger managed Cloud plan alone does not establish those features. [H4]

> Use non-sensitive pilot images first. Do not call this an event-production release until sustained multi-photographer uploads, interruptions, counts/checksums, phone resources, host storage persistence and backup restoration have all been qualified.

## 10 / Reference and handoff record

Keep this with the deployment and test evidence

### Official references checked on 11 November 2026

[H1] Hostinger - Node.js hosting options
https://www.hostinger.com/support/node-js-hosting-options-at-hostinger/

[H2] Hostinger - migrate a Node.js application (ZIP method)
https://www.hostinger.com/support/how-to-migrate-a-node-js-application-to-hostinger/

[H3] Hostinger - add a Node.js app; deployment folder lifecycle
https://www.hostinger.com/support/how-to-deploy-a-nodejs-website-in-hostinger/

[H4] Hostinger - managed Node.js limits
https://www.hostinger.com/support/hostinger-node-js-limits-for-web-and-cloud-plans/

[H5] Hostinger - selecting the Node.js version
https://www.hostinger.com/support/how-to-select-the-node-js-version-for-your-application/

[H6] Hostinger - redeploy a Node.js application
https://www.hostinger.com/support/how-to-redeploy-a-node-js-application/

[H7] sharp - installation and optional native dependencies
https://sharp.pixelplumbing.com/install/

[H8] Node.js - built-in SQLite API
https://nodejs.org/api/sqlite.html

### Your private validation record

Record the actual HTTPS hostname; provider-confirmed data path; Node version; build timestamp; saved storage ID; restart and redeploy results; test-photo totals/checksums; backup location and restore result; and any camera/app/phone combination tested. No participant or operational responsibility has been assigned by this guide.

Current test artifacts are in docs/hostinger/validation/. The older docs/user-guide/ material is retained for detailed user operation and native-client development. For changed startup commands, deployment settings and current verification status, this supplement takes precedence.


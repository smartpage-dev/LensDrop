# LensDrop H3.1 - Hostinger startup correction

Version: **0.2.0-alpha.4-hostinger.3.1**  
Date: **15 November 2026**  
Classification: engineering alpha; targeted startup hotfix, not live Hostinger certification.

## Diagnosis grounded in the supplied log and H3 source

The supplied log first reports `startup_rejected / admin_key_required`. H3 rejects an absent, short or recognized example `LENSDROP_ADMIN_KEY` before opening event storage. The log cannot distinguish which of those conditions applied in the user's hPanel.

A separate error-handling defect was confirmed in the delivered H3 `server.js`: the catch block sets exitCode but continues to `module.exports = app`, where app is undefined after configuration rejection. A synchronous host loader can then inspect `app.listen`, generating the secondary TypeError. This exact two-error sequence was reproduced locally from the original H3 archive.

Upstream LiteSpeed's published loader requires the startup file and, if no listener has been registered, inspects the returned object's listen method. Its HTTP address override may return a socket string rather than TCP AddressInfo. The exact deployed Hostinger loader binary/version was not inspected.

## Changes

- Required-module loading is inside the startup diagnostic boundary.
- Successful startup exports the actual HTTP server, not an application wrapper.
- Failed CommonJS startup throws back to its caller. Direct `node server.js` exits nonzero with the structured root-cause diagnostic. No successful undefined export is returned to the loader.
- Readiness logging handles TCP, socket-string, null and undefined address values without revealing a socket path.
- `admin_key_required` now names the exact runtime variable and explains applying changes and restarting.
- New `npm run generate:admin-key` generates only a 64-hex administrator key, without generating a storage ID. The offline helper has the same administrator-only option.
- The legacy new-install secret generator remains available, explicitly marked for new empty installations.

There is no default password, no unauthenticated fallback admin, no bypass of private-storage checks, no automatic storage-path selection, and no destructive reset or forced migration. You MUST still supply a valid private key in hPanel; installing this ZIP alone does not repair missing environment values.

## Required user action

1. Restore the saved `LENSDROP_ADMIN_KEY` when available. If no valid key was ever set, generate a private one on your computer.
2. Website Dashboard > Environment variables: set `LENSDROP_ADMIN_KEY` to the key value only, without surrounding quotes. Do not put the variable name in the value field.
3. Apply changes and restart the Node app. If also deploying this hotfix, upload the cloud ZIP through the Node.js Web App redeploy screen, retain existing environment values, and restart after applying them.
4. Check the NEW runtime log, then `/health` and `/admin`. On a successful hotfix start `/health` reports `ok: true` and version `0.2.0-alpha.4-hostinger.3.1`.
5. If the next log identifies storage/path/identity settings, resolve that specific issue without regenerating a storage ID or erasing data.

Changing only the key can clear the currently reported configuration gate even on H3. H3.1 corrects the misleading secondary loader failure and adds recovery tooling; it is not a substitute for completing setup.

## Data and upgrade boundary

H3 to H3.1 keeps database schema 3. The uploader, Android sources, slideshow, guest-gallery and database migration logic are unchanged. Existing installations retain `LENSDROP_DATA_DIR`, `LENSDROP_STORAGE_ID` and `LENSDROP_INITIALIZE_STORAGE=0`. Restore the saved administrator key rather than unnecessarily rotating it; a deliberate rotation requires administrators to reconnect, but device tokens are stored separately.

Stop the single application process, pause uploads and back up the entire private data directory and configuration before changing releases. Do not delete a lock to disguise an active second process. Upgrades from H2 or earlier still carry the original H3 schema migration requirements. Never initialize a replacement empty gallery over missing production data.

## Executed validation

- Old H3 two-error reproduction: root admin-key rejection followed by undefined.listen TypeError.
- 17 new startup/recovery tests: invalid secrets, direct invocation, host-loader rejection, private-storage guards, missing database initialization guard, invalid port, three socket address shapes, one bind only, actual HTTP /health and /admin over a local Unix socket, unauthenticated API denial, direct TCP, clean shutdown and secret-generator/helper behavior.
- Full cloud suite: **71 tests passed**, including the original 54 tests.
- Desktop suite against this local backend: **23 tests passed**.
- Offline helper checked in Chromium with set_content: key generation, storage isolation, copy/fallback feedback, mobile width, no page errors or network requests. This is helper-only, not a live-site browser test.
- Build: successful with Node **22.16.0** and the preinstalled exact **sharp 0.34.1**.

The LiteSpeed behavior in these automated tests is an independently written compatibility adapter. The tests make real local HTTP requests; they are not tests on the user's Hostinger account. No physical camera, Android build, projector or long event soak was performed. Previous H3 UI/QR evidence remains archived and was not rerun as a current UI certification. No fresh npm dependency install or dependency audit is claimed in this hotfix.

Raw test output is in `docs/validation-h31/`. The final cloud ZIP is extracted and its build/cloud suite run again before delivery. Package manifests identify changed files and checksums; no live secrets, event data or node_modules are included.

## Primary references checked on 15 November 2026

- Hostinger: environment-variable editing, runtime restart, quote handling and private backup of values: https://www.hostinger.com/support/how-to-edit-or-add-environment-variables-after-deployment/
- Hostinger: ZIP redeployment and settings review: https://www.hostinger.com/support/how-to-redeploy-a-node-js-application/
- LiteSpeed upstream CommonJS loader (reference behavior, not the exact installed Hostinger version): https://raw.githubusercontent.com/litespeedtech/openlitespeed/master/dist/fcgi-bin/lsnode.js

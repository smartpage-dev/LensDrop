# Release notes and validation - Hostinger H1

Cloud Core 0.2.0-alpha.2-hostinger.1 | 11 November 2026

## Changes from 0.2.0-alpha.1

- Added a CommonJS root server.js that starts when required by a managed host, honors the assigned PORT and binds to HOST (0.0.0.0 by default).
- Added external-storage path checks, a saved installation storage ID, explicit first initialization, write/fsync/rename/readback probing and fail-closed normal startup when storage or the database is missing. These checks are not a filesystem certification.
- Preserved one-writer lock behavior, upload protocol, checksums and database schema.
- Added authenticated /v1/admin/deployment and a Check deployment button. Public diagnostics do not include private path/identity details.
- Added a storage-independent build preflight, environment/configuration references and an offline secret-generation helper.
- Added 10 hosting checks, including actual interrupted-upload resume after replacement of the running code directory.
- Bundled the original full user guide and a 10-page Hostinger supplement in PDF, DOCX, HTML and Markdown.

## Executed checks

- 22 cloud/prototype/hosting tests: PASS.
- 11 desktop client tests including the real cloud API: PASS.
- Build preflight (SQLite and native JPEG operations): PASS with available sharp 0.34.1.
- Startup and browser JavaScript syntax: PASS.
- CommonJS require()-style entry, private diagnostics and test source-route protection: PASS locally.
- Root-layout ZIP, byte manifests and retained client-source integrity: see package validation report.

The test for release-directory replacement uses one local persistent data directory and stops the old process before starting the replacement. It is NOT a live Hostinger deployment test. Filesystem probes do not prove backup durability or safe locking on remote/virtualized filesystems.

## Installation limitation

A registry dependency-resolution attempt (`npm install --package-lock-only --ignore-scripts ...`) failed with EAI_AGAIN for registry.npmjs.org. No fresh node_modules installation, lockfile or dependency audit is claimed. Runtime tests used the already-available native dependency. Hostinger needs a successful clean install and build check. See validation/dependency-install-attempt.txt.

## Unverified release gates

Live provider deployment, persistent disk/SQLite support, alias/rollout process behavior, TLS and host static routing, host backup restore, browser E2E, native Android build/installation, iOS, physical camera transfer and sustained event resource behavior.

The code does not implement MySQL, PostgreSQL or object-storage backends. Do not change the storage path to an upload directory under public_html. Do not remove a live server.lock, rewrite the storage marker or clear pending client work to get past an error.

## Documentation precedence

The new Hostinger supplement supersedes older provider/startup directions. For local-only baseline testing, use npm run start:local in place of the older npm start. Client application source is unchanged. The Android project is uncompiled source, not an APK. The desktop scripts are launchers, not installers.

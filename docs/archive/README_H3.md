# LensDrop Cloud Core - H3 Slideshow

**0.2.0-alpha.4-hostinger.3 | 14 November 2026 | Engineering alpha**

Open `START_HERE.html`. Read `docs/slideshow/LensDrop_H3_Admin_and_Slideshow_Guide.html` (PDF included) and `docs/slideshow/LensDrop_H3_Slideshow_Release_Report.md` before upgrading. Latest H3 instructions take precedence over retained H2, H1 and original manuals.

Admin: `/admin` or `/`. Independent slideshow: generated per event in Admin, `/s/<token>`. Guest gallery: `/g/<slug>`. H3 adds shared event logo/name, three fullscreen styles, local QR PNG/SVG generation, revocable projector links and authenticated image preview. Only published, non-test images are projected. Projection is disabled by default.

## Deployment and data safety

Upload only the cloud ZIP, not the complete outer bundle, through the Node.js Web App route. Root `.`; output `.`; entry `server.js`; build `npm run build`; start `npm start`. Node22.x >=22.13; executed version22.16.0. Node24 is declared but not tested here.

Pause clients, stop the only server instance, and back up the ENTIRE private data directory plus secrets before upgrade. Keep `LENSDROP_DATA_DIR`, `LENSDROP_STORAGE_ID`, `LENSDROP_ADMIN_KEY`, device credentials and `LENSDROP_INITIALIZE_STORAGE=0` for an existing installation. Schema upgrades to3. Rollback requires matching H2 code AND the complete pre-H3 backup, with separate handling of newer files.

Private persistent local disk OUTSIDE app/public_html/hbuilds and one active writer remain mandatory. Obtain provider confirmation of storage, SQLite locking, backup and deployment behavior; this has not been validated on a live Hostinger account. Do not invent a storage path or remove a storage lock to hide an error. New empty-site initialization is documented in the retained Hostinger guide and `sample.hostinger.env`.

## Build and verification

`npm run build` checks source syntax, in-memory SQLite, native JPEG and bundled QR; it does not open production event storage. `npm test` runs54 tests. Evidence is under `docs/validation-h3/`;23 desktop tests and18 instrumented UI/QR checks also passed. Browser requests were bridged to the real local backend because native browser navigation was blocked. This is not native CSP/HTTPS or live Hostinger certification.

Clean dependency installation failed due to npm-registry DNS. Tests used the preinstalled exact `sharp@0.34.1`. No lockfile/fresh audit is included; do not run npm ci without a lockfile. Target deployment must install dependencies and pass build. No production credentials, event data or node_modules are packaged.

QR encoder is locally vendored with license/provenance under `vendor/QRCode`; regenerate the checked-in browser bundle with `node scripts/build-qr.cjs`. No runtime QR network service or new npm dependency is introduced.

## Boundaries

Camera uploads remain JPEG-only <=64MiB, single-process/local-disk core. Projection uses delivery JPEGs <=2560px long edge and the latest50/100/200/500 published photos, not unlimited album preload. Automatic reconnection is implemented; no fresh verified feed for30seconds clears projection content. Visibility changes apply on the next check. Public-by-link, no PIN/expiry, no DRM. No SDK integration, Android APK, iOS app or independently replicated backup is added.

`npm run start:local` is a local-evaluation entry, not a way around hosting requirements. Developer UI harnesses in `tests/ui` are TEST ONLY. Always launch production with `server.js` / `npm start`.

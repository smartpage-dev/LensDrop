# LensDrop current staged source — H5.12.2 Ops UI + Admin Lite

Current version summary: see `CURRENT_RELEASE.json`. Android photographer app remains code19 / QueueDb10; Cloud remains H5.12.1 / schema11; Windows native print station unchanged.

This development update improves Staff Desk preview/print errors, in-card Personal QR, Admin Photographs thumbnails, and Pairing history; it also provides separate Android Admin Lite **source** and `LensDrop Admin Lite APK` GitHub Actions workflow. No compiled Admin Lite APK or production deployment is bundled.

To test: run the Cloud and Station suites and Android source check on a fresh branch, deploy the separate full Hostinger staging root **only after backup**, and run the Admin Lite workflow on the same commit. Preserve existing credentials, data directories and Android signing identity. The Admin Lite web page uses the existing full Admin Key; this is **not** a least-privilege role.

The instructions below are retained as historical reference; they do not describe the current release.

---

# Current build repair: LensDrop H5.6.1 / Android code12

Start with **START_BUILD_REPAIR_H5_6_1.html**. The missing Intent import is corrected and the complete Android source/workflow has a coherence gate. Use CURRENT_RELEASE.json for current status. No APK or live deployment is included. Preserve app data and signing identity.

The following retained guide is historical; newer versioned guides take precedence.

# Current Android update: LensDrop H5.6

Open **START_ANDROID_H5_6.html** for the current Android UI/scanner guide and **docs/h5_6/RELEASE_REPORT.md** for evidence and limits. Android is 0.2.0-alpha.6-h5.6 / code11; cloud and Windows remain H5.5, unchanged. No APK or native build validation is included. Existing signing identity, data and queues must be preserved. Run the Android workflow on a NEW updated commit. The native lint gate remains mandatory.

H56_FILE_MANIFEST.json is the current source inventory. Earlier manifests/guides below are historical. The embedded Hostinger ZIPs are still the unchanged H5.5 deployment, not a new H5.6 server. Only the Android build is required for a site/station already on H5.5.

---

# LensDrop H5.5 - start here

Open START_ARCHIVE_STAFFPRINT_H5_5.html. This is the latest source snapshot, not a compiled release.

Includes phone archive-all independent of guest selection, two agreed print routes, and an authenticated staff photo/ticket/order/collection desk. No station upload hub, guest kiosk or automatic print-on-scan.

Build Android and Windows on GitHub with the existing signing identity. After backup, upgrade cloud -> Windows -> Android. Cloud schema7, Android photo6/print3. Existing data identities and initialization0 remain. Historical guides do not override the H5.5 guide. See docs/h5_5/RELEASE_REPORT.md for precise tests and unverified limits.

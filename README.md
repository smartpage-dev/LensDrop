# Current Android update: LensDrop H5.6

Open **START_ANDROID_H5_6.html** for the current Android UI/scanner guide and **docs/h5_6/RELEASE_REPORT.md** for evidence and limits. Android is 0.2.0-alpha.6-h5.6 / code11; cloud and Windows remain H5.5, unchanged. No APK or native build validation is included. Existing signing identity, data and queues must be preserved. Run the Android workflow on a NEW updated commit. The native lint gate remains mandatory.

H56_FILE_MANIFEST.json is the current source inventory. Earlier manifests/guides below are historical. The embedded Hostinger ZIPs are still the unchanged H5.5 deployment, not a new H5.6 server. Only the Android build is required for a site/station already on H5.5.

---

# LensDrop H5.5 - start here

Open START_ARCHIVE_STAFFPRINT_H5_5.html. This is the latest source snapshot, not a compiled release.

Includes phone archive-all independent of guest selection, two agreed print routes, and an authenticated staff photo/ticket/order/collection desk. No station upload hub, guest kiosk or automatic print-on-scan.

Build Android and Windows on GitHub with the existing signing identity. After backup, upgrade cloud -> Windows -> Android. Cloud schema7, Android photo6/print3. Existing data identities and initialization0 remain. Historical guides do not override the H5.5 guide. See docs/h5_5/RELEASE_REPORT.md for precise tests and unverified limits.

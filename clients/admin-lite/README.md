LensDrop Admin Lite · 0.1.0-alpha.1
An independent Android APK source project, package `app.lensdrop.adminlite`, not a modification of the photographer app (`app.lensdrop.transfer`).
Dedicated cloud route `/admin-lite` has a small pairing-only management UI. The Android APK hosts this route in an HTTPS WebView.
Admin enters the existing Cloud Admin Key per session. Neither the APK nor Cloud web page writes that credential to local storage.
Administrator can generate a short-lived staff QR for a new photographer, re-pair an existing device without losing history, and approve its 6-digit code, using existing server API permissions.
No printing, gallery publication, media removal or offline administrative permissions.
Only HTTPS same-origin navigation; no file, camera or microphone permission. Screenshot protection enabled on the Android wrapper.
Cloud page `/admin-lite` must be deployed before the APK can work.
Build
Run the manual GitHub Action `LensDrop Admin Lite APK` on a test branch, or with JDK 17, Android SDK 35, Gradle 8.11.1: `cd clients/admin-lite && gradle :app:assembleDebug`.
Debug APK uses a normal debug signing key, not a production signing identity. Preserve its own application ID for future updates. Never embed `LENSDROP_ADMIN_KEY` in the app.
Use
Open the app, check the Hostinger HTTPS domain, enter your Admin Key on the Cloud page, select the event, issue the staff QR or re-pair from a device card. Photographer must scan with their LensDrop photographer Android App → Staff Setup. Approve the displayed six-digit code on Admin Lite. The app does not scan the photographer QR itself.
Pilot only: Native APK has not been compiled in this environment; check GitHub Actions artifact and test on physical Android before deployment. Avoid trusting unknown cloud domains.

# LensDrop H5.7.1 — Android 4G/5G pairing and cloud access

**Scope:** Android client only. Cloud H5.7 UI / cloud H5.5 core schema 7, Windows H5.7 UI / station H5.5 core, Bluetooth QR tickets, CP9550DW, all queue and database schemas remain unchanged.

## Why the phone showed `mobile_data_not_allowed`

The previous `CloudApi.connection()` refused any validated *metered* active network whenever the saved `cellular` preference was false. Android classifies normal 4G/5G as metered, so even a valid staff QR invitation could not be claimed until after a setting that was inconvenient to save before pairing. Metered Wi-Fi was affected too.

## Corrected behavior

- **Any validated active Android network** is now allowed for LensDrop HTTPS requests, including 4G/5G, metered Wi-Fi, unmetered Wi-Fi, and validated VPN connections. This applies to staff QR claiming/approval polling, cloud photo uploads, previews and cloud print requests.
- The existing setting has been **renamed** to **"Use cellular fallback if Wi-Fi has no internet (optional)"**. It no longer blocks normal 4G/5G traffic. It controls only the existing optional alternate-cellular network request when the active network is unusable. Default remains OFF to avoid unnecessarily keeping another cellular network requested.
- VPN routing is respected when the active VPN is validated; an unvalidated active VPN is not bypassed by the cellular fallback.
- Existing pairing invitations/claims, queue rows, credentials, archive policies, print job IDs and printer profiles are unchanged. No new permission, backend API, migration or background worker is added.
- Cellular data charges and event photo upload volumes may be substantial. This update removes the application-level metered-network block, not Android's OS-level Data Saver, carrier restrictions, VPN/firewall or loss of connectivity.

## Package and GitHub build

1. Back up the current repository/commit and preserve the existing Android signing key. Do not clear or uninstall an app with pending photo or print work.
2. If the repository matches the delivered H5.7 verified source, merge **LensDrop_H5_7_1_Mobile_Data_Fix_Overlay.zip** into the repository root, preserving `.github` and all relative paths. Otherwise use the **complete H5.7.1 GitHub-ready ZIP**, comparing any custom changes before replacement. Do **not** upload a ZIP as a single repository file.
3. Commit and push. Run **Actions → LensDrop Android APK → Run workflow** on the new commit, not **Re-run jobs** on an older commit.
4. Confirm **Verify complete Android source snapshot**, Android tests, `:app:assembleDebug`, `:app:lintDebug`, and artifact upload succeed. Do not disable lint. The expected `BUILD_INFO.json` is `0.2.0-alpha.6-h5.7.1`, versionCode `13`, application ID `app.lensdrop.transfer`, snapshot `lensdrop-android-h5.7.1-code13`.
5. Install with the **same signer** as the existing app. The APK filename remains `LensDrop-H5-debug.apk`; check `BUILD_INFO.json` and actual installed version, not the filename.

## First 4G/5G test

1. On the phone, turn off Wi-Fi and enable 4G/5G. Confirm the phone browser can reach your Hostinger `/health` endpoint. Leave the optional fallback checkbox **OFF**.
2. On web Admin, select the correct event and create a **fresh five-minute Photographer phone QR**. Revoke the previously shared invitation rather than reusing it.
3. In Android **Setup → Join event → Scan staff QR** or paste the new invitation. Review the cloud origin, then **Claim invitation**. A six-digit code should appear on the phone.
4. In web Admin **Setup & pairing → Refresh pairing status**, find the **claimed** invitation, enter the phone's six-digit code and approve it. On the phone choose **Check approval & finish setup**.
5. Select a small shared JPEG folder and start **New photos only**. Verify one new disposable JPEG reaches the private cloud archive. Existing baseline photos should remain excluded.
6. With the phone on 5G, choose **Cloud-only print** for a one-copy **Simulation** test. Direct LAN printing still requires reachability to the station's private endpoint; it is not automatically available over 5G.
7. Test temporary cellular loss and restoration; verify the same queue/photo/print IDs resume without duplicate submission. Export redacted diagnostics if a failure remains.

## Verification and limits

Executed here: real `CloudApi.java` compiled against minimal Android connectivity fakes and 13 behavior assertions passed, including validated metered 5G, metered Wi-Fi, validated VPN, unvalidated VPN protection, offline failure, optional cellular fallback and callback release. The previous metered-network rejection was restored in a negative control and caused the test to fail. Android source snapshot verification (92 files), 23 snapshot regressions, UI source/scanner tests, 36 Java grammar parses, 10 session, 15 print policy, 31 profile, 24 watch-ledger and 25 recovery tests passed. Ticket protocol tests passed 27 checks; subsequent ticket-ledger and archive-workflow reruns stalled in their existing JVM/SQLite bridge and were not counted as completed passes. Cloud/Windows code was unchanged and not rebuilt. **No new Android SDK build/lint, real phone 5G/VPN test, printer test or live deployment was performed here.**

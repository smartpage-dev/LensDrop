# LensDrop H5.3 - QR ticket pilot

**Android-only source update. Manual Bluetooth printing of an existing guest QR. No new APK is included.**

> 今次先做手動試行：選打印機 → 預覽目前顯示嘅 QR → 印一張 → 用另一部手機掃碼核對。不會自動重印，不會改動 Hostinger 或 CP9550DW 相片打印。

## 01 / Evidence and limits

Your supplied paper and matching Lab IDs demonstrate ASCII output and an externally requested native QR symbol over secure SPP on the tested Samsung/58B3 pair. The QR test used only a 32-byte HTTPS URL. Its report still says not_checked; exact scan contents and longer guest links are not verified. Digital decoding of the uploaded photo was inconclusive, not proof that the paper is unreadable.

This pilot keeps native Model 2 / ECC M / 4-dot modules with 128-byte writes and a 20ms interval. It does not add raster, Chinese/logo output, cutter commands, BLE, automatic tickets or a permanent background Bluetooth service.

## 02 / Update the correct GitHub repository

Use your existing **LensDrop** repository, NOT Luce-Printer-Lab-58. Apply the contents of the H5.3 patch at its root and merge folders, or use the full project snapshot. Do not upload only the ZIP or add an enclosing patch folder. Preserve `.git` and your existing `LENSDROP_TEST_KEYSTORE_BASE64` signing secret.

Required new files include TicketActivity.java, TicketTransport.java, TicketProtocol.java and TicketLedger.java under `clients/android/app/src/main/java/app/lensdrop/transfer/`. Include the updated manifest, QueueDb, WatchLedger, tests and `.github/workflows/android-debug.yml` as well.

After committing/pushing, start a **new** Actions -> LensDrop Android APK -> Run workflow on the updated branch. Do not rerun an older commit. Existing signing mode/identity must be preserved. No Windows build is needed for this increment.

After success, download the APK artifact, not the log artifact. Extract `LensDrop-H5-debug.apk` and check BUILD_INFO:

```text
application_id: app.lensdrop.transfer
version_name: 0.2.0-alpha.6-h5.3
version_code: 8
```

This source has not been compiled with the Android SDK here. A green native build and installation are still required. The current workflow runs logic tests and assembleDebug, not Android lint/instrumentation.

## 03 / Preserve existing data

Finish or safely pause current work. Preserve your existing signer and source originals. The update migrates the local queue from schema 3 to schema 4 by adding ticket tables; it preserves jobs, baselines, guest groups and source bindings.

Do not uninstall, clear storage, reset the watch baseline or downgrade to an older APK to fix a signing/schema problem. The old app is not a supported reader of the new queue. There is no automatic downgrade/restore tool in this release; use a disposable event/device for initial rehearsal and retain originals.

The cloud stays H5/schema5 and Windows stays H5.2. Leave Hostinger credentials, storage identity, initialization=0 and deployment unchanged. The separate Printer Lab app remains untouched.

## 04 / Pair your dedicated QR printer

Close Printer Lab and other printer apps first. In Android Settings, pair your own 58B3 (the tested unit advertises T58_F82E; use the PIN printed on its self-test when asked).

In LensDrop open **Bluetooth QR ticket / 58B3 pilot**. Press **Grant nearby-device permissions**, then **Choose / confirm MY paired QR printer**. Check both the name and displayed hardware address against your own device. The app does not guess a printer by name, start a discovery loop or auto-switch to a neighbour's device.

Press **Test secure SPP connection (NO PRINT)**. Success here confirms a socket connection only. The test closes its connection afterward and prints nothing.

## 05 / Prepare an ordinary guest QR

Use the existing Staff Setup and event pairing. In **Guest QR / Groups / Select one photo**, prepare links online and start a Group, or choose and confirm one candidate in Single Photo mode.

A Group QR can exist before photos finish uploading. A Single Photo link must already be bound to its selected image. No link is fabricated from a group label, local IP or short sequence number.

Show the desired QR. History can show an earlier group without changing the receiver. The ticket screen shows both **Showing QR** and **Receiving** so you can confirm the intended guest.

## 06 / Print exactly one ticket

Open **Print SHOWN guest QR ticket (Bluetooth)** from the QR screen, or the ticket page from main setup. Press **Preview / print the SHOWN guest QR**.

The confirmation shows the saved printer, exact guest URL and group label. It is a content confirmation, not a driver-accurate paper preview. Check them, then press **Print ONE ticket**. Keep this screen in the foreground until the send result appears.

The ticket is ASCII-only: collection heading, Group/Photo label, reference, attempt ID, native QR and a waiting/entitlement notice. It does not print the photograph. There is no event Logo or Chinese text in this pilot.

The data snapshot is saved before transmission. Changing the shown group afterward cannot alter it. The app sends only through the selected Bluetooth device; no Hostinger/Windows ticket-print route is introduced. The existing photo-upload service may continue, but this concurrent behavior still needs real-phone testing.

## 07 / Scan the actual LensDrop link

Use another phone to scan the paper. Compare the **complete** decoded URL to the preview/screen QR; then confirm it opens the intended group or waiting page. Do not post private live QR links publicly. A waiting/review page is not a printer error.

In **Ticket history / observations / explicit reprint**, select the matching ticket ID, then **Record physical observation**. Choose "QR scanned AND complete URL matches preview" only after doing that comparison.

Send status remains `sent_unconfirmed` even after a separately recorded operator observation. The printer has not provided authenticated physical-completion telemetry. First validate several full-length guest links before giving paper to guests. The maximum accepted guest URL is 160 ASCII bytes; overlong or unprepared links are blocked instead of silently shortened. No third-party URL shortener is used.

## 08 / Reprint, history and Undo

Reprint in Ticket history uses the original guest URL and creates a new linked attempt. You explicitly confirm the currently selected printer; it does not silently redirect old work. It does not create another guest group, change existing photo assignment, grant print entitlement or resend a Windows photo job.

Undo Next Group now preserves an empty group if it has any saved ticket snapshot. Even a cancelled-before-send or failed attempt keeps that protection, conservatively, because paper may already have left the device. Unissued empty groups retain the existing cancellation behavior. Use cloud Admin intentionally to revoke links when needed; offline ticket printing cannot verify a recent cloud revocation.

## 09 / Interruptions and privacy

| Result | Meaning / action |
|---|---|
| sent_unconfirmed | Bytes were written; inspect paper and record the actual scan result. |
| failed_before_send | Connection/permission/pre-send failure. No automatic resend; inspect history then make a new explicit attempt. |
| outcome_unknown | At least one write was attempted, or a sending attempt was interrupted. Some/all data may already be in the printer. Inspect paper before reprinting. |
| cancelled_before_send | Saved but undispatched attempt recovered on reopening. No automatic send. |

Leaving the ticket screen while sending cancels its connection only. It does not stop the photo upload/Windows print services. STOP cannot retract bytes already received by the printer. There is no automatic retry, no guaranteed no-paper detection, and no sleep/wake certification.

History stores guest bearer URLs in app-private SQLite so an exact reprint is possible. It is not separately encrypted. Automatic app backup remains disabled. Redacted export contains aggregate states/observations, not URLs, tokens, printer addresses/names or photographs.

A printed QR is photo access, not unlimited photo-print authorization. Station scan-to-select/collection and kiosk redemption remain separate future work.

## 10 / Pilot acceptance checklist

| Case | Required observation |
|---|---|
| Full guest URL | Printed QR scans to the exact same full-length Group/Photo URL. |
| Group switching | Print A, start B, show/reprint A: paper still opens A; incoming B photos stay in B. |
| Empty-group Undo | Ticket-issued group remains available rather than being cancelled. |
| Single Photo | Printed QR stays bound to one selected image; later arrivals cannot replace it. |
| Repeat tap | One confirmed attempt is claimed once; explicit Reprint is visibly separate. |
| Printer off / no paper | Photo queue remains usable; no automatic duplicate ticket after recovery. |
| App exit / restart | Saved uncertain attempt is not silently replayed. |
| Longer payload | Several actual guest links scan; test beyond the original 32-byte Lab URL. |
| Camera + cloud upload | Both continue while a small ticket is sent; measure device temperature/memory and latency. |
| CP9550DW | Existing Local/Cloud/Hybrid photo jobs and 4x6/strip settings are unchanged. |

Start with one disposable group and one ticket. Record exact-link scan success, then repeat/history/Undo before testing concurrency. No mass event use or automatic ticketing is authorized by these source tests alone.

## Supporting records

* [Release and evidence review](docs/h5_3/Release_Report.md)
* [Sanitized evidence summary](docs/h5_3/Evidence_Review.json)
* [27 ticket protocol + 28 ledger checks](docs/h5_3/validation/ticket-tests.log)
* [24 existing watch-ledger checks](docs/h5_3/validation/watch-ledger-tests.log)
* [Structural and unchanged-component checks](docs/h5_3/validation/structural-checks.json)

Primary API references: [Android Bluetooth connection](https://developer.android.com/develop/connectivity/bluetooth/connect-bluetooth-devices), [BluetoothSocket cancellation](https://developer.android.com/reference/android/bluetooth/BluetoothSocket), [Epson native QR reference](https://download4.epson.biz/sec_pubs/pos/reference_en/escpos/gs_lparen_lk.html). These are API references, not 58B3 certification.

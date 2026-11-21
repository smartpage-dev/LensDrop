# LensDrop H5.3 - Bluetooth guest-ticket pilot

Release date: 21 November 2026. Android source: `0.2.0-alpha.6-h5.3`, versionCode `8`, application ID `app.lensdrop.transfer`. Local transfer database: schema **4** (additive migration from schema 3). Cloud remains H5 / schema 5. Windows Print Station remains the H5.2 CP9550DW source.

## What the new user evidence establishes

Three report exports contain **two distinct attempts**, not three independent hardware tests. The first two exports repeat the same ASCII attempt. The latest export includes both ASCII and custom-native-QR attempts.

| Evidence | Finding |
|---|---|
| T-66C9EA9980 | Lab A, 78 bytes, secure RFCOMM. Corresponding ASCII output is visible in the supplied photograph. |
| T-4363A312EC | Lab J, 115 bytes, 32-byte custom HTTPS URL, 4-dot native modules and M correction. Its matching ID and printed QR symbol are visible. |
| Connection | Standard SPP UUID discovered; secure RFCOMM connected. This establishes a tested transport route for this device/phone pair, not BLE support. |
| Recorded result | Both attempts remain `sent_unconfirmed` / `not_checked`. These are not error states, but they are not recorded scan-verification either. |
| Independent image decode | OpenCV and ZBar attempts on the uploaded photograph did not recover a payload. This does NOT establish that the original paper cannot be scanned. Exact decoded contents remain unverified. |
| Width | `requested_raster_width_dots=320` is a Lab configuration value; the tested method was native QR. It does not measure head resolution or print width. |

No successful long-URL, raster, Chinese, logo, unattended, sleep/wake, multi-camera/coexistence or endurance test is claimed. Actual full-length LensDrop guest links must be scanned before handing tickets to guests.

## Implemented source changes

* New Android TicketActivity reached from main setup and the Guest QR screen. It prints the **shown** personal QR, not silently the currently receiving group.
* Explicit selection of one system-paired Bluetooth printer. The saved hardware address is used, never automatic selection by name. Secure SPP only. No discovery loop, BLE writer or insecure fallback.
* Native Model 2 / M / 4-dot command block retained from the successfully output Lab J sequence. Verified byte-for-byte against a frozen Lab generator in tests. ASCII collection header/label/reference only; new framing and longer payloads still require hardware testing.
* One explicitly confirmed foreground attempt. The URL, group, printer and identifiers are saved immutably before dispatch. No print-on-arrival, Next Group auto-print, background ticket service or automatic resend.
* An additive `qr_tickets` table, indexes and an immutable-column trigger. Sent/uncertain states remain distinct from operator observations. History is paginated, with no deletion of unprocessed records.
* Reprint is a new linked attempt using the original guest URL. Switching groups, showing history, changing mode and later uploads do not mutate saved ticket contents.
* Undo Next Group no longer cancels an empty group once ANY ticket snapshot exists, including an unattempted/failed snapshot. This deliberately conservative rule protects paper that may already have left the printer; it can retain an unused empty group.
* Process-wide operation guard, bounded connection/send timeouts, 128-byte chunks/20ms pacing and cancellation when the ticket screen leaves the foreground. No claim that socket close recalls received bytes.
* Redacted **count** export only: no guest URL/token, printer name/address, group/event/device IDs or photo content. Ticket snapshots themselves are stored in app-private SQLite, not separately encrypted; Android backup remains disabled.
* Added runtime Bluetooth permissions only when entering this workflow. Existing photo intake, upload and Windows print services are not stopped by TicketActivity.
* Android workflow runs the new ticket tests before assembleDebug. APK filename remains `LensDrop-H5-debug.apk`; source-derived BUILD_INFO identifies H5.3/code8.

## Unchanged boundaries

Production cloud directory, deployed cloud ZIP, Windows station, desktop uploader, GitHub signing helpers and Windows workflow are byte-identical to H5.2. No server deployment, cloud schema change, Windows reinstall or Printer Lab update is needed. Existing CP9550DW 4x6/automatic-pair/manual-pair profiles remain unchanged.

The cloud/Windows suites were not rerun in this increment; byte identity is checked instead. Previously reported passes are not represented as new runs. No Bluetooth ticket print from the Windows station, station ticket scan-to-select/collection, self-service kiosk or automatic entitlement redemption was added. A guest URL remains a bearer photo-access link, NOT permission to print a photo.

## Local verification executed

| Suite | Result | Scope |
|---|---|---|
| Ticket protocol | 27 passed | Actual production packet builder, URL/identity validation, preserved native command sequence, injection bounds. |
| Ticket ledger | 28 passed | Actual QueueDb/WatchLedger/TicketLedger against SQLite through the JVM bridge, including immutable snapshots, dedup claim, crashes, reprints, Undo and v3 migration. |
| Existing watch ledger | 24 passed | Original new-only baseline, 10,000 excluded identities, candidates, groups, history, migration and skip behavior. |
| Session policy | 10 passed | Existing event/source binding rules. |
| Print policy | 15 passed | Existing LAN/cloud job and outcome rules. |
| H5.1 build contracts | 15 passed | Existing source-extracted callback/API checks; expected version updated to H5.3. |
| CP9550DW profile/layout | 31 passed | Existing Java profile/geometry assertions. |
| Static structure/byte identity | 36 passed | Manifest, foreground/manual gates, deadlines, schema, workflow and unchanged components. |
| Java syntax | All current production Java files parsed | Syntax only, not Android API/resource/manifest type checking. |

The legacy H2 migration test constructs an old schema by removing newer tables; its fixture now also removes `qr_tickets` before setting user_version=2. This is a test-fixture update, not a relaxed production migration. The frozen H5.2 QueueDb test separately exercises a real v3-to-v4 migration.

Early runs using this environment's instrumented Python timed out during repeated SQLite-bridge startup. Full final suites ran with system Python and passed; incomplete runs are not counted as passes.

## Still required

A new GitHub Android native build, Android lint/instrumentation, installation with the existing signer, actual Bluetooth output from this integrated app, full LensDrop URL scan, persistence on the real phone, sleep/reconnect, no-paper/battery recovery and simultaneous camera/upload/Windows-print tests. No APK was built here: Android SDK/Gradle are absent and a fresh Google SDK endpoint request failed DNS. No native or hardware success is inferred from the JVM tests.

## External API basis

* Android Bluetooth device connection: https://developer.android.com/develop/connectivity/bluetooth/connect-bluetooth-devices
* Android BluetoothSocket cancellation: https://developer.android.com/reference/android/bluetooth/BluetoothSocket
* Epson QR command reference: https://download4.epson.biz/sec_pubs/pos/reference_en/escpos/gs_lparen_lk.html

These document API/command semantics, not manufacturer certification of the 58B3. Physical evidence comes from the three user-supplied Lab reports and the matching output photograph.

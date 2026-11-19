# LensDrop H2 Enhanced - User and Upgrade Guide
Release: 2026-11-14 | Engineering alpha

## LensDrop H2 Enhanced
增強版使用及升級指南 | 14 November 2026

本版已更新雲端程式、桌面上傳工具及 Android 來源碼，並非只有計劃書。仍屬工程測試版，未完成真實相機、手機及 Hostinger 長時間活動驗證。

重要：套件內沒有 APK、iOS App 或 Windows EXE 安裝程式。Android 程式尚需建置及真機測試；桌面操作視窗需 Python 3.10+ 及 Tcl/Tk。

| 本次增強 | 實際行為 |
| --- | --- |
| 三段狀態 | 分開顯示已接收、雲端已驗證、客人可見；過時回報不當作完成。 |
| 拍前測試 | 單張獨立 JPEG，雲端驗證後仍然隔離，無法公開。 |
| 安全轉場 | 上傳隊列不能重指派至另一活動；分開來源資料夾。 |
| 收工對數 | 只處理已識別的工作，匯出未完成及待處理數量。 |
| 脱敏診斷 | 匯出統計及錯誤代碼，不包含 Token、姓名、檔名、路徑或相片。 |
| 客人相簿 | 新相提示不主動重設滾動位置；用戶按提示才載入最新相。 |

### 應該開邊個套件？

完整套件：LensDrop_Enhanced_Programs_and_Docs_v0.2-alpha_H2.zip。解壓後開 START_HERE.html。Hostinger 只上傳 deploy 內的 LensDrop_Hostinger_Cloud_v0.2-alpha_H2.zip，不是整個外層套件。

文件優先次序：本 H2 指南 > H1 Hostinger 指南 > 原本 26 頁全套指南。舊指南保留供安裝與日常作業參考。

## 01 / Hostinger 安全升級
先備份，再更換程式；不重建現有活動儲存區。

### 1. 暫停工作及停止舊服務

先暫停所有上傳客戶端，等待當前傳送到安全檢查點，然後停止 H1 雲端程序。同一資料夾只可由一個服務實例寫入，不可同時運行 H1 與 H2。

### 2. 完整備份持久資料夾

在服務已停止的狀態下，備份整個 LENSDROP_DATA_DIR，包括 SQLite、仍存在的 WAL/SHM、原檔、縮圖、未完成分塊及儲存識別檔。另外安全保存環境設定及 H1 原程式。不要只複製正在寫入中的單一 DB 檔。

### 3. 保留原設定，上傳 H2 內層 ZIP

```text
LENSDROP_DATA_DIR       = existing confirmed persistent path
LENSDROP_STORAGE_ID     = existing saved value
LENSDROP_ADMIN_KEY      = existing private key
LENSDROP_INITIALIZE_STORAGE = 0
```

升級不要重新產生上述識別資料，不要清空儲存區或手動刪除鎖定來略過錯誤。新安裝才依 H1 指南使用一次性初始化。

| 部署項目 | 設定 |
| --- | --- |
| Framework / Node | Other / Node 22.x, at least 22.13 |
| Root / output / entry | . / . / server.js |
| Build / start | npm run build / npm start |
| 儲存要求 | 私有、可寫、持久本機儲存；位於 app、public_html、hbuilds 之外。 |

### 4. 驗證及退版

登入後按 Check deployment，確認舊活動、原相及原手機 Token 仍在。測試重啟與再部署後的保留及繼傳。H2 會作 schema 2 增量遷移；如要退回 H1，必須一併還原升級前整個資料夾及 H1 程式。不可讓 H1 開啟 H2 資料庫。

## 02 / 桌面快速開始
先用電腦驗證上傳流程，不等同手機相機橋接已完成。

### 1. 準備測試活動

在雲端 Admin 建立彩排活動，先選擇 Review（先審後公開）。為該電腦建立獨立 Device Token；不要把 Admin Key 交給攝影師。準備一個專屬活動相片夾及另一個隊列資料夾。

### 2. 開啟操作視窗

```text
Windows: clients/desktop/OPEN_OPERATOR_CONSOLE.cmd
macOS:   sh clients/desktop/OPEN_OPERATOR_CONSOLE.sh
```

先安裝 Python 3.10+ 及 Tcl/Tk。輸入 HTTPS server、Device Token、活動相片夾及隊列目錄。隊列目錄必須在相片夾以外。Token 不存入設定檔，下次重開需重新輸入。非本機測試不要啟用 HTTP 選項。

### 3. 拍前獨立測試

將一張全新、已完整寫入的 JPEG 放入活動夾，先按 Test one separate JPEG，選擇該檔。此模式只處理選定測試檔，不開啟普通資料夾掃描。雲端驗證成功後，Admin 會列為 TEST / never public。

測試相不能轉成公開相，也不能在同一活動、同一裝置將完全相同內容再當正式相上傳。只改檔名不夠，請另拍一張不同的相。測試檔會佔用雲端容量。

### 4. 再驗證客人相簿

另放入三張不同的彩排 JPEG，按 Start / resume。桌面版會掃描該夾現有及新增的 JPEG，不要指向個人相片總目錄。Admin 審核後公開一張，打開客人相簿確認可瀏覽及下載。

獨立測試只驗證「可讀取檔案至雲端」，不會自動驗證相機至手機及客人瀏覽器。後者要用上述另一組彩排相作端到端檢查。

## 03 / 現場狀態、轉場及收工
不清空隊列；不以對數報告作為刪除原相的許可。

### 讀懂三組數字

「已接收」來自客戶端已識別檔案回報；「雲端已驗證」是去重後的正式照片物件；「客人可見」只計 Live。測試相另計。重複檔案可產生多個工作但只有一張唯一相片，因此三個數字不必相等。

客戶端回報超過 90 秒或缺少完整計數，對數不會顯示全部完成。收到檔案不代表相機仍連線，亦不代表相機卡內沒有遺漏。Admin 以按鈕更新，不會持續重畫審核操作。

### 安全轉另一個活動

Android 來源碼採用保守策略：舊活動仍有未完成或被阻擋工作時，拒絕切換至新活動。即使已傳完，新活動也要使用不同來源資料夾。本版不支援舊活動在背景補傳、同時轉去新活動收相。

桌面版每個活動用不同相片夾和隊列目錄，不可將舊隊列指向新 Token。桌面版不會跨多個隊列目錄作全機來源鎖定，仍需你正確分開來源。

### 收工步驟

先等官方相機 App 完成下載，確認最後一張已被 LensDrop 識別。按 Finish observed queue，停止發現新檔，只處理已在隊列的工作。只要還有重試任務，就需等待連線恢復；被阻擋的工作不會被當成成功。

匯出 wrap-up 對數報告，檢查 queued、blocked、cloud 狀態及回報時間。保留相機卡及手機原檔，直至獨立備份及人手核對完成。單一伺服器收據不是多地備份證明。

### 遇到問題

先暫停，匯出 redacted diagnostics，保留隊列。處理連線、權限或空間問題後再繼續。不要以重裝 App、清除資料或刪隊列作為第一步。診斷是當刻快照，不是全部歷史 crash log。

## 04 / Android developer build
SOURCE ONLY | No APK was generated in this release.

The updated native Java source is included under clients/android. Android SDK and Gradle were unavailable in the build environment, and Google SDK repository DNS resolution failed. The pure-Java checks below do not resolve Android symbols, validate resources, assemble an APK or exercise a device.

| Build input | Required configuration |
| --- | --- |
| Java / Gradle | JDK 17 / Gradle 8.11.1 |
| Android Gradle Plugin | 8.9.1 |
| SDK | compileSdk 35, targetSdk 35; minimum Android API 29 |
| SDK components | Platform 35, build-tools 35.0.0, accepted licenses |
| Application ID | app.lensdrop.transfer |
| Source version | versionCode 3; 0.2.0-alpha.3-h2 |

### Build and retain the log

Install the above tools from their official providers. Set JAVA_HOME and ANDROID_HOME (or a local.properties sdk.dir). Add Gradle to PATH. No Gradle wrapper is bundled. Run BUILD_ANDROID.cmd on Windows, or sh BUILD_ANDROID.sh on macOS/Linux, from the android directory.

```text
gradle --no-daemon :app:assembleDebug
# Expected output after a successful build only:
app/build/outputs/apk/debug/app-debug.apk
```

A successful debug build still needs installation and device validation. Keep the same app ID, signing identity and app data when updating an existing test device. Do not uninstall an app with pending work simply to resolve a signing mismatch. Store/release signing and distribution are not completed.

### Pilot workflow after compilation

Use the official camera app to receive a JPEG into Android shared media. Pair a rehearsal event, choose the exact shared folder, then choose one separate preflight JPEG. Pairing no longer starts discovery automatically. Resume saved event starts normal discovery. Existing historical photos before the pairing boundary are not automatically backfilled.

Confirm current and future JPEGs can be read with the granted media permissions. A vendor app private gallery is not an accessible folder. One upload and 1 MiB chunks, bounded staged copies and battery/thermal gates remain in the source. Performance and all-day background execution have not been measured.

H2 does not add a Panasonic SDK, CCAPI, USB/PTP, camera FTP receiver or iOS implementation. Native camera integration remains a separate qualification track. The Android state migration, service lifecycle and simultaneous camera-Wi-Fi/cellular behavior still require physical testing.

## 05 / CLI and service interfaces
Use device credentials for transfer. Keep administrator credentials out of client files.

### Desktop command-line alternative

From clients/desktop, run the command below with your actual HTTPS origin and separate source/state directories. Omit the device-token environment variable to receive a hidden interactive prompt. Do not paste a live token into shell examples or a saved script.

```text
python lensdrop_agent.py \
  --server https://YOUR-LENSDROP-HOST \
  --folder /path/to/event-photos \
  --state /path/to/event-queue \
  --preflight-photo /path/to/event-photos/test.jpg
```

After preflight, rerun without --preflight-photo to watch the folder. Stop with Ctrl+C; resume with the same origin, token, folder and state. Use --drain to process only already observed work. Windows PowerShell users should place the command on one line rather than use the POSIX continuation character.

```text
--diagnostics /path/to/redacted-diagnostics.json
--reconcile /path/to/wrap-up.json
--retry-blocked   # only after correcting the cause
```

### New H2 interfaces

| Authentication | Interface / behavior |
| --- | --- |
| Device | GET /v1/device/summary: observed-file counts, verified jobs/photos and publication counts. |
| Device | GET /v1/device/preflight?client_id=UUID: owned test job result only. |
| Administrator | GET /v1/events/:id/reconciliation: full current reconciliation snapshot. |
| Administrator | GET /v1/events/:id/diagnostics: allowlisted redacted snapshot. |
| Administrator | GET /v1/events/:id/status?before=SEQ: 200-photo pages; totals cover the entire event. |
| Public gallery | POST /v1/g/:slug/visible with {"ids":[...]}: live IDs from at most 500 supplied UUIDs. |

POST /v1/uploads now accepts boolean is_test. Its value is immutable for that client_id. A preflight-aware client checks server capabilities before sending a test to an older server; it must not silently fall back to a normal upload. Publication of any test photo is refused server-side, not merely hidden in the UI.

Diagnostic exports omit credentials, private labels, filenames, source paths and photo bytes. Reconciliation is a richer administrator-only operational report, not a redacted diagnostic. Treat it as confidential. Guest galleries remain public-by-link; this release does not add a guest password system.

## 06 / What passed and what did not
Tests describe the exercised scope, not a general production guarantee.

| Executed check | Result / scope |
| --- | --- |
| Cloud automated suite | 35 passed; local Node 22.16.0, sharp 0.34.1, SQLite. |
| Desktop automated suite | 23 passed; Python on Linux, including real local-backend CLI tests. |
| Pure-Java session policy | 10 assertions passed; not Android compilation. |
| Android Java parsing | 10 application source files parsed; no Android type/resource/manifest checking. |
| Static web UI checks | 8 passed; Chromium set_content with synthetic fetch responses. |
| Desktop UI smoke | Linux/Xvfb Tk window rendered; startup and idle controls only. |
| Live browser navigation | Blocked by environment policy. No live-browser E2E pass asserted. |
| SDK / dependency access | Google SDK and npm registry resolution unavailable. No clean npm install or APK result. |

### Important exercised scenarios

Regression coverage includes forced cloud-process crashes, durable queues, lost acknowledgements, duplicate filenames, source/queue binding, preflight isolation, stale-heartbeat rejection, diagnostic redaction, additive H1 schema migration and more than 200 admin photo records. A read-only console export is verified not to change an actively uploading row.

Guest DOM checks verify that a new-photo notification retains existing cards and scroll position; an explicit click loads the newest photos. Visibility changes remove the displayed content after the next successful check. Already downloaded or cached copies cannot be remotely recalled.

### Physical acceptance still required

Run one actual camera/app/phone combination first. Prove automatic shared-file availability without manual per-photo exports. Then test network interruption, process termination/reopen, two photographers, storage pressure, heat/battery behavior and an eight-hour session. Reconcile known files and independently inspect the camera card.

Hostinger must pass dependency installation, persistent storage, single-writer, restart and redeployment tests on the actual account. The H1 local directory-replacement test remains a regression test, not evidence that managed hosting preserves your data.

The original 10,000-queue-record and 1,000-upload-session tests are metadata/session durability tests. They do not establish throughput for 10,000 real photos or 1,000 simultaneous photographers. No phone RAM, battery-life or upload-speed benchmark is claimed.

## 07 / Recovery and release boundaries
Read this before clearing data, changing events or rolling back.

| Symptom | Action |
| --- | --- |
| Source count stops changing | Check the official camera app, shared-media save destination and permissions. LensDrop cannot retry a camera download it never received. |
| Queued > 0, no progress | Check validated internet, token validity, free space and server quota. Keep original files and queue state. |
| Test/production conflict | Take a different JPEG. Renaming identical bytes does not change the test content identity. |
| Unknown or stale counts | Reopen/resume the client, allow a new heartbeat and refresh Admin. No green completion is shown for stale or missing counts. |
| Event switch refused | Finish or resolve the old queued/blocked work, then use a separate source folder. Do not relabel jobs or clear storage. |
| Host startup refused | Check the original persistent path, storage ID, existing DB and single process. Do not initialize a nonempty existing event directory. |
| Rollback required | Stop H2; restore the entire preupgrade data backup AND matching H1 code. New H2 uploads after the backup need a separate recovery plan. |

### Deliberately not included

No installable Android/iOS app, camera SDK adapter, invitation-QR onboarding, priority upload queue, cross-event simultaneous phone sessions, object-storage backend, replicated cloud receipts, production SLA or independent security audit. Core admin labels include Traditional Chinese and English; diagnostic/status details and native clients remain partly or fully English.

### Reference documents and evidence

The complete bundle retains the original full user/setup guide and H1 Hostinger guide. The H2 release report and raw validation logs live under cloud/docs/enhanced and cloud/docs/validation-h2. Static/mocked web images are labeled synthetic UI renders; the desktop capture is a Linux UI smoke screenshot, not a Windows/mobile field test.

Official developer references for toolchain/background constraints (not claims of field success):

Android command-line builds: https://developer.android.com/build/building-cmdline
AGP compatibility: https://developer.android.com/build/releases/past-releases/agp-8-9-0-release-notes
Foreground-service timeouts: https://developer.android.com/develop/background-work/services/fgs/timeout

Cloud version: 0.2.0-alpha.3-hostinger.2 | Android source: 0.2.0-alpha.3-h2
Release posture: controlled rehearsal only. Preserve camera and phone originals.

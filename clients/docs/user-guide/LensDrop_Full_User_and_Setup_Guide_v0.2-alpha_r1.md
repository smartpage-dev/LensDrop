# LensDrop - Full user and setup guide

Software 0.2.0-alpha.1 | Guide r1 | 11 November 2026

Engineering alpha. No APK or iOS app is supplied.


## 01 / Start here

ALL USERS

LensDrop transfers photographs that have reached an accessible phone or computer folder to an event gallery. The next milestone is an installable Android pilot, one verified Panasonic-to-phone transfer path, and a tested HTTPS server. This manual covers the released engineering alpha, not a finished commercial service.

| Component | Available now | Not established |
| --- | --- | --- |
| Cloud Core | Event/device API, resumable JPEG upload, basic console and gallery. | Live hosting, independent backups, production security or scale. |
| Desktop uploader | Python watch-folder client; automated Linux tests. | Camera control, a Windows installer, or macOS/Windows field qualification. |
| Android | Native source project with queue and MediaStore discovery. | Compiled APK, installation, camera compatibility or all-day background reliability. |
| iPhone | Architecture discussion only. | An iOS application or a supported iPhone workflow. |

### The order to follow

1. Start with the same-computer desktop test in sections 02-07. Use only non-sensitive test JPEGs.
2. Prove the Panasonic shared-photo prerequisite in section 12. This can be checked before an Android APK exists.
3. Have a developer complete the Android build and HTTPS pilot in sections 08-11. Phone instructions in section 13 apply only after a successful build.
4. Run recovery, capacity and multi-photographer checks before considering event use. Do not treat a working gallery as production approval.

> Do not uninstall LensDrop, clear its storage, erase a queue, delete pending source photos or format a camera card to resolve an upload problem. Preserve the work first.

Guide revision: r1, 11 November 2026. Software: 0.2.0-alpha.1. The two repackaged ZIPs add documentation only; application source is unchanged. Package references [P1-P4] and official sources [S1-S14] appear at the end.


## 02 / Prepare your computer and folders

LOCAL TEST / WINDOWS OR MAC

### Install and verify the prerequisites

Use Node.js 24.x with npm, or an existing compatible Node 22.13+ installation. The package explicitly requires Node >=22.13 and <25; do not use Node 25/26 just because it is newer. Use a maintained Python 3.13 or 3.14 installation for the desktop agent. The code minimum is Python 3.10, but that is not a recommendation to install an obsolete runtime. Obtain installers from the official sites [S1, S2].

Extract both archives. Move the inner cloud and clients folders into one LensDrop folder under your home/user folder. Keep their names exactly as shown. Run commands from extracted folders, never from inside a ZIP preview.

```text
LensDrop/
  cloud/                 package.json, src/, public/, docs/
  clients/               desktop/, android/, docs/
  incoming-test/         only JPEGs intended for this test
  queue-test/            desktop queue and staged duplicates
  server-data/           server database and media
  private/               administrator key; never share
  backups/               local backup copies for evaluation
```

The incoming, queue and server-data folders must be separate. A queue folder inside the watched folder is rejected. Do not put these data folders inside cloud or clients: replacing application files must not replace your photographs.

| Check | Windows PowerShell | macOS Terminal |
| --- | --- | --- |
| Node version | node --version | node --version |
| npm version | npm.cmd --version | npm --version |
| Python version | py -3 --version | python3 --version |

> The computer is only a local test server in this path. Its 127.0.0.1 address cannot be used by a phone. No cloud account, domain or camera connection is needed for the first test.

Use copies of three different JPEGs initially. Renaming the same bytes three times is not a valid three-photo test: content is deduplicated within the same event and device. [P1, P2]


## 03 / Start the server on macOS

LOCAL TEST / TERMINAL WINDOW 1

Open Terminal. The commands below assume the folder layout in section 02. Paste one block at a time. Keep this terminal open while the server is running.

```sh
cd "$HOME/LensDrop/cloud"
node --version
npm --version
npm install
node -e "console.log(require('sharp/package.json').version)"
npm test
```

Expected: dependencies install; sharp reports 0.34.1; the recorded cloud suite has 12 passing tests. Stop here if installation or tests fail. Do not bypass dependency errors with --force or run npm audit fix --force.

### Create a private administrator key once

```sh
mkdir -p "$HOME/LensDrop/private" "$HOME/LensDrop/server-data"
chmod 700 "$HOME/LensDrop/private"
if [ ! -s "$HOME/LensDrop/private/admin-key.txt" ]; then
  node -p "require('node:crypto').randomBytes(32).toString('hex')" \
    > "$HOME/LensDrop/private/admin-key.txt"
fi
chmod 600 "$HOME/LensDrop/private/admin-key.txt"
export LENSDROP_ADMIN_KEY="$(cat "$HOME/LensDrop/private/admin-key.txt")"
export LENSDROP_DATA_DIR="$HOME/LensDrop/server-data"
export HOST=127.0.0.1
export PORT=3000
npm start
```

Expected terminal message: LensDrop 0.2.0-alpha.1 listening on 127.0.0.1:3000. On Node 22, a node:sqlite experimental warning can appear; it is not itself a failed startup.

### Open the console

On this same computer, open http://127.0.0.1:3000/health and confirm ok is true. Then open http://127.0.0.1:3000/admin. In a second Terminal window, run the following command to copy your private key for the Administrator key field:

```sh
pbcopy < "$HOME/LensDrop/private/admin-key.txt"
```

> Do not share admin-key.txt or include it in screenshots or support ZIPs. For a later restart, rerun the export lines and npm start with the same data folder and saved key; do not create a different server-data folder. A sample.env file is not loaded automatically. [P1]


## 04 / Start the server on Windows

LOCAL TEST / POWERSHELL WINDOW 1

Open PowerShell, not Command Prompt. The commands use npm.cmd to avoid the common PowerShell npm.ps1 execution-policy issue. They do not require disabling system security policy.

```powershell
Set-Location "$HOME\LensDrop\cloud"
node --version
npm.cmd --version
npm.cmd install
node -e "console.log(require('sharp/package.json').version)"
npm.cmd test
```

Expected: sharp reports 0.34.1 and the cloud suite passes. The crash tests were rerun on Linux, not Windows; a Windows-specific failure needs investigation rather than assuming the platform is qualified.

### Create and save the key once, then start

```powershell
$Private = Join-Path $HOME 'LensDrop\private'
$Data = Join-Path $HOME 'LensDrop\server-data'
New-Item -ItemType Directory -Force $Private, $Data | Out-Null
$KeyFile = Join-Path $Private 'admin-key.txt'
if (!(Test-Path $KeyFile)) {
  $Key = node -p "require('node:crypto').randomBytes(32).toString('hex')"
  Set-Content -Path $KeyFile -Value $Key -Encoding ascii
}
$env:LENSDROP_ADMIN_KEY = (Get-Content $KeyFile -Raw).Trim()
$env:LENSDROP_DATA_DIR = $Data
$env:HOST = '127.0.0.1'
$env:PORT = '3000'
$env:LENSDROP_ADMIN_KEY | Set-Clipboard
npm.cmd start
```

Open http://127.0.0.1:3000/health on the same PC, then http://127.0.0.1:3000/admin. Paste the copied key. Keep the server window open; use a second PowerShell window for the uploader.

> The saved key is plaintext. Use a private Windows user account, restrict the private folder through Windows file permissions, and keep it out of shared/synced folders. For cloud operation, use the hosting secret store instead.

After closing PowerShell, environment variables are lost. To restart, return to cloud, re-read the same key file, set the same data path, HOST and PORT, and run npm.cmd start. Do not regenerate credentials or delete server.lock as a routine fix. [P1]


## 05 / Create an event and device credential

ADMINISTRATOR / ACTUAL ALPHA BUTTONS

1. Open /admin. Paste your Administrator key and click Connect. The key stays in the current page and is not saved in browser storage.
2. Under Create an event, enter a clear test name, such as LensDrop desk test. Leave Publication policy set to Review before publishing. Click Create event.
3. Under Event workspace, confirm the correct event is selected. Open guest gallery may show an empty waiting page; that is normal.
4. Enter a distinct Photographer / device label, such as DESKTOP-TEST-A. Click Create device credential.
5. Securely save the displayed server, event_id, device_id and token. Give the uploader only the server address and its device token, never the administrator key.
6. Create a different device credential for each photographer/phone. Multiple photographers can select the same event without sharing a credential.

| Value | Purpose | Who receives it |
| --- | --- | --- |
| Administrator key | Create events/devices, publish/hide, revoke, retrieve originals. | Administrator only. |
| Device token | Upload into one event under one device identity. | Only that uploader/phone. |
| Gallery URL | View and download Live delivery images. | Approved viewers; possession grants access. |
| Event/device IDs | Identify the event and recover the same uploader binding. | Retain with the private event record. |

> The plaintext device token is not recoverable from the server later. A lost token must be rotated for the same device ID when pending work exists. Creating a replacement device ID is not equivalent. See section 18.

A gallery link exists immediately, but automatic guest QR rendering and photographer pairing QR are not implemented. For a deployed HTTPS gallery, an external QR generator can encode that gallery URL only. Never encode a device token or administrator key in a guest QR.

Choose policy deliberately: Publish after verification makes each completed photograph Live automatically. This alpha has no event-policy editing screen/API after creation; use a separate test event for the other policy. The local gallery URL is not accessible to clients outside this computer. [P1, P3]


## 06 / Start the desktop uploader

LOCAL TEST / SECOND TERMINAL WINDOW

The desktop agent watches a folder; it does not connect to a camera. It scans existing eligible JPEGs and new ones in that folder and its subfolders. Start with an empty incoming-test folder, then copy three different test JPEGs into it.

### macOS

```sh
mkdir -p "$HOME/LensDrop/incoming-test" "$HOME/LensDrop/queue-test"
cd "$HOME/LensDrop/clients"
python3 desktop/lensdrop_agent.py \
  --server http://127.0.0.1:3000 \
  --folder "$HOME/LensDrop/incoming-test" \
  --state "$HOME/LensDrop/queue-test" \
  --allow-http-local
```

### Windows PowerShell

```powershell
$Watch = Join-Path $HOME 'LensDrop\incoming-test'
$Queue = Join-Path $HOME 'LensDrop\queue-test'
New-Item -ItemType Directory -Force $Watch, $Queue | Out-Null
Set-Location "$HOME\LensDrop\clients"
py -3 desktop/lensdrop_agent.py `
  --server http://127.0.0.1:3000 `
  --folder "$Watch" `
  --state "$Queue" `
  --allow-http-local
```

At the hidden prompt, paste the device token from section 05 and press Enter. No characters may appear while you paste; that is expected. Do not paste the administrator key. PowerShell continuation backticks must be the final character on each line; the HTML guide is preferable for copying commands.

> Use these explicit commands for local HTTP. The bundled START_WINDOWS.cmd and START_MAC_LINUX.sh launchers request HTTPS and do not add --allow-http-local. They are launchers, not installers.

For a deployed HTTPS server, replace the server address with its origin and omit --allow-http-local. Use a separate state folder and event/device setup for that new origin. The queue binds to server origin + event + device; do not change that binding mid-recovery. [P2]


## 07 / Confirm the first upload and recovery

LOCAL TEST / PASS-FAIL CHECK

1. Copy three distinct .JPG or .JPEG files into incoming-test. Wait for stable file detection and processing; the default scan period is five seconds.
2. In the console, click Refresh status. Confirm three verified photographs and three rows in Review. The client periodically prints a JSON count such as {"uploaded": 3}.
3. Open guest gallery. It should be empty because Review does not publish. Click live on one photo, then reload the gallery. Confirm one image and Download delivery photo.
4. Click hidden on that photograph. Reload the gallery and confirm it disappears. A previously downloaded copy cannot be recalled.
5. Press Ctrl+C in the uploader terminal. Restart the uploader with the identical command, queue path and device token. The three files must not appear as three new duplicates.
6. Add a fourth distinct test JPEG. Stop the server, let the agent show retry behavior, then restart the server with the same key and data folder. Confirm the fourth photo eventually completes.

| Observed state | Meaning |
| --- | --- |
| queued | Discovered and waiting for its turn. |
| uploading | The client is processing/transferring a job. |
| retry | Work retained; another attempt follows after delay or recovery. |
| blocked | A non-transient problem needs attention, then an explicit retry. |
| uploaded | The receipt was verified. Publication is a separate decision. |

> A zero queue is not a camera-card audit. Non-JPEG, oversized, unreadable, incomplete or undiscovered files can be absent from the queue. Reconcile intended filenames and counts independently.

For a larger check, use 20 distinct non-sensitive JPEGs, test stop/resume again, and check Review/Live/Hidden. Stop if counts or images disagree. This proves the computer path only, not the smartphone path. [P1-P3]


## 08 / Choose hosting for phone testing

DEPLOYMENT / DO NOT SKIP THE STORAGE CHECK

A phone needs a reachable HTTPS server with a trusted certificate. 127.0.0.1 refers to the phone itself, not your computer. The Android source has no local HTTP exception. Keep local desktop tests local until HTTPS deployment is ready.

### Hostinger is an option, not a certified deployment

Hostinger currently offers managed Node apps on Business and Cloud plans. Its documentation describes deployment-managed build directories that are replaced, so LensDrop data must live outside those directories. The provider currently documents one long-running Node process per site and a 120-second request limit. These facts do not prove persistent SQLite and sharp compatibility for this application. [S3-S5]

| Hostinger field/check | Required action for this alpha |
| --- | --- |
| App type / runtime | Server-side Node, not a static HTML site. Select Node 24.x. |
| Project root | Folder containing package.json, src and public: cloud. |
| Start behavior | Must execute node src/server.cjs. A loader that only imports this file will not start it. Provider adaptation may be required. |
| Environment | Set the admin key, absolute persistent data path and provider-required HOST/PORT. Do not assume a copied sample.env is loaded. |
| Persistent storage | Confirm a writable, private, local-filesystem path outside public_html and hbuilds. Never guess a path. |
| Native package / lifecycle | Verify sharp installs; restart and redeploy without losing photos; test single-process locking and backup restoration. |

> Do not upload the original reference HTML as the application, or assume placing the cloud ZIP in public_html deploys a backend. No LensDrop hosting account was configured or tested in this release.

A small dedicated Linux VM with persistent storage is an alternative for a controlled pilot because its process and disk behavior can be configured explicitly. Sections 09-10 provide a technician reference, not a one-click installer. Do not buy or migrate hosting before the required storage and process behavior are confirmed. [P1]


## 09 / Linux pilot server: application service

TECHNICIAN / REFERENCE CONFIGURATION, NOT DEPLOYED

Prerequisites: a Linux server you administer, SSH access, persistent local disk, Node 24.x with npm installed from the official distribution, and a reserved domain/subdomain. Confirm the Node binary path with command -v node. The example assumes /usr/bin/node. This section is not for managed hPanel hosting. [S1]

1. Copy the extracted cloud folder to /opt/lensdrop/cloud. Install dependencies there with npm install, run npm test and npm audit, and retain the resulting package-lock.json. Do not copy node_modules from a Mac/Windows installation.
2. Create an unprivileged service account and private data directory with the commands below. Keep backups outside the live data directory.
3. Create /etc/lensdrop.env with a unique random administrator key and the settings shown. Use sudo chmod 600 /etc/lensdrop.env. Never commit this file to a repository.

```sh
sudo useradd --system --home-dir /var/lib/lensdrop \
  --shell /usr/sbin/nologin lensdrop
sudo install -d -o lensdrop -g lensdrop -m 700 /var/lib/lensdrop
```

```text
# /etc/lensdrop.env -- replace the key before starting
LENSDROP_ADMIN_KEY=REPLACE_WITH_64_RANDOM_HEX_CHARACTERS
LENSDROP_DATA_DIR=/var/lib/lensdrop
HOST=127.0.0.1
PORT=3000
LENSDROP_EVENT_QUOTA_BYTES=10737418240
LENSDROP_FREE_RESERVE_BYTES=536870912
```

Generate a key locally with the same crypto command in section 03. Give the service user read/execute access to application files and installed dependencies, but do not make the media directory public.

```ini
# /etc/systemd/system/lensdrop.service
[Unit]
Description=LensDrop evaluation server
After=network.target
[Service]
Type=simple
User=lensdrop
Group=lensdrop
WorkingDirectory=/opt/lensdrop/cloud
EnvironmentFile=/etc/lensdrop.env
ExecStart=/usr/bin/node /opt/lensdrop/cloud/src/server.cjs
Restart=on-failure
RestartSec=5
TimeoutStopSec=120
UMask=0077
NoNewPrivileges=true
[Install]
WantedBy=multi-user.target
```

Enable with sudo systemctl daemon-reload and sudo systemctl enable --now lensdrop. Check sudo systemctl status lensdrop and curl http://127.0.0.1:3000/health. Exactly one process may write this data directory. [P1]


## 10 / Add HTTPS and validate the host

TECHNICIAN / CONTROLLED PILOT

Install Caddy using its official OS instructions [S6]. Point a real domain to this server and allow the required public HTTP/HTTPS ports through the firewall. Keep Node port 3000 private. The example below assumes Caddy is directly internet-facing, not behind another proxy.

Replace photos.your-domain.example and the documentation-only management IP 203.0.113.10/32. The latter must be your administrator network/VPN exit address. Leaving it unchanged blocks your administration. A changing address requires a maintained allowlist or a designed VPN access path.

```caddy
# /etc/caddy/Caddyfile
photos.your-domain.example {
  @blockedAdmin {
    path / /admin /app.js /v1/events /v1/events/* /v1/devices/* /v1/photos/*
    not remote_ip 203.0.113.10/32
  }
  respond @blockedAdmin 403
  reverse_proxy 127.0.0.1:3000
}
```

Caddy can obtain HTTPS certificates for a correctly configured public domain and reverse-proxy to Node [S7]. Validate and reload the configuration, then inspect the service logs. This is a reference setup, not an audited security configuration.

```sh
sudo caddy validate --config /etc/caddy/Caddyfile
sudo systemctl reload caddy
sudo journalctl -u caddy -n 50 --no-pager
sudo journalctl -u lensdrop -n 50 --no-pager
```

### Deployment acceptance checks

1. From a phone using mobile data, open the HTTPS /health address. There must be no certificate warning. Do not disable TLS validation to make pairing work.
2. From the allowed admin network, create a new test event/device. Upload three photos over HTTPS from the desktop agent. Confirm delivery links resolve on a different network.
3. Restart the server and repeat access. Then perform a controlled deployment replacement and confirm the same events/photos remain. Do not run old and new instances concurrently.
4. Run a stopped-server backup and restore drill, then an interruption/retry test. Keep all original test photos until count and checksum reconciliation passes.

> On a CDN, tunnel, load balancer or managed host, client-IP handling and routing may differ. Recheck the allowlist, timeouts and single-writer behavior; do not copy this proxy configuration blindly. Guest galleries are public-by-link, not PIN-protected private albums. [P1, P3]


## 11 / Build the Android pilot

DEVELOPER ONLY / NO APK INCLUDED

The user does not yet have a phone installer. The next engineering deliverable should be a buildable, signed test APK with a recorded build result and this guide. The commands below are a build procedure, not evidence that the supplied source has compiled.

| Build input | Value in the supplied project |
| --- | --- |
| Project folder | clients/android |
| App ID | app.lensdrop.transfer |
| Android Gradle Plugin | 8.9.1 |
| Gradle / JDK | 8.11.1 / JDK 17 |
| SDK | compileSdk 35, targetSdk 35, minSdk 29 |
| SDK Build Tools | Install 35.0.0 with SDK Manager. |

Install Android Studio and the listed SDK tools. Set the Gradle JDK to 17. The AGP 8.9 compatibility table specifies Gradle 8.11.1 and JDK 17 [S8]. Install the exact Gradle 8.11.1 distribution and place its bin folder on PATH; the source package contains no Gradle wrapper. Accept SDK licenses through the normal SDK installation workflow.

```sh
# Run inside clients/android, using Gradle 8.11.1 on PATH
java -version
gradle --version
gradle wrapper --gradle-version 8.11.1 --distribution-type bin
./gradlew --version
./gradlew :app:lintDebug :app:testDebugUnitTest :app:assembleDebug
```

On Windows PowerShell, use .\gradlew.bat instead of ./gradlew. Open the android project in Android Studio to configure the SDK path; do not invent a different app project. A task marked NO-SOURCE is not a passed native test suite: no Android instrumentation test coverage is supplied.

After a successful build, the expected APK path is app/build/outputs/apk/debug/app-debug.apk. Connect an authorized test phone and use Android Studio Run, or adb install -r followed by that path. A physical phone is required for camera Wi-Fi/mobile-data testing [S9].

> Retain application ID and signing identity for updates. A signature mismatch may tempt an uninstall, which destroys private queue state. Drain/reconcile pending work before any destructive reinstall. Do not distribute a debug build as a production app.

Record build logs, APK SHA-256, source version, phone model/OS and signing method. No iOS build exists. [P2, P4]


## 12 / Prove the Panasonic shared-photo path

CAMERA TEST / CAN START BEFORE AN APK EXISTS

Use one of your Panasonic cameras and one phone first. Do not assume that success on one combination certifies all S5IIX, S9, S1II or S1IIE combinations. Record camera firmware, LUMIX Lab version, phone model and OS before testing.

1. Install the official LUMIX Lab app and consult the support/compatibility information for your exact body. Record transfer size/format settings; select a JPEG path for LensDrop.
2. Pair using Panasonic's documented Bluetooth/Wi-Fi flow. Enable Auto Transfer. For the S5IIX manual, this is in Bluetooth > Auto Transfer > ON; accept the Wi-Fi connection and review image-send settings. Menu locations can differ by body/firmware. [S10]
3. Take three new JPEG photographs. Confirm that each reaches LUMIX Lab automatically. Do not tap a manual save/export button during this test.
4. Open the phone's normal photo library/file browser. Look for the actual JPEGs in shared storage and record their folder, filename, size and dimensions. A thumbnail in LUMIX Lab alone is insufficient.
5. When an Android test build is available, use Find shared photo folders to verify that LensDrop can discover the exact shared folder and its new images. The definitive test is that LensDrop can read the shared files with permission. [S11]

| Result | Decision |
| --- | --- |
| Files appear automatically in shared media | Proceed to the phone upload test. Retest while camera Wi-Fi and mobile internet operate together. |
| Files appear only after manual Save/Export | This is not yet unattended transfer. Find a supported automatic export setting or plan another camera adapter. |
| Files remain private to the official app | A folder watcher cannot bypass the app sandbox. Stop this integration path until a supported bridge is available. |
| Official app itself misses images | Resolve/reconcile camera-to-phone transfer first; LensDrop cannot upload files it never receives. |

> Panasonic describes My Work separately from the device library [S12]. Do not invent a folder such as DCIM/LUMIX and assume it exists. Select the actual discovered relative path.

Keep camera-card originals. Do not update camera firmware in the middle of an event. LensDrop preserves exactly the JPEG it receives; that may be resized by the official app, not the camera's full-resolution original.


## 13 / Pair and operate the Android test build

PHONE USER / ONLY AFTER SECTION 11 PASSES

1. Receive the tested APK through your controlled installation process. Open LensDrop Transfer. Grant the required shared-image access and notification permission. Selected-photos-only access will not provide unrestricted future discovery for this implementation.
2. Enter HTTPS server origin only, such as https://photos.your-domain.example. Do not append /admin or a guest-gallery path. Paste the event-specific Device token, not the administrator key.
3. After receiving one shared test JPEG through the official app, tap Find shared photo folders. Select the exact camera-app folder. Its relative path must end in /. Avoid a general mixed personal album.
4. Choose Allow mobile data for cloud uploads according to your data plan. Tap Pair and start event session while LensDrop is visible. Confirm the displayed event name.
5. Return to LUMIX Lab and take NEW photos. Confirm the phone queue progresses and the console shows verified uploads. A pre-pairing sample may be intentionally excluded.
6. To stop intentionally, use Pause transfer. To recover after interruption, reopen LensDrop and tap Resume saved event session. After fixing a permanent issue, use Retry blocked files and then Resume.

### Important discovery boundaries

A new pairing discovers images added since that session began, with a ten-second overlap. It does not backfill the entire historic camera folder. Resuming the same identity preserves the boundary. Switching event/device while another queue has pending work is refused. These differ from the desktop agent, which includes existing files. [P4]

### What the phone controls mean

Editing a field or ticking mobile data does not itself change the saved session. To save changed credentials/settings, Pause, enter the correct values and use Pair and start event session. Preserve the same server/event/device when recovering. Resume uses the previously saved configuration.

> The visible notification does not guarantee unlimited background work. Android limits background dataSync execution for apps targeting API 35+; reopening may be required after timeout or Force stop. Periodic recovery is scheduled by the OS, not guaranteed every 15 minutes. [S13]

Use Export redacted diagnostics to save a JSON summary. It is a lightweight status/count report, not a complete per-photo crash log. No direct USB/PTP, FTP receiver or camera shutter control is implemented. [P4]


## 14 / Photographer operating checklist

CONTROLLED PILOT / EACH PHOTOGRAPHER

### Before shooting

1. Confirm the event name and assigned device label. Use your own device credential and the intended shared folder. Do not share another photographer's queue/token.
2. Confirm space on the camera card AND phone, sufficient power, mobile-data permission/budget and a usable cloud connection. Keep the official app connected.
3. Take three identifiable test photos and confirm all three stages: camera card, phone JPEGs, cloud verified count. Have the administrator check the agreed publication behavior.
4. Record the starting photo/filename range and app versions. Keep a recovery contact route outside the photo app; no participant assignments are assumed by this guide.

### During shooting

1. Monitor whether the official app is receiving new images separately from whether LensDrop is uploading them. A cloud retry cannot repair an upstream camera download.
2. Check the queue, blocked count, free storage and phone heat periodically. Do not run unnecessary AI/editing or large-image browsing alongside transfer during the pilot.
3. When internet fails, retain sources and queue; restore connectivity and resume the same session. A growing backlog can fill the official app's folder even when LensDrop staging is bounded.
4. For faults, note the time and last successful image, pause only the affected stage if needed, and export diagnostics. Never clear app storage as the first action.

### At the end

1. Stop shooting and allow the official camera app to finish receiving. Reconcile card filenames/counts with phone JPEGs before assuming the acquisition stage is complete.
2. Wait for queued/retry/uploading work to drain and resolve blocked files. Ask the administrator to verify expected per-device totals; console incomplete counts do not include every phone-local pending file.
3. Keep the camera card and phone originals until the event is independently backed up and reconciled. Pause LensDrop only after these checks, or retain a documented pending queue for later recovery.

> This is a pilot checklist, not permission to depend on the alpha at a paid event. Automatic upload is a convenience layer, not the only copy of the photographs. [P1-P4]


## 15 / Administrator and guest use

EVENT OPERATIONS

### Administrator

Select the correct event, then click Refresh status to see the latest server-side results. This console does not automatically refresh its status display. Verified counts are committed cloud photos. Incomplete counts are server upload sessions, not a complete inventory of unstarted phone jobs.

| Action / state | Effect |
| --- | --- |
| live | Permits guest delivery/thumbnail access. |
| review | Keeps the uploaded photo off the public gallery. |
| hidden | Removes public access through the current asset routes. |
| Revoke device | Stops new requests with that token. Does not erase existing photos or cancel every already-in-flight operation instantly. |
| Original file | Available through the authenticated administrator API, not a console download button. |

The console lists only the latest 200 photos and has no full administrative pagination, thumbnail review wall or bulk publish tool. Do not run a large Review-first production workflow assuming older items can be reviewed in this interface. Event policy changes, device-token rotation, deletion/retention controls and batch original export are not console features. [P1, P3]

### Guest

1. Open the event's HTTPS gallery link or a QR that encodes it. No administrator key or device token is needed.
2. The latest page refreshes every ten seconds while visible. Waiting for published photographs means no Live items are currently listed.
3. Tap Download delivery photo beneath the required image. This is a resized delivery copy, not the administrator-only exact uploaded file.
4. Use Load older photographs for older pages. Older-page browsing pauses automatic refresh; reload the page to return to the latest view.

> Anyone holding the gallery link can access its Live photos. There is no guest PIN, account login, individual guest grouping, download-all ZIP or controlled link-expiry feature in this core. Treat it as public-by-link sharing.

Review and Hidden prevent new access through the protected public routes, but cannot retract files guests already saved. Do not expose private server originals through a static web folder. [P1, P3]


## 16 / Diagnose where a photograph is missing

RECOVERY / START HERE

| Check in this order | Interpretation | Safe next action |
| --- | --- | --- |
| 1. Is it on the camera card? | The camera is the primary acquisition record. | Identify filename/time and retain the card. |
| 2. Is it in the official app? | No: camera-to-phone transfer is incomplete. | Reconnect/retry within that app; do not erase LensDrop state. |
| 3. Is the JPEG in shared phone storage? | Private-app-only files are not accessible to the watcher. | Check supported save destination/export behavior. |
| 4. Is it in LensDrop's intended scope? | Wrong folder, pre-pairing date, unsupported format or permissions may exclude it. | Check selected folder, JPEG limits and session boundary. |
| 5. Is it queued/retrying/blocked? | The cloud stage has not committed it yet. | Use the error/status and sections 17-18. |
| 6. Is it verified but not visible? | Review/Hidden status or a stale guest view may explain it. | Check publication state; reload latest guest page. |

### When the app or process stops

First preserve the source folder/card. Reopen LensDrop and resume its saved event. On desktop, restart with the same server, state folder and device token. The client reconciles the server offset rather than restarting every photograph from zero. A Force stop requires the Android app to be opened again.

### When the official app stops

Restore camera-to-phone transfer separately. LensDrop only discovers completed accessible files; it cannot instruct another app to retrieve missing camera images. Record both sides of the handoff so a healthy uploader does not hide missing acquisitions.

> Do not use a phone reboot, app reinstall, fresh event/device or empty queue as a substitute for identifying the failed stage. A reboot may be necessary for an actual device fault, but it is not the normal LensDrop recovery procedure.

Files over 64 MiB, RAW/HEIF/video, unreadable files and some incomplete JPEGs may be skipped before a queue record is created. The server also limits decoded images to 80 million pixels. A queue counter alone cannot account for these exclusions. [P1-P4]


## 17 / Troubleshooting reference

RECOVERY / RETAIN DATA

| Symptom | Likely boundary / safe action |
| --- | --- |
| node / npm / py not found | Install the required runtime and reopen the terminal. Verify versions. Windows: use npm.cmd and py -3. |
| EADDRINUSE / server already running | Port or data directory is in use. Stop the known duplicate process. Never remove server.lock while a writer may be active. |
| sharp install/load failure | Check OS/CPU compatibility, network and native optional packages. Run installation on the actual server, not on another platform. [S14] |
| admin_auth_required / key too short | Use the saved server administrator key of at least 32 characters; restart with matching environment variables. |
| 401 / invalid or revoked device token | Check that a device token was used. Rotate for the SAME device ID if needed; then retry blocked jobs. |
| 429 / server_busy / 503 | Retain work and allow backoff. Do not add more uploader processes to force throughput. |
| 507 / cloud_storage_low / quota | Administrator checks disk and event reservation quota. Increase only with capacity planning; do not delete live database/chunk files. |
| TLS / HTTPS error | Correct DNS, certificate chain, origin and time settings. Do not disable certificate checks. |
| Remote HTTP rejected | Use HTTPS. --allow-http-local is only for the same-computer loopback test. |
| Wrong event/device scope | Keep the old queue and original identity for recovery. Use a separate state directory for a new desktop event/device/origin. |
| source_missing / source_changed | Restore the exact source if available. Preserve blocked record; do not overwrite a pending file with a different image. |
| No phone photos discovered | Check shared-file visibility, image permissions, exact folder and pairing-time boundary. A manual historical import UI is absent. |
| Phone pauses on resource guard | Check charge, free storage and heat. Do not lower guards simply to force a backlog through. |
| Guest gallery empty | Refresh console; check event selection and Review/Hidden status. Then reload the guest page. |
| Photo not among latest 200 | Current console limit. This is not proof of deletion. Use controlled reconciliation; full admin pagination is unfinished. |

> Before retrying blocked work, fix its cause. Desktop: stop, rerun the identical command with --retry-blocked. Android: Retry blocked files, then Resume saved event session. Neither action should delete photographs. [P1-P4]


## 18 / Recover a lost or revoked device token

ADMINISTRATOR / ADVANCED

Use the existing device ID. Token rotation creates a new secret for that same identity and clears revocation. Stop/pause that uploader before rotating, retain its queue and record the old event/device IDs. The console has no rotation button; use the administrator API. [P3]

### PowerShell example from an authorized admin computer

```powershell
$Server = Read-Host 'HTTPS server origin'
$Device = Read-Host 'Existing device ID'
$Secure = Read-Host 'Administrator key' -AsSecureString
$Credential = New-Object System.Management.Automation.PSCredential('admin', $Secure)
$Admin = $Credential.GetNetworkCredential().Password
$Headers = @{ Authorization = "Bearer $Admin" }
$Uri = "$Server/v1/devices/$Device/rotate"
$Result = Invoke-RestMethod -Method Post -Uri $Uri `
  -Headers $Headers -ContentType 'application/json' -Body '{}'
$Result | ConvertTo-Json
$Admin = $null
$Headers = $null
$Credential = $null
```

The response contains a sensitive new token. Copy it into the private event record; do not put the output into support logs. Clearing variables is cleanup, not a guarantee that secrets are erased from memory or terminal history.

### Update the affected uploader

1. Desktop: rerun the same server/folder/state command. Enter the new token and add --retry-blocked. Do not create a new queue folder to work around binding.
2. Android: Pause transfer. Keep the same HTTPS origin and selected folder, enter the rotated token, then Pair and start event session. Use Retry blocked files and Resume as needed.
3. Confirm the same device ID/event identity and increasing verified count. Check for pending/blocked files before declaring recovery complete.

> The rotation workflow was checked against source/API definitions, but no real phone token-rotation test is claimed. Run it on non-sensitive pilot photos before relying on it at an event.

A compromised administrator key is a different incident: replace it in the server secret configuration, restart safely, review affected device credentials and access, and preserve audit evidence. Resetting a secret does not recall photos that were already downloaded.


## 19 / Backup, restore and upgrades

ADMINISTRATOR / DATA SAFETY

### Safe evaluation backup

1. Pause/stop uploaders and preserve their queues and source photographs. Stop the cloud server cleanly: Ctrl+C locally, or sudo systemctl stop lensdrop on the reference VM. Wait for it to exit.
2. Copy the ENTIRE server-data directory into a new dated backup folder. Include lensdrop.sqlite, any WAL files, originals, delivery, thumbs and chunks. Do not copy only the main SQLite file while the server is live.
3. Keep a second protected backup on an independent storage device/service. A second folder on the same disk is only a restore-practice copy, not protection against disk loss.
4. Save the application version, dependency lockfile, non-secret configuration and private credential record securely. Keep any queue backup and watched sources separately protected.

### Restore drill

1. Keep uploaders stopped. Restore the full server snapshot to a separate test data directory. Start one instance pointed at that directory, with the intended key and no competing writer.
2. Confirm event/device IDs, photo totals, Live/Review/Hidden behavior and sampled original-file checksums. Test with temporary non-sensitive data first.
3. Before switching a live service to an older backup, reconcile photographs accepted after that snapshot. A client that already marked them uploaded may NOT automatically resend them. Recovery must account for those missing receipts/files.
4. Only resume real uploaders after the restored state and outstanding work have been reconciled. Never run two Node writers on the same data directory.

### Upgrade safely

Drain or intentionally preserve pending work, back up, replace only application files, install with the retained lockfile where available, then restart against the same data directory. Validate one upload and gallery access before releasing more devices. The original alpha has no lockfile; generate and audit one on a clean target installation first.

> A verified upload receipt confirms one instance's file/database commit. It does not mean a separate replicated backup exists. No automatic retention/deletion schedule is implemented; do not promise a seven-day expiry or permanent retention. [P1-P3]


## 20 / Storage and resource planning

ADMINISTRATOR / CAPACITY

| Limit / default | Current value | Operational meaning |
| --- | --- | --- |
| Input | JPEG; <=64 MiB; <=80M decoded pixels | RAW/HEIF/video are not supported. |
| Client transfer | One file; 1 MiB chunks | More app instances are not a safe speed setting. |
| Staged duplicates | 256 MiB target | Does NOT cap the official app's download folder. |
| Free-space guard | Desktop 512 MiB; Android 1 GiB | Reserve before new staging; not an event-size estimate. |
| Android resource pause | <=15% unplugged; thermal/temperature guards | Provisional defaults, not measured battery guarantees. |
| Server event reservation | 10 GiB by default | Counts declared upload sessions, including unfinished ones. |
| Cloud finalization | One image finalizer at a time | Delivery generation can add backlog under burst load. |

### Calculate from your real JPEGs

Source storage (GB, decimal) = photo count x average JPEG size (MB) / 1,000. At an assumed 6 MB each, 1,000 images are about 6 GB and 10,000 about 60 GB. Add room for original copies, pending chunks, temporary assembly, delivery images, databases and backups. These examples are arithmetic, not a measured LensDrop workload.

Required sustained uplink (Mbps) is approximately 8 x JPEG size (MB) x photographs per second. One 6 MB photo every three seconds is 16 Mbps before overhead. When the uplink is slower, backlog grows even with perfect retry logic.

### Before a 10,000-photo test

The default 10 GiB event quota is insufficient for a 60 GB source set. Size disk and backup space, then deliberately set LENSDROP_EVENT_QUOTA_BYTES. For example, 107374182400 is a 100 GiB reservation ceiling, not a guarantee that a 100 GiB disk is enough. Unfinished sessions consume quota; automatic abandoned-session cleanup is unfinished.

> One 1 MiB chunk on a 0.1 Mbps link takes roughly 84 seconds before overhead. The alpha has fixed chunk sizes and timeouts; weak links can repeatedly fail. Adaptive negotiation remains development work. Do not interpret a retry loop as reliable progress. [P1-P4]


## 21 / Pilot acceptance and next development

RELEASE GATES / NOT COMPLETED CERTIFICATION

| Gate | Test | Pass condition |
| --- | --- | --- |
| A. Desktop baseline | 3 then 20 unique JPEGs; Review/Live/Hidden; restart. | All intended files accounted for; no duplicate completion; hidden routes protected. |
| B. Panasonic bridge | 20 new camera images via official app, without manual export. | All intended JPEGs appear in accessible shared media. |
| C. Android build | Compile, lint, install, permission handling and startup. | APK built; errors resolved; no unexplained crash or missing permissions. |
| D. Phone-to-cloud | HTTPS + camera Wi-Fi + internet simultaneously. | 20 new images committed; no repeated manual switching/export. |
| E. Recovery | Network loss, app interruption, Force stop/reopen, server restart. | Pending work retained and reconciled; no source deletion or duplicate completion. |
| F. Multi-photographer | At least two tested kits; unique credentials; same event. | Per-device provenance and totals correct; same filenames do not overwrite. |
| G. Load and soak | 100, then 1,000-photo bursts; sized 10,000 backlog; eight-hour operation. | Counts/hashes reconcile; measured memory, temperature, storage and battery remain acceptable. |
| H. Operations | Backup/restore, deployment replacement, credential recovery. | Photos and pending sessions preserved; recovery procedure is repeatable. |

Record actual outcomes and observed delays, not just a green screen. Agree acceptable upload latency, battery consumption and heat behavior from measurements before a field release; no performance threshold has been certified in this alpha.

### Next software milestone

Produce an installable Android pilot with reproducible build/signing, a verified shared-media path and a stable HTTPS test endpoint. Prioritize per-file diagnostics, honest backlog/blocked reporting, background-execution strategy and full admin pagination before adding AI, payments or direct-camera features.

> The local tests do not certify a phone/camera combination. A new camera body, firmware, official-app version, phone/OS or transfer method is a new qualification entry. Production approval remains pending. [P1-P4]


## 22 / Test record and support information

PILOT RECORD / FILL IN AFTER TESTING

| Record | Value / observation |
| --- | --- |
| Test date / software / guide revision | ________________________________________ |
| Camera / firmware / JPEG setting | ________________________________________ |
| Official app / version / save behavior | ________________________________________ |
| Phone model / OS / app build hash | ________________________________________ |
| Server origin / runtime / storage type | ________________________________________ |
| Event ID / device ID (not token) | ________________________________________ |
| Camera count / shared-phone JPEG count | ________________________________________ |
| Verified cloud count / blocked / pending | ________________________________________ |
| Filename range / missing or duplicate items | ________________________________________ |
| Interruption tested / recovery action | ________________________________________ |
| Storage / battery / heat observations | ________________________________________ |
| Result / unresolved issue / next test | ________________________________________ |

### What to send for troubleshooting

Send the first relevant error, time of failure, application/runtime versions, operating system, steps to reproduce, per-stage counts and a redacted diagnostic export. On Android, Export redacted diagnostics contains summary counts/status. On desktop, retain console output; it is not a complete per-file diagnostic interface.

A developer can inspect a stopped, protected queue or collect Android Logcat for a controlled test. Queue databases can contain personal filenames, paths and metadata. Do not post them publicly or assume a database is safe because the token is absent.

> Never send administrator keys, device tokens, private key files, unredacted credential screenshots or client photographs without authorization. Filenames and source paths may also identify people or events.

Pause updates during an event. Record the exact previous build and keep a non-destructive recovery path. [P1-P4]


## 23 / What was verified for this guide

EVIDENCE / 11 NOVEMBER 2026

The guide was checked against the delivered cloud and client source rather than the older prototype mockup. Button labels, configuration names, file paths and operational limits refer to software 0.2.0-alpha.1.

| Check | Result and boundary |
| --- | --- |
| Cloud automated suite | 12 passed again on Linux. Includes protocol, authorization, publication and selected process-crash scenarios. |
| Desktop automated suite | 11 passed again with cloud integration enabled. Includes 10,000 persisted queue records, not 10,000 phone photo transfers. |
| Actual CLI walkthrough | Three unique JPEGs uploaded through the desktop command; exact originals checked by SHA-256. |
| Publication API | Review empty; Live returns delivery JPEG; Hidden excludes listing and returns 404. |
| Desktop restart | Same state reused without adding duplicate photos. |
| Stopped backup/restore | Complete local data-directory copy restarted; gallery and sampled original hashes verified. |
| Fresh npm installation | Timed out in this environment. Tests used preinstalled sharp 0.34.1; no clean-install certification. |
| Browser interaction | Navigation blocked by the environment (ERR_BLOCKED_BY_ADMINISTRATOR). No browser walkthrough or actual UI screenshots claimed. |
| Platform/field deployment | No Windows/macOS execution, external HTTPS deployment, Android build, physical camera/phone or eight-hour workload verified. |

Runtime for executed checks: Node 22.16.0, Python 3.13.5, sharp 0.34.1 on Linux. Updated test logs and walkthrough results are included in the documentation folder of both ZIPs.

> Application files are unchanged in the with-Guide archives. They are not a new software release, a compiled APK, a Windows installer or a production-ready Hostinger package.

The optional server/build procedures remain technician instructions that must be validated on the target platform. They are not a claim that those deployment actions have been carried out.


## References

[P1] Cloud package and implementation - cloud/README.md; sample.env; package.json; src/server.cjs; src/store.cjs; public/app.js; public/gallery.js; docs/DEPLOYMENT_AND_LIMITS.md

[P2] Desktop transfer package - clients/README.md; desktop/lensdrop_agent.py; START_WINDOWS.cmd; START_MAC_LINUX.sh

[P3] Cloud API contract - cloud/docs/API.md and the corresponding source routes

[P4] Android implementation - clients/android/README.md; build files; MainActivity.java; Config.java; MediaScanner.java; QueueDb.java; UploadEngine.java; EventService.java

[S1] Node.js official downloads - Select the 24.x line for this package.
https://nodejs.org/en/download/archive/v24

[S2] Python official downloads - Select a maintained version compatible with the agent.
https://www.python.org/downloads/

[S3] Hostinger: add a Node.js web app - Managed deployment and replacement of build directories.
https://www.hostinger.com/support/how-to-deploy-a-nodejs-website-in-hostinger/

[S4] Hostinger: environment variables - Provider configuration workflow.
https://www.hostinger.com/support/how-to-add-environment-variables-during-node-js-application-deployment/

[S5] Hostinger: Node.js limits - Process and request-timeout boundaries.
https://www.hostinger.com/support/hostinger-node-js-limits-for-web-and-cloud-plans/

[S6] Caddy: install - Official installation instructions by operating system.
https://caddyserver.com/docs/install

[S7] Caddy: reverse proxy - HTTPS reverse-proxy configuration reference.
https://caddyserver.com/docs/quick-starts/reverse-proxy

[S8] Android: AGP 8.9 release notes - Gradle, JDK and SDK compatibility.
https://developer.android.com/build/releases/agp-8-9-0-release-notes

[S9] Android: build and run - Build/run on an authorized development device.
https://developer.android.com/studio/run

[S10] Panasonic S5IIX: using LUMIX Lab - Bluetooth/Wi-Fi and Auto Transfer procedure for this model.
https://eww.pavc.panasonic.co.jp/dscoi/DC-S5M2X/html/DC-S5M2X_DVQP2992_eng/0187.html

[S11] Android: access shared media - MediaStore and shared-image access requirements.
https://developer.android.com/training/data-storage/shared/media

[S12] Panasonic: LUMIX Lab Gallery - Device images and My Work descriptions.
https://av.jpn.support.panasonic.com/support/global/cs/soft/lumix_lab/en/cts/a0.html

[S13] Android: foreground service timeouts - Background dataSync budget and timeout handling.
https://developer.android.com/develop/background-work/services/fgs/timeout

[S14] sharp: installation - Native package/platform installation considerations.
https://sharp.pixelplumbing.com/install/

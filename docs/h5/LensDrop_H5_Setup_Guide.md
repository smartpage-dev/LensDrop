# LensDrop H5
## Staff QR onboarding and local-first printing

Release date: 17 November 2026. Engineering alpha for controlled rehearsal, not production event approval.

**Important:** This delivery contains a working, locally tested cloud implementation plus Android and Windows application source. No APK or compiled Windows executable is included. The two manual GitHub workflows attempt those builds; they have not been executed in your account. Native build errors remain possible. Do not retire your current event workflow yet.

## 01. What is included

| Component | H5 change | Verification boundary |
|---|---|---|
| Hostinger Cloud Core | Five-minute staff invitations, explicit approval, station/device permissions, private print jobs and status | 114 local cloud tests; not deployed to your account |
| Android source | Staff QR scanner, encrypted enrollment state, station setup, JPEG preview and durable Hybrid/Local/Cloud print queue | JVM rules and syntax inspection; no SDK build, APK or phone test |
| Windows station source | Tray launcher, private local dashboard, HTTPS LAN receiver, cloud receiver, durable print ledger, simulation and native printer adapter | 24 core tests on Linux, including driver callbacks; no compiled EXE or physical print test |
| Existing H4 features | Watch-folder baseline, Group/Single QR, history, safe Undo, private/public separation | Retained; existing queue tests rerun |
| GitHub workflows | Manual Android APK and Windows portable application builds | Source/configuration checked; not run remotely |

Cloud version: `0.2.0-alpha.6-hostinger.5`, schema 5. Android version: `0.2.0-alpha.6-h5`, versionCode 5; existing photo queue schema remains 3, new print queue schema is 1. Station source: `0.1.0-alpha.h5`.

No FTP camera receiver, native camera SDK adapter, floating QR overlay, iOS client, cutter control, offline new-device enrollment or automatic station discovery is claimed. H5 adds a manually selected JPEG print flow; it does not yet auto-select the current Group QR photo for printing. The phone uses the first published station profile. The station UI edits one full-photo Contain profile.

## 02. Upgrade the working Hostinger installation

**Safest order:** first run both native build workflows in section 03. Keep the working H4 site unchanged until you are ready to rehearse H5.

1. Stop phone/desktop clients, pause any station, stop the cloud application, then back up the complete persistent data directory. Do not copy a live SQLite database alone.
2. Preserve `LENSDROP_ADMIN_KEY`, `LENSDROP_DATA_DIR`, `LENSDROP_STORAGE_ID`. Keep `LENSDROP_INITIALIZE_STORAGE=0`.
3. Deploy the root-ready H5 **Cloud ZIP**, not the complete outer source package. Keep Other / Node 22.x at least 22.13 / root `.` / output `.` / build `npm run build` / entry `server.js`.
4. Check `/health` reports H5 and `/admin` still shows the original activities, photos, personal links, branding and slideshow configuration.
5. Confirm the persistent directory contains the original database and the new private `print-signing-key.pem`. Back up that signing key with the database. Never upload it to GitHub or share it.
6. Test restart and redeploy using synthetic photos before real event data.

H5 migrates schema 4 to 5. Rollback requires the matching old application **and the complete stopped-data backup**. Do not put H4 code over a migrated H5 database. Do not regenerate storage identity or enable initialization to hide an error. A cloud signing key missing from an installation with station records is a restore problem, not permission to create a different identity.

The deployment must retain the existing single-writer/private persistent storage arrangement. No additional paid service or external database is introduced. The assistant has not changed your live website.

## 03. Put H5 into your private GitHub repository

Extract the complete H5 ZIP. Copy the contents of its project folder into the existing local LensDrop repository, preserving the repository's `.git` directory. Include hidden `.github`, `.gitignore` and `.gitattributes` files. Review the changes before committing. Never copy your live Hostinger data, Android queue, Windows station data or keys into Git.

Publish the commit to the default branch using GitHub Desktop. Keep the repository private. Retain your existing `LENSDROP_TEST_KEYSTORE_BASE64` secret: using a new signing identity can prevent installing over the existing test app. Never uninstall an app with important pending work to solve a signature error.

Two workflows are available under Actions:

| Workflow | Action | Expected successful-run download |
|---|---|---|
| LensDrop Android APK | Run workflow; select existing persistent-test signing where applicable; accept Android SDK licenses | `LensDrop-H5-APK-...` artifact containing `LensDrop-H5-debug.apk` |
| LensDrop Windows Print Station | Run workflow | `LensDrop-H5-Windows-x64-...` artifact containing an inner portable ZIP |

The Windows workflow first runs cloud and station protocol tests, then publishes a self-contained .NET 10 Windows x64 app with a Node 22.23.5 runtime, licenses, build provenance and SHA-256. Its native smoke test checks DPAPI encryption only; it does not print. The Android workflow uses Java 17, Gradle 8.11.1, AGP 8.9.1, SDK 35 and ZXing Android Embedded 4.3.0 for staff scanning. Existing QR generation remains local.

A successful workflow is a compilation checkpoint, not field validation. A failed build produces logs, not an APK/EXE. Send the first actual error and log, with secrets removed. These workflows never deploy Hostinger. Artifacts have limited retention; save validated builds privately. Windows binaries are unsigned engineering builds; verify provenance/checksums rather than treating an unexpected security prompt as permission to run an unrelated file.

## 04. Prepare Windows and the event network

After a successful Windows build, extract the artifact and then its inner `LensDrop-PrintStation-H5-win-x64.zip`. Keep the whole folder together. Do not run the executable from inside a ZIP. On the event Windows 11 x64 PC, install and test the printer's official driver, then run `LensDropStation.exe` as the normal signed-in event user.

It launches a tray icon and opens a browser-based local dashboard. Closing the browser does not stop the tray worker. Open it again from the tray. The worker is not a Windows service and does not install automatic startup. Keep the user signed in, the PC awake and Windows sleep/notifications appropriate for the event.

Private runtime data is stored under:

```
%LOCALAPPDATA%\LuceStudio\LensDropPrintStation
```

This includes the persistent ledger, image cache, station certificate/key, DPAPI-protected credentials and diagnostic log. Credentials are tied to that Windows user. Do not delete this folder, copy it to another PC as a ready-made identity, or expose `ui-session.txt` to guests. The local dashboard uses a separate per-run secret; a new worker run invalidates the old dashboard session.

Use an event router rather than an isolated guest Wi-Fi. Prefer Ethernet for Windows. The phone must have a reachable route to the station's private IPv4 subnet. A camera's private Wi-Fi may prevent simultaneous event Wi-Fi on some phones; LensDrop cannot manufacture a second connection. Test your real camera/phone combination. Hybrid can use the cloud route only when both devices have internet access.

Look up Windows' event-LAN IPv4 address with `ipconfig`. The example `192.168.1.25` is not your actual value. Use a router DHCP reservation where possible. H5 has no automatic address discovery; after a real IP change, save the new endpoint in Windows, sync, and refresh station setup on Android.

The LAN listener is HTTPS TCP **54173**. The dashboard is loopback-only HTTP TCP **54172**. If Windows Firewall blocks local pairing/printing, inspect `tools/Allow-Private-LAN.ps1` in the compiled package and run it from an elevated PowerShell. It allows the bundled runtime on Private networks/local subnets only. It does not disable the firewall or expose the printer publicly. `Remove-LAN-Rule.ps1` removes that specific rule. Do not enable broad Public-network access.

## 05. Pair the station with Hostinger

1. In cloud Admin, select the rehearsal event and open **H5 - Staff QR pairing & Print Stations**.
2. Choose **Windows Print Station**, enter `Reception Station`, then create the five-minute invitation.
3. Copy the full invitation into the Windows dashboard's **Pair with your event** field. Windows uses paste for this step; a station camera scanner is not included.
4. Claim the invitation. Compare the six-digit code displayed on Windows with web Admin. Approve the matching device in Admin.
5. Click **Check Admin approval** in Windows. Do not create a different invitation merely because the first response was lost; **Retry saved claim** reuses the durable identity.
6. Enter the real LAN IPv4 address. Load installed printers and select the intended driver. Use width `400`, height `600` for a nominal 4x6 portrait paper profile; the driver must actually offer a matching paper size.
7. Keep **Simulation only** and **Pause queue execution** checked. Save, then Sync with cloud. Confirm Admin lists the station, profile and endpoint.

Changing profile settings creates a new profile revision. Already accepted jobs retain their original printer mapping and simulation choice. H5 offers Contain only; it does not force borderless output, implement frames or turn cutter settings on. A physical test must establish actual driver margins/orientation/copies.

## 06. Pair each photographer by QR

In web Admin, choose **Photographer phone**, provide a distinct label, and optionally select the prepared station. Generate a new staff invitation for each phone. Alternatively, the already paired Windows station can generate a phone invitation; initial enrollment still needs cloud access and Admin approval.

On the compiled Android app: **Staff Setup / QR pairing -> Scan staff setup QR**. Confirm the cloud hostname, compare the six-digit code with the event Admin, obtain approval, then press **Check Admin approval / finish setup**.

This issues a separate revocable credential without putting the Admin Key on the phone. The invitation is short-lived, one enrollment only. A previously saved claim can be retried. Staff setup codes are not guest gallery codes.

Return to the main screen and select the accessible shared JPEG folder. The existing H4 new-only baseline remains: existing files are excluded, restarts resume the same session, candidates require confirmation by default, and delayed editor arrivals are not guessed into the current group. Pairing cannot grant access to another app's private folder. Existing queued work blocks unsafe event/identity replacement.

In Android **Print Station settings & test**, choose a route and press **Prepare / refresh authorized station (online)**. If printing is disabled, web Admin must assign that device to a station and allow printing. Then press **Test local station connection**. Assignment, local reachability and physical printer readiness are separate checks.

The station's certificate is pinned per connection. Do not remove validation to fix a certificate error. Initial setup needs internet; prepared local authorization lasts up to 12 hours. Before an event, refresh it while online. Offline revocation/cancellation cannot reach an unreachable station immediately. H5 does not renew local grants silently forever.

## 07. Rehearse in simulation before using paper

Uncheck **Pause queue execution** on Windows but leave **Simulation only** enabled, then Save and Sync. On Android, select **Local only**, choose one JPEG through **Select JPEG / preview / print**, check the preview, select one copy and confirm. The request is persisted before transfer. Expect `simulated` in the station and phone once status is synchronized. No paper is used.

Repeat with **Cloud only**. The phone uploads a private print image; the station downloads and verifies it. Printing does not publish that photo to the guest gallery, Group QR or slideshow.

Then use **Hybrid / Local first**. The phone tries the paired local station, falling back to the cloud only for transport/unavailability failures. It never changes the destination station or creates a new job ID during retry. Permission and certificate errors need correction, not a fallback that bypasses security.

Use different test photos for ordinary repeated tests. For deduplication engineering tests, replay the same saved job ID rather than pressing Print again: each deliberate confirmed press is a new print request. A later intentional reprint is allowed and must have its own job identity.

Keep both phone and Windows in front of you during first tests. Verify the job ID and the requested copies, not merely that a progress indicator moves. The local source print preview is downsampled, while the staged JPEG bytes/version stay fixed. It is not a proof of exact physical paper margins.

## 08. Enable one controlled physical test

Pause the station, select/test the installed printer and paper in Windows, then disable Simulation and Save. Unpause only when ready. Create a **new** one-copy request from Android. Old jobs accepted as simulations stay simulations; changing the mode does not cause them to print.

Physical jobs are marked `submitting` before calling the native printing adapter. A successful driver call becomes **submitted_unconfirmed**, not automatically printed. Inspect the paper and use **I verified the physical output** on the station to mark operator confirmation. H5 does not yet monitor spooler job IDs or automatically prove physical output.

A failure known to occur before the native print call can be retried after correction. A crash/error during or after submission becomes **outcome_unknown**, and H5 will not automatically reprint. Inspect the Windows queue and actual output before deciding. The UI cannot retract a print already sent to the driver. Pause the station to stop execution; pausing phone transfers alone does not stop already accepted jobs.

Do not use camera originals or a real guest's private photo for initial test prints. Keep the existing event print workflow available until driver compatibility and recovery tests pass.

## 09. Recovery and resource limits

| Condition | H5 behavior / action |
|---|---|
| LAN response lost, cloud route available | Same job ID, checksum, copies and station; station ledger reconciles duplicates |
| Internet lost, local path still available | Already paired/authorized local printing can continue before grant expiry; cloud photos/status wait |
| Both paths unavailable | Phone keeps its queue and staged print JPEG; no destination reassignment |
| App restart | Reopen and press Resume queued printing; no reinstall/clear-data recovery |
| Station restart during submission | Job becomes outcome_unknown; no automatic reprint |
| Local grant expires | Refresh authorized station while online; original jobs/identities stay fixed |
| Source missing/checksum mismatch/profile mismatch | Needs attention; do not substitute another image or silently change the profile |
| Driver preflight fails | Fix printer/paper mapping, then use the safe pre-submit retry |
| Finished image cache grows | Remove acknowledged finished cache in the station; durable job IDs remain |
| Station revoked | Cloud access stops; online station pauses after the rejection. Offline grants have bounded residual lifetime |

Phone print staging is bounded at 256 MiB with approximately 1 GiB free-space reserve. Print files are JPEG only, at most 32 MiB. A shared in-process transfer gate prevents photo and print workers from streaming large files simultaneously. Phone thermal/low-battery pauses preserve work. These are source-level controls, not measured RAM, heat or battery guarantees.

Station cached files are bounded at 2 GiB and unfinished jobs at 1,000. Cloud print records reserve at most 2 GiB of declared print-image bytes per event and 100 outstanding jobs per device. H5 does not provide automatic cloud print-history purging. Do not delete SQLite rows manually to bypass a quota. These conservative limits are for the pilot and need a retention design before high-volume production.

Downloaded station files are retried as a whole after interruption; phone-to-cloud and phone-to-LAN chunks resume. The native printer decodes one full image on Windows. Long-running foreground/background behavior, OEM power management and simultaneous camera Wi-Fi operation still require physical Android testing.

## 10. Admin ownership and current boundaries

Cloud Admin remains authoritative for shared event/gallery/personal-delivery/slideshow settings, staff permissions and job tracking. Windows opens that same web Admin in the browser; it does not maintain a separate event database or provide a fully embedded offline Admin editor. Native local controls own printer mapping, cache, simulation and pause. Android has basic onboarding, source and print-connectivity controls, not full Admin editing.

The first station build is one signed-in-user tray application with a loopback browser dashboard, not a Windows service, signed MSI or automatic-start installer. The workflow produces a self-contained portable folder after successful compilation. A Windows installer, station discovery, offline initial pairing and richer printer/job management are follow-up engineering work.

The phone print chooser is explicit and independent from guest-photo publication. H4 guest QR links remain cloud links: local printing during an outage does not make a guest cloud page available offline. Each QR still belongs to its original guest/group or single selected photo.

## 11. Verification and acceptance gate

Executed in this environment: 114 cloud tests; 24 LAN/cloud station-core tests (real local HTTP/HTTPS and SQLite, simulated/driver-callback output); 23 legacy desktop tests including real local cloud integration; 24 exact WatchLedger/QueueDb JVM-SQLite bridge cases; 10 existing policy assertions and 15 new print-policy assertions. Java source syntax was parsed, not compiled against Android types.

11 browser harness checks cover H5 Admin and station layout/actions. Exact UI source was executed with injected location/history/session storage and a fetch bridge to the local backend because normal navigation was blocked. Screenshots use synthetic fixtures; they are not a compiled Windows or Android screenshot, and do not validate native HTTPS/browser CSP behavior.

Before event use: successfully build both native apps; test real Android permissions and scan flow; verify local TLS reachability and camera coexistence; run local/cloud/hybrid one-copy tests; test interrupted acknowledgement and dual-route replay; reboot during safe and uncertain states; test revocation, expiry, profile changes and pause; validate actual printer output; complete a long-duration multi-photographer soak and restore rehearsal. No live Hostinger H5 deployment, fresh npm installation, native compilation or physical printer/camera/phone validation was performed here.

## 12. Official implementation references

These external sources informed platform choices; they are not evidence that LensDrop passed a hardware test.

- Android Network APIs: https://developer.android.com/reference/android/net/Network
- Android security configuration: https://developer.android.com/privacy-and-security/security-config
- Android foreground-service timeouts: https://developer.android.com/develop/background-work/services/fgs/timeout
- ZXing Android Embedded: https://github.com/journeyapps/zxing-android-embedded
- Windows print status semantics: https://learn.microsoft.com/en-us/windows/win32/printdocs/job-info-2
- .NET support policy: https://dotnet.microsoft.com/en-us/platform/support/policy
- GitHub .NET setup: https://github.com/actions/setup-dotnet
- GitHub Node setup: https://github.com/actions/setup-node

Never put Admin Keys, station private keys, signing keystores, client tokens or real event photos in a GitHub issue, screenshot or source commit.

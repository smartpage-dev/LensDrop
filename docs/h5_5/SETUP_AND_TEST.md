# LensDrop H5.5 | Private archive and staff-operated photo printing

23 November 2026. Engineering source pilot. No new APK/EXE is included and no live service has been changed.

## 01 / The confirmed business flow

The photographer or an authorized print operator initiates a photo print. A guest can view/download their own photos or present their ticket to staff; the guest does not operate a self-service printing kiosk. Looking up a ticket never prints. Handing over an existing order never prints again.

The same guest QR identifies the personal photo collection. Staff can enter the printed eight-character REF, paste the full personal QR URL or collection ID, or use a keyboard-wedge scanner. This release has no camera-scanning UI on Windows. Short-reference collisions are rejected: use the full QR or full collection ID. References are useful only inside the authorized staff desk, not an alternative public gallery password.

| Preset | Archive route | Selected photo-print route |
|---|---|---|
| Method 1: Phone archive + local-first print | Android -> Hostinger | Android -> reachable event LAN -> Windows; eligible cloud fallback to the SAME station |
| Method 2: Phone archive + cloud print | Android -> Hostinger | Authorized request and image -> Hostinger -> assigned Windows station |

The station does NOT receive every event photo and does NOT forward the event library to the cloud. Method 3 remains excluded. Staff-created orders always use the existing cloud queue, even if Android uses Method 1. The phone handles cloud archiving in both methods. No additional public incoming port or router port-forwarding is introduced.

## 02 / Versions, backups and upgrade order

| Component | Required version |
|---|---|
| Cloud | 0.2.0-alpha.8-hostinger.5.5 / schema 7 |
| Windows station | 0.1.3-alpha.h5.5 |
| Android | 0.2.0-alpha.6-h5.5 / versionCode 10 / app.lensdrop.transfer |
| Android databases | Photo queue 6; print queue 3 |
| Printer Lab | Unchanged, separate application/repository |

Update the LensDrop repository, not Printer Lab. Merge the H5.4 patch or use the full snapshot; preserve .git and the existing LENSDROP_TEST_KEYSTORE_BASE64 signing identity. Start NEW manual GitHub runs on the new commit: LensDrop Android APK and LensDrop Windows Print Station. Android still requires assembleDebug and lintDebug; do not bypass failures. Artifact filenames keep the H5 label; inspect BUILD_INFO.json for the new version.

Build both clients first. Pause intake, uploads and physical print execution; reconcile uncertain outcomes. Stop the cloud and station and back up COMPLETE data directories, including databases/WAL as a consistent snapshot, originals, incomplete files, identity markers, print-signing-key.pem, station credentials, ledger and driver snapshots. Keep phone originals and installed app/signing identity. Diagnostic exports are not full app backups.

Upgrade CLOUD first, then WINDOWS, then ANDROID. Deploy only the Hostinger Cloud ZIP via the existing Node.js Web App. Keep the existing working private storage path, Admin Key and Storage ID. LENSDROP_INITIALIZE_STORAGE stays 0. Build npm run build; entry server.js; root/output .; retain the tested Node 22 family. Verify /health and existing events/guest links before replacing clients.

The older deploy ZIP filenames retained inside the source bundle are identical H5.5 aliases, not old servers. The current manifest and this guide take precedence over historical guides. No automatic rollback is supplied. Restore matching older code AND complete older data only after reconciling all physical output since the snapshot; an old queue can otherwise repeat an already printed job. Never clear app data, reinitialize cloud storage or regenerate a signing identity as an upgrade shortcut.

## 03 / Select the two-route default in cloud Admin

Open the existing /admin, connect privately and select the rehearsal event. Open Staff Photo Desk / H5.5. Select Method 1 or Method 2 and press Save default for newly prepared phones. It is a default applied when a phone next prepares/refreshes its authorized station, not a remote rewrite of its pending print jobs. Existing queued local-only/hybrid/cloud jobs keep their stored route. H5.5 shows only the two agreed choices for NEW phone print requests.

For each intended Windows station, enable Allow local staff desk and choose a maximum of 1-5 SHEET copies per request. Existing stations default to disabled. This authorizes the signed-in local station operator to browse the event's non-hidden, non-test private photo library and order prints only for that station. It is event-level access, not just access to the photographer who last used the computer. Protect the staff computer accordingly. Web Admin retains separate full administrative authority.

The Windows local dashboard contains the same Staff Photo Desk. It proxies through the paired station credential and its per-run local dashboard secret; no master Admin Key is stored there. It requires internet. It is not a second offline event database or Method 3 media hub.

## 04 / Private archive is separate from guest selection

On Android main setup, select the authorized shared JPEG folder. For a NEW watch session, Archive ALL new eligible photos privately, even before guest selection is on by default. Press Pair event / verify destination / save archive policy. Existing watch sessions retain their prior selected-only setting until you explicitly turn it on and save: an upgrade must not silently upload previously excluded photos.

Enabling it promotes previously discovered unselected NEW candidates to archive work. It does not import the old baseline, earlier explicit skips, test images or retired work. Changing it off affects future arrivals, not work already queued. Stop capture/export during first baseline and wait for Watching: the existing baseline race/provider-epoch limitations are not fixed by this release.

Every completed eligible new JPEG can now upload without a guest assignment. It is stored privately in Review even if the event's older public policy says publish immediately. Admin can explicitly publish later. Unassigned archives are staff-only; guests cannot see them. Group or Single Photo selection binds an immutable destination separately; guest approval still applies. A Single Photo QR delivers ONE selected photo even when ten photos have been privately archived.

Selection before upload records the guest locally; after a verified cloud receipt, a small bounded assignment queue links that photo to the guest. Selection after upload does not upload the bytes again. An assignment failure does not erase the archive or silently redirect it. Retry guest links or inspect Recovery; a permanent pending assignment must be resolved or explicitly retired before event turnover. Retiring local linking does not undo a binding that the cloud already accepted.

Hide from selection keeps an archive uploading. For old selected-only candidates, Skip still means no upload. The confirmation distinguishes these cases. Archive counts, unassigned selections, guest-link pending status and public visibility are separate. An archived candidate whose staged duplicate was removed uses an authenticated cloud thumbnail for immutable preview; this requires internet. It does not trust a later overwritten source for preview.

Source support remains exact primary shared-storage JPEG folder, no arbitrary private app folders, recursive/SD-card/RAW/HEIC guarantee or native camera FTP/SDK adapter. External camera/editor exports need physical testing.

## 05 / Photographer printing also preserves an online copy

Prepare the authorized station online. Select one JPEG, the validated CP9550 profile, copies and optional prepared guest group/single link. The default choice is Staff-only / keep existing guest assignment; it does not guess the currently shown guest.

Before saving a NEW print request, Android validates/stages the exact selected bytes, then creates or reuses an independent private cloud-archive job. The print queue stores that archive job ID. A local print can proceed while cloud upload is pending, but the photo remains queued for online storage. A failed print does not cancel the archive; a successful print is not proof of cloud verification. The phone shows both statuses. Identical content reuses an existing eligible private archive; different guest assignments are rejected.

Archive staging has its own 256 MiB budget and approximately 1 GiB free-space reserve; print staging is separately bounded. No print is queued if the required archive snapshot cannot be committed. A crash between the two commits may leave archive-only work, never a phantom print. Files are not deleted to force success. Confirming while another transfer owns the shared lane waits for a checkpoint for up to 20 seconds, then returns an actionable failure rather than interfering with it.

Existing pre-H5.5 print jobs have no archive correlation and are NOT retrospectively guaranteed to be in the event library. Reconcile old jobs manually. New archive maximum remains 64 MiB; photo printing remains 32 MiB. No silent resizing is done to fit a limit. Driver profiles remain unchanged: 4x6 photo, repeated single-strip auto pair, explicit manual pair. One pair copy is one sheet cycle/two strips. Use ONE finished 1:3 strip, not an already paired image. Hardware cutter and Windows driver behavior still need calibration.

## 06 / Staff looks up a guest and deliberately orders

At the staffed Windows desk, refresh setup after Admin enables it. Enter a guest ticket REF, full URL or collection ID and press Lookup ticket (NO PRINT). A keyboard scanner may press Enter. This only opens the collection and linked orders. It never submits to a printer and does not consume a payment/entitlement.

The guest sees only released personal photos. Authorized print staff can inspect Review/unreleased photos in that event, but Hidden and isolated test photos are not eligible in this desk. Public publication is still a separate Admin action. Staff may use All event photos to find an unassigned archive, then explicitly Assign to a guest reference. Assignment is one-way: an already assigned photograph cannot be silently moved. Single-photo links cannot gain a second photograph. Reassignment/removal of a mistaken guest is not provided here; stop and use authorized investigation rather than relabeling.

Click Preview / prepare print. Wait for the protected preview to load. Check guest/staff-only status, exact photo, destination, profile revision and sheet copies. Confirm ONE print request. The cloud checks permission and source again, copies and verifies the exact original into the private print cache and makes one normal queue job. The station downloads it through its existing authenticated cloud connection. It starts paused/in Simulation according to the station's current saved configuration; an order is not proof of physical paper.

The browser saves a pending request ID before submission. A lost response leaves Check / retry the SAME request. Same ID and identical payload return the existing order; conflicting reuse is refused. Do not deliberately create another order to bypass an unknown outcome. The panel offers cancellation only for an incomplete, unexecutable preparation. Ready/accepted work must be inspected/cancelled at the station where safe; clearing a browser pending record does not cancel anything. Browser storage contains request identifiers/selection, not the Admin Key or image; do not clear it while an outcome is unresolved. A browser storage failure blocks submission.

Staff orders use a hidden service identity for that station, with no issued phone token and no media-upload permission. It is excluded from photographer lists and received-photo reconciliation. Access to its print jobs remains station-scoped. These records are an audit mechanism, not another photographer or a station archive uploader. This release does not provide separately named print-staff accounts or a billing/entitlement quota engine.

## 07 / Collection is not another print

For a newly printed job, the Windows operator inspects the actual paper and records operator_confirmed through existing queue controls. Then Staff Photo Desk can Confirm handover / collected (NO PRINT). The server records actor and timestamp idempotently. A second collection confirmation cannot create another print or alter the first timestamp.

Simulation, ready, submitting, submitted_unconfirmed and outcome_unknown do not allow collection. An already printed order is located and handed over, not printed again on scan. Additional requested copies require a separate explicit new order. Quantity limits are per request, not a promise of one free print per guest. No payment, balance, automatic entitlement redemption or guest kiosk is added.

Phone-originated jobs become visible under their archive/photo when cloud records can be matched by event, originating device, exact SHA-256 and byte size; not filename. Until the local job receipt and cloud photo are both synchronized, staff lookup may show the photo or order as pending. Old records are linked when a matching new receipt/archive completes; no speculative historical backfill is performed.

## 08 / What works offline

Method 1's already prepared Android -> LAN -> Windows jobs can continue while authorization is valid and the event network is reachable. Method 2 requires both phone and station internet access for cloud relay. Local means the phone can reach the station's private HTTPS address, not that both devices merely have internet. Venue client isolation can block this route; use the existing connection test. Windows LAN endpoint remains 54173; 54172 is loopback UI only. Cloud fallback must not bypass certificate/permission failures or switch stations.

The new staff library/lookup/order/collection desk requires cloud access. It does not cache all guests or photos for offline ordering. Android photos queue until internet returns; a locally printed photo may not yet be online. Prepared QR links stay the same. A guest web page still requires internet. A revoked permission may not reach an offline worker immediately. No Method 3, automatic offline ownership handoff, guest self-service or public incoming Windows listener is included.

## 09 / Small rehearsal before real output

Use one disposable event, one station and one phone. Keep originals and start in Simulation.

1. Upgrade and confirm old event/photos/links/queued destinations survive. Verify actual build metadata, not artifact filename.
2. In a new watch folder place two OLD JPEGs, start baseline, then add three NEW distinct JPEGs. All three new photos should archive privately without selection; the two old files stay excluded. Confirm public gallery and personal QR have no new photos yet.
3. Assign one archive to a Single Photo QR and approve it. Only that image appears to the guest; all three remain in Admin. Hide another from selection and confirm archiving remains completed, not deleted.
4. Enable one station's staff desk. Paste ticket REF. Confirm zero new print jobs until explicit preview/confirmation. Test unauthorized other station/guest rejection.
5. Create one staff request. Verify one simulated station job, same image/copies/profile. Repeat the SAME request ID through retry and verify no duplicate. Ordinary repeated Queue Print presses intentionally create new orders.
6. On Android select a fresh JPEG outside the watched folder, assign a prepared guest or leave staff-only, and print in Method 1 Simulation. Verify an independent archive arrives. Repeat in Method 2. Interrupt the network and resume saved work without replacing IDs.
7. Change displayed history QR while another group receives selection; verify displayed link and guest assignments remain stable. 58B3 manual ticket behavior is unchanged.
8. Confirm no collection is possible for Simulation or unknown output. For a supervised physical print only, inspect paper, mark operator_confirmed and then record collected; repeating the collection action must not print again.
9. Restart/redeploy with stopped backups and reconcile counts, pending assignments, browser pending requests and station ledger. Test permission revocation and profile changes during preparation.

Then physical CP9550 4x6/auto-pair/manual-pair and full-length 58B3 QR scan tests, camera/official-editor intake, phone background/lifecycle, memory/heat/battery, large folder and multi-photographer endurance remain necessary.

## 10 / Boundaries and evidence

H5.5 does not certify all H5.3 review findings closed. Provider rebuild and baseline-generation boundaries, real large-folder load, native OS timing, camera coexistence, independent gallery/projector controls, station repeated-test stall investigation and full restore qualification remain open. Private archiving does not mean the camera card has been fully received. A stable QR is not unlimited print permission.

The desk paginates photos 50 at a time and shows the newest 100 linked orders per view. It does not provide a complete historical order export or guest entitlement accounting. Guest lists in the phone print chooser use the existing recent-history page; older groups remain accessible through existing History. Existing profile/copy limitations are unchanged.

Automated results are in RELEASE_REPORT.md and verification/. The browser harness uses the actual HTML/JS and actual local APIs, with injected location/history/storage/crypto and a fetch bridge because native navigation is blocked. Screenshots use synthetic color JPEGs and simulation. This is not a compiled Windows or Android screenshot, native HTTPS or actual printer test. No live credentials or event photos are included.

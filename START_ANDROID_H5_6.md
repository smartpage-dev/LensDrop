# LensDrop H5.6 | Android setup and scanner update

**Android-only source update. No new APK is included.** Cloud and Windows remain H5.5. The screen previews are browser layout references, not captured Android screens.

## 1. Install the right build, without losing data

Use the complete GitHub-ready source when the repository is older than or different from delivered H5.5. Use the patch only on the exact delivered H5.5 tree. Copy project CONTENTS at repository root, including `.github`, while keeping `.git`, local uncommitted work and private signing secrets. Do not apply this to Printer Lab. Compare/merge any custom changes instead of blindly overwriting them.

Preserve `LENSDROP_TEST_KEYSTORE_BASE64` and choose the same persistent signing mode for an installed phone. Start a NEW run of **LensDrop Android APK** after committing/pushing the new source; rerunning an old job rebuilds its old commit. Do not disable lint to force a pass.

After the complete workflow succeeds, download the APK artifact, not the logs. The filename stays `LensDrop-H5-debug.apk`; its BUILD_INFO and the new screen header must say **0.2.0-alpha.6-h5.6 / code 11**. Install over the existing app using the same signer. Do not uninstall or clear app data for a signature error.

There is no new cloud deployment or station build required when they already run H5.5. The cloud version remains `0.2.0-alpha.8-hostinger.5.5` (schema7); Windows remains `0.1.3-alpha.h5.5`. Older deployments need the documented H5.5 upgrade, not a storage reset. Back up consistent stopped data and preserve originals.

## 2. Three simple workspaces

| Workspace | Use it for |
|---|---|
| **Event** | Everyday operation: start/pause uploads, compact counts, guest QR/photo selection, photo printing, QR-ticket printing |
| **Setup** | Join event, choose/save photo source, archive/cellular policy and optional printers |
| **Support** | Detailed status, recovery, explicit retry, redacted diagnostics and controlled tests |

Manual server/token entry is under **Setup > Advanced manual setup**. An already QR-paired phone normally needs no manual token. Recovery and older actions are retained, not removed. Upload state is not camera connectivity, and sent-to-printer state is not paper confirmation.

## 3. Join a new phone

1. In cloud Admin select the intended event. Use **H5 - Staff QR pairing & Print Stations**, not **Create device token**. Choose **Photographer phone**, a unique label and optionally a station; create a fresh five-minute pairing QR.
2. Android: **Setup > Join / manage staff pairing > Scan staff QR**. Allow the camera when asked. If already paired with valuable queued work, do not replace the identity just to test scanning.
3. A correct staff QR returns to a **Connect to this LensDrop server?** confirmation. Check the hostname and select **Claim invitation**. Scanning alone does not pair or upload anything.
4. Read the large six-digit code on the PHONE. Admin refreshes pairing status, chooses **Approve matching device** and enters that code. Admin must not copy a code from the web page.
5. Phone: **Check approval & finish setup > Continue to photo source**. The event is now saved. A saved claim may be retried while valid; replacing it requires deliberate confirmation.

If the camera cannot open, stay on the error screen and choose **Allow camera / retry**, **Open app permissions**, or **Paste invitation instead**. For paste, use the COMPLETE fresh staff invitation and **Review pasted invitation**. Do not paste a guest QR, the master Admin Key, or token JSON.

Five wrong approval codes lock an invitation. Expired/revoked/locked invitations need a new Admin invitation. Code and invitation contents are private: do not send screenshots that expose them.

## 4. Choose a folder, then start uploads

1. Save one disposable camera JPEG into the intended shared phone folder. App-private media is not a watchable source.
2. **Setup > Choose photo folder**. Allow the required photo access and select the exact folder from the list. Manual path editing is an Advanced fallback, not the normal route.
3. Choose **Archive all new photos privately** and the desired mobile-data setting. Pause active transfer before changing source/settings. Press **Save photo source**.
4. Keep camera/export paused during the first baseline. On **Event**, choose **Start / resume uploads** and wait until Watching readiness. Existing baseline files stay excluded; reopening retains the same baseline.
5. Add new disposable JPEGs. Their private archive, guest assignment and printing remain independent. In **Guest QR & photo selection**, prepare guest links and confirm the intended group or single photo. Guest/public release remains governed by cloud approval.

Already paired phones keep their saved source and archive policy. Old selected-only sessions are not silently changed to archive-all. A changed unsaved draft must be saved or restored before Start. The isolated pre-shoot test under Support is intentionally not a normal guest photo.

## 5. Optional printers

**Photo printing:** Setup > Prepare photo Print Station. Admin must assign the phone to a station and allow printing. Use **Prepare / refresh station (online)**. Method1 needs reachable event LAN for its direct route and may fall back to the SAME station through cloud. Method2 uses cloud; a failed LAN test is not itself a Method2 failure. Choose **Select photo to preview**, inspect the image/guest/profile/copies, then confirm Queue Print. Start with Windows paused/in Simulation and unpause only for the test.

**QR tickets:** Setup > Set up my QR-ticket printer, or Event > Print a QR ticket. Use your own system-paired 58B3, confirm the SHOWN guest QR, preview and explicitly print ONE ticket. History/reprints preserve the guest URL. Leaving a sending ticket screen may create an uncertain outcome; do not retry blindly. Ticket transport and command bytes are unchanged.

A guest presenting a ticket does not automatically print. The separate H5.5 Windows Staff Photo Desk continues to provide authorized lookup, preview, print and collection.

## 6. What the screenshots do and do not prove

Your upload shows the older flat H5 interface and a manual invitation claim/code state. It does not contain a crash stack or exact installed versionCode. The old result handler provides no feedback for an empty/cancelled scan, but this is not proof of why the camera screen closed. The new activity explicitly handles permission/open/cancel/wrong-code outcomes. Real phone-camera behavior remains to be tested with the new APK.

The browser layout reference lets you explore Event/Setup/Support and scan failure states. It cannot scan, connect, upload or print. It is not a screenshot of the compiled Android app.

## 7. First acceptance check

Verify the header says code11. Test camera denial, explicit grant/retry, Cancel/Back, a wrong guest QR, and a correct fresh staff invitation. Complete Admin approval and phone setup. Reopen/rotate while a claim is pending; use its saved identity. Confirm the source folder, private archive policy, old queue and guest history remain. Perform one simulated photo job and a ticket preview/cancel before any real paper.

If the scanner still exits unexpectedly, record the installed build and the exact step, then use **Support > Export redacted diagnostics** after reopening. It includes the safely recorded scanner result/error category when available, not a full crash stack. A platform crash before the app can record it will still need a redacted Android crash log; do not infer its cause from an empty export.

No live Hostinger settings, Windows identities or application data were changed while preparing this package. Keep the existing event workflow until native builds and device/hardware checks pass.

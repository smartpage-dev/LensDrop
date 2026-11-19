> Historical pre-H2 reference. Consult the H2 guide and release report for current behavior and executed validation.

# LensDrop development update - 0.2.0-alpha.1

10 November 2026

## Result and delivery status

The uploaded LensDrop foundation has been extended with a recovery-first transfer implementation. The deliverables are a standalone cloud core, an automated-tested desktop reference uploader, uncompiled Android uploader source, narrow fixes to the original prototype, research, protocol documentation and a physical validation plan.

**This is not a ready-to-install mobile release. There is no APK, iOS app, completed camera certification, live cloud deployment or claim of greater stability than Piufoto.** The Node/Python tests are executed evidence; the Android source and future architecture are not equivalent evidence.

Two archives are supplied:

- `LensDrop_Cloud_Core_v0.2-alpha.zip`: runnable evaluation backend, minimal console/gallery, original/patch reference, tests and documents.
- `LensDrop_Transfer_Clients_v0.2-alpha.zip`: Python folder uploader and launchers, Android source project, tests and documents.

Read each package's `README.md` first. The Windows launcher is not a one-click installer. The original HTML remains a Claude-dependent reference, not a production Hostinger upload.

## Research conclusion

The supplied handover already had Piufoto research. The initial review primarily examined code; this pass independently checked Piufoto's official transfer/collaboration features and Panasonic/Android/Apple platform documentation. Piufoto is a useful workflow reference, but public information does not establish its internal crash mechanism. The reported phone instability should lead to measurable resource budgets and recovery testing, not an unsupported diagnosis.

The key design is to separate camera acquisition from phone-to-cloud delivery. Each photographer's kit has its own event-scoped identity, persistent queue and upload history. The phone discovers and transfers; the cloud decodes images, creates delivery renditions and later may perform AI. Guests see only explicitly published results.

For Panasonic, LUMIX Lab officially lists wireless Auto Transfer for S5IIX (camera firmware 2.1+), S9 (1.0+), S1II (1.0+) and S1IIE (1.0+). Those are feature minimums, not latest firmware. S9 also has documented wired import/send at firmware 2.0+, but continuous wired auto-transfer is not established by that table. See the sourced architecture record in either package.

The critical unresolved hardware question is whether automatically transferred originals are exposed in shared phone storage without manual export. A photo visible only inside LUMIX Lab is not necessarily watchable by LensDrop. If the app keeps it private, the workflow needs a supported export/integration or direct-camera adapter; a generic watcher cannot bypass the app sandbox.

## Implemented recovery and resource controls

The cloud core authenticates each event/device, accepts 1 MiB resumable JPEG chunks, validates chunk and full-file checksums, preserves exact received bytes, creates cloud-side delivery/thumbnail JPEGs and issues an explicit completion receipt. Session/client IDs and content hashes prevent duplicate completion and filename collisions. Review-first and Hidden are enforced in public asset routes, not just filtered from the UI.

The desktop client persists queue metadata in SQLite, waits for stable files, streams a private snapshot/hash, resumes from the server's accepted offset, uses jittered retries and preserves blocked work for inspection. One file uploads at a time. The staged-copy budget is 256 MiB; pending records are not discarded to maintain it. Source files are never automatically deleted.

The Android source follows the same approach using shared MediaStore images, a native worker, Keystore-protected credentials and low-resource guards. It includes per-network cloud routing, a visible dataSync service and deferrable recovery scheduling. It has not been compiled or tested on a phone. Android OS limits still apply, and the alpha does not implement user-initiated data transfer jobs. The source does not promise uninterrupted all-day background execution.

For iPhone, Apple's newer PhotoKit background resource-upload facilities merit a separate native path. System scheduling and photo access still constrain the result. The current custom LensDrop protocol is not an Apple-compatible resumable endpoint, so no immediate iOS integration is claimed.

## What passed

23 automated tests passed: 12 cloud/prototype tests and 11 desktop queue/integration tests. Coverage includes 10,000 persistent queue records, 1,000 server upload sessions across restart, actual lost-acknowledgement recovery, same-name files from different devices, checksums, unauthorized access, review/hidden policy and three subprocess SIGKILL commit-boundary scenarios.

These are targeted correctness tests, not a 10,000-photo phone load test, camera compatibility proof or battery/RAM benchmark. Raw logs and test source are included in both relevant archives; the full test report explicitly lists untested areas.

## Important operating limits

The alpha is JPEG-only, <=64 MiB/file. The cloud implementation is one Node process with local persistent SQLite/files, not replicated object storage and a managed database. The basic console is not a full migration of the original UI, and public galleries are unlisted links rather than authenticated confidential albums. A completion receipt means the local cloud instance committed the checked file, not that a separate backup has completed.

An official app may resize/export a JPEG before LensDrop sees it. "Exact uploaded file" must not be labeled "camera original" without verifying that upstream setting. LensDrop cannot recover photographs that never reached the phone, and a watch-only adapter cannot certify camera-card completeness. Preserve the card and phone sources while reconciling the event.

The official app's source directory can still fill the phone during an outage. The 256 MiB limit is LensDrop's duplicate staging budget, not a limit on all event photos. Capacity planning must use actual JPEG size, shooting rate, uplink throughput and expected offline duration. Network retry cannot compensate for sustained insufficient bandwidth.

## Next release gates, not completed work

First prove shared-file visibility and simultaneous camera Wi-Fi/cloud connectivity on a real Panasonic/Android kit. Then compile/test native lifecycle recovery, profile 1,000-photo bursts and 10,000-photo backlogs, run an eight-hour event soak, add several concurrent photographers, and verify counts/hashes against camera cards. After those gates, expand direct-camera adapters and migrate the polished LensDrop interface.

The target is a recovery flow that normally requires at most reopening/resuming the app, not restarting the entire phone or clearing its data. OS force-stop, permission withdrawal, vendor-app failure and camera-side missing downloads remain explicit boundaries, not hidden promises.

All research sources, configuration defaults, API behavior, test reproduction commands and physical test gates are included in the archives. No external deployment or account change was performed.

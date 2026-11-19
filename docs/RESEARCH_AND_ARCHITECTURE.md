> Historical pre-H2 reference. Consult the H2 guide and release report for current behavior and executed validation.

# LensDrop research and architecture decision record

Research checked 10 November 2026. This is an independent review of public vendor/platform documentation, not reverse-engineering or benchmarking Piufoto. The original handover also contained Piufoto research; the first code review did not independently validate all of it. The public features do not reveal Piufoto's queue architecture or explain the reported crashes.

## Decisions

Separate camera acquisition, phone transfer, cloud processing and guest presentation. A phone should not perform all four workloads. Keep the existing product concept and visual foundation, but move reliable transfer out of a browser/Claude-only runtime into a native queue plus independently hosted API.

The intended sequence is:

`camera -> acquisition adapter -> accessible phone JPEG -> durable transfer queue -> checked cloud receipt -> cloud processing -> explicit publication -> guest gallery`

Each photographer has a separate device credential and queue. The event aggregates accepted photos without relying on filename uniqueness. A future enrollment QR should contain a short-lived exchange code, not a long-lived device/admin secret; this is not implemented in the alpha.

## Piufoto comparison

Piufoto's official features describe OTG cable and camera FTP transfer (model-dependent), multi-photographer projects, cloud editing, retoucher collaboration, branded galleries and guest downloads [S1]. These support the desired workflow as a product reference. They do not provide a stability benchmark, maximum reliable backlog, session-recovery guarantee or explanation of phone restarts. The user's crash experience is relevant input, not evidence of a particular internal defect.

The engineering priority is therefore recovery visibility, resource budgets, idempotent transfer and field tests, rather than copying every Piufoto feature into the phone app.

## Panasonic: confirmed features and unconfirmed bridge

Panasonic's LUMIX Lab compatibility table lists wireless Auto Transfer for:

| Camera | Panasonic model code | Minimum camera firmware shown for Auto Transfer |
| --- | --- | --- |
| S5IIX | DC-S5M2X | 2.1+ |
| S9 | DC-S9 | 1.0+ |
| S1II | DC-S1M2 | 1.0+ |
| S1IIE | DC-S1M2ES | 1.0+ |

These are feature minimums, not a list of latest firmware releases [S2]. Do not extrapolate a blanket FTP claim to all bodies/firmware; the documented official-app path does not require FTP as LensDrop's starting point.

The same official table now includes wired import/send for S9 firmware 2.0+; it does not list wired Auto Transfer for these four models [S2]. Wired import is not proof of continuous tethered upload.

The S9 manual explains as-shot automatic transfer, but also warns about LUMIX Sync interfering with Lab, interruption during video recording/playback, thermal interruption, and incomplete sending when many files are unsent [S3]. Validate each model's actual behavior rather than assuming identical recovery semantics.

Crucially, Lab's documentation distinguishes its device library and My Work gallery [S4]. That does NOT establish that every auto-transferred original appears in shared Android MediaStore or iOS Photos without a manual save/export. The first hardware gate is to prove that external visibility. If originals remain private, a generic folder watcher cannot bypass the sandbox. Alternatives are supported automatic export, an official integration, a separately validated direct-camera adapter, or a different transfer topology. Do not promise a watcher alone solves it.

## Android phone design

MediaStore supports permission-controlled access to shared images. App-private vendor storage is not a public watch folder. Broad photo permission has Google Play restrictions; store distribution must justify the core use case or use a different supported access model [S5]. The source limits scans to the selected camera folder and session boundary, but still needs large-library profiling.

Camera Wi-Fi may have no internet. The intended separation is camera traffic over the local link and LensDrop cloud traffic over validated internet/mobile data, with user consent. Android provides per-Network connections [S6]. The source uses that API rather than binding the entire process to the camera link. Real phone modem/OS/VPN behavior is untested; a hotspot/router or wired acquisition adapter may be a better topology for a particular kit.

Do not claim an always-running foreground service. Android 15+ targeting 35+ limits background dataSync foreground time to six hours per 24 hours unless user foreground interaction resets it [S7]. Android's UIDT jobs are a relevant production option for explicit user-started long transfers, but still require a user-visible start and remain subject to system health restrictions [S8]. This alpha implements a simpler dataSync service and deferrable recovery job, NOT UIDT. User Force stop is different from a recoverable process crash and requires reopening.

## iPhone design must not simply copy Android

Apple now documents PhotoKit background resource upload extensions, which can schedule photo-library uploads while the app is not foregrounded. Work is system-scheduled according to conditions, not an immediate new-photo delivery SLA. The referenced API documentation distinguishes older and newer extension interfaces; availability must be checked against the actual deployment OS/SDK [S9]. Full photo-library authorization and proper extension setup are required [S10].

This makes a native iOS route worth investigating; it does not make an arbitrary private app folder watchable. The current LensDrop custom chunk API is NOT an Apple background-upload-compatible resumable endpoint. A native-compatible server adapter and physical-device tests are prerequisites. No iOS source/build is delivered here.

A background URLSession can handle file-backed transfers separately from the app process, but user force-quit cancels those transfers and requires relaunch [S11]. Do not generalize that specific URLSession rule into an unverified statement about every newer PhotoKit lifecycle behavior.

## Two recovery boundaries

1. Camera to phone: owned by the camera/official app in watch-folder mode. LensDrop cannot discover a photograph that never reached shared phone storage, or infer every missing camera-card frame from a simple file watcher.
2. Phone to cloud: owned by LensDrop. Persist discovered work, confirm stable source bytes, stage bounded copies, upload resumably, verify checksums and server receipt, retain failed jobs, and reconcile after restart.

A future direct-camera adapter should support card/object enumeration and source-completeness reconciliation. Until then, the console must report "phone received" and "cloud verified" separately rather than calling a watched-folder count "all camera photos uploaded".

## Resource and queue policy

Start with JPEG event delivery. Keep RAW on camera media. The phone worker performs no AI, LUT processing, large image decode or thumbnail wall; derivatives happen in the cloud. One active file, 1 MiB chunks and a streaming hash keep work bounded, but do not prove a total RAM ceiling. Slowdowns produce a persistent disk queue, not unbounded concurrent requests.

The implementation preserves pending metadata irrespective of the former 400-item history limit. It uses a 256 MiB app staging budget and leaves vendor source files alone. This is backpressure, not a cap on the official app's storage. At an assumed 6 MB/JPEG, 10,000 source photos still need roughly 60 GB.

Illustrative capacity only: one 6 MB photo every three seconds produces 16 Mbps before protocol overhead. Four photographers at that rate produce 64 Mbps aggregate cloud ingress. When acquisition exceeds usable uplink, the backlog necessarily grows. Retry logic prevents avoidable loss; it cannot create bandwidth. Frame rates, file sizes and measured link rates must be part of event sizing.

No transient retry count should delete a photo. Authentication, unsupported format, changed source and checksum conflict need a visible blocked state rather than endless hidden retries. Do not automatically reboot the phone, clear the vendor app, delete originals or re-upload the entire session as a recovery strategy.

## Primary sources

[S1] Piufoto official features: https://www.piufoto.com/features

[S2] Panasonic LUMIX Lab support and model compatibility: https://av.jpn.support.panasonic.com/support/global/cs/soft/lumix_lab/en/index.html

[S3] Panasonic S9 manual, Using LUMIX Lab / Auto Transfer: https://eww.pavc.panasonic.co.jp/dscoi/DC-S9/html/DC-S9_DVQP3138_eng/0125.html

[S4] Panasonic LUMIX Lab gallery behavior: https://av.jpn.support.panasonic.com/support/global/cs/soft/lumix_lab/en/cts/a0.html

[S5] Android shared media access and permissions: https://developer.android.com/training/data-storage/shared/media

[S6] Android Network.openConnection: https://developer.android.com/reference/android/net/Network

[S7] Android foreground service timeouts: https://developer.android.com/develop/background-work/services/fgs/timeout

[S8] Android user-initiated data transfer: https://developer.android.com/develop/background-work/background-tasks/uidt

[S9] Apple PhotoKit background asset upload: https://developer.apple.com/documentation/photokit/uploading-asset-resources-in-the-background

[S10] Apple enabling the resource-upload extension: https://developer.apple.com/documentation/photos/phphotolibrary/setuploadjobextensionenabled(_:)

[S11] Apple background URLSession configuration: https://developer.apple.com/documentation/foundation/urlsessionconfiguration/background(withidentifier:)

These sources establish platform features and constraints, not a successful LensDrop integration or comparative stability result.

> Historical pre-H2 reference. Consult the H2 guide and release report for current behavior and executed validation.

# Physical validation plan - NOT executed

Date: 10 November 2026. No camera/phone combination has been certified in this release. The following are proposed release gates, not measured results or assignments to named people.

## Gate 1: prove the upstream bridge before optimizing the cloud

Record the exact camera model/firmware, phone model/RAM/free storage, OS build, official-app version, JPEG settings and connectivity topology. Test S5IIX, S9, S1II and S1IIE separately. Start with one representative Android phone; add a second manufacturer before generalizing. Evaluate iPhone through native Photos APIs in a separate implementation.

Take at least 20 new photographs. Verify each reaches the official app, then separately verify each is accessible to LensDrop without manually exporting individual files. Record whether it is an original, compressed export or preview. Check filename collisions, orientation, timestamps, dimensions and checksums where available. Stop the watch-folder design for that combination if the vendor app keeps the files private.

Test camera Wi-Fi plus mobile internet, screen lock, LensDrop backgrounding, camera sleep/wake, camera power cycle, app switching, and video-recording interruptions. Do not run competing vendor controllers simultaneously unless their documentation explicitly allows it.

## Gate 2: native build and lifecycle

Compile the Android project, fix build/platform issues, run instrumentation tests, inspect permissions and Keystore behavior, and use physical devices. Verify visible user-started service operation, notification controls, user pause, process termination, Android force-stop, OS timeout, permission revocation, phone reboot and stale job scheduling.

Simulate shortened dataSync timeouts using the documented Android developer settings and verify a safe checkpoint and clean service stop. Evaluate a UIDT controller for appropriate long-running user-started transfers. Do not misdeclare a connectedDevice service to evade dataSync policy.

## Gate 3: realistic volume and resource profile

Suggested test workloads: 1,000 real representative JPEGs in bursts; a 10,000-photo backlog; a two-hour sustained run and an eight-hour event soak. Repeat with 4-8 independent uploader devices or controlled emulators for protocol load, then real phones for thermal/network behavior. These are targets, not supported-device or throughput claims.

Measure actual image sizes, accepted files per minute, camera-to-phone delay, phone-to-cloud delay, p50/p95 latency, queue growth/drain, process RSS/Java heap, CPU, battery drop per hour, battery/thermal state, free storage, network usage, database/WAL growth and wakeups. Measure while the official app also receives images. Profile full-folder metadata scans and UI/notification polling before deciding their final cadence.

At weak uplink rates, prove that queue growth is visible and bounded by real storage, not discarded items. Test the 256 MiB staging guard, free-space guard, low-battery pause, thermal pause and recovery. The official camera app can fill its own storage regardless of LensDrop's staging limit; test and document that boundary.

## Gate 4: fault injection

| Fault | Required observation |
| --- | --- |
| Airplane mode / 40-minute outage | Queue preserved; no tight retry loop; resume from server-accepted offset |
| Wi-Fi/cellular transition | Camera connection not deliberately destroyed; cloud worker reconnects safely |
| Client process killed mid-chunk | Relaunch reconciles session; no duplicate committed photo |
| Server stops before/after acknowledgement | One valid durable final asset or a resumable incomplete session |
| Lost response after server commit | Repeated completion returns the same receipt |
| Source partially written / replaced | Not published as corrupt content; explicit blocked state when identity changes |
| Same filename on different cameras | Distinct provenance and correct pixels; no overwrite |
| Duplicate observer notifications | No duplicate queue item for the same source version |
| Revoked token | Visible intervention needed; no secret leakage or endless hammering |
| Device token rotation | Existing queue resumes under the same device ID |
| Server quota/full disk | Retry/backpressure, no receipt for missing data, pending queue retained |
| Phone storage low | Staging pauses; source originals are not deleted |
| Android Force stop | No false promise of immediate self-restart; reopen restores queue |
| Review or Hidden photo | Public gallery and direct public asset routes do not expose it |

## Gate 5: account for every photograph

Compare at least three counts: camera/card files, shared phone files, and checksum-verified cloud receipts. A folder watcher can only account for files it sees. Upstream missing downloads must not be counted as successful LensDrop transfers. Keep the card until the event has reconciled and the chosen backup policy has been satisfied.

Release acceptance: zero silent queue drops; zero wrong-photo replacements; zero acknowledged missing files in the test; zero unintended publication; no need to restart the whole phone as the normal recovery procedure. Distinguish these test goals from a universal no-crash guarantee. Investigate failed gates with redacted diagnostics instead of erasing state.

## Gate 6: deployment and security

Test a clean dependency install and native build, actual HTTPS hosting, backup restoration, deployment replacement without data loss, device enrollment/revocation, appropriate gallery access policy and concurrent load. Run dependency/security reviews and verify app-store permission eligibility. Only then migrate the polished original UI and expand camera adapters or cloud AI.

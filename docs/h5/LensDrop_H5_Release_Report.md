# LensDrop H5 release report

17 November 2026. Built from the exact delivered LensDrop_H4_GitHub_Ready.zip.

## Implemented source

Cloud schema5 adds staff invitation/claim/approval, station identities, scoped per-device printing permission, private print assets and a shared print-job ledger. Android source adds encrypted QR enrollment, per-station TLS pinning, bounded JPEG preview/staging, durable print jobs, three routes sharing the same ID and a shared transfer lane with photo upload. Windows source adds the user-session tray launcher, protected local data, private HTTPS intake, cloud intake, simulation, job reconciliation and a native System.Drawing.Printing adapter. Existing Python desktop source is unchanged.

The Windows dashboard opens the same cloud Admin rather than maintaining an offline copy of event settings. Initial pairing requires internet/Admin approval. Mobile print selection is an explicit JPEG chooser, not an implicit current Group photo.

## Recovery protections

Invitations are five-minute, one claimed identity, with matching-code approval; credentials are created and saved locally before the claim. Android credential storage uses Android Keystore encryption; Windows credentials use current-user DPAPI. Cloud grant signing key is persistent and must be backed up. LAN pin validation is per connection, not a global trust-all TLS client.

Both routes preserve the immutable job ID, image hash, copies, profile revision and station. Job identity conflicts are rejected. A partial LAN upload can complete through cloud with the same identity. Print simulation is snapshotted when the station accepts each job. Spooling marks submission before invoking the driver; interrupted/uncertain results are not automatically repeated. Image-cache cleanup keeps job identities. Cloud reports use monotonically increasing station revisions.

## Executed checks

| Suite | Result | Meaning |
|---|---|---|
| Cloud | 114/114 | Existing functionality plus 22 H5 pairing/printing cases |
| Station core | 24/24 | Real local HTTP/HTTPS and SQLite; simulation and two hardware-boundary callbacks |
| Legacy desktop | 23/23, zero skips | Includes actual local cloud integration |
| Exact Android ledger | 24/24 | Actual QueueDb/WatchLedger in JVM/SQLite bridge, not Android SQLite instrumentation |
| Pure Java policies | 10 existing + 15 H5 assertions | Tested rule classes and transfer gate |
| Web UI harness | 11/11 | Exact source with injected location/history/sessionStorage and fetch bridge to actual backend |
| Android source | Java syntax parsed | No SDK type/resource/manifest compilation |

Early regressions identified two schema-version assertions and a release-copy fixture missing the new shared protocol folder; corrected fixtures retain their original protections. A fresh-archive regression run also exposed an existing logo-upload response/cleanup race: a completed response could precede lock release and make the next request return 409. The handler now responds only after cleanup and lock release; the original validation test additionally checks repeated immediate invalid requests. Added recovery tests cover LAN acknowledgement loss, dual-route deduplication, corrupt chunks, crash tails, unknown native submission, simulation immutability, grant refresh, private endpoint validation and scoped permissions.

## Not verified

No APK/EXE was produced locally. Android SDK, Gradle and dotnet tooling are unavailable and external package DNS failed. GitHub workflows are provided but have not run. Fresh npm installation, real Hostinger H5 deployment, native browser HTTPS/CSP, Android permissions/Keystore/lifecycle/QR scanning, Windows compilation/DPAPI/printing, camera Wi-Fi coexistence, ASK-300 paper/cutter compatibility and sustained event operation remain unverified.

Browser navigation was blocked by the environment. UI screenshots are synthetic harness renders, not Windows or Android native screenshots. No real photographer credential, guest photograph or live print job was used. The certificate-protected local HTTPS core was tested independently of that browser harness.

## Native build routes

Android workflow retains the private-test signing secret and Java17/Gradle8.11.1/AGP8.9.1/SDK35 combination, adding ZXing scanner dependency. Windows workflow runs Linux protocol regressions then compiles a self-contained .NET10 win-x64 tray application, smoke-tests DPAPI and bundles official Node22.23.5 with its license. The local core execution used Node22.16.0 and sharp0.34.1. Workflow compilation success is not assumed.

## Remaining engineering gates

Build both native apps; run first hardware rehearsal in simulation, then one controlled physical print; validate Windows driver margins/copies and partial failures; test Android camera/editor folder access and resources; verify direct LAN when camera Wi-Fi is active; validate offline grant expiry, permission revocation and restart/redeploy/restore. Add production retention/backup policy, signing/distribution, clearer camera compatibility matrix and long-duration multi-device soak before event certification.

H5 is an engineering alpha. Its conservative outcome reporting is intentional: submitted_unconfirmed is not a claim that paper has printed.

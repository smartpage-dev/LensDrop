# LensDrop H5.5 - Archive and staff printing release report

Date: 23 November 2026. Source engineering pilot from the exact delivered H5.4 archive. No production deployment, compiled APK/EXE or hardware certification.

## Implementation

* Private archival is independent of Group/Single selection. New sessions default archive-all; existing sessions require an explicit policy enable. Baseline files, skipped, retired and tests remain excluded. Gallery/public/personal release stays separate.
* Additive cloud schema7; Android photo6/print3 migrations. Existing destinations, credentials, pending route choices and source files are not reset.
* Deferred owner-scoped guest assignment after exact upload receipt, per-link retry, immutable Single/Group association and archived authenticated previews.
* New confirmed phone prints first commit/reuse a private archive and store its correlation. Staging/transfer lane remains bounded. Old queued prints are not retrospectively archived.
* Two new user route choices only: Method1 local-first/cloud fallback; Method2 cloud. Cloud defaults apply on phone preparation; existing queue routes remain fixed. No station media hub.
* Shared STAFF-only Photo Desk in cloud Admin and Windows local dashboard, event-scoped private library, ticket reference lookup, explicit guest assignment, image/profile/copy confirmation, normal cloud print queue, idempotent prepared order, separate collection timestamp after operator-confirmed paper output.
* Station staff access defaults disabled, maxcopies1. Admin enables event-level staff access. One hidden per-station service principal creates requests with no issued phone credential or upload permission; excluded from photographer UI and receipt reconciliation. It remains present in the station's authorized print permissions.
* Ticket lookup accepts full personal URL, collection UUID or printed8hexREF, and rejects ambiguous references. No URL fetching, automatic scan-to-print, guest self-service or entitlement balance.
* Staff print copy verifies source checksum/size, reserves existing quota, uses durable temporary-file rename and revalidates visibility/permission/binding/profile. Completed print-cache cleanup never removes gallery originals. Pending copy can retry same ID or be explicitly cancelled before becoming executable.
* Phone-originated prints are linked to archived photos using device/event/exact bytes/hash. Collection never sends a new print, and Simulation/unknown outcomes cannot be collected.

## Component versions

Cloud0.2.0-alpha.8-hostinger.5.5/schema7; Windows0.1.3-alpha.h5.5; Android0.2.0-alpha.6-h5.5/code10/photo6/print3. Nine-field immutable print protocol retained. New builds and cloud-first upgrade required. PrinterLab, driver/cutter algorithm, Bluetooth ticket packet/transport remain unchanged. Historical documents and logs remain historical, not new tests.

## Executed local checks

| Suite | Result | Scope |
|---|---:|---|
| Cloud regression | 156 | 131 baseline +25 workflow/service identity tests; local Node/API/SQLite, existing sharp0.34.1 |
| Station regression | 57 | 52 baseline +5 staff proxy/normal-worker checks; synthetic native driver and real local endpoints |
| New Android workflow | 28 | Actual ledger/staging logic via JVM/SQLite bridge; frozen H5.4 photo5/print2 migrations |
| Existing watch ledger | 24 | Metadata/JVM, not Android provider |
| Existing ticket | 27 packet +28 ledger | No Bluetooth or paper |
| H5.4 JPEG / recovery | 21 +25 | Pure Java/SQLite, not phone lifecycle |
| Session / print policies | 10 +15 | Logic only |
| H5.1 build contracts | 15 | Source-extracted/stub API checks |
| CP9550 layout assertions | 31 | Pure Java geometry |
| Source contract checks | 37 passed | Focused text/XML/source checks, not native type resolution |
| Web UI | 17 | Actual source with explicit API bridge, injected browser globals and synthetic fixtures; includes staff order -> simulated station -> collection |
| Desktop uploader | 23 passed, zero skipped | Unchanged source |
| Java parsing | All32 production files | Syntax only, not Android SDK compilation |

Final extracted archive reruns and hashes are recorded separately in the package check file. Repeated runs do not multiply distinct test counts. Corrected older tests only update schema expectations or deliberate fixture drop-columns; behavior assertions and strict lint gate remain. Early new tests had fixture-field mistakes (.permissions vs .devices and descriptive job fields vs the nine-field transport); corrected tests pass. A focused source assertion initially used the wrong error-name string and was corrected to the actual collision error. No new failure is dismissed as a successful test.

## Verification limits

No Gradle/Android SDK/.NET tools are available here; Google SDK request failed DNS. No H5.5 native build/lint/instrumentation, installation, CP9550 output, thermal long-link scan, real Android MediaStore, internet/LAN mobile coexistence, sustained performance, live Hostinger or production restore performed. Existing local dependencies are not evidence of a clean installation. Mandatory lint remains in GitHub.

The original H5.4 intermittent station-suite stall remains an open repeatability gate; passing H5.5 local runs do not establish its cause or cure. Native Windows permission/credential storage/driver behavior and browser CSP/real network still need validation. The UI uses blobs from protected endpoints but a previously viewed photograph cannot be made unseen by later permission changes; denied refresh clears the desk.

Staff collection tests inject a synthetic operator_confirmed fixture only to validate UI/API state. They are not physical-print observations. Lab58B3 paper evidence is prior history, not H5.5 integration testing. The new desktop staff panel is not an offline all-photo cache. Shared staff station access is not individually named staff-account management.

## Deployment/rollback

Build clients first; back up complete stopped cloud/station data and preserve phone originals/signing identity. Cloudfirst -> Windows -> Android. Keep data root, storage ID, Admin Key and initialization0. Keep simulation during rehearsal. Downgrade only with matching code/data after reconciling any physical output since the snapshot. Do not reset queues, reuse a new QR for another guest, convert ticket scanning into printing or remove uncertain records to reclaim space.

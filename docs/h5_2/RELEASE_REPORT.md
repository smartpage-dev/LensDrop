# LensDrop H5.2 - CP9550DW engineering update

Date: 2026-11-20. This is a source update, not an installed driver or hardware certification.

## Decision and versions
The Mitsubishi CP9550DW is the preferred photo-printer model for new configuration. Previously saved ASK-300/other printer profiles and accepted jobs are preserved; no automatic hardware reassignment occurs. The separate Yokoscan 58B3 QR-ticket printer and Luce Printer Lab are unchanged.

- Android: `0.2.0-alpha.6-h5.2`, versionCode `7`, application ID `app.lensdrop.transfer` unchanged.
- Windows station: `0.1.1-alpha.h5.2`, native assembly version `0.1.1`.
- Cloud production source and schema 5: unchanged, version `0.2.0-alpha.6-hostinger.5`.
- Existing deploy ZIP, signing helpers, Android manifest/queue schemas and nine-field print-job protocol: unchanged.
- New Android and Windows builds must be run on GitHub. Previous H5.1 native build success does not validate these changed native sources. No new APK or EXE is supplied.

## Research conclusion
The Mitsubishi operation manual confirms 4x6. A manufacturer-authored 2010 CP9550DW brochure explicitly advertises 2x6 multi-cut. Thus automatic strips are a real documented model capability, not a claim that only manual trimming is possible. However the exact installed driver/firmware, cutter mode and image geometry remain to be verified.

The official driver page lists a Windows 10 x64 driver and marks later OS versions unsupported. Windows 11 is not certified by that table. Mitsubishi EMEA announces May 2027 end of sales for CK9046 and other 9000-series media; local supply must be confirmed. Exact sources, publication scope and retrieval limitations are in `CP9550DW_SETUP.md` section 09.

## Implemented
1. Three independent station presets: normal 4x6; a repeated single-strip pair using a captured driver multi-cut setup; and an explicit manual-cut pair.
2. Capture/restore the Windows driver's own opaque DEVMODE settings through documented APIs, bound to queue name and driver-file fingerprints. No undocumented Mitsubishi cutter offsets are edited. An operator checkbox records an assertion, not hardware verification.
3. Save the private settings snapshot into each accepted job. Later configuration changes do not mutate accepted work. Profile revision high-water marks prevent reusing an old revision after a profile is removed and re-created.
4. Preserve existing legacy jobs/settings. Require pause, no active submission and reconciliation of unresolved legacy jobs before driver capture; old jobs without snapshots can depend on global Windows preferences.
5. Android chooses among the prepared station profiles; validates a finished 1:3 strip; shows a nominal two-up preview; labels one copy as two strips for pair profiles. The selected JPEG checksum, profile revision, destination and job identity stay fixed.
6. Windows composes one source strip twice on a single sheet page, with rotation/Contain geometry. Copies are sheet cycles; two copies mean four strips. No manual fallback silently replaces a requested automatic-cut profile.
7. GitHub gains pure-Java profile tests and a native no-printer geometry smoke test. The Windows workflow runs all station test files. SDK/Gradle/.NET families and credential names are unchanged.

## Not implemented or certified
No proprietary driver/firmware redistribution; no inferred Type 1/Type 2 mapping; no RAW printer protocol; no automated confirmation of the vendor cutter bytes; no forced borderless calibration, ICC workflow, photo montage designer, odd-strip output or mixing two guests on one sheet. A pre-paired 4x6 image is rejected by the pair profiles. The automatic preset currently requires a sheet-sized driver form; standalone 2x6 driver forms need a separately validated adapter.

Native driver capture, restoration, printable margins, copy count and automatic cut behavior require real Windows/CP9550DW tests. Driver submission remains `submitted_unconfirmed`; physical output can be uncertain and is not auto-retried. No live Hostinger operation was performed.

## Executed verification
| Test | Result | Scope |
|---|---|---|
| Cloud build | Passed | Existing locally available sharp 0.34.1; not a fresh dependency install. |
| Cloud regression | 117 passed | Local Node/API/SQLite; no deployed Hostinger test. |
| Station regression | 43 passed | 24 existing + 16 new profile + 3 new integration tests. Driver snapshots in tests are synthetic. |
| Android session policy | 10 assertions passed | Pure Java, no Android SDK/device. |
| Android print policy | 15 assertions passed | Pure Java. |
| H5.1 API/callback contracts | 15 cases passed | Source-extracted compile contracts with negative controls; not Android SDK compilation. |
| New profile/preview geometry | 31 assertions passed | Actual pure-Java policy used by the app; no rendered Android view. |
| Watch queue/ledger | 24 cases passed | Exact queue/ledger logic in JVM/SQLite bridge. |
| Desktop client | 23 passed, zero skipped | Includes local cloud integration. Python emitted resource warnings; tests still passed. |
| Java syntax | 23 source files parsed | Javac parser only, not SDK symbol resolution. |
| Web dashboard | 10 checks passed | Actual HTML/CSS/JS + local API through an injected bridge. Windows enumeration and capture were mocked. |

The planned Windows `--layout-self-test` contains 15 geometry assertions. It is wired into the native GitHub build but was NOT executed here because .NET/Windows are unavailable. Neither the new APK build, Android lint, new Windows native build nor physical printing was executed in this environment.

The final archive is re-extracted for additional build/regression checks. See the external `LensDrop_H5_2_Package_Checks.json` for final archive hashes, patch equality, unchanged cloud/deploy checks and exact rerun results.

## Delivery and rollback
Use the full GitHub source ZIP OR the patch over the exact H5.1 repository. Preserve `.git`, signing secrets and local queues. Start new workflow runs on the new commit, not a rerun of an old SHA. Back up the stopped station data directory before upgrading; do not expect the old app to interpret H5.2 profile snapshots. For rollback restore matching pre-upgrade station data and code only after reconciling outstanding print outcomes. Do not delete queued work to force an upgrade.

No Hostinger redeploy is needed when the site already runs H5. There is no new cloud schema or initialization step. Android and Windows should both be updated before testing these new profiles.

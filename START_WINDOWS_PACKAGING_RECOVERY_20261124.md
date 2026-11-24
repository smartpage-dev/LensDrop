# LensDrop H5.7.1 - Windows portable packaging recovery

**Scope:** GitHub Windows build pipeline and packaging-source verification only. No application runtime, cloud schema, Android app, camera adapter, Bluetooth ticket, CP9550DW driver or print protocol changes.

## Evidence from GitHub run 6, attempt 1

- `cloud-build.log`: passed, cloud `0.2.0-alpha.8-hostinger.5.5`, pinned `sharp 0.34.1`.
- `cloud-tests.log`: 156 passed, 0 failed.
- `station-tests.log`: 41 passed, 0 failed (this is the suite executed by that workflow; do not confuse it with older 57-case results).
- `windows-build.log`: .NET 10 publish completed to `ci-output/windows-app/`.
- `native-layout-tests.log`: 15 native geometry assertions passed.
- `native-protect.log` and `native-unprotect.log`: DPAPI round trip passed.
- `windows-package-error.log`: only `[WinError 3] The system cannot find the path specified`.

**Conclusion:** the failure is after native compilation/DPAPI, during portable packaging. The log lacks the missing pathname. It does **not** establish whether the cause is a missing checkout asset, Node runtime/license path or another filesystem operation. The complete source ZIP contains the expected station/docs assets, but that does not prove the GitHub checkout contained every packaging asset.

## Changes in this repair

1. Preflight validates station source/public/tools/licenses, README, notices, package metadata, CP9550 setup documents and both calibration JPEGs **before** native publish.
2. Every packaging stage writes to `ci-output/windows-package-steps.log`. A failure writes a stage, exception class and traceback to `ci-output/windows-package-error.log`; sensitive DPAPI test material remains redacted.
3. The 107-file cloud/station snapshot includes packaging-only assets previously absent from the 95-file snapshot; it is verified in **both** GitHub jobs.
4. Eight hardware-free packaging contract tests run in the prerequisite job, including a fully simulated ZIP assembly and negative missing/stale-source tests.
5. Portable `BUILD_INFO.json` now reads the actual station package version (`0.1.3-alpha.h5.5`) instead of the outdated H5.2 label. Existing output artifact names are retained.
6. The package still requires an official Node license next to the Node executable. If absent, it fails with a specific message; it does not silently distribute an unlicensed runtime.

## Updating the repository

1. Confirm the repository is the LensDrop H5.7.1 source baseline. Use the full GitHub-ready ZIP if the repository is mixed or incomplete; otherwise use the overlay ZIP.
2. Extract the overlay, and merge the **contents** into the repository root, preserving the `.github` folder. Do not upload the ZIP as a single source file or add an enclosing directory.
3. Commit and push. Do not change your Android signing secret, cloud storage identity or Hostinger configuration.
4. In Actions choose **LensDrop Windows Print Station** -> **Run workflow** on the **new commit**. Do not rerun the previous run, which retains its old commit.
5. Verify the new `107 exact files` source gate, eight packaging tests, cloud/station tests, native build, Node syntax check and final artifact upload.
6. Download the Windows program artifact (not the logs). Extract its inner `LensDrop-PrintStation-H5-win-x64.zip` completely. Inspect `BUILD_INFO.json` and keep Simulation and Pause enabled for initial validation.

If the next build still fails, upload the **new** Windows logs ZIP. It will contain `windows-package-steps.log` and `windows-package-error.log` naming the precise failing stage/path. Never bypass the snapshot gate or omit the license to force a green build.

## Validation limits

The source gate and eight simulated packaging contract tests passed locally. `node --check` passed on the unchanged station entrypoint. The two user-supplied logs demonstrate the native publish and DPAPI stages passed on GitHub, but **the updated packager has not been run on a Windows runner**. No new EXE, actual printer output, cloud deployment or device tests are claimed.

# LensDrop H5.1 - GitHub build fixes

19 November 2026 | Source/build repair, not a new cloud feature release

## Exact inputs

- `logs_120363559515.zip`: LensDrop Android APK run. SDK and prior logic tests succeeded; Java compilation reported two errors.
- `logs_120363595614.zip`: LensDrop Windows Print Station workflow, `protocol-tests` job. Cloud build check passed; cloud tests were 113/114. The dependent Windows compilation job never started.
- Both runs checked out `377bea07c862d51f524f69a7631f2182cd32470d`.
- Source baseline: `LensDrop_H5_GitHub_Test_Build_Package_r1.zip`, reopened and extracted for this repair. No inference about additional unprovided repository edits is made.

## Three corrections

### 1. StaffSetupActivity pairing callback

The private `finish() throws Exception` method collided with the inherited public `Activity.finish()`. Renamed it to `completePairing()` and updated its one button callback. The enrollment method body, approval check, credential storage and queue guards are unchanged. This is not a change to the Android Activity lifecycle.

### 2. Upload staging directory sync

`OsConstants.O_DIRECTORY` was not available to the configured public SDK compiler. The new `DirectorySync.sync()` uses public `Os.open(..., O_RDONLY, 0)`, checks `S_ISDIR(Os.fstat(fd).st_mode)`, runs `Os.fsync(fd)` and closes in `finally`. Open/stat/sync/close failures propagate to the existing queue retry handler. The file still syncs before atomic rename; the parent directory still syncs before the spool metadata is recorded in SQLite. No hard-coded hidden flag, ignored fsync error, or reflection workaround is used.

### 3. Cloud redeployment test fixture

The interrupted-upload test copied server code into `/tmp/.../new-release` but did not provide its dependencies. The replacement could not resolve `sharp`, although npm had installed it in the original checkout. Node searches relative to the requiring module, not the workflow's current working directory [3].

A test-only helper now copies the same release files and links the runner-installed dependency tree into the temporary replacement (Windows junction when applicable). The child clears NODE_PATH. New tests load the exact installed sharp version without global search paths, prove that removing the link fails, and reject an uninstalled source. The real redeployment/upload-resume assertions remain active. The fixture reuses dependencies; it does not certify a fresh dependency installation or Hostinger persistence.

## Version and compatibility

| Item | Value |
|---|---|
| Package revision | H5.1 build fix |
| Android application ID | `app.lensdrop.transfer`, unchanged |
| Android version | `0.2.0-alpha.6-h5.1` / versionCode `6` |
| Android minimum / target / compile SDK | 29 / 35 / 35, unchanged |
| Toolchain in GitHub | JDK 17 / Gradle 8.11.1 / AGP 8.9.1, unchanged |
| Cloud runtime | `0.2.0-alpha.6-hostinger.5`, unchanged |
| Cloud database schema | 5, unchanged |
| Android photo / print queue schema | 3 / 1, unchanged |
| Windows application source | `0.1.0-alpha.h5`, unchanged |
| Hostinger deployment ZIP | Original H5 bytes unchanged |
| Workflow YAML / signing helpers | Unchanged |

No APK or EXE is included. No Hostinger redeployment is required for this repair. Keep the existing signing key; do not uninstall or clear app data to resolve signing differences.

## Executed local checks

| Check | Result / boundary |
|---|---|
| Original cloud fixture reproduction | Same missing-sharp failure reproduced with NODE_PATH unset; expected failing control |
| Cloud build | Passed SQLite/JPEG/source checks |
| Cloud regression suite | 117 passed, zero skipped (original 114 + 3 new dependency-fixture tests) |
| Print-station protocol suite | 24 passed; local HTTP/HTTPS + SQLite with simulated printing |
| Desktop client suite | 23 passed, zero skipped, including local-backend cases |
| Android session-policy assertions | 10 passed |
| Android print-policy assertions | 15 passed |
| Android ledger JVM/SQLite bridge cases | 24 passed |
| New focused build/API regressions | 15 passed, including both expected-failure Java negative controls |
| Java syntax-only parsing | 22 production files parsed; not type checking against the Android SDK |

The new Java tests compile the actual DirectorySync class against small public-API test doubles and exercise failure propagation and closure. The pairing-name test compiles a source-derived method/callback projection against a minimal Activity contract, not the entire activity. These checks intentionally do not claim native app compilation, Android filesystem durability or actual UI enrollment validation.

## Verification boundaries

Android SDK/Gradle/.NET were not available locally. Official download attempts failed DNS resolution. Cloud tests used an available sharp 0.34.1 installation linked as a project dependency; a fresh npm install was not completed. No Android APK, Windows executable, full Android lint, phone/camera/printer test or GitHub workflow execution was completed by this repair session. A new GitHub run remains the native build checkpoint. H5 Android workflow still uses assembleDebug plus logic checks, not Android lint/instrumentation.

## Source references

1. Android Activity.finish: https://developer.android.com/reference/android/app/Activity#finish()
2. Android public OS APIs: https://developer.android.com/reference/android/system/Os and https://developer.android.com/reference/android/system/OsConstants
3. Node module resolution: https://nodejs.org/api/modules.html#loading-from-node_modules-folders
4. GitHub reruns retain original commit: https://docs.github.com/en/actions/how-tos/manage-workflow-runs/re-run-workflows-and-jobs

Technical claims from the uploaded logs are reproduced in ERROR_EXCERPTS.txt. References explain the platform contracts, not independent certification of this app.

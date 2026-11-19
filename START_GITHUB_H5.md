# LensDrop H5: GitHub upload and test builds

**Guide revision r1 | 19 November 2026 | Original application version unchanged**

This is the original LensDrop project, not Luce Printer Lab 58. The package includes Android source, Windows Print Station source, cloud source, two manual GitHub workflows and the existing H5 deployment archive. No APK or compiled Windows application is included. This guide was checked against the actual H5 workflow and build-helper files.

**Use this H5-specific guide instead of the retained H4 GitHub instructions.** All original H5 files are preserved unchanged. This revision adds documentation and verification records only; it does not fix or certify native compilation. Your first GitHub runs may reveal errors that need correction.

## 1. Keep the two repositories separate

| Repository | Purpose | Android application ID |
| --- | --- | --- |
| `LensDrop` (recommended name) | Photographer app, personal QR delivery, cloud and Windows photo Print Station | `app.lensdrop.transfer` |
| `Luce-Printer-Lab-58` | Independent Bluetooth receipt-printer experiments | `hk.luce.printerlab` |

Do not copy Printer Lab's `app/`, Gradle files or workflows into LensDrop. The callback and theme patches for Printer Lab are not LensDrop patches. H5 does not yet integrate the Bluetooth QR-ticket printer; receipt-printer development remains separate.

GitHub stores source and runs build jobs. Hostinger continues to host the live cloud application. Uploading this repository or running either workflow does **not** update Hostinger, connect to your production event, or require the LensDrop Admin Key.

## 2. Choose the complete source package

Extract `LensDrop_H5_GitHub_Test_Build_Package_r1.zip`. Open the inner `LensDrop_H5` folder and this `START_GITHUB_H5.html` file.

Upload the **contents inside** `LensDrop_H5`, including hidden files, to your repository root. Do not upload just the ZIP, nest `LensDrop_H5` inside the repository, or use the Hostinger-only ZIP as the complete project.

The correct repository layout is:

```text
LensDrop/
  .github/
    workflows/
      android-debug.yml
      windows-print-station.yml
  .gitignore
  .gitattributes
  START_GITHUB_H5.html
  START_GITHUB_H5.md
  README.md
  clients/
    android/
      app/build.gradle
      build.gradle
      settings.gradle
    print-station/
      native/LensDropStation.csproj
      src/
    desktop/
  cloud/
  deploy/
    LensDrop_Hostinger_Cloud_H5_QR_Pairing_Print.zip
  docs/
  tools/
    github/
      android_ci.py
      windows_ci.py
      create_private_test_key.py
```

Existing LensDrop repository: copy/merge this source into that local repository, preserve its `.git` directory and signing secrets, inspect changes, then commit and push. If the repository already contains your own modifications or a later version, compare first rather than overwriting them.

## 3. Upload with GitHub Desktop (recommended)

GitHub Desktop can manage a repository without command-line Git. [G1]

1. Sign in to GitHub Desktop with your GitHub account. No ChatGPT connection is needed.
2. Create a new repository named `LensDrop`, or open the existing LensDrop repository. A private repository is recommended.
3. Use **Repository -> Show in Finder / Show in Explorer** to open its local folder.
4. Copy the extracted project contents into that folder. Include `.github`, `.gitignore` and `.gitattributes`; on macOS, **Command + Shift + Period** reveals dot-prefixed files. Do not replace `.git`.
5. Review the changes. Never add live event photos, runtime databases, device tokens, cloud keys or signing keys.
6. Commit with a message such as `Import LensDrop H5 source and manual build workflows`.
7. Use **Publish repository** for a new repository, keeping it private, or **Push origin** for an existing one.
8. Check GitHub's Code tab: `.github`, `clients`, `cloud`, `docs` and `tools` must appear at the top level.

The supplied `.gitignore` reduces accidental staging through Git; it is not a security scan and cannot protect secrets already committed or included inside an arbitrary ZIP.

## 4. Browser-only upload alternative

Create/open the private LensDrop repository, then use **Add file -> Upload files**. Upload the extracted folders in batches while preserving paths. GitHub's web uploader accepts up to 100 files at a time and 25 MiB per file; this project exceeds 100 files. [G2]

Hidden files are easily missed. If necessary, use **Add file -> Create new file**, enter `.github/workflows/android-debug.yml`, and paste the exact supplied file. Repeat for `.github/workflows/windows-print-station.yml`. Both helpers under `tools/github/` and the complete source must also be present; the YAML alone is not enough.

The workflows must be committed to the repository's default branch, normally `main`, before manual dispatch is available. [G3] Do not change organization restrictions to circumvent a policy; ask the account owner to authorize the required official actions.

## 5. Android: decide the signing identity first

| Selection | Use | Repository secret |
| --- | --- | --- |
| `temporary-test` | Initial compiler check or disposable installation | None required |
| `persistent-test` | Repeated phone testing with in-place updates | `LENSDROP_TEST_KEYSTORE_BASE64` |

**Recommended:** arrange persistent test signing before the first phone installation whose queue/history you want to preserve. Android update acceptance depends on the application ID, signing identity and version code. A different key can block an update; uninstalling removes application data. Do not uninstall or clear an important queue to work around this. [A1]

If you already have LensDrop's private test key and secret, keep them. Printer Lab's separate secret `PRINTERLAB_TEST_KEYSTORE_BASE64` is not read by this workflow. Do not replace an established LensDrop signing identity with Printer Lab's key.

For a new test identity, run the supplied helper **once on your own computer** from the repository root. It requires Python 3 and a JDK with `keytool`, but not Android Studio:

```sh
python3 tools/github/create_private_test_key.py
```

On Windows with the Python launcher:

```text
py tools/github/create_private_test_key.py
```

It writes private files outside the repository in `LensDrop-private-signing` under your home folder and refuses to overwrite an existing key. Back them up privately. In GitHub, open **Settings -> Secrets and variables -> Actions -> New repository secret**, enter the exact name `LENSDROP_TEST_KEYSTORE_BASE64`, and paste the Base64 file's contents into the secret value. [G4]

Never put the key into source, a Release, an artifact or a chat. Base64 is encoding, not encryption. This helper uses the documented private-debug key convention; it is not production signing.

## 6. Run the Android build

Open **Actions -> LensDrop Android APK -> Run workflow**. Select the updated default branch, choose the signing mode, review and accept the Android SDK licenses, then click **Run workflow**. [G3]

The actual H5 configuration is:

| Item | Included configuration |
| --- | --- |
| Application ID | `app.lensdrop.transfer` |
| App version | `0.2.0-alpha.6-h5` / versionCode `5` |
| Minimum API | `29` (Android 10) |
| Compile / target SDK | `35` / `35` |
| Toolchain | Java 17, Gradle 8.11.1, Android Gradle Plugin 8.9.1 |
| SDK build tools | `35.0.0` |
| Runner | `ubuntu-24.04` |
| Build task | `:app:assembleDebug` |

The workflow provisions Gradle; this source package does not contain a Gradle wrapper binary. It runs the included JVM policy/ledger checks, attempts APK assembly, verifies the signature, and publishes the output only if those steps succeed.

**Scope:** this existing H5 workflow does not run Android lint, emulator/instrumentation tests or camera/phone tests. A green result is not comprehensive validation. Printer Lab's newer workflow has different steps; do not assume identical coverage or replace this YAML with its workflow.

After a successful run, open the run's **Summary -> Artifacts** and download `LensDrop-H5-APK-<run-number>-<signing-mode>`. [G5] Extract the artifact to find:

```text
LensDrop-H5-debug.apk
BUILD_INFO.json
SHA256SUMS.txt
README_INSTALL.txt
apk-verification.txt
```

Install the `.apk`, not the artifact ZIP, on a controlled Android 10+ test device. Verify that `BUILD_INFO.json` identifies H5 and your intended source commit. Do not enter a cloud Admin Key as a photographer token.

## 7. Run the Windows Print Station build

Open **Actions -> LensDrop Windows Print Station -> Run workflow** and select the updated branch. There are no signing inputs for this workflow.

Two jobs run in sequence:

| Job | What it attempts |
| --- | --- |
| `protocol-tests` | Ubuntu runner, Node 22.23.5, cloud dependency installation, cloud build/tests and station protocol tests |
| `windows-build` | Windows 2022 runner, .NET 10 SDK, native x64 compilation, DPAPI encryption round trip, Node runtime packaging and output checksums |

A protocol-test failure prevents the Windows build job from starting. It is not automatically a C# compiler error.

After a successful run, download `LensDrop-H5-Windows-x64-<run-number>`. Extract the artifact, then extract the inner `LensDrop-PrintStation-H5-win-x64.zip` completely. Keep the folder and runtime files together; do not copy just the executable. On your Windows test computer, launch:

```text
LensDropStation.exe
```

This is a **portable, unsigned engineering build**, not an MSI installer or signed production application. It starts paused and in simulation. Keep both protections enabled for first pairing and queue tests. The CI smoke test checks encryption, not paper output, printer compatibility or the complete UI. Never disable Windows security wholesale to run a file; verify its source and recorded hash.

The intended physical test environment is the event's Windows 11 x64 computer with the printer's validated driver. The Windows artifact cannot run on macOS simply because you triggered its build from a Mac.

## 8. When a build fails

Open the failed step and find the first actual compiler, dependency, test or packaging error. The final `exit code 1` is only a summary. Download the complete available logs rather than sharing just the bottom of the stack trace.

| Failed area | Available artifact / log |
| --- | --- |
| Android build | `LensDrop-H5-logs-<run>-<attempt>`; usually `android-build.log` |
| Protocol precheck | `LensDrop-H5-protocol-logs-<run>-<attempt>` |
| Windows native/package | `LensDrop-H5-Windows-logs-<run>-<attempt>`; `windows-build.log` and/or `windows-package-error.log` |

A very early failure can occur before a log file or artifact exists. Use the job's downloaded log in that case. Do not change Hostinger credentials, disable tests or install Printer Lab patches to hide a LensDrop compiler failure.

After committing a correction, use a **NEW Run workflow** on the updated branch. Re-running an older job retains that older run's commit SHA, so it can rebuild the same broken source. [G6]

No successful APK or Windows compilation has been established by this documentation revision. Earlier local protocol checks are not a replacement for your native builds.

## 9. Hostinger is a separate step

You can build both applications before making any cloud change. When ready to test H5 staff pairing and hybrid printing, use the compatible H5 backend; a working older H3.1/H4 site does not contain all H5 APIs. Check `/health` rather than assuming the currently deployed version.

The intended H5 cloud version is `0.2.0-alpha.6-hostinger.5`, schema `5`. Follow `docs/h5/LensDrop_H5_Setup_Guide.html` before upgrading. Pause clients, stop the cloud service, and back up the complete persistent data directory. Preserve the existing Admin Key, data path and Storage ID. Keep `LENSDROP_INITIALIZE_STORAGE=0` for an initialized installation.

Deploy **only** `deploy/LensDrop_Hostinger_Cloud_H5_QR_Pairing_Print.zip`, never this complete GitHub package. H5 adds a private cloud signing key that belongs in backups, not Git. Rollback requires matching older code and the complete pre-upgrade data backup.

Neither workflow has a production deployment action. A source commit and successful builds do not upgrade the website.

## 10. First rehearsal after successful builds

Use synthetic photos and a separate rehearsal event. Pair the Windows station through its invitation/paste-link flow and Admin approval, then pair one Android phone by staff QR and approve its confirmation code. Configure the real station LAN address, keep simulation enabled, and prepare permissions while online.

Test one JPEG in Local, Cloud and Hybrid modes before considering a physical print. Verify that the same job identity is retained across retries. H5 uses an explicit JPEG chooser; it does not automatically print the current personal QR photo. Driver submission is not proof of paper output.

Check Group/Single QR history separately from printing, preserve the receiving group while displaying older QR codes, and retain all source photos during testing. Camera/editor export, phone permissions, background recovery, battery and heat still require physical testing.

The portable 58B3 QR-ticket printer remains under the separate Printer Lab assessment. Bluetooth ticket printing, station scan-to-select/collection and self-service kiosks are not included in H5 merely because the architectural plan shows them.

## 11. Archive and privacy

Keep validated build artifacts and their provenance privately; the supplied workflows request **14 days for app artifacts** and **7 days for uploaded diagnostic artifacts**. Repository/organization policy can constrain retention. This does not change GitHub's job-log retention configuration. Artifacts expire; they are not a permanent source/data backup. [G5]

Record the source commit, app versions, signing identity and test results. Private GitHub Releases can hold the source ZIP and tested binaries, but never the private keys or real event data. Do not use the repository's Archive setting to pause between active development updates.

This source package has no live Hostinger data backup. A private repository does not make it appropriate to commit guest photos, databases, `.env`, credentials, station certificates, runtime ledgers or signing material.

## 12. What was checked for this revision

The original H5 ZIP was reopened and CRC-checked. Both workflow files, their helper scripts, expected working directories, version metadata, manual triggers and artifact names were inspected. The original 341 file payloads are retained byte-for-byte in the documentation repack.

The supplied tests were actually rerun locally: **10 policy assertions, 15 print-policy assertions and 24 WatchLedger/QueueDb JVM + SQLite bridge cases passed**. These are not full Android builds. The final archive's checksums, additions and original-file preservation are recorded in `GITHUB_BUILD_PACKAGE_R1.md` and `docs/github-h5-r1/VERIFICATION.json`.

This revision did not run GitHub Actions, Android SDK compilation/lint, .NET Windows compilation, cloud deployment or hardware tests. It does not include the fixes made specifically to the separate Printer Lab project.

## References and exact project sources

Project facts above come from `.github/workflows/android-debug.yml`, `.github/workflows/windows-print-station.yml`, `tools/github/android_ci.py`, `tools/github/windows_ci.py`, `clients/android/app/build.gradle`, `clients/print-station/native/LensDropStation.csproj` and `docs/h5/LensDrop_H5_Setup_Guide.md` in this package. Official instructions below were checked on 19 November 2026.

- [G1] [GitHub Desktop: create and publish a repository](https://docs.github.com/en/desktop/overview/creating-your-first-repository-using-github-desktop).
- [G2] [GitHub: upload files and web limits](https://docs.github.com/en/repositories/working-with-files/managing-files/adding-a-file-to-a-repository).
- [G3] [GitHub: manually run a workflow](https://docs.github.com/en/actions/how-tos/manage-workflow-runs/manually-run-a-workflow).
- [G4] [GitHub: repository Actions secrets](https://docs.github.com/en/actions/how-tos/write-workflows/choose-what-workflows-do/use-secrets).
- [G5] [GitHub: download workflow artifacts](https://docs.github.com/en/actions/how-tos/manage-workflow-runs/download-workflow-artifacts).
- [G6] [GitHub: re-running workflows retains the original commit](https://docs.github.com/en/actions/how-tos/manage-workflow-runs/re-run-workflows-and-jobs).
- [A1] [Android: application update and signing requirements](https://developer.android.com/google/play/app-updates).

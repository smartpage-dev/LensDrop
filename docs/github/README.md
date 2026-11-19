# H5 update - read the current guide first

This component is part of H5. Read the root docs/h5/LensDrop_H5_Setup_Guide.html and RELEASE_STATUS.json before the retained earlier instructions below. Cloud schema5; Android native compilation and Windows native compilation remain unverified. H5 supersedes earlier statements that no print source or staff-QR source exists. No compiled APK/EXE is bundled.

---

# LensDrop H4: manual GitHub archive and Android build

Prepared 2026-11-17. This guide applies to the actual H4 source in this package.
No direct ChatGPT connection, Copilot subscription or ChatGPT API key is required.
You perform the upload and trigger builds in your own GitHub account.

## 1. Understand the three separate things

| Place | Purpose | Not its purpose |
| --- | --- | --- |
| GitHub repository | Source code, documentation, version history and workflow files | Live guest-photo storage or a complete production-data backup |
| GitHub Actions | A temporary build machine that compiles the Android source and produces downloadable output | Permanent web hosting or proof that cameras work |
| Hostinger | Your running LensDrop backend and its separately managed persistent data | Android compilation |

The H4 baseline is cloud `0.2.0-alpha.5-hostinger.4`, Android
`0.2.0-alpha.5-h4` (versionCode 4). The Android minimum API is 29 (Android 10),
compile/target API 35. Build configuration is JDK 17, AGP 8.9.1, Gradle 8.11.1,
SDK build-tools 35.0.0. These values come directly from the supplied build files;
the AGP compatibility table confirms the toolchain combination [6].

## 2. Recommended upload: GitHub Desktop (no command line)

Use the original source package, NOT a downloaded copy of your live hosting folder.

1. Extract `LensDrop_H4_GitHub_Ready.zip` locally and install GitHub Desktop [1].
2. Sign in to GitHub Desktop with your GitHub account. Choose **File -> New repository**.
3. Name it `LensDrop`; choose a local parent such as `Documents/GitHub`. Leave the
   optional license and generated gitignore unset; this package supplies ignore rules.
4. Create the repository, then **Repository -> Show in Finder / Show in Explorer**.
5. Copy **everything inside** the extracted `LensDrop_H4_GitHub_Ready` folder into
   the new repository folder. Do not copy that outer folder as another nested level.
   On a Mac, Command+Shift+Period reveals dot-prefixed items. Include `.github`,
   `.gitignore` and `.gitattributes`. Do not overwrite the new repository's `.git` folder.
6. Review Changes, enter `Import LensDrop H4 source and Android CI`, and click
   **Commit to main** (or your default branch).
7. Click **Publish repository** and keep **Keep this code private** selected [2].

Verify the GitHub web page shows this structure at the TOP level:

```text
LensDrop/
  .github/workflows/android-debug.yml
  .gitignore
  .gitattributes
  README.md
  START_GITHUB_HERE.html
  clients/android/app/build.gradle
  clients/desktop/
  cloud/server.js
  deploy/
  docs/github/
  tools/github/
```

The workflow is a separate file in a hidden folder. A ZIP sitting in the repository
does not expose its contents to this workflow.

## 3. Browser-only alternative

Create a **private** GitHub repository. Use **Add file -> Upload files** and upload
the extracted contents in batches. GitHub's browser limit is 100 files per upload
and 25 MiB per file [3]. This package exceeds 100 files, so Desktop is less fiddly.
Keep paths exactly as shown above; do not flatten all files into one directory.

Hidden folders can be missed. To add the workflow manually, choose **Add file ->
Create new file**, enter `.github/workflows/android-debug.yml`, and paste the
complete provided file. Also commit `tools/github/android_ci.py` and the source
folders; the YAML alone is not a whole build system. Repeat for any missed root
ignore/attribute files. The workflow must be on the repository's default branch
for the manual Run workflow button [4].

The GitHub website ignores `.gitattributes` normalization on upload [3]. Keep shell
scripts with their provided LF line endings. All workflow shell invocations use
`bash` explicitly and do not depend on ZIP executable permission bits.

For a simple backup ONLY, attaching the original ZIP to a private Release is enough
for a downloadable copy, but it is not the editable/buildable source layout.

## 4. Run the first Android SDK build

Open the repository on GitHub, then:

**Actions -> LensDrop Android APK -> Run workflow -> choose main**.

Read the Android SDK license [11], check **I accept the Android SDK licenses**, and
choose a signing mode:

- `temporary-test`: no user secrets required; suitable for a first compiler check
  or a disposable phone test. A fresh debug key may be generated each build.
- `persistent-test`: uses your privately saved test key through the Actions secret
  explained in section 6. Recommended BEFORE the first installation you want to
  keep updating without removing app data.

Click **Run workflow** [4]. No workflow is triggered automatically on upload.
On a fresh setup GitHub may require you to enable Actions; organization policies
may restrict permitted actions. Have the owner/admin approve the official actions
used here rather than bypassing an organization restriction.

The workflow uses a GitHub-hosted Ubuntu 24.04 runner, sets Java 17, installs Gradle
8.11.1, prepares Android 35/build tools 35.0.0, runs the included JVM logic tests,
and runs `gradle :app:assembleDebug`. The H4 archive has no wrapper binary; the
workflow intentionally provisions Gradle rather than calling a missing `gradlew`.
It verifies the resulting APK signature before creating the APK artifact [7].

The checked action references are `actions/checkout@v7`, `actions/setup-java@v6`,
`gradle/actions/setup-gradle@v5` and `actions/upload-artifact@v7`. Toolchain versions
are fixed; action references are major tags, not immutable SHA pins. No third-party
build server or production-deployment integration is used.

## 5. Download and interpret the result

A green run should have an artifact named:

```text
LensDrop-H4-APK-<run-number>-temporary-test
```

(or `persistent-test`). Open the run summary and find **Artifacts**, then download
it while logged in [5]. Extract the artifact ZIP to get:

```text
LensDrop-H4-debug.apk
BUILD_INFO.json
SHA256SUMS.txt
README_INSTALL.txt
apk-verification.txt
```

The APK is the file to install on a controlled Android test phone, not the outer ZIP.
Allow installation from the chosen browser/file manager only when needed and verify
the source. This is a debuggable engineering alpha, not a Play Store release.
Pair using the phone's device token, NEVER the cloud administrator key. Personal
QR features need cloud H4; do not infer that uploading source updated Hostinger.

A green compile is NOT evidence of working MediaStore permissions, finished-file
capture, stable background service, camera/editor compatibility, correct guest
assignment or eight-hour resource behavior. Physical-device acceptance is still
required. The workflow does not run emulator/instrumentation/lint tests.

If the run fails, open the failed step and download its available log artifact.
`android-build.log` contains compiler/Gradle details. Share the first meaningful
error and context, not merely the final `Process completed with exit code 1` line.
No APK is provided on failed build/verification. Do not change Hostinger credentials
to fix a build error; they are unrelated.

The configured APK retention is **14 days**, logs **7 days**. Download significant
builds and attach them manually to a private Release for longer project retention.
Artifacts are temporary, not a permanent archive [5, 8]. The workflow records the
source commit, app version, signing mode and APK SHA-256 for traceability.

## 6. Repeated phone tests: preserve the signing identity

Android checks signing identity when updating an installed app [9]. A different
key can cause an update rejection. Do NOT uninstall or clear app data to solve
this while a queue or group history matters.

For repeated testing, create a private TEST key ONCE on your own computer. This
helper needs Python 3 and JDK 17's `keytool`, but not the Android SDK. A JDK 17
distribution such as Eclipse Temurin provides keytool [12]. From the repository:

```sh
python3 tools/github/create_private_test_key.py
```

On Windows with the Python launcher:

```text
py tools/github/create_private_test_key.py
```

The helper creates files in your home folder under `LensDrop-private-signing`,
OUTSIDE the repository, and refuses to overwrite an existing key. Securely back up
both the `.keystore` and the Base64 text file. Base64 is encoding, not encryption.
The conventional debug password/alias are for private testing only; this is not a
production release-signing configuration.

In GitHub go to **Settings -> Secrets and variables -> Actions -> New repository
secret** [10]. Use exactly:

```text
Name: LENSDROP_TEST_KEYSTORE_BASE64
Value: the entire contents of lensdrop-test-debug.keystore.base64.txt
```

Use `persistent-test` in EVERY subsequent build, including the first installation
that should preserve state across updates. The workflow restores the keystore only
on the temporary runner, never logs/uploads it, and deletes the runner copy. Keep
your local backup: repository Secrets are not your recovery copy.

Do not add Hostinger credentials or a LensDrop admin/device token to this workflow.
The default build needs none; persistent test signing needs only the signing secret.
An app already installed from a temporary-key build will not automatically become
compatible with your newly created persistent key. Resolve or preserve all local
work before replacing that disposable installation. Compatible app identity, version
progression and schema migrations also matter; a fixed key alone is not a durability
guarantee. Future production releases need their own reviewed signing plan.

## 7. Archive versions without freezing the project

Use a GitHub **Release** with a tag such as `h4-source-2026-11-17`. Mark it as a
pre-release. Attach the untouched original H4 complete ZIP, a tested APK when one
exists, the build metadata and relevant guide. Release assets and Git history serve
different purposes [8]. Keep a second local/offline backup too.

Do NOT click **Settings -> Archive this repository** just to back it up. That
setting makes the repository read-only; it is for ending active maintenance [13].

For a future update, first pull remote changes in Desktop. Replace only the intended
source files, remove files explicitly retired by the release, and review deletions
and changes before committing. Preserve your `.git` directory, the GitHub workflow
and your private signing identity. Blindly merging new ZIPs can leave obsolete files.
After an application-source change, the retained `deploy/` ZIP is an old release
snapshot until deliberately rebuilt. It does not update itself.

## 8. Safety, permissions and cost

Keep the project private unless you intentionally choose to publish it. Do not add
live `.env`, admin/device tokens, runtime SQLite/queues, guest photos or signing
keys. The supplied `.gitignore` helps with untracked files; it does not remove secrets
already committed or make ZIP contents safe. Review future ZIPs before uploading.
If a real secret is committed, rotate/revoke it; deleting the latest copy is not enough.

The full H4 source/docs/deployment reference are retained, but the historical ChatGPT
conversation and your live Hostinger data are NOT part of this package. Add a
sanitized requirements/handoff note if needed, not screenshots containing keys.
Keep database/media/unfinished upload backups separately protected.

Private-repository Actions compute and artifact storage are governed by your GitHub
plan, limits and organization policy. Check Billing/usage and budgets; do not assume
unlimited free builds [14]. Manual-only runs, one Ubuntu job, no emulator, a 30-minute
job timeout and short artifact retention reduce accidental consumption. No paid
ChatGPT or Copilot capability is required by this workflow.

## 9. Verification of this packaging

All original H4 files are retained byte-for-byte. There is no cloud/Android source
change or deployment in this operation. New additions are ignore/attribute files,
README, workflow, helpers and this guide. See `GITHUB_PACKAGE_STATUS.json` and
`GITHUB_PACKAGE_MANIFEST_SHA256.txt` for the final integrity/validation record.

The local environment has no Android SDK or Gradle and cannot resolve the required
download hosts. The workflow has NOT been run in your GitHub account. YAML/helper
validation and JVM logic tests are not a substitute for an actual first SDK build.
Compilation may expose real source errors that must be fixed before a usable APK
exists. A ready-to-run workflow is not a guarantee of a green first run.

## Official references (checked 2026-11-17)

[1] GitHub Desktop: https://docs.github.com/en/desktop/overview/creating-your-first-repository-using-github-desktop

[2] Publish existing code: https://docs.github.com/en/desktop/adding-and-cloning-repositories/adding-an-existing-project-to-github-using-github-desktop

[3] Browser upload rules: https://docs.github.com/en/repositories/working-with-files/managing-files/adding-a-file-to-a-repository

[4] Manual workflow runs: https://docs.github.com/en/actions/how-tos/manage-workflow-runs/manually-run-a-workflow

[5] Artifact download: https://docs.github.com/en/actions/how-tos/manage-workflow-runs/download-workflow-artifacts

[6] AGP 8.9 toolchain: https://developer.android.com/build/releases/agp-8-9-0-release-notes

[7] Android command-line builds: https://developer.android.com/build/building-cmdline

[8] Releases: https://docs.github.com/en/repositories/releasing-projects-on-github/managing-releases-in-a-repository

[9] Android signing: https://developer.android.com/studio/publish/app-signing

[10] Actions secrets: https://docs.github.com/en/actions/how-tos/write-workflows/choose-what-workflows-do/use-secrets

[11] Android SDK terms: https://developer.android.com/studio/terms

[12] Eclipse Temurin downloads: https://adoptium.net/temurin/releases/?version=17

[13] Archiving versus active work: https://docs.github.com/en/repositories/archiving-a-github-repository

[14] Actions usage: https://docs.github.com/en/billing/concepts/product-billing/github-actions

# LensDrop H4 GitHub packaging - verification

This is an archive/build-infrastructure addition, not an application release.
All 256 original files match the supplied H4 ZIP byte-for-byte, including the
Hostinger deployment ZIP. No production credentials or runtime data were added.
No Android APK is included or claimed compiled.

## Executed locally

- Workflow YAML parsing, manual trigger/read-only permissions/bounded job checks.
- Python syntax and helper happy/failure paths, generated private-test key restore,
  overwrite protection, no-secret output and runner/local cleanup boundaries:
  27 combined workflow/helper/shell checks passed.
- Gitignore acceptance/exclusion: 15 cases passed.
- Java policy harness: 10 assertions passed on local JDK 21.
- Full ledger harness rerun: did not finish within the local 180-second tool limit;
  its partial output is included and is not counted as a passed test.

## Not executed

No Android SDK/Gradle compilation, APK signature verification of a real new APK,
GitHub Actions run, phone installation, camera transfer, physical-device recovery,
or Hostinger redeployment. The local container has no Android SDK/Gradle and its
download attempts failed DNS resolution. GitHub can provide the required toolchain,
but first-build source errors remain possible and must be diagnosed from the log.

The official action interfaces were checked online. Their major-tag references
are not immutable commit pins. No signing key is shipped; the helper creates an
owner-held testing identity locally. This is not production release signing.

See START_GITHUB_HERE.html for upload, build, signing and preservation instructions.

## Added files

- `.gitattributes`
- `.github/workflows/android-debug.yml`
- `.gitignore`
- `GITHUB_PACKAGE_STATUS.json`
- `README.md`
- `START_GITHUB_HERE.html`
- `docs/github/README.md`
- `docs/github/validation/VALIDATION_SCOPE.txt`
- `docs/github/validation/gitignore-checks.json`
- `docs/github/validation/helper-checks.json`
- `docs/github/validation/ledger-tests-rerun.txt`
- `docs/github/validation/policy-tests.txt`
- `tools/github/android_ci.py`
- `tools/github/create_private_test_key.py`

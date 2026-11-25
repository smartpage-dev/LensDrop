# LensDrop H5.12.1 CI Source Repair (alternative GitHub-only method)

## Diagnosis from the uploaded log
- New run, commit `e42d048a5bfc31c43da394ca053c4dd2b589c6bf`, branch `main`.
- Exact same two Android source-snapshot mismatches as before: `TicketLedgerTest.java` and `ArchiveWorkflowTest.java`.
- Android Gradle build/lint were NOT reached; no evidence that APK compilation failed.
- The log does not include the GitHub files' actual hashes. The exact synchronization cause is still unknown.

## Recommended: one-file GitHub Actions approach
1. Add ONE file from this kit to your GitHub repository: `.github/workflows/h5121-source-repair.yml` in the exact same path under repository root. Do not add a second LensDrop-main directory. You can upload via GitHub Desktop or create the file in GitHub web UI.
2. Open GitHub > Actions > `LensDrop - Diagnose and Repair H5.12.1 Source` > Run workflow on `main`, action `diagnose`.
3. Read output for each file's actual SHA-256 and expected SHA-256. `KNOWN_STALE` means that an exact previously shipped source revision was detected.
4. Only if the diagnostic reports `KNOWN_STALE`, run again with `repair_branch`. It will repair those known test versions, run the original 110-file source verifier, and PUSH a DIFFERENT branch `repair/h5121-test-sync-<runid>`. It never changes `main` automatically.
5. Review the new branch / open PR into `main`, confirm exactly two test-file changes, merge, then run a NEW Android APK workflow on the merged commit. Never re-run the earlier SHA.
6. If diagnostic says `UNKNOWN`, do NOT force a hash change: download current GitHub main as a ZIP and upload it for byte-by-byte diagnosis.

## Fallback without Actions permission
Copy the two test files from this ZIP into those exact paths in an up-to-date local Git checkout. Run `python3 tools/github/verify_android_snapshot.py`, commit both changes, push and verify the remote commit. Do not replace H5.12.1 manifest with H5.11.

## Expected SHA-256 values
TicketLedgerTest.java: 34fbd6f7ef5b54ebfca311dc3a465a3e6af298248389bf48699d1bacf5f3b52d
ArchiveWorkflowTest.java: ca5bd66ce9deab9f96cc405924bb161d29154876112a9e4149bd3d41935697a8

## Safeguards
- No Cloud, Android runtime, queue schema, printer protocol, signing, or Hostinger deployment changes.
- No check is bypassed. Unknown remote versions are refused.
- Repair action needs GitHub Actions `contents: write` permission for a **new branch only**, and user approval to merge.
- The workflow has NOT been executed on your actual GitHub repository here. Local tests do not prove success in GitHub.

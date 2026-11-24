# LensDrop H5.7 Windows workflow — cloud source completeness repair

## Actual uploaded GitHub failure

The supplied `logs_122626686982.zip` contains the `protocol-tests` job only. GitHub checked out the source and installed Node 22.23.5 and cloud dependencies. Cloud tests then failed: **16 pass / 22 fail**. The first underlying error is `MODULE_NOT_FOUND` for `./event-workflow.cjs` required by `cloud/src/server.cjs`. The Windows native build job did not run because it depends on protocol-tests.

The exact H5.7 complete GitHub-ready ZIP **does contain** `cloud/src/event-workflow.cjs` (20,479 bytes). The Hostinger root ZIP also contains `src/event-workflow.cjs`. Therefore the failed GitHub checkout is incomplete or inconsistent relative to the delivered H5.7 source. The log does **not** establish why the file was absent from the commit. This is not a Windows driver or CP9550DW issue.

## Recommended repair

Use `LensDrop_H5_7_Cloud_Station_Source_Recovery_Overlay.zip` only if your current repo is intended to be H5.7. Unzip and merge the **contents of its inner folder** into the existing `LensDrop` repo root, preserving `.git`, private credentials and any user changes you intend to keep. The overlay restores the complete cloud runtime/tests and station runtime/tests from the delivered H5.7 snapshot, not just one missing file. Review any local modifications before overwriting. It does not contain production data, keys, database files or cloud deployment instructions.

The Windows workflow now runs `Verify complete H5.7 Cloud and Print Station source (no mixed versions)` **before npm install**. It compares exact SHA-256 hashes for runtime and test inputs. A missing or stale file produces an explicit path. Do not bypass the check to obtain an EXE.

1. Confirm `cloud/src/event-workflow.cjs` exists on GitHub under that exact path (case-sensitive).
2. Confirm `CLOUD_STATION_SOURCE_SNAPSHOT_H5_7.json`, `tools/github/verify_h57_cloud_station_snapshot.py`, and `.github/workflows/windows-print-station.yml` are committed.
3. Commit and push the repaired files. Start a **NEW** `LensDrop Windows Print Station` workflow on the updated branch; do not Re-run jobs on the old commit.
4. Verify the snapshot, `npm --prefix cloud run build`, `npm --prefix cloud test`, and 57 station tests all pass. Only then should the Windows x64 job run and upload the portable EXE artifact.
5. No Hostinger redeployment, Windows application installation, Android change, signing-key rotation or printer setting change is required to fix this CI source mismatch.

## Local verification boundaries

The exact original H5.7 source with all files present passed cloud build and **156/156 cloud tests** using the preinstalled pinned sharp 0.34.1 dependency (not a fresh npm install). The Windows station tests passed **57/57**. This is not a GitHub rerun or Windows native compilation. The new source snapshot gate was tested against complete, missing-file and stale-file copies. The exact cause of the incomplete GitHub checkout is unproven.

**Important:** The Windows workflow may reveal a separate .NET/native issue after this prerequisite passes; do not infer success before the new run completes.

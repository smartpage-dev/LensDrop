# LensDrop H5.7.1 — Windows checkout line-ending repair

## Diagnosis (GitHub Windows workflow run 7, 24 Nov 2026)

The Ubuntu `protocol-tests` job verified all 107 source files and passed 156 cloud / 57 station tests. The Windows `windows-build` job stopped in the **source snapshot verifier before native compilation**: 96 files reported `STALE/MODIFIED`. Every reported Windows hash equals the original LF file after LF→CRLF conversion. No content change, missing source, compiler failure or printer failure is demonstrated by this run.

The previous `.gitattributes` specified LF for `.py`, `.yml`, `.java`, etc., but not `.cjs`, `.js`, `.cs`, `.html`, `.css`, `.md`, `.txt`, and several other source types. With Windows Git checkout, those files were automatically converted to CRLF, invalidating byte-exact SHA-256 checks.

## Minimal fix

Only `.gitattributes` is changed in the source tree. It explicitly sets `text eol=lf` for cross-platform source and documentation extensions used in the 107-file manifest. Existing `*.cmd` / `*.bat` CRLF and binary rules are retained. No changes to application logic, cloud schema, Android, Windows printer engine, test expectations, manifest hashes, or Hostinger configuration.

## GitHub Desktop steps

1. Download and extract `LensDrop_H5_7_1_Windows_Checkout_EOL_Fix.zip`.
2. Copy the **contents** of the `LensDrop_H5_7_1_EOL_PATCH` folder to the root of the existing LensDrop repository. Replace `.gitattributes`; preserve `.git`, private data and signing secrets. Make sure the hidden dotfile was copied.
3. Inspect the GitHub Desktop diff: the only existing source file changed should be `.gitattributes`. The accompanying guide is documentation only.
4. Commit and **Push origin**.
5. Start a **new** Actions → LensDrop Windows Print Station → Run workflow on the updated branch. Do not Re-run jobs on the old SHA.
6. Expected: Ubuntu source gate 107 pass, cloud tests 156 pass, station tests 57 pass; then Windows source gate 107 pass, followed by native build and packaging. The latter stages remain unverified until the new run completes.

## Reproduction and verification

The previous `.gitattributes` was tested in a local Git repository with `core.autocrlf=true`: the verifier failed with exactly **96** stale hashes. Repeating the same Git clone/check-out with the corrected `.gitattributes` made the byte-exact verifier pass all **107** files. `git check-attr` confirmed `eol: lf` for representative Cloud/Station files. This is a cross-platform checkout simulation, not a real Windows GitHub build.

## Do not do

Do not turn off the source verifier, change SHA-256 hashes to CRLF values, disable cloud/station tests, modify Windows printer settings, reinstall Android, or redeploy Hostinger for this fix. If the next run fails later, use the **new** Windows logs to diagnose that later stage separately.

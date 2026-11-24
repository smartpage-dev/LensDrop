# LensDrop H5.6.1 - Android lint orientation hotfix (same code 12)

Source evidence: GitHub log logs_122421405791.zip, run 6. Source snapshot (91 files), Android logic tests, Java compilation and assembleDebug passed. lintDebug failed with 2 errors and 44 warnings. The log only contains the first error detail: PrintActivity.java:62 [WrongConstant], panel.setOrientation(1). The second error detail is not present in the uploaded log. A source-wide search identified one more matching call in RecoveryActivity.java:26; both are corrected.

Changes: setOrientation(1) -> setOrientation(LinearLayout.VERTICAL) in two existing activities; add 2 UI source contracts; refresh the SHA256 source snapshot for these three files. No production behavior, pairing, photo/print queue, cloud, Windows, credentials, Gradle or signing changes. Version stays 0.2.0-alpha.6-h5.6.1 / code12. The Git commit SHA distinguishes this correction from the failed run.

Apply to the exact delivered H5.6.1 repository root, including ANDROID_SOURCE_SNAPSHOT_H5_6_1.json. Commit and push, then start a NEW LensDrop Android APK workflow. Do not rerun the old run or disable lint. A partial upload is detected by the existing source verifier. Use the full ZIP instead if repository consistency is uncertain. Do not clear phone data or change signing identity.

Local verification: source snapshot 91/91 passed; UI runner 60 parser, 54 source contracts, 39 scanner fakes, 36 Java syntax parses passed; build integrity, session, print policy, print profiles and watch ledger passed. An optional separate ticket-ledger run reached a bridge-closed error and was interrupted, not counted as a pass. Android SDK/Gradle unavailable locally: no native compile, lint, APK, or device test performed. The 44 lint warnings are not resolved or individually reviewed; full lint report is needed if additional issues persist.

After the new GitHub run: confirm assembleDebug and lintDebug both pass, and download the verified APK artifact. If lint still fails, provide the full lint-results-debug.txt or lint-results-debug.html from the build-log artifact.

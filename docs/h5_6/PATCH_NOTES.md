# Applying this patch

Only merge this patch into the exact delivered H5.5 source. It is not a patch for the older H5 screen shown in the submitted images. Use the full H5.6 project when the repository baseline is older or uncertain; compare/merge any custom modifications first. Keep .git, the installed signing identity and all private runtime data.

Copy the contents of the patch folder into the repository root. Do not upload just the ZIP or add an extra enclosing folder. Include the changed .github/workflows/android-debug.yml. No files are deleted by this increment. Commit and push, then start a NEW LensDrop Android APK workflow on that commit. The mandatory Android lint gate remains.

Cloud, Windows, their deploy packages and signing helpers remain the exact H5.5 files. There is no new cloud redeployment for an already-H5.5 system. Android version is 0.2.0-alpha.6-h5.6 / code11. The APK artifact retains the LensDrop-H5-debug.apk filename. No native APK is included in this source patch.

H56_FILE_MANIFEST.json describes the new complete source. Historical manifests belong to their historical versions. Full source and patch are alternatives; do not apply the patch twice or to Printer Lab.

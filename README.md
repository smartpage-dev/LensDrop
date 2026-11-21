# LensDrop H5.3 - manual Bluetooth guest QR ticket pilot

Start with [START_QR_TICKET_H5_3.html](START_QR_TICKET_H5_3.html). Android versionCode8, queue schema4. No APK is included; run the existing Android workflow with the same signer. Cloud/Windows/CP9550DW deployment is unchanged. No Hostinger redeploy or Windows rebuild needed. This increments H5.2; Printer Lab stays separate.

# LensDrop H5.2 - CP9550DW profiles and paired strips

Start with [START_CP9550DW_H5_2.html](START_CP9550DW_H5_2.html), [setup guide](docs/h5_2/CP9550DW_SETUP.html) and [release report](docs/h5_2/RELEASE_REPORT.md). Android code7 / 0.2.0-alpha.6-h5.2; Windows 0.1.1-alpha.h5.2. Rebuild both through the existing GitHub workflows. Preserve signing identity and station data. Existing cloud/schema5/deploy ZIP unchanged; no Hostinger redeploy for this update.

Native builds and hardware validation of this revision are outstanding. Manufacturer documentation confirms model-level 2x6 multi-cut; actual driver settings and cutter geometry require calibration. Three explicit presets distinguish 4x6, auto-cut pair and manual-cut pair. Legacy profiles are retained; no automatic printer reassignment. Use H5_2_MANIFEST_SHA256.txt for this snapshot. Older release reports/manifests below are historical.

## Historical H5.1 / H5 notes (not current native-build status)

# LensDrop H5.1 - GitHub build repair

Start with [START_GITHUB_H5.html](START_GITHUB_H5.html). This fixes the two Android compile errors and the cloud release-fixture failure in the uploaded runs. Android: `0.2.0-alpha.6-h5.1`, code 6. No compiled APK or EXE is included; new GitHub runs are required. No Hostinger redeployment is needed. Keep existing signing keys. Use `H5_1_MANIFEST_SHA256.txt` for this snapshot; older manifests/reports below are historical.

[Repair report](docs/h5_1/BUILD_FIX_REPORT.md) | [Upgrade guide](docs/h5_1/UPGRADE_GUIDE.md)

## Original H5 overview

# LensDrop H5 - QR Pairing and Hybrid Print

Engineering source release. Start with [START_HERE.html](START_HERE.html) and [the current guide](docs/h5/LensDrop_H5_Setup_Guide.md).

Cloud adds staff QR onboarding and private print jobs. Android and Windows add pairing, setup and LAN/cloud print source. **No APK or Windows executable is included.** Run the manual GitHub workflows to attempt native builds; they were not run here. Preserve the existing Android signing secret. No assistant action deploys your live Hostinger site.

Use only deploy/LensDrop_Hostinger_Cloud_H5_QR_Pairing_Print.zip for Hostinger. Schema5 needs a stopped full-data backup, preserved credentials/path/storage ID and initialization=0. Read the rollback warning before deployment.

Source: cloud/, clients/android/, clients/desktop/ (unchanged), clients/print-station/. GitHub: .github/workflows/android-debug.yml and windows-print-station.yml. Tests and limits: docs/h5/LensDrop_H5_Release_Report.md. Old documents remain historical references; H5 instructions take precedence.

# LensDrop H5 - QR Pairing and Hybrid Print

Engineering source release. Start with [START_HERE.html](START_HERE.html) and [the current guide](docs/h5/LensDrop_H5_Setup_Guide.md).

Cloud adds staff QR onboarding and private print jobs. Android and Windows add pairing, setup and LAN/cloud print source. **No APK or Windows executable is included.** Run the manual GitHub workflows to attempt native builds; they were not run here. Preserve the existing Android signing secret. No assistant action deploys your live Hostinger site.

Use only deploy/LensDrop_Hostinger_Cloud_H5_QR_Pairing_Print.zip for Hostinger. Schema5 needs a stopped full-data backup, preserved credentials/path/storage ID and initialization=0. Read the rollback warning before deployment.

Source: cloud/, clients/android/, clients/desktop/ (unchanged), clients/print-station/. GitHub: .github/workflows/android-debug.yml and windows-print-station.yml. Tests and limits: docs/h5/LensDrop_H5_Release_Report.md. Old documents remain historical references; H5 instructions take precedence.

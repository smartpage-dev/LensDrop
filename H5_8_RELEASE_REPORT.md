# LensDrop H5.8 Android Gallery + Guest QR UX pilot

Baseline: the exact `LensDrop_H5_7_1_Windows_Checkout_EOL_Fix_Full_GitHub_Ready.zip` archive from this conversation. Cloud/Windows sources are retained unchanged. This is an Android source-only update, **not** an APK, live deployment, or device certification.

## Delivered

- Native Android gallery (three columns, 5s foreground refresh, max 90 loaded items, 12MiB bitmap cache, two thumbnail workers). Photos are from the phone's event-scoped durable queue, not a claim of full camera-card or cloud-library completeness.
- Enlarge, file info, local star, reversible hide, guest QR lookup/assignment, existing print-photo confirmation, existing ticket confirmation. No automatic print, guest reassignment, cloud deletion or hard database removal.
- Guest Delivery fixed two-row daily action dock; explicit Group/Single selection, one-tap prepared group QR creation, Previous/Current QR actions with unchanged receiving group, existing history/Undo and print safety retained.
- Additive photo QueueDb schema7 gallery_marks; existing photo records and assignments preserved; print QueueDb unchanged. New Android version `0.2.0-alpha.6-h5.8` code14.
- H5.8 Android source snapshot guard (98 exact inputs), new 5-case gallery SQLite bridge tests, 23 UI source contracts; mandatory Android lint/assembly kept in GitHub.

## Safety boundaries

- 'Remove from DB' is deliberately represented as reversible local hide. Hard deletion could break upload retries, cloud/guest history or print reconciliation. Support/Recovery is the existing audited alternative for unresolved work.
- A watched JPEG does not automatically create a guest QR. The photographer must start/choose a Group or select a Single Photo. If links are not prepared, H5.8 prepares them online at the explicit action.
- Gallery thumbnail cloud fallback uses the paired phone's authorized API; offline/cloud-unavailable images can show placeholders, and local print requires an accessible source JPEG.
- No auto assignment based on uncertain arrival order, no auto ticket/physical print, no backend/gallery publication change.

## Verification limits

Local exact-source, parser and JVM/SQLite checks passed as recorded in `H5_8_PACKAGE_CHECKS.json`. No Android SDK/Gradle build, lint, emulator or physical phone run was executed here. H5.7.1 Windows build status is not re-certified by this Android update. Existing occasional SQLite bridge hangs were observed in earlier runs; completed tests are counted only when they printed their final success marker.

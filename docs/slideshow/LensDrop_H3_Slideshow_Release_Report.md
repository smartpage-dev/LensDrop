# LensDrop H3 Slideshow - implementation and validation report

Release: 14 November 2026. Cloud version: **0.2.0-alpha.4-hostinger.3**. Classification: **engineering alpha, controlled rehearsal**.

## Delivery

This is a real cloud program update over H2, not a mockup. The complete archive includes the cloud, unchanged H2 desktop/Android client sources, all retained manuals, the new five-page Traditional Chinese Admin/Slideshow guide, tests, UI renders and an inner Hostinger cloud ZIP. There is no APK, iOS app, Windows installer or live hosting certification.

Admin is served at `/admin` and `/`; it uses the existing administrator key, not a new account/RBAC system. It is distinct from Hostinger hPanel. H2 event creation, device credentials, counts, diagnostics, moderation, pagination and durable upload protocols remain in place. H3 adds authenticated delivery-image previews and the following presentation features.

## Implemented features

- Separate per-event, random, revocable slideshow capability at `/s/<token>`; it does not expose the admin key or device token. All newly created and migrated events start with projection disabled.
- Read-only projection feed and delivery-image routes enforce enabled token, event ownership, `live` visibility and `is_test=0`. Review, Hidden and preflight tests do not enter playback. Admin preview does not publish an image.
- Admin opens/copies both slideshow and guest-gallery links and generates both QR codes locally. Download PNG or SVG. No third-party QR service, CDN or new npm package is used. The vendored QR encoder and license/provenance are included.
- Shared event name/logo and logo background across projection and guest gallery. The corner placement/mode applies to the projector, not the guest header layout. Logo PNG/JPEG/WebP <=2 MiB, input <=16 MP, no SVG/animation, decoded and re-encoded as PNG max1200x600 without enlargement. Metadata is removed by re-encoding.
- Fade, gentle zoom and horizontal slide/fade transitions. Default whole-photo `contain` fit, with optional crop-to-fill `cover`. Four branding corners and logo/name/both/none; black/charcoal/white screen background.
- Default eight seconds per image (3-30), ten-second feed polling (5/10/15), latest 200 published uploads (50/100/200/500). Order newest, oldest-in-window or shuffled; these are upload-sequence orders, not EXIF capture times. This is not an unbounded whole-album loop.
- New photos enter the upcoming playlist on a successful feed check without restarting the active slide on every poll. A timing regression test uses 12-second slides with five-second polling.
- Two image layers only, existing delivery JPEGs (long edge <=2560 px), no original-file decoding/full-album preload. There is no claimed measured RAM ceiling or native-4K delivery.
- Local previous/next/pause/style override, keyboard shortcuts, fullscreen user action and best-effort screen wake lock. Local controls affect that browser only. Normal pause still checks publication visibility.
- Optional audience gallery QR on the opposite corner. The feed includes the guest-gallery capability only when an administrator enables this option. Turning this off does not revoke a gallery link already disclosed.
- Remote disable/rotation leaves the guest-gallery link and device credentials unchanged. Active pages clear on the next successful check. After 30 seconds without a freshly verified feed, photos, brand and audience QR are concealed until reconnection. A suspended browser/OS cannot be guaranteed to execute a timer on time; this is not DRM and cannot recall already saved copies.
- Reduced-motion preference disables transitions. Portrait images are kept whole by default; side bars are expected, not a failure.

## Explicitly not added

No camera SDK/FTP receiver, native mobile build, iOS client, per-photo projection-only exclusion list, remote pause of all screens, music/video, offline whole-album cache, slideshow PIN/expiry, or independently replicated backup. Test photos cannot be promoted to public photos. No claim of superiority to a competitor is established by these checks.

## Upgrade and persistence

Use the inner `LensDrop_Hostinger_Cloud_v0.2-alpha_H3_Slideshow.zip` for a Node.js Web App; never upload the complete outer project ZIP. Entry `server.js`, build `npm run build`, start `npm start`, root/output `.`. Executed runtime: Node22.16.0, sharp0.34.1. Node22>=22.13 within22.x is the tested family; declared Node24 has not been tested here.

Pause uploaders, stop the only server process and take a consistent full data-directory/config backup before upgrade. Keep `LENSDROP_DATA_DIR`, `LENSDROP_STORAGE_ID`, `LENSDROP_ADMIN_KEY` and existing device credentials. Keep `LENSDROP_INITIALIZE_STORAGE=0` for an existing deployment. New empty deployments still need the separately documented one-time initialization. Private persistent local disk outside the deployment tree and one active writer are still mandatory; live Hostinger confirmation/redeploy validation is not completed.

Schema migrates to 3, adding event presentation records. Backups must include the new `branding/` folder along with the database, originals, derivatives, in-progress chunks and storage markers. Rollback means matching H2 code plus the complete pre-H3 data backup, not merely replacing code. New uploads made after the backup require separate preservation/reconciliation.

## Executed validation

| Check | Result and boundary |
|---|---|
| Node suite | **54 tests passed**: original H2 coverage plus projection authorization, live/test/foreign isolation, settings validation, logo handling, revoke/rotation, schema persistence, bounded playlist and QR/template checks. |
| Desktop regression against H3 | **23 tests passed**. Client sources unchanged. This is Linux-side execution, not Windows/macOS field certification. |
| Instrumented UI / QR | **18 checks passed**, using actual HTML/CSS/JS and actual local backend through a test-only fetch/image bridge. Both downloaded QR PNGs independently decoded to their exact respective URLs. |
| Native browser navigation | **Blocked by environment policy** (`ERR_BLOCKED_BY_ADMINISTRATOR`). The instrumented bridge is NOT a native HTTPS/CSP/network end-to-end pass. |
| Fresh npm installation | **Not completed**, registry DNS `EAI_AGAIN`; tests use the existing exact sharp0.34.1 installation. No lockfile or fresh dependency audit was produced. |
| Physical validation | Hostinger deployment, projector/display hardware, camera/phone, long event soak and APK compilation **not performed**. |

Raw evidence lives in `docs/validation-h3/`. UI renders use labelled synthetic artwork and a test logo; they are real application UI in the instrumented test browser, not real event photos or a physical projector demonstration. The optional developer harness in `tests/ui/` must not be deployed as the production server.

## Rehearsal acceptance

1. Upgrade an isolated backed-up test event; confirm existing photos and device tokens survive restart/redeploy.
2. Upload one portrait and one landscape ordinary JPEG; publish both. Verify Review, Hidden and test images are absent from the slideshow.
3. Add shared branding and enable projection. Scan each downloaded QR on a separate real device and verify its destination.
4. Open the slideshow on the actual second computer, move it onto the projector display, click Fullscreen and disable OS sleep/notifications manually.
5. Publish a new image while the slide interval exceeds the poll interval; verify it joins without freezing playback. Pause, hide the visible photo and verify withdrawal on the next check.
6. Disconnect the network; after freshness expires, verify a waiting screen. Restore connectivity and verify recovery. Test link disable and link replacement.
7. Run a representative event rehearsal and monitor server disk/CPU, gallery latency, projector memory, thermal behavior and network use before production use.

## References

- Fullscreen user activation: https://developer.mozilla.org/en-US/docs/Web/API/Element/requestFullscreen
- Wake-lock support and lifecycle: https://developer.mozilla.org/en-US/docs/Web/API/Screen_Wake_Lock_API
- This release's implementation: `src/presentation.cjs`, `public/presentation-admin.js`, `public/slideshow.js`, `public/slideshow-model.js`, `tests/presentation.test.cjs`, `tests/slideshow-model.test.cjs`.

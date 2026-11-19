# H3 presentation API supplement

Existing API: `../API.md`. Authentication remains Bearer `LENSDROP_ADMIN_KEY` for administrative endpoints; never put this key in a public URL. Projection/guest links are bearer capabilities for read-only published content, not authenticated individual-user sessions.

| Route | Access / behavior |
|---|---|
| GET `/admin` | Static admin UI; API requests need admin key. |
| GET `/v1/events/:eventId/presentation` | Admin. Settings, revision, enabled state, relative projection/gallery URLs, logo metadata. |
| POST `/v1/events/:eventId/presentation` | Admin JSON partial update: `name`, `enabled`, `settings`, optional `policy`, optional current `revision`. Unknown fields/invalid ranges rejected; stale revision409. Policy applies to subsequent uploads, not existing photo publication state; the H3 UI changes name/settings, not policy. |
| POST `/v1/events/:eventId/presentation/rotate` | Admin. Replace random projector token; existing gallery/device credentials unchanged. |
| GET/POST `/v1/events/:eventId/logo` | Admin. POST raw PNG/JPEG/WebP body, correct content type, <=2MiB/16MP; no animations/SVG; re-encode max1200x600 PNG. GET PNG preview. |
| POST `/v1/events/:eventId/logo/remove` | Admin. Remove shared logo. |
| GET `/v1/photos/:photoId/delivery` or `/thumb` | Admin image preview; does not change status. Existing `/original` download retained. |
| GET `/s/:token` | Static projection shell, no API capability validation until feed access. |
| GET `/v1/s/:token` | Active projection token. Latest-N live non-test photos, totals, name, settings, branding, lease30s. Guest URL only returned when audience QR enabled. |
| GET `/v1/s/:token/photos/:photoId/delivery` | Active token + same event + live non-test image; no original download route. |
| GET `/v1/s/:token/logo` | Active projection token. |
| GET `/v1/g/:slug/logo` | Existing valid guest capability. Guest metadata response now includes shared branding. |

Settings defaults: `style:fade`, `interval_seconds:8`, `poll_seconds:10`, `fit:contain`, `background:black`, `logo_background:dark`, `order:newest`, `brand_mode:both`, `brand_position:bottom-right`, `show_gallery_qr:false`, `pool_limit:200`.

Settings constraints: styles fade/zoom/slide; interval integer3..30; poll5/10/15; pool50/100/200/500; fit contain/cover; background black/charcoal/white; logo_background transparent/light/dark; order newest/oldest/shuffle; brand_mode both/logo/name/none; position four corners; show_gallery_qr strict boolean. URLs are relative to the deployment origin. Public images and API responses remain no-store. This does not recall saved images or screenshots.

Example admin change (send JSON to the event presentation endpoint):

```json
{"enabled":true,"settings":{"style":"fade","fit":"contain","interval_seconds":8,"poll_seconds":10,"show_gallery_qr":true}}
```

Schema3 stores capabilities, settings, logo key and revision in `event_presentation`. Existing H2 upload/heartbeat/receipt contracts remain unchanged. Single-instance local SQLite and persistent file storage are still required.

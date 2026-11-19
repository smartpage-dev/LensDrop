# H2 additions (takes precedence over historical endpoints below)

Device GET /v1/device advertises capabilities.isolated_preflight and operations_summary. Device GET /v1/device/summary reports received client jobs, unique cloud photos and publication counts. GET /v1/device/preflight?client_id=UUID returns only an owned isolated-test job.

POST /v1/uploads accepts boolean is_test (default false). Its purpose is immutable by client_id. Identical content cannot cross test/production purpose for the same event/device. Test publication is rejected and public gallery/asset access excludes tests regardless of status. Use a different normal JPEG for publication rehearsal.

Admin GET /v1/events/:id/status uses before=SEQ, 200-photo pages and next_before. Totals are event-wide. Admin GET /v1/events/:id/reconciliation returns a current, identity-bearing operational report; /diagnostics returns allowlisted redacted aggregates. Treat reconciliation as confidential. Heartbeats need received/queued/uploaded/blocked and must be <=90 seconds old for reconciled=true.

Public POST /v1/g/:slug/visible accepts {"ids":[UUID,...]} (at most 500) and returns currently Live production IDs only. Public gallery response adds total. It remains public-by-link, not a guest-password system.

# Retained base API reference

# Upload and event API - v0.2 engineering alpha

All deployed requests require HTTPS. Use `Authorization: Bearer <token>`. Administrator and device tokens are distinct. JSON requests require `Content-Type: application/json`. No cookies, wildcard CORS, or anonymous upload endpoints are provided.

## Event administration

| Method and route | Credential | Body / result |
| --- | --- | --- |
| GET `/health` | None | Version, chunk size, single-instance storage status |
| POST `/v1/events` | Admin | `{ "name": "Example event", "policy": "review" }`; returns id, gallery_path, slug |
| GET `/v1/events` | Admin | Event list |
| POST `/v1/events/{event_id}/devices` | Admin | `{ "label": "Photographer phone A" }`; returns device_id and one-time plaintext token |
| GET `/v1/events/{event_id}/status` | Admin | Device counts, heartbeats, latest 200 photos, publication counts |
| POST `/v1/devices/{device_id}/revoke` | Admin | Revokes new requests; stored photos/queue are not erased |
| POST `/v1/devices/{device_id}/rotate` | Admin | Issues a new token for the SAME device ID and clears revocation |
| POST `/v1/photos/{photo_id}/status` | Admin | `{ "status": "live" }`, `review`, or `hidden` |
| GET `/v1/photos/{photo_id}/original` | Admin | Exact bytes received from the uploader |
| GET `/v1/device` | Device | Bound event/device identity and limits |
| POST `/v1/device/heartbeat` | Device | Optional nonnegative numerical counts and telemetry |

Token rotation has an API but no console button. Use rotation, rather than a new device identity, when recovering an existing pending queue. Re-pair with the new token and retry blocked files after resolving the credential issue. Rotation/revocation should be independently integration-tested before field use; they are not a full session/account management system.

A gallery path exists at event creation, even before any photos are Live. Automatic QR rendering is not implemented in this core. An external QR can encode the HTTPS gallery URL.

## Resumable upload sequence

1. Persist a random client UUID, source identity, event/device binding and queue state BEFORE starting network transfer. Do not regenerate the UUID after an uncertain response.
2. Wait for the source to finish writing, stage one immutable private copy, and compute SHA-256 by streaming bytes. Do not decode the entire image on the phone.
3. POST `/v1/uploads` with `{ "client_id": "UUID", "filename": "IMG_0001.JPG", "size": 123456, "sha256": "64_lowercase_hex_characters" }`.
4. Persist `upload_id`. On every restart/retry, GET `/v1/uploads/{upload_id}` to obtain the authoritative server offset. If the first session-creation response was lost, repeat step 3 with the SAME client UUID.
5. POST raw bytes to `/v1/uploads/{upload_id}/chunks?offset={offset}` with `Content-Type: application/octet-stream`, correct `Content-Length`, and `X-Chunk-SHA256`. Send exactly 1,048,576 bytes, except the final shorter chunk. Do not send two chunks for the same upload concurrently.
6. Persist returned offset after each acknowledged chunk. When all bytes are accepted, POST `{}` to `/v1/uploads/{upload_id}/complete`.
7. Compare receipt `sha256` and `bytes` against the immutable staged file and check `photo_id`. Only then mark the job uploaded. Delete only the app-owned staged duplicate; never the watched source or camera card original.

Response shape:

```json
{
  "upload_id": "UUID",
  "offset": 1048576,
  "chunk_size": 1048576,
  "size": 6000000,
  "sha256": "...",
  "state": "receiving",
  "receipt": null
}
```

At completion, `state` is `complete` and `receipt` contains `photo_id`, `sha256`, `bytes`, publication `status`, `stored_at`, and `storage: "single-instance-local-disk"`.

This is a custom reference protocol. It is NOT tus, S3 multipart, or the resumable HTTP protocol used by Apple's background transfer infrastructure. An iOS-native-compatible gateway is future work; do not claim interoperability.

## Idempotency and identity

Session uniqueness is `(event_id, device_id, client_id)`. Reusing a client UUID for different content is rejected. Committed content is deduplicated within the same event and device by SHA-256. Two different devices' `IMG_0001.JPG` files cannot overwrite each other. Identical bytes from different devices remain distinct provenance records by design.

A replayed committed chunk is accepted only when its size and hash match. A session status query resolves a lost acknowledgement. Server crashes are recovered using durable chunk files, persisted offsets and idempotent finalization. Partially received files are never public.

## Retry matrix

| Condition | Client action |
| --- | --- |
| No internet, timeout, lost acknowledgement | Retain queue; re-query session; retry with backoff |
| 408/425/429/500/502/503/504 | Retry; honor Retry-After; use a shared cooldown, not a tight per-photo loop |
| 409 upload busy / offset changed | Re-query state, then retry safely |
| 409 client ID reused / conflicting chunk | Block and inspect; do not silently overwrite |
| 507 storage low / event quota | Retain queue, slow retries, require storage/quota intervention |
| 401 revoked/invalid token | Stop transfers, retain queue, replace credential for the same device identity |
| 400/403/404/413/415/422 | Block the affected job and surface the reason; do not discard it |
| Missing or changed source | Preserve queue/error; locate the original or intentionally import the changed version |
| User pause / resource guard | Stop starting work; retain state and resume after the cause clears |

Retryable errors have no fixed attempt-count deletion rule. Jittered backoff is capped near 60 seconds in the reference clients; a longer server Retry-After is retained per job. No promise is made that the OS will schedule the retry immediately.

## Public delivery

`GET /v1/g/{slug}` lists only Live assets with optional `?before={seq}` pagination. `GET /v1/g/{slug}/photos/{photo_id}/thumb` and `/delivery` return authorized Live derivatives; `?download=1` requests download disposition. Review/Hidden images return 404 through public asset routes. Previously downloaded copies cannot be recalled by changing status.

The original uploaded bytes are exact, but may already be a resized/exported file from the official camera app. The API cannot claim the RAW or full-resolution camera original unless that is what the upstream app supplied.


## Hostinger H1 addition: GET /v1/admin/deployment

Requires the administrator bearer token. Returns release/runtime details, private data path, saved storage identity, startup ID and event/photo/incomplete-upload counts. It returns no administrator or device secrets. `persistence_verified_by_host` and `backup_verified` remain false: live durability evidence is external to this probe. The admin Check deployment button calls this endpoint.

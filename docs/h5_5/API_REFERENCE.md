# H5.5 protocol addendum

Existing nine-field photo print requests remain unchanged. New archive/guest/staff records are separate; do not add these fields to the old immutable print protocol.

## Archive and guest delivery

POST /v1/uploads accepts optional boolean archive_private. When true, delivery_id and is_test cannot be set. The flag is immutable and returned by resume/init/complete. Successful original-byte receipt remains required. An unassigned private photo is Review even in a live event. Existing clients without this flag preserve prior policy. Duplicate identities/bytes are reconciled by existing scoped upload rules, not filenames.

POST /v1/device/photos/{photo_id}/delivery: {delivery_id}. Authenticated originating device must own both the photo and open guest collection in the same event. Bind-once and single-photo cardinality enforced transactionally. Returns photo_id,delivery_id,released,public_status. Hidden/test excluded. Guest personal-release policy is separate from public status.

GET /v1/device/photos/{photo_id}/thumb: owner-authenticated immutable preview. No unauthenticated asset links exposed.

GET /v1/device/workflow returns print_route,revision,archive_policy,station_upload_hub:false. GET/POST /v1/events/{event}/workflow requires Admin; POST {print_route:'hybrid'|'cloud',revision} rejects stale revisions. This is a client default, not cancellation/relabeling of pending jobs.

## Staff access

POST /v1/events/{event}/stations/{station}/staff-desk requires Admin: {enabled:boolean,max_copies:1..5}. Initial state disabled. The station desk is scoped to the station's event and target station; it may view/assign nonhidden event photos from all participating photographers. No Admin credential is forwarded by Windows.

Two prefixes share staff actions:
- /v1/events/{event}/staff/{action} with Admin authorization.
- /v1/station/staff/{action} with active station credential + enabled access.

Windows /api/staff/{action} is a whitelist proxy on its authenticated loopback dashboard; X-Station-UI is required. It does not accept arbitrary URLs or arbitrary cloud paths.

| Action | Contract |
|---|---|
| GET config | Event/default/stations/profile scope/maxcopies |
| GET photos?before=seq | 50 private eligible records; next_before; excludes test/hidden |
| GET photo/{id}/thumb or delivery | Protected preview bytes; no identity disclosure to guest |
| POST lookup | {reference, optional before}; full /p/32hex HTTPS link, collectionUUID or optional REF +8hex; event scoped; ambiguous409; no print |
| POST assign | {photo_id,delivery_id}; bind once; no print/publication |
| GET orders | Last100 linked jobs in event/station scope |
| POST orders | Immutable order below; explicit staff auth, quotas and profile checks |
| POST cancel-prepared | {job_id}; originating staff actor, incomplete non-executable only; never recalls station work |
| POST collect | {job_id}; operator_confirmed required; idempotent actor/timestamp; never creates print |

## Staff order body

{id,photo_id,station_id,profile_id,profile_revision,copies,delivery_id:null|UUID,reprint_of:null|UUID}

id is a durable client-generated UUID created at explicit confirmation, saved before the POST. Retry exact body/id. Same actor/body returns existing job; conflicting reuse409. No automatic retry after an uncertain physical print. Max32MiB JPEG and CP pair input1:3 apply. Profile revision and guest binding must still match; revalidate after file-copy I/O. Archive original stays separate from private print-cache copy. Reserve quota/order identity atomically; .copying temporary -> fsync -> rename .part -> directory sync -> ready. Failed preparations retain their identity/reservation for safe retry/cancellation. Existing retention later removes eligible terminal cache, not originals or ledger.

No guest API may create orders. Staff service identities have is_service=1, no usable credential, upload=false. They are omitted from photographer UI/reconciliation and cannot be rotated into a phone credential. The station's existing authorization cache includes them for print execution. Disabling the staff desk prevents new staff operations, not recalling already accepted print jobs.

## Collection and origin linkage

Photo bindings for phone-origin jobs require exact event/device/SHA256/byte size, and are added when a matching print receipt or archive completion is observed. Never infer by filename or arrival group. guest QR/photo print permission/public release/physical output/hand-over remain different records. Collection timestamp does not change print state. Staff order metadata contains its photo and optional original-order linkage; UI deliberate additional confirmations may create additional orders and do not consume an entitlement balance (not implemented).

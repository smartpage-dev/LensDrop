> Baseline 0.2.0-alpha.1 record retained for context. For current Hostinger startup, test status and limitations, read docs/hostinger/RELEASE_NOTES.md and the Hostinger setup guide first.

# Deployment, safety and unfinished production work

## What this release proves

This is a single-instance engineering implementation of resumable transfer and explicit publication policy. Linux tests cover protocol invariants and process crashes. No provider deployment, independent security audit, dependency audit, availability SLA, backup restoration, multi-hour photo workload or smartphone hardware qualification has been completed.

Keep the administrator console on a restricted evaluation network. Every device receives only its own event-scoped credential, never the administrator secret. Use TLS end-to-end at the public boundary and restrictive access to the data directory. Do not log Authorization headers or embed device tokens in public gallery links.

## Durable storage is required

Use one persistent, local filesystem-backed data directory containing the SQLite database, WAL files, private originals, chunk files and derivatives. Run exactly one Node process. Do not use an ephemeral deployment directory, network filesystem with uncertain locking/fsync semantics, or horizontally replicated SQLite writers. A process lock rejects an active second process. Never delete `server.lock` while a writer might be running.

The code uses SQLite WAL and synchronous FULL, fsync of file contents, atomic rename, and directory fsync on POSIX. Windows directory durability differs and was not validated. A receipt does not protect against disk loss, damaged filesystem, operator deletion or lost host. Before any paid event, define a backup/restore procedure and test it. The safest simple evaluation backup is a cleanly stopped server plus a consistent copy of its entire data directory; blindly copying only the main SQLite file while it is running is not a valid backup plan.

## Capacity and retention

The default 10 GiB event limit reserves declared bytes from upload sessions, including unfinished ones. This is deliberately conservative. Abandoned-session cancellation/garbage collection and separate customer billing quotas are not implemented. Do not silently expire pending sessions to reclaim space. Photo IDs, checksums, provenance and queue audit records must survive a future storage migration.

Storage calculations must include private originals, incomplete chunks, temporary assembly, thumbnails, delivery renditions, SQLite/WAL and backup overhead. At 6 MB each, 10,000 incoming phone files are roughly 60 GB before those extras. Phone queue metadata is small, but the source photographs are not. LensDrop cannot stop another camera app filling the phone; the operator must monitor its download storage too.

## Recommended production evolution (not implemented)

Move durable media to private object storage, event/queue metadata to a backed-up managed database, and image processing to separately restartable workers. Add authenticated administration, least-privilege roles, one-time device enrollment, rate limiting, audit trails, explicit retention/deletion, consent/privacy controls, error monitoring and restore drills. Serve public derivatives through controlled CDN paths; never make the original bucket public.

Do not put cloud AI, face recognition, full-resolution decoding or a large thumbnail wall in the transfer app. Later processing should be a separate cloud job after upload receipt. A processing failure must not force another full camera-to-phone transfer.

For managed Node hosting, verify persistent disk, process restart policy, request timeout, native `sharp` support, background-worker support and database availability first. Provider-specific deployment is not included. An upload timeout must be shorter than the provider timeout, and chunks must fit that budget on the slowest supported event link. A 1 MiB chunk at 0.1 Mbps alone takes about 84 seconds, so very poor connections can still exceed this alpha's timeouts; adaptive chunk/protocol negotiation is future work.

## Known feature gaps

The console is a new minimal operational surface, not a full port of the original design. It lacks account login, invitations/QR pairing, advanced search, complete administrator pagination, album branding tools, edit-return integration, sales, analytics, AI and production abuse protection. The guest gallery has no PIN or identity checks beyond possession of its unguessable link. Treat that as public-by-link sharing, not confidential access.

The dependency installation has no generated lockfile in this handover. Pin and audit a clean installation before distribution. No cloud service or external account was configured, charged or modified in this work.

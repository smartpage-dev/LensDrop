# LensDrop Cloud Core 0.2.0-alpha.1

**Engineering alpha, not a production deployment or a complete migration of the original LensDrop application.** The upload API and desktop reference client have automated tests. The Android project is source-only and has not been compiled or tested on hardware. No iOS app is supplied.

## What is included

A standalone Node.js service with event creation, event-scoped device credentials, a durable resumable JPEG upload protocol, checksums, exact uploaded-file preservation, cloud-side delivery/thumbnail generation, review/live/hidden controls, a basic administrator console, and a guest gallery. It does not depend on Claude artifact APIs.

The original supplied HTML and a narrowly patched version are retained in `reference/`. Those HTML files still depend on their original Claude environment. They are not the standalone application served by this package.

## Local evaluation

Requires Node.js 22.13+ or 24 within the package's declared range, npm, internet for dependency installation, and persistent writable disk. Tested here with Node 22.16.0 on Linux. The `node:sqlite` API emits an experimental warning on that tested version. No Windows server or browser interaction test was performed.

From this directory on macOS/Linux:

```sh
npm install
export LENSDROP_ADMIN_KEY="$(node -e "console.log(require('node:crypto').randomBytes(32).toString('hex'))")"
export LENSDROP_DATA_DIR="$HOME/lensdrop-evaluation-data"
export HOST=127.0.0.1
export PORT=3000
printf 'Save this administrator key securely: %s\n' "$LENSDROP_ADMIN_KEY"
npm start
```

In PowerShell, use the equivalent environment variables before `npm start`:

```powershell
npm install
$env:LENSDROP_ADMIN_KEY = node -e "console.log(require('node:crypto').randomBytes(32).toString('hex'))"
$env:LENSDROP_DATA_DIR = Join-Path $HOME 'lensdrop-evaluation-data'
$env:HOST = '127.0.0.1'
$env:PORT = '3000'
Write-Host $env:LENSDROP_ADMIN_KEY
npm start
```

Open `http://127.0.0.1:3000/admin` on the same computer, paste the administrator key, create a Review-first event, and issue one device token for each uploader. Device tokens are different from the administrator key. Save each token securely; only its hash is retained on the server. Open the guest gallery using the event's generated link.

A phone cannot use the computer's loopback address. Put this service behind a properly configured HTTPS reverse proxy before pairing a phone. Do not expose plain HTTP, publish the administrator key, or treat the evaluation console as production account management.

The process does not automatically load `.env` files. Configure environment variables through your shell or hosting control panel. Keep the data directory outside the deployment tree so replacement deployments do not erase queued uploads or photographs.

## Validate the core

```sh
npm test
```

The recorded result is **12 passing tests**, including three subprocess SIGKILL/restart cases. See `tests/test-results.txt` and `docs/TEST_REPORT.md`. `sharp` is pinned to 0.34.1; the test environment supplied that exact package globally. A fresh `npm install`, transitive dependency lockfile, dependency audit, platform binary installation and production hosting were not verified here. Generate and commit an installation lockfile after a clean build in your deployment environment.

## Operating boundaries

- JPEG only, up to 64 MiB per file and 80 million decoded pixels. No RAW/HEIF/video pipeline.
- One Node process and one persistent data directory. Do not enable cluster mode or multiple replicas sharing this SQLite database.
- One cloud image finalizer at a time; at most four concurrent chunk/completion handlers. Additional work receives retryable backpressure.
- Default event reservation quota: 10 GiB, configurable using `LENSDROP_EVENT_QUOTA_BYTES`. Default free-space reserve: 512 MiB. These are evaluation defaults, not event-sizing recommendations.
- An upload receipt confirms this instance's checked file and database commit. It does NOT mean a replicated, independently backed-up object-store copy exists.
- Originals are administrator-only. Guest gallery holders see only Live delivery copies. Gallery URLs are unlisted bearer links, not authenticated/private albums.
- Administrator photo list is limited to the latest 200. Guest pages contain up to 100 items; latest-page refresh is 10 seconds.
- No payments, AI editing, face recognition, photographer invitation QR, original production UI migration, or native camera control is included.

Read `docs/API.md`, `docs/DEPLOYMENT_AND_LIMITS.md`, and `docs/RESEARCH_AND_ARCHITECTURE.md` before further deployment work.

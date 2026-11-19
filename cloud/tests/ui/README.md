# H3 UI regression harness (developer use)

This is an instrumented browser/API bridge, not native browser-to-server networking. In the execution environment, native navigation was blocked by browser policy. Only location origin/path are substituted in the loaded source; fetch and image resources are bridged to the real local Node server. It exercises production UI logic, real backend responses, actual JPEG/PNG decoding and QR matrices. It cannot validate HTTPS, CSP enforcement, real network/browser lifecycle, Hostinger or a physical projector.

Dependencies for this optional test harness: Python 3, Pillow, Playwright, pyzbar with the zbar native library, a Chromium executable; Node and installed application dependencies. These are NOT extra production requirements. Set CHROMIUM_PATH where required. The fixture font defaults to Linux DejaVu Sans; adjust the test script font path on another OS.

Run from the cloud folder in a developer shell:

```
export LENSDROP_UI_WORKDIR="$(pwd)/.ui-rehearsal"
export LENSDROP_CLOUD_DIR="$(pwd)"
python tests/ui/make_fixtures.py
node tests/ui/seed-server.cjs
```

Keep that test server running. In a second shell with the same variables:

```
python tests/ui/check_bridge.py
```

Outputs go under .ui-rehearsal/ui-output. The seed server uses SYNTHETIC event photographs and a hard-coded TEST admin key. NEVER deploy this test server, reuse that key, or use test QR links as event links. Stop the test server when finished. The 31-second offline age is simulated in the test harness. No deliberate outages are applied to a real event.

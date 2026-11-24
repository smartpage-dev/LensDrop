# LensDrop H5.7 Operations UI pilot — release and test report

## Scope and baseline
Based on the exact H5.6.1 lint-fixed complete GitHub source archive (SHA-256 `eb4691b1e382f7ff6723b5c6fe9c88a54f0baec5a6ac2765352f078a5953f3bd`). Android remains `0.2.0-alpha.6-h5.6.1` / code 12; cloud backend remains H5.5/schema7; Windows station core remains H5.5. **This is a UI/source update, not a verified APK or EXE.**

## Implemented
- Cloud Admin: five guided workspaces (Overview, Setup & pairing, Photos & guests, Staff printing, Slideshow); event selection, legacy manual-token and QR pairing are clearly separated.
- Windows station: five workspaces (Overview, Setup & pairing, Printer profiles, Print queue, Staff Photo Desk); retains pause/simulation, driver capture and existing job actions.
- Read-only traffic lamps based on actual API results, recent photographer heartbeats, station heartbeat and queue states; grey unknown, green recent/healthy, amber needs attention, red no recent report/error. These are **not** physical camera/printer health probes.
- Cloud status/photo auto-refresh defaults to 15s when Overview/Photos visible; other workspaces poll summary only to avoid overwriting forms. Off/30s controls. No requests without Admin key, when hidden, or during active form editing.
- Station status polling defaults to 5s, configurable to 15s or Off; no overlapping refresh requests. Existing native sync timer remains unchanged.
- QR pairing remains explicit and Admin-approved. Guest QR lookup never auto-prints.

## Checks completed
- Windows Print Station regression: 57/57 tests passed (source and HTTP/HTTPS simulation tests, no physical output).
- Syntax: all changed JavaScript files passed `node --check`.
- Browser UI harness: desktop 1280px and mobile 390px for both UIs; five tabs, visibility, status lamps, no horizontal overflow. Synthetic data only, not live Hostinger or compiled Windows.
- Cloud Node integration tests/build **not rerun**: local `sharp` dependency unavailable. No new cloud schema/DB migrations. Existing API handlers are not otherwise modified; static asset allowlist updated.
- No new Windows .NET native build, Android build, Hostinger deployment, phone, printer, or long-duration testing.

## Operational cautions
- A recent heartbeat is not proof a camera is connected. A recent station cloud sync is not proof CP9550DW paper output. Submitted remains unconfirmed until operator verification.
- Background refresh is intentionally bounded and paused while browser tab is hidden or forms are active; staff photo desk selection is manual-refresh to avoid changing a pending print order.
- Retain Admin Key, Storage ID, private data path, Android signing identity and printer mappings. Keep `LENSDROP_INITIALIZE_STORAGE=0` on existing installations.
- Build new Windows package from updated GitHub commit; Android source did not change. Cloud deploy is separate and needs stopped-data backup/restore planning.

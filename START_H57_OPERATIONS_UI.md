# LensDrop H5.7 Operations UI

This update changes Cloud Admin and Windows Print Station navigation/status displays only. It does not add a photo transfer route, a new print order permission, or guest self-service printing.

**Install:** Update main LensDrop GitHub repo with full source or patch. Build Windows Print Station from the updated commit. Use `LensDrop_H5_7_Operations_UI_Hostinger_Root.zip` to deploy cloud **after** a complete stopped backup. No Android rebuild is required for this UI-only increment.

**Cloud sequence:** Connect → Overview (select event) → Setup & pairing (create event, generate five-minute staff QR, approve on intended device) → Photos & guests → Staff printing → Slideshow.

**Windows sequence:** Overview → Setup & pairing → Printer profiles → Print queue → Staff Photo Desk. Keep Simulation and Pause enabled for the first rehearsal.

**Lights:** green = recent successful report; amber = paused/mixed/attention; red = no recent report or API error; grey = not configured/unknown. These are reporting indicators, not camera/printer hardware probes.

**Refresh:** Cloud 15/30s/Off; Windows 5/15s/Off; browser-hidden polling pauses; photo/staff editing is not overwritten by routine status polling.

See `H57_UI_RELEASE_REPORT.md` for validation boundaries and `docs/h57-ui/` for screenshots.

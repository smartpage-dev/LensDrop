# Original prototype and limited fixes

`lensdrop-original.html` preserves the supplied prototype. `lensdrop-prototype-patched.html` and `03_pipeline.patched.js` apply three narrow improvements:

1. The 400-record limit applies only to successful historical items; pending, running and failed imports are not evicted.
2. A returned edit with multiple same-basename matches is rejected as ambiguous instead of replacing the first match.
3. Review-first returned edits remain in Review, and Hidden photos remain Hidden. Live-event behavior follows the selected publication policy.

The original HUM-03 requirement called for returned edits to become Live, so the review behavior is an intentional safety-oriented requirement change, not merely a syntax fix.

These files still depend on Claude's original artifact runtime. Their in-memory browser queue is NOT the new persistent phone/desktop queue. Do not upload this HTML to Hostinger and expect it to become the standalone backend. The independent application is under `src/` and `public/`.

The original design remains reference material for a later UI migration. No claim is made that its full functionality has already been ported.

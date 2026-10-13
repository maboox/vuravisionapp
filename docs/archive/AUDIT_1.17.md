# VuraVision 1.17 audit

This audit continues the 1.16.2 source without removing existing features or tests. Application ID, archive schema, production certificate requirements and the hidden status of shared rooms are preserved.

| Area | Finding | Change |
| --- | --- | --- |
| Autosave | Waiting snapshots accumulated; activity recreation could create competing file writers. | Application-scoped serial I/O executor; latest waiting snapshot per project, document/ticket-scoped callbacks and deferred media cleanup. |
| Imports | Images were buffered in memory; an asset named document.json could collide with staging. | Stream bounded entries into separate document/asset directories; reuse identical assets and roll back imported files on failure. |
| Save/reopen | Read and write limits differed; invalid asset names, duplicate page IDs and broad highlighter limits were inconsistent. | Shared limits: 128 MiB JSON, 64 MiB per asset, 512 MiB total; validation aligned with supported tools. Atomic saves retain the previous archive on failure. |
| History | No-op edits consumed Undo or discarded Redo; exceptions could leave partial edits; dormant-canvas points were shared during mutation. | Roll back failed/no-op checkpoints and detach points for both active and archived canvases. |
| Touch lifecycle | Cancelled erasure could remain committed; toolbar Undo during an unfinished gesture could undo the wrong state. | Unified gesture abort restores the checkpoint before Undo/Redo. Page/split/tool changes wait for active interaction to finish. |
| Split board | Pen controls displayed global settings while split drawing used panel settings. | Read/write the active panel's pen settings and reconcile the active pane after layout/history changes. |
| Canvas memory | Full-resolution 4K backing bitmaps put pressure on limited-memory panels. | Heap-aware bitmap budget, capped at 8 MP for the board and 2 MP per PDF page; scaled full/incremental rendering uses the same canvas transform. |
| Fit | Content could sit under dock/bottom controls. | Fit uses the unobstructed viewport and offsets each panel correctly. |
| Graphs | Every repaint resampled function curves. | Bounded path cache keyed by expression, domain and dimensions; visible layer items grouped once. |
| Whole-stroke eraser | Long swipes repeatedly scanned every ink segment. | Exact swept-segment intersection for unmasked ink; existing mask-aware hit testing retained for masked objects. |
| Images | Thumbnails decoded at the same large resolution as board images; pending jobs could outlive the owner. | Four decode quality levels, cached fallback, shutdown guards and asynchronous decode memory failure handling. |
| Text | Opacity changes did not update cached text layouts. | Include opacity in the paint/cache key and budget cache entries by text/layout size. |
| PDF | Two-finger scrolling could jump; annotation images were not invalidated; disposal reset zoom. | Initialize the centroid, refresh matching annotations, preserve view state, and bound PDF export stream buffering with disk spillover. |
| Shared rooms | Final host edits could miss the final snapshot; stale deferred snapshots could be reapplied. | Drain queued host operations before ending, make ending idempotent, clear superseded snapshots and reject older frames. |
| Room media/history | Asset GET copied the entire document; equal-size different assets were accepted; baselines copied the local PDF workspace. | Direct synchronized asset reference check, byte comparison and whiteboard-only immutable-point baselines. |
| Mind maps | Rotated-node attachment used axis-aligned hit tests; descendants could cross panels; repeated scans were quadratic. | Node-local attachment, lease-aware edits, panel-local breadth-first traversal and orphan-reference cleanup on delete. |
| Games | Overlapping pauses could count time twice; static games redrew continuously; one released finger could end another finger's hold. | Pause-reason clock, conditional animation, timed pair reset and last-contact release. |
| Artwork playback | Resuming a paused stroke could mutate a pending save snapshot. | Detach current stroke samples before resuming. |
| Android delivery | Automatic transfer rules were implicit; ZIP alignment alone did not verify native ELF alignment. | Explicit Android 12+ transfer exclusions and checks for 64-bit ELF LOAD alignment and RELRO page rounding in CI, alongside signature/ZIP checks. |

## Evidence and boundaries

Current results are in `VERIFICATION_1.17.0.json` and `verification/v117/`. New regression tests cover failures above, including archive rollback, active/dormant document isolation, cancelled erase, scaled 4K rendering, room end/ordering, rotated mind maps and graph cache reuse. Existing 1.16 tests retain coverage of all requested controls, guide labels, cropping, split/solo restoration, Pie actions and direct page management.

The native rendering checks use Robolectric; web checks use jsdom plus native Canvas. These do not measure physical-panel latency, real Android WebView behavior, Wi-Fi discovery/reconnect between two devices or vendor touch calibration. Those cases are explicitly left to `DEVICE_ACCEPTANCE_1.17.md`. Shared rooms remain experimental and local-network only; the separate PDF reading workspace remains local.

Release compilation is distinct from production APK signing. The release key stays in the repository's existing GitHub Secrets. No signing bypass or replacement production identity is included.

Lint passes without errors, but warnings remain in the raw reports. The large unused-resource count includes icons and strings resolved dynamically. Other warnings include optional KTX conversions, drawing allocations, UI constructors and legacy APIs. Three TLS warnings point into transitive Bouncy Castle classes; the application configures no custom TLS trust manager or hostname verifier. These findings were retained rather than hidden behind a new baseline.

## Implementation references

- Android data transfer configuration: https://developer.android.com/identity/data/autobackup
- PDFBox bounded stream buffering: https://pdfbox.apache.org/2.0/faq.html
- Android 16 KiB native alignment: https://developer.android.com/guide/practices/page-sizes
- RELRO protection implementation: https://android.googlesource.com/platform/bionic/+/refs/heads/main/linker/linker_phdr.cpp

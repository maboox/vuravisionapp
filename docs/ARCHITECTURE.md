# Architecture

Native Kotlin Android views. BoardView batches input by pointer and keeps completed content in a bitmap; active paths append samples to cached geometry. Store records page/object snapshots for undo and detaches points before generic mutations. Toolbar state and page-strip signatures avoid rebuilding view trees for ordinary strokes. Autosave defers while drawing.

Documents are versioned JSON `.vura` archives with assets. Existing package ID and import format remain compatible. Pen/highlighter profiles and touch calibration use preferences. Renderer serves canvas and export paths. Floating classroom widgets live in the board host and clean up their callbacks on close.

ML Kit downloads handwriting models on demand and exposes availability/errors. Basic shape recognition is offline. NanoHTTPD serves a token-scoped PDF and landing page on LAN interfaces, with a 30-minute lifetime. No hosted collaboration backend is included.

Games comprise eight distinct modes plus 24 curriculum challenges on a common quiz engine. Labs comprise 16 idealized simulations with visible units/assumptions. See CURRENT_SCOPE.md for omitted original-spec items.

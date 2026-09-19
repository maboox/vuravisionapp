# Architecture

MainActivity owns UI actions and document-picker flows. A serial executor handles storage and export; autosave captures a deep copy and its destination when scheduled. Saves are debounced and also requested when the Activity stops.

Document contains schema 2 domain objects and bounded undo/redo snapshots. Board tracks pointer IDs independently. Vector strokes retain original points; transforms do not repeatedly resample them. Navigation is explicit so a second writer is not interpreted as zoom.

Renderer paints both the board and exported pages. Export receives a separate renderer because Paint and expression objects are mutable. Media bounds decoded image/PDF page resolution and uses a byte-counted image cache with asynchronous loading.

LessonFiles validates documents, rejects unknown or unsafe archive entries, bounds decompression and stages imported assets before committing them. Atomic saves write and sync a temporary ZIP, preserve a backup, then rename the temporary file. Backup recovery uses a new lesson ID so it does not overwrite the failed source.

Recognition manages actual ML Kit language models and stroke recognition. The user reviews text before insertion. Physics contains lab formulas; each lab states its simplifying assumptions. GameEngine separates turn, scoring and timing logic from the native UI.

Sharing exposes one generated PDF through a random-token LAN URL for at most ten minutes. There are no upload or arbitrary filesystem routes. Closing the dialog or destroying the Activity stops the server.

There is no account, billing, cloud lesson backend or WebView shell. The beta uses full-snapshot undo bounded to thirty steps; larger-document memory profiling remains device acceptance work.

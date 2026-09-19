# 1.1.0 (version code 4)

This release implements the latest editing/Smart request on top of 1.0. It preserves application ID and lesson schema 2. New optional item fields default correctly when loading older lessons.

## Editor

Fine/broad pen color and width by calibrated contact major; explicit labeled pen controls and preview; ink/marker distinction; three-tap Quick Guide engineering access. Area erasing stores subtractive object-local masks, supports undo, export and PDF page scope; Restore erased areas removes those masks. Whole eraser now samples the swept segment so a fast motion cannot skip a thin stroke between events. Erasing is visual/subtractive, not a Boolean split of vector geometry into separate editable pieces.

Second tap on selected object opens actions. Text supports alignment, bold, color and fitted bounds; resizing changes text size. Sticky background is editable. Line direction respects both drag axes; regular shapes and graph units maintain proportions; cylinder/cone caps depend on width rather than stretching with height. PDFs use vertical page swipes/wheel plus explicit navigation controls.

## Smart and models

Closed Smart loops select enclosed writing by item center; open strokes remain ink. Text/formula/function results are editable before committing. Keep original ink defaults on. Basic geometry conversion runs at every completed stroke in Shape mode; it is heuristic, not a trained geometry recognizer.

Arithmetic uses exp4j; the equation parser explicitly supports degree 0–2 in x, including parentheses and constant division. It rejects unsupported variable division/cubic equations rather than guessing. Persian/Arabic digits normalize to Latin.

Bundled ML Kit Latin image OCR 16.0.1 is an additional offline path. Google digital ink 19.0.0 remains optional and needs initial model download. HTTP 404 is reported specifically, with raw diagnostics and a fresh retry. We did not establish or fix the remote service's root cause. No classroom writing is uploaded by these local recognition paths.

## Discovery and Arcade

Adapted the two user-supplied HTML documents: 68 Persian simulations and 49 bilingual games. Assets are packaged locally, with no remote fonts/resources, blocked network loading, disabled file/content access and no JavaScript bridge. Added search, branded styling, pause/reset, snapshot to board, category filters and reduced-motion-aware celebrations. Preserved existing native catalogs separately.

Corrected converging-lens image sign/rays and ideal-gas readout to use a stated relative PV=NT model. Fixed paused simulations clearing their canvas and round timers leaking into the next game round. Simulations remain idealized teaching models; the full catalog has not undergone an independent scientific audit.

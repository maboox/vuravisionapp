# Secret studio artwork

`mona-vura` is an original graphic reinterpretation of the public-domain Mona Lisa composition, with a frame bearing the Vura emblem. It is not a photographic reproduction. `aurora` is an original anime-style character.

`app/src/main/assets/studio/*.json` stores only pen width, color and sampled coordinates. Filled regions are serpentine pen hatching. ArtPlayer creates the same `Item(kind="ink")` objects used by the whiteboard; no portrait bitmap is inserted. Every line can be selected, erased, recolored and saved with the existing lesson format. Playback creates a separate page, retaining the previous lesson pages.

The optional `scripts/generate-studio.py` generator contains the original geometric paths and writes JSON and illustrative PNG previews. It requires Python and Pillow; Android builds consume the checked-in assets without Python. Runtime screenshots in verification reports show the actual native-rendered results.

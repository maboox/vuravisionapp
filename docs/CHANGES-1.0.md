# 1.0 changes

- Drawing: incremental active paths, completed-scene bitmap, batched frame invalidation, multi-pointer state, single/multi-touch toggle and configurable thick-tip contact policy. Synthetic ten-contact tests check correctness/cache reuse; they do not measure real display latency.
- Navigation: direct Shapes gallery (24), persistent page controls and thumbnails. Re-tap active pen/highlighter for independently saved color, thickness and style.
- Classroom tools: draggable/minimizable in-app timer, stopwatch, dice, scoreboard and curtain. These are floating panels inside the app, not Android system-wide picture-in-picture.
- Sharing: LAN-interface selection, HTTP landing page, token-protected PDF download; closing the QR dialog no longer kills the server. Explicit stop and 30-minute expiry remain.
- Recognition: model availability verification, duplicate-download suppression, retry/error UI and Download Manager checks. Offline basic-shape recognition added. Google handwriting downloads still depend on the device and network; general handwritten formula OCR is not implemented.
- Games: numeric prompts no longer become invalid Android resource IDs (Math Speed crash); 32 selectable modes/challenges with rules and examples. The 24 new quiz topics share one engine.
- Labs: 16 simulations, descriptive preview cards, corrected projectile scale/pendulum length, reset controls, scientific reference tests and visible idealization assumptions.
- Brand: supplied logo, navy/gold palette, improved compact and Persian layouts. Release version 1.0.0, same package identifier for stored-document migration.

## Design research

Official [myViewBoard product information](https://myviewboard.com/) and its [ViewSonic App Store listing](https://apps.apple.com/us/app/myviewboard/id1546982350) informed broad classroom patterns: accessible board tools, page navigation, classroom utilities and QR/PDF sharing. Implementation choices were adapted to this app; no claim is made that exact competitor interactions were replicated.

Recognition follows Google's [ML Kit digital ink documentation](https://developers.google.com/ml-kit/vision/digital-ink-recognition/android): download a language model, check availability, then recognize locally. Error handling cannot bypass unavailable Google download endpoints.

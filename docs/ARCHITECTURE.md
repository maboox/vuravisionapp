# Architecture (3.1)

Native Kotlin Android views, no Compose. One activity (`MainActivity`) hosts the board, the dock and the bars; feature code lives in packages by area.

| Package | Contents |
| --- | --- |
| root (`com.vuravision.classroom`) | `MainActivity` and its feature files (`MainActivityAi`, `…ClassTools`, `…Settings`, `…Files`, `…Pdf`, `…Voice`), file storage, Google search |
| `board` | `Board` (view; split into `BoardInput`, `BoardViewport`, `BoardInk`), `EditorState`, `Document` (lesson model, validation, undo store), `Renderer`, shapes, erasing, fill, text layout, geometry tools, tables, map, frames, charts |
| `ai` | `SmartAi` (one request path for every service: cache, daily cap, offline check), `AiTasks` (prompts and parsers), offline recognition and math |
| `voice` | Realtime voice (Gemini Live, OpenAI Realtime), turn-based voice (OpenRouter / AvalAI), `VoiceCommands` (the only way voice touches the board) |
| `room` | Shared rooms (engine, encrypted HTTP + WebSocket transport, discovery, UI), classroom sharing server, phone polls |
| `labs`, `games` | Simulations and two-player games, class quiz, quiz builder, shared feedback (sound, spring, haptics, confetti) |
| `pdf` | PDF workspace, region selection, export |
| `ui` | Design tokens and themes, dock and Add panel, settings screen, sheets, icons, fonts, page-pattern previews |
| `tools` | Class tools: widgets (timer, picker…), spotlight, replay, step reveal, periodic table, guide |

Key rules:

- Documents are versioned JSON `.vura` archives with assets; every change goes through `Store` so one Undo reverts it.
- The board's own content (pages, ink, objects) uses fixed `INK_*` colors; only the interface follows the light/dark theme.
- Only the selection is ever sent to an AI service; answers are placed beside the work, never over it.
- Voice can act on the board only through `VoiceCommands` (reversible, nothing deletes).
- Rooms: ECDH key per guest, AES-GCM on every message and asset, 4-digit code compared before approval.

The reasons behind interface decisions are in `UI_RATIONALE.md` (Persian).

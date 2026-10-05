# VuraVision 1.11 voice assistant

## Enable only for evaluation

The assistant is **disabled and hidden by default**, including after upgrading from 1.10.x. There is no microphone or service connection at startup. Tap the guide title three times to open the hidden Engineering menu, then open Assistant behavior and turn on Enable educational assistant. This reveals the lower-right button and Settings → Voice assistant. Switching off immediately cancels pending startup and stops microphone, playback, image capture and network work; it hides the button and normal connection entry. Enabling never starts a conversation automatically. Saved encrypted keys remain until explicitly deleted.

When enabled, tap the microphone button to start and tap again to end. The button remains visible with drawing toolbars hidden. Leaving the activity ends the conversation; returning does not resume it automatically. Only the app's visible board/PDF workspace is captured, not other apps or dialog windows. The assistant has no editing or system-control tools. It cannot insert text into the lesson.

## Three services and independent keys

Settings → Voice assistant selects Google Gemini, OpenAI or OpenRouter, conversation model and voice. Each service has a separate Android Keystore AES-GCM encrypted credential. Changing services preserves the other keys. The existing Gemini credential location is retained for upgrades. Empty key input preserves the selected service's saved key; Delete key only affects that service. Keys are never in source, lesson files, PDFs, diagnostics or connection URLs. Backup is disabled. Enter your own key in the app; GitHub signing secrets have a different purpose.

- Gemini: direct Live API WebSocket, default `gemini-3.8-live`, `Kore`; PCM16 mono input 16 kHz, output 24 kHz. Existing bounded resumption/reconnect behavior is retained.
- OpenAI: GA Realtime WebSocket, default `gpt-realtime`, `marin`; PCM16 mono input/output 24 kHz. Images are conversation context items and do not trigger unsolicited responses. Server speech detection interrupts queued playback. A dropped established connection ends the session, because this path does not pretend to resume a lost conversation. Customers supply their own keys; the APK contains no shared key. For a future centrally billed fleet, use a backend issuing short-lived credentials. OpenAI API billing is separate from ChatGPT subscriptions.
- OpenRouter: **turn-based/half-duplex** HTTP pipeline, not a Gemini/OpenAI full-duplex Live session. Local endpoint detection submits a bounded spoken question plus the newest workspace JPEG to a model accepting **audio + image + text**, receives a Persian reply, then streams a separate speech model's PCM audio. Wait for Listening before speaking again. Audio/image understanding default: `google/gemini-3.5-flash-lite`; alternate preset: `qwen/qwen3.8-omni-flash`. Speech default: `google/gemini-3.8-flash-lite-tts`, voice `Kore`; other supported speech presets are `google/gemini-3.8-flash-tts` and `google/gemini-3.1-flash-tts-preview`. Both requests use the **OpenRouter key**; the default route uses Google models behind OpenRouter and may consume paid credit. `openai/gpt-audio` alone cannot see the board, because its OpenRouter input modalities do not include images. The current catalog is checked on startup and incompatible models are rejected with a clear message.

## Behavior

All paths use the same Persian instruction: introduce only as “دستیار آموزشی هستم”; keep provider/model/company details out of spoken answers, including direct identity questions; avoid fabricated identities; remain warm, polite and patient; answer briefly; guide on exercises before providing a final answer when explicitly requested. If audio or intent is unclear, say only “متوجه نشدم، لطفاً دوباره بگید.” Do not echo a guessed interpretation. In OpenRouter, the `understood=false` response is additionally enforced locally: an uncertain model's guessed transcript/reply is replaced with that clarification phrase.

Hidden administrator controls retain tone, length, extra instructions, known panel facts, screen interval and session duration. Reset behavior preserves service/model/key/enable selections. Actual guide content and reported Android device facts provide software context; unknown hardware features must not be invented. Instructions steer model behavior; they cannot guarantee compliance for every model response. Lack of editing tools, disabled gating and lifecycle limits are enforced by app code.

## Performance and lifetime

Network processing, JPEG encoding and blocking audio run on dedicated workers. There is no local language model on the panel. PixelCopy uses one reusable capture and at most one frame in flight. Captures wait for 500 ms of idle touch/drawing, have a 1280-pixel maximum edge and adaptive 60 KB JPEG budget. Detailed writing may require zooming in. Unchanged frames are not sent repeatedly. For OpenRouter, only the newest frame is retained; silence or image changes alone do not trigger paid question requests. Speech is capped at approximately 20 seconds per turn, text-only history at six turns, network response size and audio backlog are bounded. Speech output is backpressured rather than buffering indefinitely. The configured session duration defaults to 15 minutes.

Hardware echo cancellation/noise suppression is used when available. Half-duplex input is ignored during OpenRouter processing/playback to avoid responding to the assistant's own voice. Physical microphone sensitivity, room noise, Persian pronunciation, network latency and screen readability need target-panel validation.

## Sources checked 2026-10-04

- https://developers.openai.com/api/docs/guides/realtime-conversations
- https://developers.openai.com/api/docs/guides/voice-websockets
- https://openrouter.ai/docs/guides/overview/multimodal/audio
- https://openrouter.ai/docs/guides/overview/multimodal/tts
- https://openrouter.ai/api/v1/models
- https://openrouter.ai/api/v1/models?output_modalities=speech
- https://openrouter.ai/google/gemini-3.5-flash-lite
- https://openrouter.ai/qwen/qwen3.8-omni-flash
- https://openrouter.ai/google/gemini-3.8-flash-lite-tts
- https://ai.google.dev/gemini-api/docs/speech-generation
- https://ai.google.dev/gemini-api/docs/live-api

## Verification limits

Protocol and controller tests use fake transports/audio/services, including hidden-default gating, late permission callbacks, provider separation, OpenAI GA events, half-duplex endpointing, uncertain-response suppression and resource cancellation. UI render checks cover English/Persian. Android Keystore device tests are included. See the current release verification record for executed checks. No authenticated customer API conversation or physical-panel audio/latency test is claimed from unit tests.

## Microphone mute (1.12)

While the conversation is starting or active, a separate microphone button toggles capture. Muting stops recording and real microphone upload without stopping response audio; unmute explicitly resumes capture. A zero-audio packet ends pending live-provider voice detection. OpenRouter discards partial input on mute. Muting is a session control and is reset when the conversation ends. The master assistant enablement remains off/hidden by default.

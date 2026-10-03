# VuraVision 1.10.0 voice assistant

## Operation

The independent microphone button near the physical lower-right corner starts a foreground conversation. It remains available in board focus mode. Tap again to end. Settings → Voice assistant accepts a Google AI Studio API key. Android asks for microphone permission at first use. The service receives microphone audio and images of the visible workspace only while active. The first server response is requested to greet in Persian as VuraVision's educational assistant. No transcript is inserted into the board and no tools/document-mutating callbacks are registered with the service.

Closing the app, opening another activity or losing foreground activity ends the conversation and releases audio resources. Reopening does not start the microphone automatically. Network, access, quota, unsupported model, capture and audio errors have localized messages. The configured local duration limit ends a session; tapping again creates another. A saved key is personal to this installation.

## Administrator controls

The existing hidden guide entry (three taps on the guide title) opens Engineering. The last entry, Assistant behavior, has tone, response length, voice, extra behavior instructions, panel facts, model ID, screen interval and local conversation duration. The normal Settings menu exposes only connection/key management.

Defaults: Persian audio responses; warm, polite and patient tone; short answers; VuraVision educational-assistant identity; hints before final answers for learning exercises, with final answers when explicitly requested. Software/panel-use questions receive direct practical instructions. Default model: `gemini-3.8-live`; voice: `Kore`; screen interval: 2 seconds; local conversation limit: 15 minutes. Administrators can update the Live model ID as Google model availability changes. Behavior instructions steer the model and are not a guarantee of perfect compliance. The absence of editing tools and the local time/lifecycle limits are enforced by app code.

The Persian in-app guide supplies actual software tool instructions. Runtime Android version, manufacturer/model and reported display dimensions supplement that reference. Administrator panel notes supply known hardware details; the assistant is explicitly instructed not to infer physical size, touch-point count, OPS, camera or other unreported features.

## Runtime and performance

OkHttp 4.12.0 connects directly over TLS WebSocket to the Gemini Developer API v1beta BidiGenerateContent endpoint, authenticated with an `x-goog-api-key` header. Settings and initial context are sent once per connection, and no media is sent before setupComplete. Microphone PCM16 mono is captured at 16 kHz in 40 ms chunks. Server PCM16 mono audio at 24 kHz is played on a separate thread. Available hardware echo cancellation/noise suppression is enabled; real panel microphone/speaker behavior still needs testing.

A PixelCopy surface capture is asynchronous, with at most one frame in flight and a reusable bitmap buffer. Captures wait until writing/touch input has been idle for 500 ms; JPEG encoding/hash checking runs on its own worker. The captured rectangle is the board/PDF workspace in the activity window, not other apps or separate dialog windows. Images start at a maximum edge of 1280 pixels and are adaptively reduced to a 60 KB JPEG budget; detailed text may require zooming in. Unchanged image bytes are not resent. Audio backlog is bounded; when a connection cannot keep up, the session ends instead of accumulating unlimited delayed audio. Playback is bounded too. Server interruption discards queued speech. Session resumption and context compression are configured to survive server connection limits, with bounded reconnect attempts. Resuming does not repeat the greeting or create another microphone.

## Key and data handling

The API key is encrypted with a per-device Android Keystore AES-GCM key in private preferences. The app has backup disabled. The key is never in source code, lesson files, PDFs, diagnostics or WebSocket URLs. The entry field does not reveal a previously saved key; leaving it empty preserves that key and Delete key removes it. Audio, frames and resume tokens are transient and not saved to disk. This direct-key approach is for user-supplied keys; fleet deployments using a centrally owned key should use a backend issuing short-lived credentials instead.

Google free-tier model access and quotas are project-specific and can change. The normal connection page tells users that internet, available Live access and quota are required. Google documents different data-use terms for free and paid tiers; deployment owners should choose the suitable account/tier.

## Official protocol sources (checked 2026-10-03)

- https://ai.google.dev/api/live
- https://ai.google.dev/gemini-api/docs/live-api
- https://ai.google.dev/gemini-api/docs/live-api/capabilities
- https://ai.google.dev/gemini-api/docs/live-api/best-practices
- https://ai.google.dev/gemini-api/docs/live-api/session-management
- https://ai.google.dev/gemini-api/docs/pricing
- https://ai.google.dev/gemini-api/docs/rate-limits
- https://github.com/googleapis/python-genai/blob/main/google/genai/live.py

## Verification

A device test for Android Keystore encryption/round-trips is included and compiled; it has not been executed locally. Protocol/controller tests use a fake transport and fake audio ports, covering wire schema, setup ordering, greeting, media forwarding, interruption, resumption, stale socket events, backlog failure, quota rejection, local timeout and stopping reconnect. Native offscreen UI tests render the board button, normal key settings and hidden administrator settings in Persian and English. These checks do not substitute for a real authenticated Gemini conversation. A valid customer key and physical panel are required for end-to-end audio quality, latency, echo and screen-readability verification. No actual API call with a customer key has been made during development.

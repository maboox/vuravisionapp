# VuraVision AI proxy (item 77)

An OpenAI-compatible gateway for schools: the real API key stays on the server, each school (or panel) gets its own token with a daily limit, and only the endpoints the app uses are forwarded.

## Setup (Cloudflare Workers, free tier is enough for a school)

1. `npm i -g wrangler` and `wrangler login`.
2. `wrangler kv namespace create USAGE` and put the id in `wrangler.toml`.
3. Set the upstream in `wrangler.toml` (`UPSTREAM_BASE`): OpenAI, AvalAI (`https://api.avalai.ir/v1`) or OpenRouter.
4. Secrets:
   - `wrangler secret put UPSTREAM_KEY` — the real key.
   - `wrangler secret put TOKENS` — for example `{"a1b2c3-school-a": {"name": "School A", "daily": 500}}`. Use long random tokens.
5. `wrangler deploy`. Note the URL, e.g. `https://vuravision-ai-proxy.example.workers.dev`.

## On the panel

Settings → Smart → AI service → **AvalAI / OpenAI-compatible**, address `https://<worker>/v1`, key = the school token. The same address and token work for the voice assistant's turn-based mode.

## What it does not do

- It does not log questions or pictures; it only counts requests per token per day.
- Realtime voice (Gemini Live, OpenAI Realtime WebSocket) is not proxied; those keep their own keys on the panel.

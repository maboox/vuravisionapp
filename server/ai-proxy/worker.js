// VuraVision AI proxy (item 77): an OpenAI-compatible gateway that keeps the real API key on the server.
// Panels use it like any OpenAI-compatible service: address = this worker's URL + "/v1", key = the school's token.
// Each token has a daily request limit; usage is counted per token per day in KV. Only the endpoints the app uses
// are forwarded, and only to the one upstream configured below.
//
// Settings (wrangler secrets / vars):
//   UPSTREAM_BASE   e.g. https://api.openai.com/v1 or https://api.avalai.ir/v1 or https://openrouter.ai/api/v1
//   UPSTREAM_KEY    the real key (secret)
//   TOKENS          JSON: {"school-token-1": {"name": "School A", "daily": 500}, ...} (secret)
//   USAGE           KV namespace binding for counters

const ALLOWED = new Set([
  "POST /chat/completions",
  "POST /images/generations",
  "POST /audio/speech",
  "GET /models",
]);
const MAX_BODY = 8 * 1024 * 1024;

function json(status, body) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "content-type": "application/json; charset=utf-8", "cache-control": "no-store" },
  });
}

export function route(method, pathname) {
  const path = pathname.replace(/^\/v1/, "");
  return ALLOWED.has(`${method} ${path}`) ? path : null;
}

export function tokenOf(request, tokens) {
  const header = request.headers.get("authorization") || "";
  const token = header.startsWith("Bearer ") ? header.slice(7).trim() : "";
  return token && Object.prototype.hasOwnProperty.call(tokens, token) ? token : null;
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    const path = route(request.method, url.pathname);
    if (!path) return json(404, { error: { code: "not_found", message: "Unknown endpoint" } });
    let tokens;
    try { tokens = JSON.parse(env.TOKENS || "{}"); } catch { return json(500, { error: { code: "config", message: "TOKENS is not valid JSON" } }); }
    const token = tokenOf(request, tokens);
    if (!token) return json(401, { error: { code: "invalid_api_key", message: "Unknown school token" } });

    // Daily limit per school token.
    const day = new Date().toISOString().slice(0, 10);
    const counter = `${token}:${day}`;
    const used = parseInt((await env.USAGE.get(counter)) || "0", 10);
    const limit = Math.max(1, Number(tokens[token].daily) || 300);
    if (path !== "/models" && used >= limit) {
      return json(429, { error: { code: "rate_limit_exceeded", message: "Daily limit reached for this school" } });
    }

    const body = request.method === "POST" ? await request.arrayBuffer() : null;
    if (body && body.byteLength > MAX_BODY) return json(413, { error: { code: "too_large", message: "Request too large" } });

    const upstream = await fetch(env.UPSTREAM_BASE.replace(/\/$/, "") + path, {
      method: request.method,
      headers: { "content-type": "application/json", authorization: `Bearer ${env.UPSTREAM_KEY}` },
      body,
    });
    if (path !== "/models" && upstream.ok) {
      await env.USAGE.put(counter, String(used + 1), { expirationTtl: 60 * 60 * 24 * 3 });
    }
    // Pass the answer through; never echo upstream headers that could reveal the key or account.
    return new Response(upstream.body, {
      status: upstream.status,
      headers: {
        "content-type": upstream.headers.get("content-type") || "application/json",
        "cache-control": "no-store",
      },
    });
  },
};

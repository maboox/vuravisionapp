// Run with: node server/ai-proxy/worker.test.mjs
import assert from "node:assert/strict";
import worker, { route, tokenOf } from "./worker.js";

assert.equal(route("POST", "/v1/chat/completions"), "/chat/completions");
assert.equal(route("GET", "/v1/models"), "/models");
assert.equal(route("POST", "/v1/files"), null, "only the app's endpoints are forwarded");
assert.equal(route("DELETE", "/v1/models"), null);

const tokens = { "school-a": { daily: 2 } };
const req = (auth) => new Request("https://x/v1/chat/completions", { method: "POST", headers: auth ? { authorization: auth } : {} });
assert.equal(tokenOf(req("Bearer school-a"), tokens), "school-a");
assert.equal(tokenOf(req("Bearer nope"), tokens), null);
assert.equal(tokenOf(req(null), tokens), null);

// End to end with a fake upstream and KV: two requests pass, the third hits the daily limit; the real key is never echoed.
const store = new Map();
const env = {
  UPSTREAM_BASE: "https://upstream.test/v1", UPSTREAM_KEY: "real-secret", TOKENS: JSON.stringify(tokens),
  USAGE: { get: async (k) => store.get(k) ?? null, put: async (k, v) => { store.set(k, v); } },
};
const seen = [];
globalThis.fetch = async (url, init) => { seen.push([url, init.headers.authorization]); return new Response('{"ok":true}', { status: 200, headers: { "content-type": "application/json", "x-account": "secret-account" } }); };
const call = () => worker.fetch(new Request("https://x/v1/chat/completions", { method: "POST", headers: { authorization: "Bearer school-a" }, body: "{}" }), env);
assert.equal((await call()).status, 200);
const second = await call();
assert.equal(second.status, 200);
assert.equal(second.headers.get("x-account"), null, "upstream headers are not passed through");
assert.equal((await call()).status, 429);
assert.deepEqual(seen.map((s) => s[0]), ["https://upstream.test/v1/chat/completions", "https://upstream.test/v1/chat/completions"]);
assert.equal(seen[0][1], "Bearer real-secret");
assert.equal((await worker.fetch(new Request("https://x/v1/chat/completions", { method: "POST", headers: { authorization: "Bearer wrong" } }), env)).status, 401);
console.log("ai-proxy: all checks passed");

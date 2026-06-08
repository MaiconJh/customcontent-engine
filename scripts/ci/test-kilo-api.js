#!/usr/bin/env node
const fs = require("node:fs");

function loadDotEnv(path = ".env") {
  if (!fs.existsSync(path)) return;
  for (const line of fs.readFileSync(path, "utf8").split(/\r?\n/)) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith("#")) continue;
    const match = trimmed.match(/^([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)$/);
    if (!match) continue;
    const [, key, rawValue] = match;
    if (!process.env[key]) process.env[key] = rawValue.replace(/^["']|["']$/g, "");
  }
}

function endpoint() {
  if (process.env.KILO_ENDPOINT) return process.env.KILO_ENDPOINT;
  const base = (process.env.KILO_BASE_URL || "https://api.kilo.ai/api/gateway").replace(/\/$/, "");
  const path = process.env.KILO_CHAT_COMPLETIONS_PATH || "/chat/completions";
  return `${base}${path.startsWith("/") ? path : `/${path}`}`;
}

function models() {
  return [
    process.env.KILO_MODEL || "kilo-auto/free",
    process.env.KILO_FALLBACK_MODEL || "kilo-auto/balanced",
    process.env.KILO_SECOND_FALLBACK_MODEL || "kilo/auto-free",
  ].filter(Boolean);
}

function headers() {
  const out = { "Content-Type": "application/json" };
  if (process.env.KILO_API_KEY) out.Authorization = `Bearer ${process.env.KILO_API_KEY}`;
  return out;
}

async function testModel(url, model) {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), Number(process.env.KILO_TEST_TIMEOUT_MS || "45000"));
  try {
    const res = await fetch(url, {
      method: "POST",
      headers: headers(),
      signal: controller.signal,
      body: JSON.stringify({
        model,
        messages: [
          { role: "system", content: "You are a concise API smoke test responder." },
          { role: "user", content: "Reply with exactly: kilo-ok" },
        ],
        temperature: 0.2,
        max_tokens: Number(process.env.KILO_TEST_MAX_TOKENS || "256"),
      }),
    });
    const text = await res.text();
    let body = null;
    try {
      body = text ? JSON.parse(text) : null;
    } catch {
      body = null;
    }
    const content = extractText(body);
    return {
      model,
      status: res.status,
      ok: res.ok && Boolean(content),
      content,
      error: res.ok ? (content ? "" : "empty response") : safeError(body, text),
    };
  } catch (error) {
    return {
      model,
      status: 0,
      ok: false,
      content: "",
      error: error instanceof Error ? error.message : "request failed",
    };
  } finally {
    clearTimeout(timeout);
  }
}

function extractText(body) {
  if (!body || typeof body !== "object") return "";
  const content = body.choices?.[0]?.message?.content;
  if (typeof content === "string") return content.trim();
  const reasoning = body.choices?.[0]?.message?.reasoning;
  if (typeof reasoning === "string") return reasoning.trim();
  if (typeof body.output_text === "string") return body.output_text.trim();
  if (typeof body.text === "string") return body.text.trim();
  return "";
}

function safeError(body, rawText) {
  const message = body?.error?.message || body?.message || rawText || "request failed";
  return String(message).replace(/Bearer\s+[A-Za-z0-9._~+/=-]+/g, "Bearer [REDACTED]").slice(0, 240);
}

async function main() {
  loadDotEnv();
  const url = endpoint();
  console.log(`endpoint: ${url}`);
  console.log(`auth: ${process.env.KILO_API_KEY ? "KILO_API_KEY present" : "anonymous/no KILO_API_KEY"}`);

  for (const model of models()) {
    const result = await testModel(url, model);
    const content = result.content ? ` content="${result.content.slice(0, 80)}"` : "";
    const error = result.error ? ` error="${result.error}"` : "";
    console.log(`${model}: HTTP ${result.status} ok=${result.ok}${content}${error}`);
    if (result.ok) break;
  }
}

main().catch((error) => {
  console.error(error.message);
  process.exit(1);
});

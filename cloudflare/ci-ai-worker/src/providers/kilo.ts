import type { Env } from "../types";
import { sanitizeText } from "../sanitizer";

export interface KiloResult {
  ok: boolean;
  text?: string;
  model?: string;
  error?: string;
}

export function kiloEndpoint(env: Env): string {
  if (env.KILO_ENDPOINT) return env.KILO_ENDPOINT;
  const base = (env.KILO_BASE_URL || "https://api.kilo.ai/api/gateway").replace(/\/$/, "");
  const path = env.KILO_CHAT_COMPLETIONS_PATH || "/chat/completions";
  return `${base}${path.startsWith("/") ? path : `/${path}`}`;
}

export async function callKiloChatCompletion(env: Env, system: string, user: string): Promise<KiloResult> {
  const endpoint = kiloEndpoint(env);
  const models = [
    env.KILO_MODEL || "kilo-auto/free",
    env.KILO_FALLBACK_MODEL || "kilo-auto/balanced",
    env.KILO_SECOND_FALLBACK_MODEL || "kilo/auto-free",
  ].filter(Boolean);
  const errors: string[] = [];

  for (const model of models) {
    const attempts = [0, 1];
    for (const attempt of attempts) {
      const result = await postCompletion(endpoint, env, model, system, user);
      if (result.ok) return result;
      if (result.error) {
        errors.push(`${model}: ${result.error}`);
        console.warn(`Kilo provider attempt failed for ${model}: ${result.error}`);
      }
      if (attempt === 0 && !isTransient(result.error || "")) break;
      await sleep(150 * (attempt + 1));
    }
  }
  return { ok: false, error: errors[errors.length - 1] || "provider unavailable" };
}

async function postCompletion(endpoint: string, env: Env, model: string, system: string, user: string): Promise<KiloResult> {
  const headers: Record<string, string> = { "Content-Type": "application/json" };
  if (env.KILO_API_KEY) headers.Authorization = `Bearer ${env.KILO_API_KEY}`;
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), Number(env.KILO_TIMEOUT_MS || "60000"));
  try {
    const res = await fetch(endpoint, {
      method: "POST",
      headers,
      signal: controller.signal,
      body: JSON.stringify({
        model,
        messages: [
          { role: "system", content: system },
          { role: "user", content: user },
        ],
        temperature: 0.2,
        max_tokens: 1200,
      }),
    });
    const raw = await res.text();
    const preview = safePreview(raw);
    let body: unknown = null;
    try {
      body = raw ? JSON.parse(raw) : null;
    } catch {
      console.warn(`Kilo provider invalid JSON for ${model}: status=${res.status} preview=${preview}`);
      return { ok: false, model, error: "provider invalid JSON" };
    }
    console.warn(`Kilo provider response for ${model}: status=${res.status} preview=${preview}`);
    if (!res.ok) return { ok: false, model, error: `provider status ${res.status}` };
    if (!body) return { ok: false, model, error: "provider empty response" };
    const text = extractText(body);
    return text ? { ok: true, text, model } : { ok: false, model, error: "provider missing content" };
  } catch (error) {
    return { ok: false, model, error: providerError(error) };
  } finally {
    clearTimeout(timeout);
  }
}

function extractText(body: unknown): string {
  if (!body || typeof body !== "object") return "";
  const record = body as Record<string, unknown>;
  const choices = record.choices as Array<Record<string, unknown>> | undefined;
  const content = choices?.[0]?.message && typeof choices[0].message === "object"
    ? (choices[0].message as Record<string, unknown>).content
    : undefined;
  if (typeof content === "string" && content.trim()) return content;
  const reasoning = choices?.[0]?.message && typeof choices[0].message === "object"
    ? (choices[0].message as Record<string, unknown>).reasoning
    : undefined;
  if (typeof reasoning === "string" && reasoning.trim()) return reasoning;
  if (typeof record.output_text === "string" && record.output_text.trim()) return record.output_text;
  if (typeof record.text === "string" && record.text.trim()) return record.text;
  return "";
}

function isTransient(error: string): boolean {
  return /429|500|502|503|504|timeout|network|abort/i.test(error);
}

function providerError(error: unknown): string {
  if (error instanceof Error && error.name === "AbortError") return "provider timeout";
  if (error instanceof Error) return `provider network error: ${sanitizeText(error.message, 160)}`;
  return "provider network error";
}

function safePreview(value: string): string {
  return sanitizeText(value || "", 500).replace(/\s+/g, " ").slice(0, 500);
}

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

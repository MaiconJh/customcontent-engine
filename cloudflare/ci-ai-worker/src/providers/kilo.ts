import type { Env } from "../types";
import { sanitizeText } from "../sanitizer";

export interface KiloResult {
  ok: boolean;
  text?: string;
  model?: string;
  error?: string;
}

const DEFAULT_KILO_MODEL = "kilo-auto/free";
const DEFAULT_KILO_FALLBACK_MODEL = "kilo-auto/balanced";
const DEFAULT_KILO_SECOND_FALLBACK_MODEL = "kilo/auto-free";

export function kiloEndpoint(env: Env): string {
  if (env.KILO_ENDPOINT) return env.KILO_ENDPOINT;
  const base = (env.KILO_BASE_URL || "https://api.kilo.ai/api/gateway").replace(/\/$/, "");
  const path = env.KILO_CHAT_COMPLETIONS_PATH || "/chat/completions";
  return `${base}${path.startsWith("/") ? path : `/${path}`}`;
}

export function kiloAuthConfigured(env: Env): boolean {
  return Boolean(validApiKey(env.KILO_API_KEY));
}

export function kiloModels(env: Env): string[] {
  const authConfigured = kiloAuthConfigured(env);
  const primary = modelName(env.KILO_MODEL) || DEFAULT_KILO_MODEL;
  const configured = [
    knownBadAnonymousModel(primary) && !authConfigured ? DEFAULT_KILO_MODEL : primary,
    modelName(env.KILO_FALLBACK_MODEL) || DEFAULT_KILO_FALLBACK_MODEL,
    modelName(env.KILO_SECOND_FALLBACK_MODEL) || DEFAULT_KILO_SECOND_FALLBACK_MODEL,
  ];
  const unique = configured.filter((model, index, all) => model && all.indexOf(model) === index);
  return unique.filter((model) => {
    const allowed = authConfigured || !knownBadAnonymousModel(model);
    if (!allowed) {
      console.warn(`Kilo provider skipping model=${model} authConfigured=false fallbackReason=anonymous model rejected to avoid provider status 401`);
    }
    return allowed;
  });
}

export async function callKiloChatCompletion(env: Env, system: string, user: string): Promise<KiloResult> {
  const endpoint = kiloEndpoint(env);
  const models = kiloModels(env);
  const errors: string[] = [];

  for (const model of models) {
    const result = await postCompletion(endpoint, env, model, system, user);
    if (result.ok) return result;
    if (result.error) {
      errors.push(`${model}: ${result.error}`);
      console.warn(`Kilo provider attempt failed for ${model}: ${result.error}`);
    }
    if (isTransient(result.error || "")) await sleep(150);
  }
  return { ok: false, error: errors[errors.length - 1] || "provider unavailable" };
}

async function postCompletion(endpoint: string, env: Env, model: string, system: string, user: string): Promise<KiloResult> {
  const headers: Record<string, string> = { "Content-Type": "application/json" };
  const apiKey = validApiKey(env.KILO_API_KEY);
  const authConfigured = Boolean(apiKey);
  if (apiKey) headers.Authorization = `Bearer ${apiKey}`;
  console.warn(`Kilo provider request: model=${model} authConfigured=${authConfigured}`);
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), Number(env.KILO_TIMEOUT_MS || "30000"));
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
      console.warn(`Kilo provider response: model=${model} authConfigured=${authConfigured} status=${res.status} fallbackReason=provider invalid JSON preview=${preview}`);
      return { ok: false, model, error: "provider invalid JSON" };
    }
    console.warn(`Kilo provider response: model=${model} authConfigured=${authConfigured} status=${res.status}`);
    if (!res.ok) {
      console.warn(`Kilo provider fallback: model=${model} authConfigured=${authConfigured} providerStatus=${res.status} fallbackReason=provider status ${res.status}`);
      return { ok: false, model, error: `provider status ${res.status}` };
    }
    if (!body) {
      console.warn(`Kilo provider fallback: model=${model} authConfigured=${authConfigured} providerStatus=${res.status} fallbackReason=provider empty response`);
      return { ok: false, model, error: "provider empty response" };
    }
    const text = extractText(body);
    if (text) return { ok: true, text, model };
    console.warn(`Kilo provider fallback: model=${model} authConfigured=${authConfigured} providerStatus=${res.status} fallbackReason=provider missing content`);
    return { ok: false, model, error: "provider missing content" };
  } catch (error) {
    const fallbackReason = providerError(error);
    console.warn(`Kilo provider fallback: model=${model} authConfigured=${authConfigured} providerStatus=0 fallbackReason=${fallbackReason}`);
    return { ok: false, model, error: fallbackReason };
  } finally {
    clearTimeout(timeout);
  }
}

function validApiKey(value: string | undefined): string {
  const trimmed = String(value || "").trim();
  if (!trimmed) return "";
  if (/^(undefined|null|\[redacted\])$/i.test(trimmed)) return "";
  return trimmed;
}

function modelName(value: string | undefined): string {
  return String(value || "").trim();
}

function knownBadAnonymousModel(model: string): boolean {
  return model.trim().toLowerCase() !== DEFAULT_KILO_MODEL;
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

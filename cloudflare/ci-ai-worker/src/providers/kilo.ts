import type { Env } from "../types";

export interface KiloResult {
  ok: boolean;
  text?: string;
  model?: string;
  error?: string;
}

export function kiloEndpoint(env: Env): string {
  const base = (env.KILO_BASE_URL || "https://api.kilo.ai/api/gateway").replace(/\/$/, "");
  const path = env.KILO_CHAT_COMPLETIONS_PATH || "/chat/completions";
  return `${base}${path.startsWith("/") ? path : `/${path}`}`;
}

export async function callKiloChatCompletion(env: Env, system: string, user: string): Promise<KiloResult> {
  const endpoint = kiloEndpoint(env);
  const models = [
    env.KILO_MODEL || "kilo/auto-free",
    env.KILO_FALLBACK_MODEL || "minimax/minimax-m2.5:free",
    env.KILO_SECOND_FALLBACK_MODEL || "z-ai/glm-5:free",
  ].filter(Boolean);

  for (const model of models) {
    const attempts = [0, 1];
    for (const attempt of attempts) {
      const result = await postCompletion(endpoint, env, model, system, user);
      if (result.ok) return result;
      if (attempt === 0 && !isTransient(result.error || "")) break;
      await sleep(150 * (attempt + 1));
    }
  }
  return { ok: false, error: "Provider unavailable." };
}

async function postCompletion(endpoint: string, env: Env, model: string, system: string, user: string): Promise<KiloResult> {
  const headers: Record<string, string> = { "Content-Type": "application/json" };
  if (env.KILO_API_KEY) headers.Authorization = `Bearer ${env.KILO_API_KEY}`;
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 45000);
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
    const body = await res.json().catch(() => null) as unknown;
    if (!res.ok) return { ok: false, model, error: `${res.status}` };
    const text = extractText(body);
    return text ? { ok: true, text, model } : { ok: false, model, error: "empty response" };
  } catch (error) {
    return { ok: false, model, error: error instanceof Error ? error.message : "request failed" };
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
  if (typeof content === "string") return content;
  if (typeof record.output_text === "string") return record.output_text;
  if (typeof record.text === "string") return record.text;
  return "";
}

function isTransient(error: string): boolean {
  return /429|500|502|503|504|timeout|network|abort/i.test(error);
}

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

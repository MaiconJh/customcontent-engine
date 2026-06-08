import { afterEach, describe, expect, it, vi } from "vitest";
import { callKiloChatCompletion, kiloEndpoint } from "../src/providers/kilo";

describe("kilo provider", () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("builds the configured endpoint", () => {
    expect(kiloEndpoint({
      KILO_BASE_URL: "https://api.kilo.ai/api/gateway/",
      KILO_CHAT_COMPLETIONS_PATH: "/chat/completions",
    })).toBe("https://api.kilo.ai/api/gateway/chat/completions");
  });

  it("returns precise provider status fallback reasons", async () => {
    vi.spyOn(console, "warn").mockImplementation(() => undefined);
    vi.stubGlobal("fetch", vi.fn(async () => new Response(JSON.stringify({ error: { message: "Unauthorized" } }), { status: 401 })));

    const result = await callKiloChatCompletion({}, "system", "user");

    expect(result.ok).toBe(false);
    expect(result.error).toContain("provider status 401");
  });

  it("uses reasoning when message content is empty", async () => {
    vi.spyOn(console, "warn").mockImplementation(() => undefined);
    vi.stubGlobal("fetch", vi.fn(async () => new Response(JSON.stringify({
      choices: [{ message: { content: "", reasoning: "Reasoning fallback text." } }],
    }), { status: 200 })));

    const result = await callKiloChatCompletion({}, "system", "user");

    expect(result.ok).toBe(true);
    expect(result.text).toBe("Reasoning fallback text.");
  });
});

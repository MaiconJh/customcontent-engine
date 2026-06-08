import { afterEach, describe, expect, it, vi } from "vitest";
import { callKiloChatCompletion, kiloAuthConfigured, kiloEndpoint, kiloModels } from "../src/providers/kilo";

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

  it("uses kilo-auto/free as the default primary model", async () => {
    vi.spyOn(console, "warn").mockImplementation(() => undefined);
    const fetchMock = vi.fn(async () => new Response(JSON.stringify({
      choices: [{ message: { content: "ok" } }],
    }), { status: 200 }));
    vi.stubGlobal("fetch", fetchMock);

    const result = await callKiloChatCompletion({}, "system", "user");
    const body = JSON.parse(String((fetchMock.mock.calls as unknown as Array<[unknown, RequestInit]>)[0][1].body));

    expect(result.ok).toBe(true);
    expect(body.model).toBe("kilo-auto/free");
    expect(kiloModels({})[0]).toBe("kilo-auto/free");
  });

  it("uses only kilo-auto/free in anonymous mode", () => {
    vi.spyOn(console, "warn").mockImplementation(() => undefined);

    expect(kiloModels({})).toEqual(["kilo-auto/free"]);
  });

  it.each([
    ["missing", undefined],
    ["empty", ""],
    ["undefined literal", "undefined"],
    ["null literal", "null"],
  ])("does not send Authorization when KILO_API_KEY is %s", async (_name, apiKey) => {
    vi.spyOn(console, "warn").mockImplementation(() => undefined);
    const fetchMock = vi.fn(async () => new Response(JSON.stringify({
      choices: [{ message: { content: "ok" } }],
    }), { status: 200 }));
    vi.stubGlobal("fetch", fetchMock);

    await callKiloChatCompletion({ KILO_API_KEY: apiKey }, "system", "user");
    const headers = (fetchMock.mock.calls as unknown as Array<[unknown, RequestInit]>)[0][1].headers as Record<string, string>;

    expect(headers.Authorization).toBeUndefined();
    expect(kiloAuthConfigured({ KILO_API_KEY: apiKey })).toBe(false);
  });

  it("sends Authorization only when KILO_API_KEY is valid", async () => {
    vi.spyOn(console, "warn").mockImplementation(() => undefined);
    const fetchMock = vi.fn(async () => new Response(JSON.stringify({
      choices: [{ message: { content: "ok" } }],
    }), { status: 200 }));
    vi.stubGlobal("fetch", fetchMock);

    await callKiloChatCompletion({ KILO_API_KEY: "kilo_test_key" }, "system", "user");
    const headers = (fetchMock.mock.calls as unknown as Array<[unknown, RequestInit]>)[0][1].headers as Record<string, string>;

    expect(headers.Authorization).toBe("Bearer kilo_test_key");
    expect(kiloAuthConfigured({ KILO_API_KEY: "kilo_test_key" })).toBe(true);
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

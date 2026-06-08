import { describe, expect, it } from "vitest";
import { kiloEndpoint } from "../src/providers/kilo";

describe("kilo provider", () => {
  it("builds the configured endpoint", () => {
    expect(kiloEndpoint({
      KILO_BASE_URL: "https://api.kilo.ai/api/gateway/",
      KILO_CHAT_COMPLETIONS_PATH: "/chat/completions",
    })).toBe("https://api.kilo.ai/api/gateway/chat/completions");
  });
});

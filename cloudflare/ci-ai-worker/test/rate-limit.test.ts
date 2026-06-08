import { describe, expect, it } from "vitest";
import { checkRateLimit, resetRateLimitForTests } from "../src/rate-limit";

describe("rate limit", () => {
  it("blocks excess requests", () => {
    resetRateLimitForTests();
    const request = new Request("https://worker/v1/analyze/diff", { headers: { "CF-Connecting-IP": "127.0.0.1" } });
    const env = { RATE_LIMIT_WINDOW_SECONDS: "60", RATE_LIMIT_MAX_REQUESTS: "1" };
    expect(checkRateLimit(request, env, "/v1/analyze/diff", "repo", "push")).toBe(true);
    expect(checkRateLimit(request, env, "/v1/analyze/diff", "repo", "push")).toBe(false);
  });
});

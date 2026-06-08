import { describe, expect, it } from "vitest";
import { errorResponse, okResponse } from "../src/response-schema";

describe("response schema", () => {
  it("returns ok responses", () => {
    expect(okResponse("diff", "## Summary\nAll good").ok).toBe(true);
  });

  it("returns error responses", async () => {
    const response = errorResponse("BAD_REQUEST", "bad", 400);
    expect(response.status).toBe(400);
    await expect(response.json()).resolves.toMatchObject({ ok: false });
  });
});

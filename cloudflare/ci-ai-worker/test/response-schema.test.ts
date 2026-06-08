import { describe, expect, it } from "vitest";
import { errorResponse, okResponse } from "../src/response-schema";

describe("response schema", () => {
  it("returns ok responses", () => {
    const response = okResponse("diff", "## Summary\nAll good", [], false, {
      initialReport: "initial",
      finalReport: "final",
      governanceReview: {
        publishDecision: "publish",
        confidence: "high",
        verdict: "Supported.",
        relevance: "Relevant.",
        truthfulness: "Supported by inputs.",
        documentationAlignment: "Aligned.",
        unsupportedClaims: [],
        documentationConflicts: [],
        recommendedIssueBody: "",
      },
    });
    expect(response.ok).toBe(true);
    expect(response.governanceReview?.publishDecision).toBe("publish");
    expect(response.finalReport).toBe("final");
  });

  it("returns error responses", async () => {
    const response = errorResponse("BAD_REQUEST", "bad", 400);
    expect(response.status).toBe(400);
    await expect(response.json()).resolves.toMatchObject({ ok: false });
  });
});

import { beforeEach, describe, expect, it, vi } from "vitest";
import { analyze } from "../src/index";
import { callKiloChatCompletion } from "../src/providers/kilo";
import type { AnalyzePayload } from "../src/types";

vi.mock("../src/providers/kilo", () => ({
  callKiloChatCompletion: vi.fn(),
}));

const provider = vi.mocked(callKiloChatCompletion);

describe("analyze provider call modes", () => {
  beforeEach(() => {
    provider.mockReset();
  });

  it("uses one provider call and local governance for push to main", async () => {
    provider.mockResolvedValueOnce({ ok: true, text: "## Summary\nKilo initial report." });

    const response = await analyze(payload({ event: "push", branch: "main" }), {});

    expect(provider).toHaveBeenCalledTimes(1);
    expect(response.initialReport).toContain("Kilo initial report");
    expect(response.governanceReview?.verdict).toContain("single-provider-call mode");
    expect(response.finalReport).toBeTruthy();
    expect(response.fallbackUsed).toBe(false);
  });

  it("keeps provider governance for pull requests", async () => {
    provider
      .mockResolvedValueOnce({ ok: true, text: "## Summary\nKilo initial report." })
      .mockResolvedValueOnce({
        ok: true,
        text: [
          "## AI Governance Review",
          "### Verdict",
          "Supported.",
          "### Relevance",
          "Relevant.",
          "### Truthfulness Check",
          "Supported by inputs.",
          "### Documentation Alignment",
          "Aligned.",
          "### Publish Decision",
          "publish",
          "### Unsupported Claims",
          "None.",
          "### Documentation Conflicts",
          "None.",
        ].join("\n"),
      });

    const response = await analyze(payload({ event: "pull_request", branch: "feature" }), {});

    expect(provider).toHaveBeenCalledTimes(2);
    expect(response.governanceReview?.verdict).toBe("Supported.");
    expect(response.finalReport).toBeTruthy();
    expect(response.fallbackUsed).toBe(false);
  });

  it("uses local fallback and preserves fallback reason when initial provider fails", async () => {
    provider.mockResolvedValueOnce({ ok: false, error: "provider timeout" });

    const response = await analyze(payload({ event: "push", branch: "main" }), {});

    expect(provider).toHaveBeenCalledTimes(1);
    expect(response.fallbackUsed).toBe(true);
    expect(response.fallbackReason).toBe("provider timeout");
    expect(response.initialReport).toContain("Diff analyzed by local fallback");
    expect(response.governanceReview?.verdict).toContain("provider timeout");
    expect(response.finalReport).toBeTruthy();
  });
});

function payload(overrides: Partial<AnalyzePayload> = {}): AnalyzePayload {
  return {
    type: "diff",
    repository: "MaiconJh/customcontent-engine",
    event: "push",
    branch: "main",
    commit: "abc",
    workflow: "CI AI Governance Bot",
    run_id: "1",
    run_url: "https://github.com/MaiconJh/customcontent-engine/actions/runs/1",
    ciLogs: "GitHub Actions build-test result: success.",
    projectContext: [{ path: "docs/PROJECT_SCOPE.md", content: "Repository scope.", truncated: false }],
    diff: [
      "diff --git a/scripts/ci/call-worker.js b/scripts/ci/call-worker.js",
      "--- a/scripts/ci/call-worker.js",
      "+++ b/scripts/ci/call-worker.js",
      "@@ -1 +1 @@",
      "+console.log('diagnostic');",
    ].join("\n"),
    ...overrides,
  } as AnalyzePayload;
}

import { describe, expect, it } from "vitest";
import { formatGithubMarkdown } from "../src/formatters/github";

describe("github formatter", () => {
  it("includes governance review sections", () => {
    const markdown = formatGithubMarkdown({
      type: "diff",
      repository: "MaiconJh/customcontent-engine",
      event: "pull_request",
      branch: "feature",
      commit: "abc",
      workflow: "ci",
      run_id: "1",
      run_url: "https://github.com/MaiconJh/customcontent-engine/actions/runs/1",
      diff: "diff",
    }, "## Summary\nInitial report.", false, "", {
      publishDecision: "publish_with_caution",
      confidence: "medium",
      verdict: "Partially supported.",
      relevance: "Relevant to the diff.",
      truthfulness: "Some claims need caution.",
      documentationAlignment: "Aligned with documented scope.",
      unsupportedClaims: ["One claim is unsupported."],
      documentationConflicts: [],
      recommendedIssueBody: "",
    });
    expect(markdown).toContain("## AI Governance Review");
    expect(markdown).toContain("### Publish Decision");
    expect(markdown).toContain("publish_with_caution");
  });
});

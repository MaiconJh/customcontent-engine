import { describe, expect, it } from "vitest";
import { buildGovernancePrompt, buildPrompt } from "../src/prompt-builder";

describe("prompt-builder", () => {
  it("respects input limits", () => {
    const prompt = buildPrompt({
      type: "diff",
      repository: "MaiconJh/customcontent-engine",
      event: "pull_request",
      branch: "feature",
      commit: "abc",
      workflow: "ci",
      run_id: "1",
      run_url: "https://github.com/MaiconJh/customcontent-engine/actions/runs/1",
      diff: "x".repeat(200),
    }, { MAX_MODEL_INPUT_CHARS: "120" });
    expect(prompt.user.length).toBeLessThanOrEqual(132);
    expect(prompt.user).toContain("[TRUNCATED]");
  });

  it("builds governance prompts with project context and initial report", () => {
    const prompt = buildGovernancePrompt({
      type: "governance",
      repository: "MaiconJh/customcontent-engine",
      event: "pull_request",
      branch: "feature",
      commit: "abc",
      workflow: "ci",
      run_id: "1",
      run_url: "https://github.com/MaiconJh/customcontent-engine/actions/runs/1",
      diff: "diff --git a/docs/PROJECT_SCOPE.md b/docs/PROJECT_SCOPE.md",
      ciLogs: "GitHub Actions build-test result: success.",
      initialReport: "Initial AI report.",
      projectContext: [{ path: "docs/PROJECT_SCOPE.md", content: "Scope document.", truncated: false }],
    }, { MAX_MODEL_INPUT_CHARS: "2000" });
    expect(prompt.system).toContain("governance reviewer");
    expect(prompt.user).toContain("Initial AI report");
    expect(prompt.user).toContain("docs/PROJECT_SCOPE.md");
  });
});

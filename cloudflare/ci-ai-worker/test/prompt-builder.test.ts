import { describe, expect, it } from "vitest";
import { buildPrompt } from "../src/prompt-builder";

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
});

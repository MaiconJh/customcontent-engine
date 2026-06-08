import { describe, expect, it } from "vitest";
import { localFallback } from "../src/index";

describe("local fallback", () => {
  it("summarizes build failures", () => {
    const markdown = localFallback({
      type: "failure",
      repository: "MaiconJh/customcontent-engine",
      event: "push",
      branch: "main",
      commit: "abc",
      workflow: "ci",
      run_id: "1",
      run_url: "https://github.com/MaiconJh/customcontent-engine/actions/runs/1",
      log: "BUILD FAILED\nExecution failed for task ':test'.",
    });
    expect(markdown).toContain("BUILD FAILED");
  });
});

import { describe, expect, it } from "vitest";
import { localFallback, localGovernance } from "../src/index";

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

  it("returns conservative local governance", () => {
    const governance = localGovernance({
      type: "governance",
      repository: "MaiconJh/customcontent-engine",
      event: "push",
      branch: "main",
      commit: "abc",
      workflow: "ci",
      run_id: "1",
      run_url: "https://github.com/MaiconJh/customcontent-engine/actions/runs/1",
      diff: "diff --git a/.github/workflows/ci.yml b/.github/workflows/ci.yml\n+permissions: write-all",
      ciLogs: "GitHub Actions build-test result: success.",
      initialReport: "The workflow permissions changed.",
      projectContext: [{ path: "docs/ARCHITECTURE_GUARDRAILS.md", content: "Use minimal workflow permissions.", truncated: false }],
    }, "provider status 503");
    expect(governance.publishDecision).toBe("publish");
    expect(governance.verdict).toContain("Local governance fallback");
  });
});

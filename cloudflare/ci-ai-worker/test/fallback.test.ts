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
    expect(governance.verdict).toContain("provider status 503");
  });

  it("does not claim production code risk for workflow-only changes", () => {
    const markdown = localFallback(diffPayload([
      "diff --git a/.github/workflows/ci-ai-review.yml b/.github/workflows/ci-ai-review.yml",
      "--- a/.github/workflows/ci-ai-review.yml",
      "+++ b/.github/workflows/ci-ai-review.yml",
      "@@ -1,3 +1,4 @@",
      "+permissions:",
    ].join("\n")));

    expect(markdown).toContain("GitHub Actions workflow changes detected");
    expect(markdown).toContain("Configuration or automation changes were detected");
    expect(markdown).not.toContain("Production code changes without a corresponding test diff");
    expect(markdown).not.toContain("Java production source files changed without a Java test diff");
  });

  it("mentions automation review for scripts/ci changes", () => {
    const markdown = localFallback(diffPayload([
      "diff --git a/scripts/ci/call-worker.js b/scripts/ci/call-worker.js",
      "--- a/scripts/ci/call-worker.js",
      "+++ b/scripts/ci/call-worker.js",
      "@@ -1 +1 @@",
      "+console.log('diagnostic');",
    ].join("\n")));

    expect(markdown).toContain("CI script changes detected");
    expect(markdown).toContain("CI script output files");
  });

  it("mentions provider deploy and schema review for Worker changes", () => {
    const markdown = localFallback(diffPayload([
      "diff --git a/cloudflare/ci-ai-worker/src/providers/kilo.ts b/cloudflare/ci-ai-worker/src/providers/kilo.ts",
      "--- a/cloudflare/ci-ai-worker/src/providers/kilo.ts",
      "+++ b/cloudflare/ci-ai-worker/src/providers/kilo.ts",
      "@@ -1 +1 @@",
      "+console.warn('provider status');",
    ].join("\n")));

    expect(markdown).toContain("Cloudflare Worker changes detected");
    expect(markdown).toContain("Worker deploy status");
    expect(markdown).toContain("Kilo provider diagnostics");
  });

  it("may mention production regression risk for Java production changes without tests", () => {
    const markdown = localFallback(diffPayload([
      "diff --git a/src/main/java/com/customcontentengine/Foo.java b/src/main/java/com/customcontentengine/Foo.java",
      "--- a/src/main/java/com/customcontentengine/Foo.java",
      "+++ b/src/main/java/com/customcontentengine/Foo.java",
      "@@ -1 +1 @@",
      "+class Foo {}",
    ].join("\n")));

    expect(markdown).toContain("Java production source files changed without a Java test diff");
  });

  it("flags unsupported production claims when no production files changed", () => {
    const governance = localGovernance({
      type: "governance",
      repository: "MaiconJh/customcontent-engine",
      event: "push",
      branch: "main",
      commit: "abc",
      workflow: "ci",
      run_id: "1",
      run_url: "https://github.com/MaiconJh/customcontent-engine/actions/runs/1",
      diff: "diff --git a/scripts/ci/call-worker.js b/scripts/ci/call-worker.js\n+++ b/scripts/ci/call-worker.js\n+console.log('x');",
      ciLogs: "GitHub Actions build-test result: success.",
      initialReport: "Production code changes without a corresponding test diff may increase regression risk.",
      projectContext: [],
    }, "provider missing content");

    expect(governance.unsupportedClaims).toContain("The report claims production code changed, but the diff does not include src/main/java files.");
  });
});

function diffPayload(diff: string) {
  return {
    type: "diff" as const,
    repository: "MaiconJh/customcontent-engine",
    event: "push" as const,
    branch: "main",
    commit: "abc",
    workflow: "ci",
    run_id: "1",
    run_url: "https://github.com/MaiconJh/customcontent-engine/actions/runs/1",
    diff,
  };
}

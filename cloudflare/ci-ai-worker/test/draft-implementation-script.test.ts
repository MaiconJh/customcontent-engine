// @ts-nocheck
import { createRequire } from "node:module";
import { describe, expect, it } from "vitest";

const require = createRequire(import.meta.url);
const script = require("../../../scripts/ci/create-ai-draft-implementation-pr.js") as {
  PLAN_MARKER: string;
  assertIssueReady: (issue: Record<string, unknown>, options?: Record<string, unknown>) => void;
  buildImplementationNote: (input: Record<string, unknown>) => string;
  buildPrBody: (input: Record<string, unknown>) => string;
  extractPlan: (body: string) => string;
  implementationBranchName: (issueNumber: number) => string;
  implementationNotePath: (issueNumber: number) => string;
  outOfScopeRisks: (text: string) => string[];
  validateSafetyGates: (input: Record<string, unknown>) => { proposedFiles: string[]; estimatedLines: number };
  validateWorkerResponse: (body: unknown) => Record<string, unknown>;
};

describe("AI draft implementation PR script gates", () => {
  it("refuses missing ai:plan", () => {
    expect(() => script.assertIssueReady(issue(["ai:approved"]))).toThrow(/missing ai:plan/);
  });

  it("refuses missing ai:approved", () => {
    expect(() => script.assertIssueReady(issue(["ai:plan"]))).toThrow(/missing ai:approved/);
  });

  it("extracts only the deduplicated plan body", () => {
    const plan = script.extractPlan(`${script.PLAN_MARKER}\n\n# AI Implementation Plan\n\nBody.\n\n---\n\nPlanning metadata:\n- Fallback used: no`);
    expect(plan).toBe("# AI Implementation Plan\n\nBody.");
    expect(script.extractPlan("# No marker")).toBe("");
  });

  it("keeps branch and note naming stable", () => {
    expect(script.implementationBranchName(6)).toBe("ai/draft-implementation-issue-6");
    expect(script.implementationNotePath(6)).toBe("docs/ai-implementation-notes/issue-6.md");
  });

  it("rejects forbidden file edits", () => {
    expect(() => script.validateSafetyGates(baseGate({
      workerResponse: workerResponse({
        proposedFiles: [".github/workflows/build-test.yml"],
        fileEdits: [{ path: ".github/workflows/build-test.yml", content: "name: unsafe\n" }],
      }),
    }))).toThrow(/forbidden file edit/);
  });

  it("enforces max file and diff limits", () => {
    expect(() => script.validateSafetyGates(baseGate({
      maxFilesChanged: 1,
      workerResponse: workerResponse({
        proposedFiles: ["docs/ai-implementation-notes/extra.md"],
        fileEdits: [{ path: "docs/ai-implementation-notes/extra.md", content: "a\n" }],
      }),
    }))).toThrow(/touches 2 files/);

    expect(() => script.validateSafetyGates(baseGate({
      maxDiffLines: 5,
      workerResponse: workerResponse({
        proposedFiles: ["docs/ai-implementation-notes/extra.md"],
        fileEdits: [{ path: "docs/ai-implementation-notes/extra.md", content: Array.from({ length: 20 }, (_, i) => `line ${i}`).join("\n") }],
      }),
    }))).toThrow(/estimated diff lines/);
  });

  it("rejects out-of-scope requests", () => {
    expect(script.outOfScopeRisks("Add an economy shop and questline")).toEqual(expect.arrayContaining(["economy system", "quest system"]));
  });

  it("requires human review in PR body and avoids local Gradle recommendations", () => {
    const body = script.buildPrBody({
      issue: issue(["ai:plan", "ai:approved"]),
      planComment: { url: "https://example.test/comment", plan: "# Plan" },
      artifact: { path: "docs/ai-plans/issue-6-approved-plan.md", planningPrUrl: "https://example.test/pr" },
      workerResponse: workerResponse({}),
      changedFiles: ["docs/ai-implementation-notes/issue-6.md"],
      safetyGateSummary: "Changed files: 1.",
    });
    expect(body).toContain("It requires human review. It must not be auto-merged.");
    expect(body).not.toMatch(/run Gradle locally|\.\/gradlew|gradlew\.bat/i);
  });

  it("validates Worker response shape", () => {
    expect(() => script.validateWorkerResponse({ ok: true, summary: "x" })).toThrow(/proposedFiles/);
    expect(script.validateWorkerResponse(workerResponse({}))).toMatchObject({ ok: true, summary: "Summary" });
  });
});

function issue(labels: string[]) {
  return {
    number: 6,
    title: "AI planning smoke test",
    body: "Small approved change.",
    html_url: "https://github.com/MaiconJh/customcontent-engine/issues/6",
    labels: labels.map((name) => ({ name })),
  };
}

function workerResponse(overrides: Record<string, unknown>) {
  return {
    ok: true,
    summary: "Summary",
    proposedFiles: [],
    fileEdits: [],
    safetyNotes: [],
    validationNotes: ["GitHub Actions build/test/integrationTest", "CI AI Governance Bot"],
    fallbackUsed: false,
    ...overrides,
  };
}

function baseGate(overrides: Record<string, unknown>) {
  return {
    issue: issue(["ai:plan", "ai:approved"]),
    approvedPlanComment: "# AI Implementation Plan\n\nKeep it small.",
    planningArtifactContent: "# Approved AI Plan Handoff\n\nKeep it small.",
    workerResponse: workerResponse({}),
    maxFilesChanged: 8,
    maxDiffLines: 600,
    notePath: "docs/ai-implementation-notes/issue-6.md",
    ...overrides,
  };
}

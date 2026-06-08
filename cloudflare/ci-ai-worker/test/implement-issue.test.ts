import { beforeEach, describe, expect, it, vi } from "vitest";
import worker from "../src/index";
import { callKiloChatCompletion } from "../src/providers/kilo";
import type { Env } from "../src/types";

vi.mock("../src/providers/kilo", () => ({
  callKiloChatCompletion: vi.fn(),
}));

const provider = vi.mocked(callKiloChatCompletion);

describe("issue implementation proposal endpoint", () => {
  beforeEach(() => {
    provider.mockReset();
  });

  it("returns provider proposed file edits when they pass safety gates", async () => {
    provider.mockResolvedValueOnce({
      ok: true,
      text: JSON.stringify({
        summary: "Add a small implementation note.",
        proposedFiles: ["docs/ai-implementation-notes/issue-42.md"],
        fileEdits: [{ path: "docs/ai-implementation-notes/issue-42.md", content: "# Note\n\nHuman review required.\n" }],
        safetyNotes: ["Planning artifact was used."],
        validationNotes: ["GitHub Actions build/test/integrationTest", "CI AI Governance Bot"],
      }),
    });

    const response = await postImplementation(payload());
    const body = await response.json() as { ok: boolean; fileEdits: Array<{ path: string }>; fallbackUsed: boolean };

    expect(response.status).toBe(200);
    expect(body.ok).toBe(true);
    expect(body.fallbackUsed).toBe(false);
    expect(body.fileEdits).toHaveLength(1);
    expect(body.fileEdits[0].path).toBe("docs/ai-implementation-notes/issue-42.md");
  });

  it("uses safe fallback when the provider fails", async () => {
    provider.mockResolvedValueOnce({ ok: false, error: "provider status 401" });

    const response = await postImplementation(payload());
    const body = await response.json() as { fallbackUsed: boolean; fallbackReason: string; fileEdits: unknown[]; validationNotes: string[] };

    expect(response.status).toBe(200);
    expect(body.fallbackUsed).toBe(true);
    expect(body.fallbackReason).toBe("provider status 401");
    expect(body.fileEdits).toHaveLength(0);
    expect(body.validationNotes.join("\n")).toContain("GitHub Actions build/test/integrationTest");
    expect(JSON.stringify(body)).not.toMatch(/run Gradle locally|\.\/gradlew|gradlew\.bat/i);
  });

  it("rejects provider edits to forbidden files by falling back to no edits", async () => {
    provider.mockResolvedValueOnce({
      ok: true,
      text: JSON.stringify({
        summary: "Change workflow.",
        proposedFiles: [".github/workflows/build-test.yml"],
        fileEdits: [{ path: ".github/workflows/build-test.yml", content: "name: unsafe\n" }],
        safetyNotes: [],
        validationNotes: [],
      }),
    });

    const response = await postImplementation(payload());
    const body = await response.json() as { fallbackUsed: boolean; fallbackReason: string; fileEdits: unknown[] };

    expect(response.status).toBe(200);
    expect(body.fallbackUsed).toBe(true);
    expect(body.fallbackReason).toContain("forbidden file edit");
    expect(body.fileEdits).toHaveLength(0);
  });

  it("returns no edits for out-of-scope approved plans", async () => {
    provider.mockResolvedValueOnce({ ok: true, text: "{}" });

    const response = await postImplementation(payload({
      issueTitle: "Add economy system",
      issueBody: "Implement player balances and a shop.",
    }));
    const body = await response.json() as { fallbackUsed: boolean; fallbackReason: string; fileEdits: unknown[]; safetyNotes: string[] };

    expect(response.status).toBe(200);
    expect(body.fallbackUsed).toBe(true);
    expect(body.fallbackReason).toContain("scope guardrails");
    expect(body.fileEdits).toHaveLength(0);
    expect(body.safetyNotes.join("\n")).toContain("economy system");
  });
});

function postImplementation(body: Record<string, unknown>) {
  return worker.fetch(new Request("https://worker.test/v1/implement/issue", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  }), env());
}

function env(): Env {
  return {
    ALLOWED_REPOSITORIES: "MaiconJh/customcontent-engine",
    RATE_LIMIT_MAX_REQUESTS: "100",
  };
}

function payload(overrides: Partial<Record<string, unknown>> = {}) {
  return {
    repository: "MaiconJh/customcontent-engine",
    issueNumber: 42,
    issueTitle: "Implement a scoped approved plan",
    issueBody: "Use the approved plan and keep the change small.",
    issueLabels: ["ai:plan", "ai:approved"],
    issueUrl: "https://github.com/MaiconJh/customcontent-engine/issues/42",
    approvedPlanComment: "# AI Implementation Plan\n\n## Proposed Steps\nKeep this scoped.",
    planningArtifactContent: "# Approved AI Plan Handoff\n\n# AI Implementation Plan\n\nKeep this scoped.",
    planningArtifactPath: "docs/ai-plans/issue-42-approved-plan.md",
    projectContext: [
      { path: "docs/PROJECT_SCOPE.md", content: "Keep scope conservative.", truncated: false },
      { path: "docs/ARCHITECTURE_GUARDRAILS.md", content: "Domain must stay platform independent.", truncated: false },
    ],
    maxFilesChanged: 8,
    maxDiffLines: 600,
    dryRun: true,
    ...overrides,
  };
}

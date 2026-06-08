import { beforeEach, describe, expect, it, vi } from "vitest";
import worker from "../src/index";
import { callKiloChatCompletion } from "../src/providers/kilo";
import type { Env } from "../src/types";

vi.mock("../src/providers/kilo", () => ({
  callKiloChatCompletion: vi.fn(),
}));

const provider = vi.mocked(callKiloChatCompletion);

describe("issue-driven planning endpoint", () => {
  beforeEach(() => {
    provider.mockReset();
  });

  it("returns a provider-generated advisory implementation plan", async () => {
    provider.mockResolvedValueOnce({
      ok: true,
      text: requiredPlan("Potentially in scope after documentation review."),
    });

    const response = await postPlan(payload());
    const body = await response.json() as { ok: boolean; plan: string; fallbackUsed: boolean };

    expect(response.status).toBe(200);
    expect(body.ok).toBe(true);
    expect(body.fallbackUsed).toBe(false);
    expect(body.plan).toContain("# AI Implementation Plan");
    expect(body.plan).toContain("## Source-of-Truth Alignment");
    expect(body.plan).toContain("GitHub Actions build/test/integrationTest");
    expect(provider).toHaveBeenCalledTimes(1);
  });

  it("uses conservative fallback when the provider fails", async () => {
    provider.mockResolvedValueOnce({ ok: false, error: "provider timeout" });

    const response = await postPlan(payload());
    const body = await response.json() as { plan: string; fallbackUsed: boolean; fallbackReason: string };

    expect(response.status).toBe(200);
    expect(body.fallbackUsed).toBe(true);
    expect(body.fallbackReason).toBe("provider timeout");
    expect(body.plan).toContain("# AI Implementation Plan");
    expect(body.plan).toContain("## Source-of-Truth Alignment");
    expect(body.plan).not.toMatch(/run Gradle locally|\.\/gradlew|gradlew\.bat/i);
  });

  it("warns on out-of-scope or ADR-level requests", async () => {
    provider.mockResolvedValueOnce({ ok: false, error: "empty response" });

    const response = await postPlan(payload({
      issueTitle: "Add an economy and quest system using NMS reflection",
      issueBody: "Please add currency, quests, and net.minecraft reflection access.",
    }));
    const body = await response.json() as { plan: string; safetyNotes: string[] };

    expect(response.status).toBe(200);
    expect(body.safetyNotes.join("\n")).toMatch(/economy system/i);
    expect(body.safetyNotes.join("\n")).toMatch(/quest system/i);
    expect(body.safetyNotes.join("\n")).toMatch(/NMS or reflection/i);
    expect(body.plan).toContain("Requires ADR or maintainer scope review");
  });

  it("rejects provider plans that recommend forbidden local validation or automation", async () => {
    provider.mockResolvedValueOnce({
      ok: true,
      text: requiredPlan("Run Gradle locally, then open a pull request automatically."),
    });

    const response = await postPlan(payload());
    const body = await response.json() as { plan: string; fallbackUsed: boolean; fallbackReason: string };

    expect(response.status).toBe(200);
    expect(body.fallbackUsed).toBe(true);
    expect(body.fallbackReason).toBe("provider plan failed planning safety checks");
    expect(body.plan).not.toMatch(/run Gradle locally|open a pull request automatically/i);
    expect(body.plan).toContain("Do not let AI open pull requests automatically.");
  });
});

function postPlan(body: Record<string, unknown>) {
  return worker.fetch(new Request("https://worker.test/v1/plan/issue", {
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
    issueTitle: "Plan a scoped documentation-safe change",
    issueBody: "Please plan a small change that preserves project scope.",
    issueLabels: ["ai:plan"],
    issueAuthor: "maintainer",
    issueUrl: "https://github.com/MaiconJh/customcontent-engine/issues/42",
    projectContext: [
      { path: "docs/AI_CONTEXT_PACK.md", content: "Derived guidance only.", truncated: false },
      { path: "docs/PROJECT_SCOPE.md", content: "Keep scope conservative.", truncated: false },
      { path: "docs/ARCHITECTURE_GUARDRAILS.md", content: "Domain must stay platform independent.", truncated: false },
      { path: "docs/adr/0001-example.md", content: "Accepted ADR.", truncated: false },
      { path: "docs/milestones/m1.md", content: "Milestone scope.", truncated: false },
    ],
    aiContextPackDrift: {
      ok: true,
      driftRisk: false,
      message: "No source documentation changes detected.",
      changedSourceDocs: [],
    },
    workflow: {
      eventName: "issues",
      runId: "1",
      runUrl: "https://github.com/MaiconJh/customcontent-engine/actions/runs/1",
    },
    ...overrides,
  };
}

function requiredPlan(scopeClassification: string): string {
  return `# AI Implementation Plan

## Request Summary
Plan the requested change conservatively.

## Scope Classification
${scopeClassification}

## Source-of-Truth Alignment
Compare against docs/AI_CONTEXT_PACK.md, docs/PROJECT_SCOPE.md, docs/ARCHITECTURE_GUARDRAILS.md, docs/adr/*.md, and docs/milestones/*.md.

## Likely Files or Areas
- Documentation or CI planning areas.

## Proposed Steps
1. Review the source-of-truth documentation.
2. Draft the smallest scoped implementation approach.

## Acceptance Criteria
- The request remains aligned with documented scope.

## Validation
- GitHub Actions build/test/integrationTest.
- CI AI Governance Bot.

## Risks
- Scope drift.

## Explicit Non-Goals
- Do not edit code automatically.
- Do not auto-merge anything.

## Human Review Required
This advisory plan must be reviewed by a maintainer.`;
}

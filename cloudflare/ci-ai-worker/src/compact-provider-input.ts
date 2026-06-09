import type { AiContextPackDrift, AnalyzePayload, Env, GovernancePayload, ProjectContextFile } from "./types";
import { SYSTEM_PROMPT } from "./prompt-builder";
import { sanitizeText } from "./sanitizer";

export type ProviderEndpoint = "analyze-diff" | "analyze-failure" | "issue-plan" | "implement-issue" | "governance";

export interface CompactProviderInput {
  system: string;
  user: string;
  endpoint: ProviderEndpoint;
  limit: number;
  chars: number;
  withinLimit: boolean;
  contextFilesUsed: string[];
  contextFilesDropped: string[];
}

interface IssuePlanLikePayload {
  repository: string;
  issueNumber: number;
  issueTitle: string;
  issueBody: string;
  issueLabels: string[];
  issueAuthor?: string;
  issueUrl?: string;
  projectContext?: ProjectContextFile[];
  aiContextPackDrift?: AiContextPackDrift;
}

interface ImplementIssueLikePayload extends IssuePlanLikePayload {
  approvedPlanComment: string;
  planningArtifactContent: string;
  planningArtifactPath?: string;
  maxFilesChanged: number;
  maxDiffLines: number;
  dryRun: boolean;
}

export function buildCompactAnalyzeInput(payload: AnalyzePayload, env: Env): CompactProviderInput {
  return payload.type === "failure" ? buildCompactFailureInput(payload, env) : buildCompactDiffInput(payload, env);
}

export function buildCompactGovernanceInput(payload: GovernancePayload, env: Env): CompactProviderInput {
  const context = compactContext(payload.projectContext || [], 1800);
  const diff = compactDiff(payload.diff || "", 1800);
  const logs = compactLogs(payload.ciLogs || payload.log || "", 1200);
  const user = sanitizeText(`Repository: ${payload.repository}
Event: ${payload.event}
Branch: ${payload.branch}
Commit: ${payload.commit}
Workflow: ${payload.workflow}
Run URL: ${payload.run_url}

Compact diff evidence:
${diff}

Compact CI evidence:
${logs}

Primary context:
${context.text}

AI context pack drift signal:
${formatDriftSignal(payload.aiContextPackDrift)}

Initial AI report to audit:
${sanitizeText(payload.initialReport, 4000)}

Audit only this report. Do not invent new findings. Return Markdown with exactly these headings:

## AI Governance Review
### Verdict
### Relevance
### Truthfulness Check
### Documentation Alignment
### Publish Decision
### Unsupported Claims
### Documentation Conflicts
### Recommended Issue Body

Allowed publish decisions: publish, publish_with_caution, amend, suppress, fallback.`, compactProviderLimit(env, "governance"));
  return compactResult("governance", `You are a governance reviewer. Audit the previous AI report using only compact evidence. Prefer conservative wording. Do not include hidden reasoning, chain-of-thought, scratchpad, or phrases such as "Let me analyze".`, user, compactProviderLimit(env, "governance"), context);
}

export function buildCompactIssuePlanInput(payload: IssuePlanLikePayload, env: Env, safetyNotes: string[]): CompactProviderInput {
  const context = compactContext(payload.projectContext || [], Math.floor(compactProviderLimit(env, "issue-plan") * 0.35));
  const user = sanitizeText(`Repository: ${payload.repository}
Issue: #${payload.issueNumber}
Issue title: ${payload.issueTitle}
Issue author: ${payload.issueAuthor || "unknown"}
Issue URL: ${payload.issueUrl || ""}
Issue labels: ${payload.issueLabels.join(", ")}

Issue body:
${sanitizeText(payload.issueBody, 3500)}

Primary context:
${context.text}

AI context pack drift signal:
${formatDriftSignal(payload.aiContextPackDrift)}

Local scope guardrail signals:
${safetyNotes.length ? safetyNotes.map((note) => `- ${note}`).join("\n") : "No explicit local guardrail signal detected."}

Create an advisory implementation plan only. It must contain exactly these sections:

# AI Implementation Plan
## Request Summary
## Scope Classification
## Source-of-Truth Alignment
## Likely Files or Areas
## Proposed Steps
## Acceptance Criteria
## Validation
## Risks
## Explicit Non-Goals
## Human Review Required

Scope guardrails:
- Strongly warn or classify as out of scope/requires ADR if the issue asks for economy systems, quest systems, generic combat systems, GUI/menu frameworks, scripting languages, generic ability frameworks, land protection, NMS/reflection, direct Bukkit/Paper usage in domain/application layers, declaring folia-supported true without validation, or changing accepted ADRs without a new ADR.
- Source-of-truth documents win over AI_CONTEXT_PACK.md if they conflict.
- Do not invent exact files unless the issue or documentation clearly supports them.
- Validation must only mention remote GitHub Actions build/test/integrationTest and CI AI Governance Bot.
- State clearly that the plan is advisory and requires maintainer review.`, compactProviderLimit(env, "issue-plan"));
  return compactResult("issue-plan", `You are an issue-driven AI planning assistant for CustomContent Engine. Produce advisory planning only. Do not edit code, commit, open PRs, auto-merge, or recommend local Gradle validation. Do not include hidden reasoning, chain-of-thought, scratchpad, or phrases such as "Let me analyze". Use English only.`, user, compactProviderLimit(env, "issue-plan"), context);
}

export function buildCompactImplementIssueInput(payload: ImplementIssueLikePayload, env: Env): CompactProviderInput {
  const context = compactContext(payload.projectContext || [], 2200);
  const user = sanitizeText(`Repository: ${payload.repository}
Issue: #${payload.issueNumber}
Issue title: ${payload.issueTitle}
Issue URL: ${payload.issueUrl || ""}
Issue labels: ${payload.issueLabels.join(", ")}
Dry run: ${payload.dryRun}
Max files changed: ${payload.maxFilesChanged}
Max diff lines: ${payload.maxDiffLines}

Issue body:
${sanitizeText(payload.issueBody, 2500)}

Approved plan comment:
${sanitizeText(payload.approvedPlanComment, 4500)}

Approved planning artifact (${payload.planningArtifactPath || "unknown"}):
${sanitizeText(payload.planningArtifactContent, 4500)}

Primary context:
${context.text}

Allowed implementation paths:
- src/main/java/**
- src/main/resources/**
- src/test/java/**
- src/integrationTest/**
- docs/ai-implementation-notes/** only when relevant

Forbidden paths:
- .github/workflows/build-test.yml
- gradle/**
- gradlew
- gradlew.bat
- settings.gradle.kts
- build.gradle.kts
- docs/PROJECT_SCOPE.md
- docs/ARCHITECTURE_GUARDRAILS.md
- docs/adr/**
- docs/milestones/**
- cloudflare/**
- scripts/ci/**
- .github/workflows/**
- README.md unless explicitly part of the approved plan

Architecture guardrails:
- domain has no Bukkit, Paper, NMS, YAML, PDC, or adapter dependency.
- application has no adapter, Bukkit, Paper, or Folia dependency.
- builtin mechanics do not depend on adapters, registries, schedulers, or services.
- no reflection, NMS, ServiceLoader, runAsync, runOnEntity, SchedulerAccess, fake Bukkit completion events, global scans, or folia-supported true.

Return exactly this JSON shape:
{
  "summary": "short summary",
  "proposedFiles": ["src/..."],
  "fileEdits": [{ "path": "src/...", "content": "complete UTF-8 file content" }],
  "safetyNotes": ["..."],
  "validationNotes": ["GitHub Actions build/test/integrationTest", "CI AI Governance Bot"]
}`, compactProviderLimit(env, "implement-issue"));
  return compactResult("implement-issue", `You are a controlled AI implementation proposer for CustomContent Engine. Return JSON only. Propose only small, safe fileEdits directly traceable to the approved issue plan. If no safe implementation is obvious, return an empty fileEdits array. Do not include hidden reasoning, chain-of-thought, scratchpad, or phrases such as "Let me analyze".`, user, compactProviderLimit(env, "implement-issue"), context);
}

function buildCompactDiffInput(payload: Extract<AnalyzePayload, { type: "diff" }>, env: Env): CompactProviderInput {
  const context = compactContext(payload.projectContext || [], 4000);
  const diff = compactDiff(payload.diff, 5500);
  const ciLogs = compactLogs(payload.ciLogs || "", 1200);
  const common = commonMetadata(payload);
  const user = sanitizeText(`${common}
Base: ${payload.base || ""}
Head: ${payload.head || ""}

Review this repository change using only compact evidence.

Changed file categories:
${fileCategorySummary(payload.diff)}

Compact diff evidence:
${diff}

Compact GitHub Actions result/logs:
${ciLogs}

Primary context:
${context.text}

AI context pack drift signal:
${formatDriftSignal(payload.aiContextPackDrift)}

Check only supported claims:
- scope and architecture consistency;
- out-of-scope behavior;
- forbidden dependencies or layer violations;
- unsupported support claims such as Folia support;
- plugin/resource metadata risks;
- GitHub Actions validation assumptions.

Return Markdown with:
## Summary
## Confirmed findings
## Possible risks
## Unsupported claims
## Documentation divergence
## Suggested follow-up`, compactProviderLimit(env, "analyze-diff"));
  return compactResult("analyze-diff", SYSTEM_PROMPT, user, compactProviderLimit(env, "analyze-diff"), context);
}

function buildCompactFailureInput(payload: Extract<AnalyzePayload, { type: "failure" }>, env: Env): CompactProviderInput {
  const context = compactContext(payload.projectContext || [], 3200);
  const evidence = compactLogs(payload.log, 5500);
  const ciLogs = compactLogs(payload.ciLogs || "", 1200);
  const user = sanitizeText(`${commonMetadata(payload)}

Analyze this build/test failure using only compact GitHub Actions evidence. GitHub Actions is the source of truth. Do not instruct maintainers to run Gradle locally.

Failure evidence:
${evidence}

Additional CI context:
${ciLogs}

Primary context:
${context.text}

AI context pack drift signal:
${formatDriftSignal(payload.aiContextPackDrift)}

Return Markdown with:
## Summary
## Likely cause
## Log evidence
## Possibly related files
## Suggested fix
## Suggested snippet
## Next steps`, compactProviderLimit(env, "analyze-failure"));
  return compactResult("analyze-failure", SYSTEM_PROMPT, user, compactProviderLimit(env, "analyze-failure"), context);
}

export function compactProviderLimit(env: Env, endpoint: ProviderEndpoint): number {
  const key = endpoint === "analyze-diff"
    ? "ANALYZE_DIFF_MAX_PROVIDER_CHARS"
    : endpoint === "analyze-failure"
      ? "FAILURE_ANALYSIS_MAX_PROVIDER_CHARS"
      : endpoint === "issue-plan"
        ? "ISSUE_PLAN_MAX_PROVIDER_CHARS"
        : endpoint === "implement-issue"
          ? "IMPLEMENT_ISSUE_MAX_PROVIDER_CHARS"
          : "GOVERNANCE_MAX_PROVIDER_CHARS";
  const defaults: Record<ProviderEndpoint, number> = {
    "analyze-diff": 14000,
    "analyze-failure": 14000,
    "issue-plan": 12000,
    "implement-issue": 16000,
    governance: 10000,
  };
  const raw = Number((env as Record<string, string | undefined>)[key]);
  return Number.isFinite(raw) && raw > 1000 ? raw : defaults[endpoint];
}

function compactResult(endpoint: ProviderEndpoint, system: string, user: string, limit: number, context: { used: string[]; dropped: string[] }): CompactProviderInput {
  const chars = system.length + user.length;
  console.warn(`Compact provider input: endpoint=${endpoint} compactMode=true providerInputChars=${chars} providerInputLimit=${limit} contextFilesUsed=${context.used.length} contextFilesDropped=${context.dropped.length}`);
  return {
    system,
    user,
    endpoint,
    limit,
    chars,
    withinLimit: chars <= limit,
    contextFilesUsed: context.used,
    contextFilesDropped: context.dropped,
  };
}

function compactContext(files: ProjectContextFile[], budget: number): { text: string; used: string[]; dropped: string[] } {
  const aiContext = files.find((file) => normalizePath(file.path) === "docs/AI_CONTEXT_PACK.md");
  const important = files.filter((file) => ["docs/PROJECT_SCOPE.md", "docs/ARCHITECTURE_GUARDRAILS.md"].includes(normalizePath(file.path)));
  const selected = [aiContext, ...important].filter((file): file is ProjectContextFile => Boolean(file));
  const used: string[] = [];
  const dropped = files.map((file) => normalizePath(file.path)).filter((path) => !selected.some((file) => normalizePath(file.path) === path));
  let remaining = Math.max(1000, budget);
  const parts: string[] = [];
  for (const file of selected) {
    const path = normalizePath(file.path);
    const max = path === "docs/AI_CONTEXT_PACK.md" ? Math.floor(budget * 0.65) : Math.floor(budget * 0.175);
    if (remaining <= 0) break;
    const content = sanitizeText(file.content || "", Math.min(max, remaining));
    parts.push(`--- ${path}${file.truncated ? " [TRUNCATED]" : ""} ---\n${content}`);
    used.push(path);
    remaining -= content.length;
  }
  if (!parts.length) return { text: "No AI_CONTEXT_PACK.md or compact source context was provided.", used, dropped };
  return { text: parts.join("\n\n"), used, dropped };
}

function compactDiff(diff: string, maxChars: number): string {
  if (!diff.trim()) return "No diff was provided.";
  const headers = [...diff.matchAll(/^diff --git a\/(.+?) b\/(.+)$/gm)].map((match) => `- ${normalizePath(match[2])}`).slice(0, 80).join("\n");
  const addedRemoved = diff
    .split(/\r?\n/)
    .filter((line) => /^(diff --git|@@ |\+[^+]|-[^-])/.test(line))
    .filter((line) => !/^[-+]\s*$/.test(line))
    .slice(0, 240)
    .join("\n");
  return sanitizeText(`Changed files:\n${headers || "Not detected."}\n\nSelected diff lines:\n${addedRemoved}`, maxChars);
}

function compactLogs(logs: string, maxChars: number): string {
  if (!logs.trim()) return "No CI logs were provided.";
  const patterns = /(BUILD FAILED|FAILURE:|ERROR|Exception|Caused by:|Execution failed|Could not|failed|denied|timeout|timed out|provider status|invalid JSON|missing content)/i;
  const selected = logs.split(/\r?\n/).filter((line) => patterns.test(line)).slice(0, 80).join("\n");
  const prefix = logs.split(/\r?\n/).slice(0, 25).join("\n");
  return sanitizeText(`${selected || prefix}\n${selected ? "" : "[first lines only]"}`, maxChars);
}

function fileCategorySummary(diff: string): string {
  const files = changedFiles(diff);
  if (!files.length) return "No changed files detected.";
  const categories = new Set<string>();
  for (const file of files) {
    if (file.startsWith("src/main/java/")) categories.add("Java production");
    else if (file.startsWith("src/test/") || file.startsWith("src/integrationTest/")) categories.add("Java tests");
    else if (file.startsWith("docs/")) categories.add("docs");
    else if (file.startsWith(".github/workflows/")) categories.add("GitHub Actions workflows");
    else if (file.startsWith("scripts/ci/")) categories.add("CI scripts");
    else if (file.startsWith("cloudflare/ci-ai-worker/")) categories.add("Cloudflare Worker");
    else if (file.startsWith("src/main/resources/")) categories.add("plugin resources");
    else categories.add("other");
  }
  return [...categories].sort().map((category) => `- ${category}`).join("\n");
}

function changedFiles(diff: string): string[] {
  const files = new Set<string>();
  for (const match of diff.matchAll(/^diff --git a\/(.+?) b\/(.+)$/gm)) files.add(normalizePath(match[2]));
  for (const match of diff.matchAll(/^\+\+\+ b\/(.+)$/gm)) if (match[1] !== "/dev/null") files.add(normalizePath(match[1]));
  return [...files].filter(Boolean).sort();
}

function commonMetadata(payload: AnalyzePayload): string {
  return `Repository: ${payload.repository}\nEvent: ${payload.event}\nBranch: ${payload.branch}\nCommit: ${payload.commit}\nWorkflow: ${payload.workflow}\nRun URL: ${payload.run_url}`;
}

function formatDriftSignal(signal: AiContextPackDrift | undefined): string {
  if (!signal) return "No AI context pack drift signal was provided.";
  return JSON.stringify({ driftRisk: signal.driftRisk, message: signal.message, changedSourceDocs: signal.changedSourceDocs }, null, 2);
}

function normalizePath(path: string): string {
  return path.replace(/\\/g, "/").replace(/^\.\//, "");
}

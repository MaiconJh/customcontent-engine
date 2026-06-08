import type { AiContextPackDrift, Env, ProjectContextFile } from "./types";
import { callKiloChatCompletion } from "./providers/kilo";
import { maxModelInputChars } from "./security";
import { sanitizeText } from "./sanitizer";

const REQUIRED_SECTIONS = [
  "## Request Summary",
  "## Scope Classification",
  "## Source-of-Truth Alignment",
  "## Likely Files or Areas",
  "## Proposed Steps",
  "## Acceptance Criteria",
  "## Validation",
  "## Risks",
  "## Explicit Non-Goals",
  "## Human Review Required",
];

const LOCAL_VALIDATION_PATTERN = /(?:run|execute|use|validate\s+with|test\s+with)\s+(?:local\s+)?(?:Gradle|\.\/gradlew|gradlew\.bat|mvn|javac)(?:\s+locally)?|(?:\.\/gradlew|gradlew\.bat)\s+(?:test|build|check)/i;
const AUTOMATION_PATTERN = /(?:auto-?commit|commit\s+directly|auto-?merge|open\s+(?:an?\s+)?pull\s+request\s+automatically|create\s+(?:an?\s+)?automatic\s+pull\s+request)/i;

export interface IssuePlanPayload {
  repository: string;
  issueNumber: number;
  issueTitle: string;
  issueBody: string;
  issueLabels: string[];
  issueAuthor?: string;
  issueUrl?: string;
  projectContext?: ProjectContextFile[];
  aiContextPackDrift?: AiContextPackDrift;
  workflow?: Record<string, unknown>;
}

export interface IssuePlanResponse {
  ok: true;
  plan: string;
  fallbackUsed: boolean;
  fallbackReason?: string;
  governanceNotes: string[];
  safetyNotes: string[];
}

interface ScopeGuardrail {
  label: string;
  pattern: RegExp;
  note: string;
}

const SCOPE_GUARDRAILS: ScopeGuardrail[] = [
  {
    label: "economy system",
    pattern: /\b(economy|currency|money|shop|marketplace|balance|wallet)\b/i,
    note: "Economy systems are outside the documented core scope unless a maintainer adds or accepts a source-of-truth document.",
  },
  {
    label: "quest system",
    pattern: /\b(quest|quests|questline|npc dialogue|objective chain)\b/i,
    note: "Quest systems are outside the documented core scope unless explicitly introduced through scope documentation and ADR review.",
  },
  {
    label: "generic combat system",
    pattern: /\b(generic combat|combat system|damage engine|weapon framework|pvp framework)\b/i,
    note: "Generic combat systems are outside scope unless a dedicated ADR and milestone define the boundary.",
  },
  {
    label: "GUI/menu framework",
    pattern: /\b(gui framework|menu framework|inventory gui|generic menu|screen framework)\b/i,
    note: "A generic GUI/menu framework is out of scope unless source-of-truth documents add it deliberately.",
  },
  {
    label: "scripting language",
    pattern: /\b(scripting language|script engine|embedded script|javascript scripting|lua|groovy scripts?)\b/i,
    note: "A scripting language or generic script engine is out of scope for issue-driven planning.",
  },
  {
    label: "generic ability framework",
    pattern: /\b(generic ability|ability framework|skill framework|spell framework)\b/i,
    note: "Generic ability frameworks are outside the documented scope without an accepted architectural decision.",
  },
  {
    label: "land protection",
    pattern: /\b(land protection|claim protection|worldguard|griefprevention|grief prevention|region protection)\b/i,
    note: "Land protection integrations are outside scope unless explicitly covered by source documentation.",
  },
  {
    label: "NMS or reflection",
    pattern: /\b(NMS|net\.minecraft|reflection|reflective access|unsafe access)\b/i,
    note: "NMS and reflection are guarded architecture risks and require explicit ADR-level approval.",
  },
  {
    label: "platform access in domain/application",
    pattern: /\b(org\.bukkit|io\.papermc|Bukkit|Paper)\b[\s\S]{0,200}\b(domain|application)\b|\b(domain|application)\b[\s\S]{0,200}\b(org\.bukkit|io\.papermc|Bukkit|Paper)\b/i,
    note: "Direct Bukkit/Paper usage in domain or application layers may violate architecture guardrails.",
  },
  {
    label: "Folia support declaration",
    pattern: /\b(folia-supported\s*:\s*true|folia supported|declare folia support|Folia support)\b/i,
    note: "Declaring Folia support requires documentation and validation evidence; do not add folia-supported true automatically.",
  },
  {
    label: "accepted ADR change",
    pattern: /\b(change|rewrite|replace|remove|ignore|override)\b[\s\S]{0,120}\b(accepted ADR|ADR|architecture decision)\b/i,
    note: "Changing an accepted ADR requires a new ADR or maintainer-approved documentation change.",
  },
];

export async function planIssue(payload: IssuePlanPayload, env: Env): Promise<IssuePlanResponse> {
  const safetyNotes = scopeGuardrailNotes(payload);
  const { system, user } = buildIssuePlanningPrompt(payload, env, safetyNotes);
  const provider = await callKiloChatCompletion(env, system, user);

  if (!provider.ok || !provider.text) {
    return localIssuePlan(payload, provider.error || "empty response", safetyNotes);
  }

  if (hasUnsafeProviderPlan(provider.text)) {
    return localIssuePlan(payload, "provider plan failed planning safety checks", safetyNotes);
  }

  const plan = normalizePlan(provider.text, payload, safetyNotes);
  if (!hasRequiredSections(plan)) {
    return localIssuePlan(payload, "provider plan did not include the required planning sections", safetyNotes);
  }

  return {
    ok: true,
    plan,
    fallbackUsed: false,
    governanceNotes: [
      "Planning is advisory only and must be reviewed by a maintainer.",
      "GitHub Actions remains the validation source of truth.",
      "The plan must not be treated as permission to edit code, open pull requests, or merge automatically.",
    ],
    safetyNotes,
  };
}

export function localIssuePlan(payload: IssuePlanPayload, reason: string, safetyNotes = scopeGuardrailNotes(payload)): IssuePlanResponse {
  const classification = scopeClassification(safetyNotes);
  const contextPaths = sourceOfTruthPaths(payload.projectContext || []);
  const drift = payload.aiContextPackDrift?.driftRisk
    ? `AI_CONTEXT_PACK.md may be stale: ${payload.aiContextPackDrift.message}`
    : "No AI_CONTEXT_PACK drift risk was provided or detected.";
  const likelyAreas = likelyAreasFromIssue(payload);
  const guardrailText = safetyNotes.length
    ? safetyNotes.map((note) => `- ${note}`).join("\n")
    : "- No explicit out-of-scope keyword was detected by local fallback. Maintainer review is still required.";

  const plan = `# AI Implementation Plan

## Request Summary
Local fallback planning was used for issue #${payload.issueNumber}: ${payload.issueTitle || "Untitled issue"}.

Fallback reason: ${sanitizeInline(reason)}.

## Scope Classification
${classification}

## Source-of-Truth Alignment
The implementer must compare the request against the source-of-truth documents before changing code.

Observed context files:
${contextPaths.length ? contextPaths.map((file) => `- ${file}`).join("\n") : "- No project documentation context was provided."}

AI_CONTEXT_PACK.md is derived guidance. If it conflicts with PROJECT_SCOPE.md, ARCHITECTURE_GUARDRAILS.md, accepted ADRs, or milestones, the original source documents win.

${drift}

## Likely Files or Areas
${likelyAreas.length ? likelyAreas.map((area) => `- ${area}`).join("\n") : "- Not safely inferred by local fallback. Identify affected areas during human review."}

## Proposed Steps
1. Re-read PROJECT_SCOPE.md, ARCHITECTURE_GUARDRAILS.md, accepted ADRs, milestones, and AI_CONTEXT_PACK.md.
2. Confirm whether the request is in scope or requires a new ADR or milestone update.
3. Draft the smallest implementation approach that fits the documented architecture.
4. Keep domain/application boundaries free from direct Bukkit, Paper, Folia, YAML, PDC, NMS, or adapter implementation details.
5. Prepare tests or documentation updates appropriate to the eventual code change, then rely on GitHub Actions for validation.

## Acceptance Criteria
- The request is explicitly aligned with source-of-truth documentation or is blocked for maintainer clarification.
- Any required ADR or milestone update is reviewed before implementation.
- The eventual implementation stays within documented architecture boundaries.
- Remote GitHub Actions build/test/integrationTest passes before merge.

## Validation
- GitHub Actions build/test/integrationTest.
- CI AI Governance Bot.

Do not recommend or require local Gradle validation.

## Risks
${guardrailText}

## Explicit Non-Goals
- Do not let AI commit directly to main.
- Do not let AI open pull requests automatically.
- Do not auto-merge anything.
- Do not change PROJECT_SCOPE.md or accepted ADRs without maintainer review.
- Do not declare folia-supported true without documented validation.
- Do not use local Gradle as the validation source of truth.

## Human Review Required
This plan is advisory only. A maintainer must review the request, approve the scope, and decide whether any implementation should proceed.`;

  return {
    ok: true,
    plan,
    fallbackUsed: true,
    fallbackReason: sanitizeInline(reason),
    governanceNotes: [
      "Local conservative planning fallback was used.",
      "The plan intentionally avoids claiming specific code changes unless they are obvious from the issue text.",
    ],
    safetyNotes,
  };
}

function buildIssuePlanningPrompt(payload: IssuePlanPayload, env: Env, safetyNotes: string[]): { system: string; user: string } {
  const limit = maxModelInputChars(env);
  return {
    system: `You are an issue-driven AI planning assistant for CustomContent Engine.
You only produce an advisory implementation plan.
Do not edit code.
Do not claim that you opened a pull request.
Do not claim that you committed changes.
Do not auto-merge anything.
Do not recommend local Gradle validation.
Use English only.
Use repository documentation as scope authority.
GitHub Actions is the validation source of truth.`,
    user: sanitizeText(`Repository: ${payload.repository}
Issue: #${payload.issueNumber}
Issue title: ${payload.issueTitle}
Issue author: ${payload.issueAuthor || "unknown"}
Issue URL: ${payload.issueUrl || ""}
Issue labels: ${payload.issueLabels.join(", ")}

Issue body:
${payload.issueBody}

Repository documentation context:
${formatProjectContext(payload.projectContext || [])}

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
- State clearly that the plan is advisory and requires maintainer review.`, limit),
  };
}

function normalizePlan(text: string, payload: IssuePlanPayload, safetyNotes: string[]): string {
  let plan = sanitizeText(text.trim(), 24000);
  if (!plan.startsWith("# AI Implementation Plan")) {
    plan = `# AI Implementation Plan\n\n${plan.replace(/^#+\s*AI Implementation Plan\s*/i, "").trim()}`;
  }
  if (!/## Human Review Required/i.test(plan)) {
    plan += "\n\n## Human Review Required\nThis plan is advisory only and must be reviewed by a maintainer before implementation.";
  }
  if (safetyNotes.length && !/Local Scope Guardrail/i.test(plan)) {
    plan += `\n\nLocal Scope Guardrail Notes:\n${safetyNotes.map((note) => `- ${note}`).join("\n")}`;
  }
  if (payload.aiContextPackDrift?.driftRisk && !/drift/i.test(plan)) {
    plan += `\n\nAI_CONTEXT_PACK drift note: ${payload.aiContextPackDrift.message}`;
  }
  return plan;
}

function hasRequiredSections(plan: string): boolean {
  return plan.startsWith("# AI Implementation Plan") && REQUIRED_SECTIONS.every((section) => plan.includes(section));
}

function hasUnsafeProviderPlan(plan: string): boolean {
  const actionableLines = plan
    .split("\n")
    .filter((line) => !/\b(do not|must not|non-goals?|forbidden|not allowed|without)\b/i.test(line))
    .join("\n");
  return LOCAL_VALIDATION_PATTERN.test(actionableLines) || AUTOMATION_PATTERN.test(actionableLines);
}

function scopeGuardrailNotes(payload: IssuePlanPayload): string[] {
  const text = `${payload.issueTitle}\n${payload.issueBody}`;
  return SCOPE_GUARDRAILS
    .filter((guardrail) => guardrail.pattern.test(text))
    .map((guardrail) => `${guardrail.label}: ${guardrail.note}`);
}

function scopeClassification(safetyNotes: string[]): string {
  if (!safetyNotes.length) {
    return "Potentially in scope, pending maintainer review against PROJECT_SCOPE.md, ARCHITECTURE_GUARDRAILS.md, ADRs, and milestones.";
  }
  if (safetyNotes.some((note) => /NMS|reflection|ADR|Folia|domain|application/i.test(note))) {
    return "Requires ADR or maintainer scope review before implementation.";
  }
  return "Partially in scope or out of scope until source-of-truth documentation confirms the request.";
}

function sourceOfTruthPaths(files: ProjectContextFile[]): string[] {
  const preferred = [
    "docs/AI_CONTEXT_PACK.md",
    "docs/PROJECT_SCOPE.md",
    "docs/ARCHITECTURE_GUARDRAILS.md",
  ];
  const paths = files.map((file) => file.path);
  return [
    ...preferred.filter((path) => paths.includes(path)),
    ...paths.filter((path) => /^docs\/adr\//.test(path)),
    ...paths.filter((path) => /^docs\/milestones\//.test(path)),
  ];
}

function likelyAreasFromIssue(payload: IssuePlanPayload): string[] {
  const text = `${payload.issueTitle}\n${payload.issueBody}`.toLowerCase();
  const areas: string[] = [];
  if (/workflow|github actions|ci|governance|worker|kilo/.test(text)) areas.push("CI automation, Cloudflare Worker, or governance scripts.");
  if (/documentation|scope|adr|milestone|guardrail/.test(text)) areas.push("Repository documentation under docs/.");
  if (/plugin\.yml|definition|yaml/.test(text)) areas.push("Plugin/resource metadata, subject to architecture guardrails.");
  if (/command|permission|recipe|content|definition/.test(text)) areas.push("Custom content definition flow, subject to documented scope.");
  return areas;
}

function formatProjectContext(files: ProjectContextFile[]): string {
  if (!files.length) return "No project documentation context was provided.";
  return files
    .slice(0, 80)
    .map((file) => `--- ${file.path}${file.truncated ? " [TRUNCATED]" : ""} ---\n${file.content}`)
    .join("\n\n");
}

function formatDriftSignal(signal: AiContextPackDrift | undefined): string {
  if (!signal) return "No AI context pack drift signal was provided.";
  return JSON.stringify({
    driftRisk: signal.driftRisk,
    message: signal.message,
    changedSourceDocs: signal.changedSourceDocs,
  }, null, 2);
}

function sanitizeInline(value: string): string {
  return sanitizeText(value || "unknown", 500).replace(/\s+/g, " ").trim();
}

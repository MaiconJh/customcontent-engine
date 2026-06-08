import type { AnalyzePayload, Env, GovernancePayload, ProjectContextFile } from "./types";
import { maxModelInputChars } from "./security";
import { sanitizeText } from "./sanitizer";

export const SYSTEM_PROMPT = `You are a technical reviewer for CI/CD and code changes.
Analyze only the provided data.
Do not invent files.
Do not invent commands.
Do not invent dependencies.
Do not expose secrets.
Do not repeat full logs.
Do not generate overly long responses.
If there is uncertainty, state it explicitly.
Return clear, concise, actionable Markdown in English.
Prefer small, functional snippets.
Do not generate dangerous code.
Do not recommend disabling tests to fix failures.
Do not recommend ignoring build errors.
Prioritize real fixes.`;

export function buildPrompt(payload: AnalyzePayload, env: Env): { system: string; user: string } {
  const limit = maxModelInputChars(env);
  const common = `Repository: ${payload.repository}
Event: ${payload.event}
Branch: ${payload.branch}
Commit: ${payload.commit}
Workflow: ${payload.workflow}
Run URL: ${payload.run_url}`;
  const context = formatProjectContext(payload.projectContext || []);
  const ciLogs = sanitizeText(payload.ciLogs || "", Math.min(12000, limit));

  if (payload.type === "failure") {
    return {
      system: SYSTEM_PROMPT,
      user: sanitizeText(`${common}

You are analyzing a build/test failure in a Java/Gradle/Maven project.
Use only the sanitized GitHub Actions log, metadata, and repository documentation context.
GitHub Actions is the source of truth for build/test/integrationTest validation.
Do not instruct maintainers to run Gradle locally.

Return Markdown with:

## Summary
## Likely cause
## Log evidence
## Possibly related files
## Suggested fix
## Suggested snippet
## Next steps

Sanitized log:
${payload.log}

Repository documentation context:
${context}`, limit),
    };
  }

  return {
    system: SYSTEM_PROMPT,
    user: sanitizeText(`${common}
Base: ${payload.base || ""}
Head: ${payload.head || ""}

You are reviewing a repository change using:
1. The git diff.
2. GitHub Actions result/logs.
3. The repository documentation context.

Your task is not to invent issues.
Your task is to determine whether the change is consistent with the documented architecture and scope.

Check:
- Does the diff violate PROJECT_SCOPE.md?
- Does it violate ARCHITECTURE_GUARDRAILS.md?
- Does it contradict any ADR?
- Does it implement features marked out of scope?
- Does it alter architectural boundaries?
- Does it add forbidden dependencies?
- Does it bypass GitHub Actions validation?
- Does it introduce runtime behavior not documented?
- Does it claim support not documented, such as Folia support?
- Does it modify plugin.yml incorrectly?
- Does it introduce local-only validation assumptions?

Do not claim a problem unless supported by the diff, CI logs, or documentation context.
If the diff is consistent with the documentation, say so clearly.

Return Markdown with:

## Summary
## Confirmed findings
## Possible risks
## Unsupported claims
## Documentation divergence
## Suggested follow-up

Sanitized diff:
${payload.diff}

GitHub Actions result/logs:
${ciLogs}

Repository documentation context:
${context}`, limit),
  };
}

export function buildGovernancePrompt(payload: GovernancePayload, env: Env): { system: string; user: string } {
  const limit = maxModelInputChars(env);
  return {
    system: `You are a governance reviewer.
Your task is to audit the previous AI report, not to invent new findings.
Check whether each claim is supported by the diff, CI logs, or repository documentation.
If the report is irrelevant, exaggerated, or unsupported, say so.
If the report missed a clear architecture/scope violation, say so.
Do not publish unsupported claims.
Prefer conservative wording.
Return clear English Markdown plus a concise machine-readable decision line.`,
    user: sanitizeText(`Repository: ${payload.repository}
Event: ${payload.event}
Branch: ${payload.branch}
Commit: ${payload.commit}
Workflow: ${payload.workflow}
Run URL: ${payload.run_url}

Git diff:
${payload.diff || ""}

CI result/logs:
${payload.ciLogs || payload.log || ""}

Project documentation context:
${formatProjectContext(payload.projectContext || [])}

Initial AI report:
${payload.initialReport}

Audit questions:
- Is the initial report relevant?
- Is the report factually supported by diff, CI logs, or projectContext?
- Are there unsupported claims?
- Are there documentation conflicts?
- Did the report miss an important scope or architecture violation?
- Should the report be published as-is, published with caution, amended, suppressed, or replaced by fallback?

Return Markdown with exactly these headings:

## AI Governance Review
### Verdict
### Relevance
### Truthfulness Check
### Documentation Alignment
### Publish Decision
### Unsupported Claims
### Documentation Conflicts
### Recommended Issue Body

Allowed publish decisions: publish, publish_with_caution, amend, suppress, fallback.`, limit),
  };
}

function formatProjectContext(files: ProjectContextFile[]): string {
  if (!files.length) return "No project documentation context was provided.";
  return files
    .slice(0, 80)
    .map((file) => `--- ${file.path}${file.truncated ? " [TRUNCATED]" : ""} ---\n${file.content}`)
    .join("\n\n");
}

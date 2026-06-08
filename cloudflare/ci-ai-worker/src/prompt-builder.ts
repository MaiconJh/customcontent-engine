import type { AnalyzePayload, Env } from "./types";
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

  if (payload.type === "failure") {
    return {
      system: SYSTEM_PROMPT,
      user: sanitizeText(`${common}

You are analyzing a build/test failure in a Java/Gradle/Maven project.
Use only the sanitized log and provided metadata.

Return Markdown with:

## Summary
## Likely cause
## Log evidence
## Possibly related files
## Suggested fix
## Suggested snippet
## Next steps

Sanitized log:
${payload.log}`, limit),
    };
  }

  return {
    system: SYSTEM_PROMPT,
    user: sanitizeText(`${common}
Base: ${payload.base || ""}
Head: ${payload.head || ""}

You are analyzing a code/documentation/configuration diff.
Use only the sanitized diff and provided metadata.
Prioritize regressions, broken tests, architecture, Gradle/Maven, YAML, JSON, documentation, security, and compatibility.

Return Markdown with:

## Summary
## Technical impact
## Risks
## Previous vs new configuration
## Guidance
## Suggested checklist

Sanitized diff:
${payload.diff}`, limit),
  };
}

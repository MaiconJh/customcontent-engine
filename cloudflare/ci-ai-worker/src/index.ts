import type { AnalyzePayload, Env, Finding } from "./types";
import { buildPrompt } from "./prompt-builder";
import { checkRateLimit } from "./rate-limit";
import { callKiloChatCompletion } from "./providers/kilo";
import { formatGithubMarkdown, normalizeFindings } from "./formatters/github";
import { errorResponse, okResponse } from "./response-schema";
import { readJsonBody, SecurityError, validatePayload, validateSharedSecret } from "./security";
import { sanitizeObject } from "./sanitizer";

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    try {
      const url = new URL(request.url);
      if (request.method === "OPTIONS") return new Response(null, { status: 403 });
      if (request.method === "GET" && url.pathname === "/health") {
        return Response.json({ ok: true, service: "ci-ai-worker" });
      }
      if (request.method !== "POST") return errorResponse("BAD_REQUEST", "Unsupported method.", 405);
      if (!["/v1/analyze/failure", "/v1/analyze/diff"].includes(url.pathname)) {
        return errorResponse("BAD_REQUEST", "Unsupported route.", 404);
      }
      await validateSharedSecret(request, env);
      const expectedType = url.pathname.endsWith("/failure") ? "failure" : "diff";
      const raw = await readJsonBody(request, env);
      const payload = sanitizeObject(validatePayload(raw, expectedType, env), Number(env.MAX_MODEL_INPUT_CHARS || "50000"));
      if (!checkRateLimit(request, env, url.pathname, payload.repository, payload.event)) {
        return errorResponse("RATE_LIMITED", "Rate limit exceeded.", 429);
      }
      const response = await analyze(payload, env);
      return Response.json(response);
    } catch (error) {
      if (error instanceof SecurityError) return errorResponse(error.code, error.message, error.status);
      return errorResponse("INTERNAL_ERROR", "Internal error.", 500);
    }
  },
};

export async function analyze(payload: AnalyzePayload, env: Env) {
  const prompt = buildPrompt(payload, env);
  const provider = await callKiloChatCompletion(env, prompt.system, prompt.user);
  const fallback = !provider.ok || !provider.text;
  if (fallback) {
    console.warn(`AI provider fallback used: ${provider.error || "empty response"}`);
  }
  const markdown = fallback ? localFallback(payload) : provider.text || "";
  const findings = payload.type === "diff" ? diffFindings(payload.diff) : failureFindings(payload.log);
  return okResponse(payload.type, formatGithubMarkdown(payload, markdown, fallback, provider.error || "empty response"), normalizeFindings(findings), fallback);
}

export function localFallback(payload: AnalyzePayload): string {
  if (payload.type === "failure") {
    const log = payload.log;
    const matched = [
      "Compilation failed", "BUILD FAILED", "Test failed", "There were failing tests", "Could not resolve",
      "Could not find", "Unsupported class file major version", "NoSuchMethodError", "ClassNotFoundException",
      "NullPointerException", "Execution failed for task", "Could not determine java version", "Permission denied",
      "./gradlew: No such file or directory",
    ].filter((pattern) => log.includes(pattern));
    return `## Summary

Build/test failed. ${matched.length ? `Detected patterns: ${matched.join(", ")}.` : "There is not enough evidence to identify a single cause."}

## Likely cause

${matched[0] || "Compilation, test, or configuration failure detected in the sanitized log."}

## Log evidence

\`\`\`text
${extractEvidence(log)}
\`\`\`

## Possibly related files

Not safely inferred from the sanitized log.

## Suggested fix

Use the GitHub Actions log as the source of truth and fix the first real failure reported by the remote workflow.

## Next steps

* Open the build.log artifact.
* Fix the root cause.
* Re-run the GitHub Actions workflow.`;
  }

  const diff = payload.diff;
  const hints = [
    [/build\.gradle|build\.gradle\.kts|pom\.xml/, "Build/dependency change detected."],
    [/\.github\/workflows/, "GitHub Actions change detected; review permissions, cache, and triggers."],
    [/docs\/adr\//, "ADR change detected; review whether the architectural decision remains consistent with scope and guardrails."],
    [/docs\/PROJECT_SCOPE\.md/, "Project scope documentation changed; review whether generated code and claims stay within documented scope."],
    [/docs\/ARCHITECTURE_GUARDRAILS\.md/, "Architecture guardrails changed; review boundary rules carefully."],
    [/plugin\.yml/, "plugin.yml changed; review plugin metadata and platform declarations."],
    [/folia-supported:\s*true/i, "Folia support declaration risk detected; verify that documentation explicitly supports the claim."],
    [/^\+import\s+(org\.bukkit|io\.papermc)/m, "Platform import added; verify it does not enter forbidden layers."],
    [/src\/main\//, "Production code changed."],
    [/src\/test\//, "Tests changed."],
    [/secrets?|env|token|permission/i, "Sensitive configuration change detected."],
  ].filter(([regex]) => (regex as RegExp).test(diff)).map(([, msg]) => msg);
  return `## Summary

Diff analyzed by local fallback. ${hints.join(" ") || "No critical pattern was detected by regex."}

## Technical impact

Review whether the changes affect build, tests, configuration, or production behavior.

## Risks

Production code changes without a corresponding test diff may increase regression risk.

## Previous vs new configuration

* Previous: see removed lines in the diff.
* New: see added lines in the diff.
* Impact: validate commands and permissions when configuration files change.
* Risk: cache, Java version, dependencies, and permissions can change CI behavior.
* Recommended adjustment: keep tests and documentation aligned.

## Guidance

Use GitHub Actions build/test/integrationTest as the validation source of truth and manually review documentation-sensitive points.

## Suggested checklist

* [ ] GitHub Actions build/test/integrationTest completed.
* [ ] Relevant tests or documentation updated.
* [ ] Configuration reviewed.`;
}

function extractEvidence(text: string): string {
  return text.split("\n").filter((line) => /BUILD FAILED|Compilation failed|Test failed|Could not|Exception|Execution failed|Permission denied/i.test(line)).slice(0, 12).join("\n").slice(0, 1600) || "No short evidence available.";
}

function failureFindings(log: string): Finding[] {
  return /BUILD FAILED|Compilation failed|There were failing tests/.test(log)
    ? [{ severity: "error", title: "Build/test failed", body: "The sanitized log indicates a real build or test failure." }]
    : [];
}

function diffFindings(diff: string): Finding[] {
  const findings: Finding[] = [];
  if (/^\+\+\+ b\/src\/main\//m.test(diff) && !/^\+\+\+ b\/src\/test\//m.test(diff)) {
    findings.push({ severity: "warning", title: "Production change without test diff", body: "Production code changed without a test change in the diff." });
  }
  if (/^\+\+\+ b\/\.github\/workflows\//m.test(diff) && /permissions:/m.test(diff)) {
    findings.push({ severity: "warning", file: ".github/workflows", title: "Workflow permissions changed", body: "Review whether permissions are still minimal." });
  }
  return findings;
}

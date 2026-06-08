import type { AnalyzePayload, Env, Finding, GovernancePayload, GovernanceVerdict } from "./types";
import { buildGovernancePrompt, buildPrompt } from "./prompt-builder";
import { checkRateLimit } from "./rate-limit";
import { callKiloChatCompletion } from "./providers/kilo";
import { formatGithubMarkdown, normalizeFindings } from "./formatters/github";
import { errorResponse, okResponse } from "./response-schema";
import { readJsonBody, SecurityError, validateGovernancePayload, validatePayload, validateSharedSecret } from "./security";
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
      if (!["/v1/analyze/failure", "/v1/analyze/diff", "/v1/analyze/governance"].includes(url.pathname)) {
        return errorResponse("BAD_REQUEST", "Unsupported route.", 404);
      }
      await validateSharedSecret(request, env);
      if (url.pathname.endsWith("/governance")) {
        const rawGovernance = await readJsonBody(request, env);
        const governancePayload = sanitizeObject(validateGovernancePayload(rawGovernance, env), Number(env.MAX_MODEL_INPUT_CHARS || "50000"));
        if (!checkRateLimit(request, env, url.pathname, governancePayload.repository, governancePayload.event)) {
          return errorResponse("RATE_LIMITED", "Rate limit exceeded.", 429);
        }
        return Response.json({ ok: true, type: "governance", governance: await governanceReview(governancePayload, env) });
      }
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
  const initialMarkdown = fallback ? localFallback(payload) : provider.text || "";
  const governance = await governanceReview({
    ...payload,
    type: "governance",
    report: initialMarkdown,
    diff: payload.type === "diff" ? payload.diff : undefined,
    log: payload.type === "failure" ? payload.log : undefined,
  }, env);
  const markdown = `${initialMarkdown.trim()}

${governance.recommendedIssueBody.trim()}`;
  const findings = payload.type === "diff" ? diffFindings(payload.diff) : failureFindings(payload.log);
  return okResponse(payload.type, formatGithubMarkdown(payload, markdown, fallback, provider.error || "empty response"), normalizeFindings(findings), fallback, governance);
}

export async function governanceReview(payload: GovernancePayload, env: Env): Promise<GovernanceVerdict> {
  const prompt = buildGovernancePrompt(payload, env);
  const provider = await callKiloChatCompletion(env, prompt.system, prompt.user);
  if (!provider.ok || !provider.text) {
    return localGovernance(payload, provider.error || "empty response");
  }
  return {
    publishDecision: inferPublishDecision(provider.text),
    confidence: "medium",
    verdict: firstParagraph(provider.text),
    unsupportedClaims: extractBullets(provider.text, "Unsupported Claims"),
    documentationConflicts: extractBullets(provider.text, "Documentation Conflicts"),
    recommendedIssueBody: provider.text,
  };
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

function localGovernance(payload: GovernancePayload, reason: string): GovernanceVerdict {
  const report = payload.report || "";
  const diff = payload.diff || "";
  const unsupportedClaims = [
    /local build|run gradle locally|\.\/gradlew|gradlew\.bat/i.test(report) ? "The report suggests local Gradle validation, but validation must be performed by GitHub Actions." : "",
    /Folia/i.test(report) && !/Folia/i.test(diff + contextText(payload)) ? "The report mentions Folia without clear support in the diff or documentation context." : "",
  ].filter(Boolean);
  const documentationConflicts = documentationConflictHints(diff);
  const publishDecision = unsupportedClaims.length ? "publish_with_caution" : "publish";
  const recommendedIssueBody = `## AI Governance Review

### Verdict
Local governance fallback reviewed the AI report. Safe reason for provider fallback: ${reason}.

### Relevance
The report is considered relevant when it references the provided diff, GitHub Actions result/logs, or project documentation context.

### Truthfulness Check
${unsupportedClaims.length ? "Some claims need caution because they are not directly supported." : "No unsupported claim was detected by local heuristics."}

### Documentation Alignment
${documentationConflicts.length ? documentationConflicts.map((item) => `* ${item}`).join("\n") : "No direct documentation conflict was detected by local heuristics."}

### Unsupported Claims
${unsupportedClaims.length ? unsupportedClaims.map((item) => `* ${item}`).join("\n") : "* None detected by local heuristics."}

### Documentation Conflicts
${documentationConflicts.length ? documentationConflicts.map((item) => `* ${item}`).join("\n") : "* None detected by local heuristics."}

### Publish Decision
${publishDecision}`;
  return {
    publishDecision,
    confidence: "low",
    verdict: unsupportedClaims.length ? "The report is partially relevant, but at least one claim needs caution." : "The report appears relevant under local governance heuristics.",
    unsupportedClaims,
    documentationConflicts,
    recommendedIssueBody,
  };
}

function documentationConflictHints(diff: string): string[] {
  const hints: string[] = [];
  if (/^\+\+\+ b\/docs\/adr\//m.test(diff)) hints.push("ADR documentation changed; ensure implementation and review claims follow the new decision.");
  if (/^\+\+\+ b\/src\/main\/java\/com\/customcontentengine\/domain\//m.test(diff) && /^\+import\s+(org\.bukkit|io\.papermc)/m.test(diff)) {
    hints.push("Domain code appears to add a platform import, which may violate documented architecture boundaries.");
  }
  if (/plugin\.yml/m.test(diff) && /folia-supported:\s*true/i.test(diff)) {
    hints.push("plugin.yml appears to declare Folia support; verify this is documented and actually supported.");
  }
  if (/\.github\/workflows\//m.test(diff) && /pull_request_target/i.test(diff)) {
    hints.push("Workflow changes mention pull_request_target; verify this does not weaken fork safety.");
  }
  return hints;
}

function contextText(payload: GovernancePayload): string {
  return (payload.projectContext || []).map((file) => `${file.path}\n${file.content}`).join("\n");
}

function inferPublishDecision(markdown: string): GovernanceVerdict["publishDecision"] {
  const lowered = markdown.toLowerCase();
  if (lowered.includes("suppress")) return "suppress";
  if (lowered.includes("publish_with_caution")) return "publish_with_caution";
  if (lowered.includes("fallback")) return "fallback";
  return "publish";
}

function firstParagraph(markdown: string): string {
  return markdown.split(/\n\s*\n/).map((part) => part.replace(/^#+\s*/gm, "").trim()).find(Boolean)?.slice(0, 500) || "Governance review completed.";
}

function extractBullets(markdown: string, heading: string): string[] {
  const idx = markdown.toLowerCase().indexOf(heading.toLowerCase());
  if (idx < 0) return [];
  return markdown.slice(idx).split("\n").filter((line) => /^\s*[-*]\s+/.test(line)).slice(0, 10).map((line) => line.replace(/^\s*[-*]\s+/, "").trim());
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

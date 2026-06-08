import type { AnalyzePayload, Env, Finding, GovernancePayload, GovernanceReview, PublishDecision } from "./types";
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
        return Response.json({ ok: true, type: "governance", governanceReview: await governanceReview(governancePayload, env) });
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
  const initialFallback = !provider.ok || !provider.text;
  let fallbackReason = initialFallback ? provider.error || "empty response" : undefined;
  if (initialFallback) {
    console.warn(`AI provider fallback used: ${provider.error || "empty response"}`);
  }
  const initialReport = initialFallback ? localFallback(payload) : provider.text || "";
  const metadata = (payload as { metadata?: Record<string, unknown> }).metadata;
  const singleProviderCall = isMainPush(payload)
    || String(metadataValue(metadata, "singleProviderCall") || "").toLowerCase() === "true"
    || String((env as Env & { CI_AI_SINGLE_PROVIDER_CALL?: string }).CI_AI_SINGLE_PROVIDER_CALL || "").toLowerCase() === "true";
  const governancePayload = {
    ...payload,
    type: "governance",
    initialReport,
    diff: payload.type === "diff" ? payload.diff : undefined,
    log: payload.type === "failure" ? payload.log : undefined,
  } satisfies GovernancePayload;
  const governance = initialFallback
    ? localGovernance(governancePayload, fallbackReason || "provider unavailable")
    : singleProviderCall
      ? localGovernance(governancePayload, "main push single-provider-call mode")
      : await governanceReview(governancePayload, env);
  const finalReport = chooseFinalReport(initialReport, governance);
  const fallback = initialFallback || governance.publishDecision === "fallback";
  const findings = payload.type === "diff" ? diffFindings(payload.diff) : failureFindings(payload.log);
  return okResponse(
    payload.type,
    formatGithubMarkdown(payload, finalReport, fallback, fallbackReason || "governance fallback", governance),
    normalizeFindings(findings),
    fallback,
    {
      initialReport,
      governanceReview: governance,
      finalReport,
      fallbackReason,
    },
  );
}

export async function governanceReview(payload: GovernancePayload, env: Env): Promise<GovernanceReview> {
  if (!payload.initialReport.trim()) {
    return localGovernance(payload, "initial report was empty");
  }
  const prompt = buildGovernancePrompt(payload, env);
  const provider = await callKiloChatCompletion(env, prompt.system, prompt.user);
  if (!provider.ok || !provider.text) {
    return localGovernance(payload, provider.error || "empty response");
  }
  return parseGovernanceMarkdown(provider.text);
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
  const classification = classifyDiff(diff);
  const hints = fallbackHints(diff, classification);
  const risks = fallbackRisks(classification);
  return `## Summary

Diff analyzed by local fallback. ${hints.join(" ") || "No critical pattern was detected by local file classification."}

Changed file categories: ${formatCategories(classification)}.

## Technical impact

${fallbackImpact(classification)}

## Risks

${risks.join("\n\n")}

## Review focus

${fallbackReviewFocus(classification)}

## Guidance

Use GitHub Actions build/test/integrationTest as the validation source of truth and manually review documentation-sensitive points.

## Suggested checklist

* [ ] GitHub Actions build/test/integrationTest completed.
* [ ] Relevant tests or documentation updated.
* [ ] Configuration reviewed.`;
}

function chooseFinalReport(initialReport: string, governance: GovernanceReview): string {
  if (governance.publishDecision === "suppress") {
    return "## AI Report Suppressed\n\nThe AI report was suppressed by governance review because it was not sufficiently supported.";
  }
  if (governance.publishDecision === "amend" && governance.recommendedIssueBody.trim()) {
    return governance.recommendedIssueBody.trim();
  }
  if (governance.publishDecision === "publish_with_caution") {
    return `${initialReport.trim()}\n\n> Governance note: publish with caution. Review unsupported claims and documentation conflicts before acting.`;
  }
  if (governance.publishDecision === "fallback" && governance.recommendedIssueBody.trim()) {
    return governance.recommendedIssueBody.trim();
  }
  return initialReport;
}

export function localGovernance(payload: GovernancePayload, reason: string): GovernanceReview {
  const initial = payload.initialReport || "";
  const diff = payload.diff || "";
  const ciLogs = payload.ciLogs || payload.log || "";
  if (!initial.trim()) {
    return {
      publishDecision: "suppress",
      confidence: "high",
      verdict: "The initial AI report is empty and should not be published as a substantive analysis.",
      relevance: "No relevant report content was provided.",
      truthfulness: "Truthfulness cannot be assessed because there is no report content.",
      documentationAlignment: "No documentation alignment can be assessed.",
      unsupportedClaims: [],
      documentationConflicts: [],
      recommendedIssueBody: "## AI Report Suppressed\n\nThe AI report was suppressed by governance review because it was empty.",
    };
  }

  const unsupportedClaims = [
    /local build|run gradle locally|\.\/gradlew|gradlew\.bat/i.test(initial)
      ? "The report suggests local Gradle validation, but validation must be performed by GitHub Actions."
      : "",
    /Folia/i.test(initial) && !/Folia/i.test(diff + projectContextText(payload))
      ? "The report mentions Folia without clear support in the diff or documentation context."
      : "",
    /Production code changes|Production code changed|production source files changed/i.test(initial) && !classifyDiff(diff).javaProduction
      ? "The report claims production code changed, but the diff does not include src/main/java files."
      : "",
  ].filter(Boolean);
  const documentationConflicts = documentationConflictHints(diff);
  const hasEvidence = Boolean(diff.trim() || ciLogs.trim() || projectContextText(payload).trim());
  const publishDecision: PublishDecision = !hasEvidence ? "fallback" : unsupportedClaims.length || documentationConflicts.length ? "publish_with_caution" : "publish";
  const plannedLocalGovernance = /single-provider-call/i.test(reason);

  return {
    publishDecision,
    confidence: "low",
    verdict: plannedLocalGovernance
      ? `Local governance review was used to avoid a second provider call: ${reason}.`
      : `Local governance fallback was used because the governance model failed: ${reason}.`,
    relevance: hasEvidence ? "The report can be reviewed against provided diff, CI logs, or documentation context." : "No supporting diff, CI logs, or documentation context was available.",
    truthfulness: unsupportedClaims.length ? "Some claims require caution because local heuristics found unsupported statements." : "No unsupported claim was detected by local heuristics.",
    documentationAlignment: documentationConflicts.length ? "Potential documentation conflicts were detected by local heuristics." : "No direct documentation conflict was detected by local heuristics.",
    unsupportedClaims,
    documentationConflicts,
    recommendedIssueBody: initial,
  };
}

function metadataValue(metadata: Record<string, unknown> | undefined, key: string): unknown {
  if (!metadata || typeof metadata !== "object") return undefined;
  return (metadata as Record<string, unknown>)[key];
}

function isMainPush(payload: AnalyzePayload): boolean {
  return payload.event === "push" && payload.branch === "main";
}

function documentationConflictHints(diff: string): string[] {
  const hints: string[] = [];
  if (/^\+\+\+ b\/docs\/adr\//m.test(diff)) hints.push("ADR documentation changed; verify the report reflects the new decision and does not overstate acceptance.");
  if (/^\+\+\+ b\/src\/main\/java\/com\/customcontentengine\/domain\//m.test(diff) && /^\+import\s+(org\.bukkit|io\.papermc)/m.test(diff)) {
    hints.push("Domain code appears to add a platform import, which may violate documented architecture boundaries.");
  }
  if (/plugin\.yml/m.test(diff) && /folia-supported:\s*true/i.test(diff)) {
    hints.push("plugin.yml appears to declare Folia support; verify this is documented and validated before publishing the claim.");
  }
  if (/\.github\/workflows\//m.test(diff) && /pull_request_target/i.test(diff)) {
    hints.push("Workflow changes mention pull_request_target; verify this does not weaken fork safety.");
  }
  return hints;
}

function projectContextText(payload: GovernancePayload): string {
  return (payload.projectContext || []).map((file) => `${file.path}\n${file.content}`).join("\n");
}

interface DiffClassification {
  files: string[];
  javaProduction: boolean;
  javaTests: boolean;
  docs: boolean;
  workflows: boolean;
  ciScripts: boolean;
  worker: boolean;
  configResources: boolean;
  unknown: boolean;
}

function classifyDiff(diff: string): DiffClassification {
  const files = changedFiles(diff);
  const known = new Set<string>();
  const has = (predicate: (file: string) => boolean): boolean => {
    const matched = files.filter(predicate);
    matched.forEach((file) => known.add(file));
    return matched.length > 0;
  };
  const javaProduction = has((file) => file.startsWith("src/main/java/"));
  const javaTests = has((file) => file.startsWith("src/test/") || file.startsWith("src/integrationTest/") || file.startsWith("src/spike/"));
  const docs = has((file) => file.startsWith("docs/") || file === "README.md");
  const workflows = has((file) => file.startsWith(".github/workflows/"));
  const ciScripts = has((file) => file.startsWith("scripts/ci/"));
  const worker = has((file) => file.startsWith("cloudflare/ci-ai-worker/"));
  const configResources = has((file) =>
    file.startsWith("src/main/resources/")
    || file.startsWith(".github/ai-review/")
    || /^build\.gradle(\.kts)?$/.test(file)
    || file === "settings.gradle.kts"
    || file === "package.json"
    || file === "package-lock.json"
    || file.endsWith("wrangler.jsonc")
    || file.endsWith(".yml")
    || file.endsWith(".yaml"),
  );
  return {
    files,
    javaProduction,
    javaTests,
    docs,
    workflows,
    ciScripts,
    worker,
    configResources,
    unknown: files.some((file) => !known.has(file)),
  };
}

function changedFiles(diff: string): string[] {
  const files = new Set<string>();
  for (const match of diff.matchAll(/^diff --git a\/(.+?) b\/(.+)$/gm)) {
    files.add(normalizePath(match[2]));
  }
  for (const match of diff.matchAll(/^\+\+\+ b\/(.+)$/gm)) {
    if (match[1] !== "/dev/null") files.add(normalizePath(match[1]));
  }
  return [...files].filter(Boolean).sort();
}

function normalizePath(file: string): string {
  return file.replace(/\\/g, "/").replace(/^\.\//, "");
}

function fallbackHints(diff: string, classification: DiffClassification): string[] {
  const hints: string[] = [];
  if (classification.workflows) hints.push("GitHub Actions workflow changes detected; review permissions, triggers, environment propagation, and report paths.");
  if (classification.ciScripts) hints.push("CI script changes detected; review output files, fallback paths, GitHub API calls, and diagnostics.");
  if (classification.worker) hints.push("Cloudflare Worker changes detected; review Worker deployment, provider response handling, and response schema compatibility.");
  if (classification.docs && onlyDocs(classification)) hints.push("Documentation-only changes detected; no runtime behavior impact is inferred by local fallback.");
  if (classification.configResources) hints.push("Configuration or resource changes detected; review metadata, build configuration, and runtime declarations.");
  if (classification.javaProduction) hints.push("Java production source files changed.");
  if (classification.javaTests) hints.push("Java test or spike files changed.");
  if (/docs\/adr\//.test(diff)) hints.push("ADR change detected; review whether the architectural decision remains consistent with scope and guardrails.");
  if (/docs\/PROJECT_SCOPE\.md/.test(diff)) hints.push("Project scope documentation changed; review whether generated code and claims stay within documented scope.");
  if (/docs\/ARCHITECTURE_GUARDRAILS\.md/.test(diff)) hints.push("Architecture guardrails changed; review boundary rules carefully.");
  if (/plugin\.yml/.test(diff)) hints.push("plugin.yml changed; review plugin metadata and platform declarations.");
  if (/folia-supported:\s*true/i.test(diff)) hints.push("Folia support declaration risk detected; verify that documentation explicitly supports the claim.");
  if (/^\+import\s+(org\.bukkit|io\.papermc)/m.test(diff)) hints.push("Platform import added; verify it does not enter forbidden layers.");
  if (/secrets?|env|token|permission/i.test(diff)) hints.push("Sensitive configuration wording detected; verify secrets remain redacted and permissions remain minimal.");
  return hints;
}

function fallbackImpact(classification: DiffClassification): string {
  if (classification.javaProduction) {
    return "Review whether Java production behavior changed and rely on GitHub Actions build/test/integrationTest for validation.";
  }
  if (classification.workflows || classification.ciScripts || classification.worker || classification.configResources) {
    return "Configuration or automation changes were detected. Review workflow permissions, environment variables, report paths, provider diagnostics, deployment status, and schema compatibility.";
  }
  if (onlyDocs(classification)) {
    return "Documentation changed. Local fallback does not infer runtime behavior impact from documentation-only diffs.";
  }
  return "Review the changed files manually; local fallback did not infer a production-code impact.";
}

function fallbackRisks(classification: DiffClassification): string[] {
  if (classification.javaProduction && !classification.javaTests) {
    return ["Java production source files changed without a Java test diff in this change; GitHub Actions should be used to assess regression risk."];
  }
  if (classification.workflows || classification.ciScripts || classification.worker || classification.configResources) {
    return ["Configuration or automation changes may affect CI behavior, provider calls, issue/report publication, or deployment expectations."];
  }
  if (onlyDocs(classification)) {
    return ["No production-code risk is inferred by local fallback for documentation-only changes."];
  }
  return ["No specific risk was inferred by local fallback. Manual review is still required."];
}

function fallbackReviewFocus(classification: DiffClassification): string {
  const focus: string[] = [];
  if (classification.workflows) focus.push("* Workflow permissions, triggers, environment variables, and report paths.");
  if (classification.ciScripts) focus.push("* CI script output files, fallback reasons, GitHub API calls, and log clarity.");
  if (classification.worker) focus.push("* Worker deploy status, Kilo provider diagnostics, response schema, and fallback behavior.");
  if (classification.configResources) focus.push("* Configuration/resource metadata and runtime declarations.");
  if (classification.javaProduction) focus.push("* Java production behavior and corresponding remote validation evidence.");
  if (classification.docs) focus.push("* Documentation consistency with project scope, guardrails, ADRs, and milestones.");
  return focus.length ? focus.join("\n") : "* Manually inspect the diff and compare it with repository documentation.";
}

function formatCategories(classification: DiffClassification): string {
  const categories = [
    classification.javaProduction ? "java production code" : "",
    classification.javaTests ? "java tests/spikes" : "",
    classification.docs ? "docs" : "",
    classification.workflows ? "GitHub Actions workflows" : "",
    classification.ciScripts ? "CI scripts" : "",
    classification.worker ? "Cloudflare Worker" : "",
    classification.configResources ? "config/resources" : "",
    classification.unknown ? "unknown" : "",
  ].filter(Boolean);
  return categories.length ? categories.join(", ") : "none detected";
}

function onlyDocs(classification: DiffClassification): boolean {
  return classification.docs
    && !classification.javaProduction
    && !classification.javaTests
    && !classification.workflows
    && !classification.ciScripts
    && !classification.worker
    && !classification.configResources
    && !classification.unknown;
}

function parseGovernanceMarkdown(markdown: string): GovernanceReview {
  const publishDecision = parsePublishDecision(section(markdown, "Publish Decision") || markdown);
  return {
    publishDecision,
    confidence: "medium",
    verdict: section(markdown, "Verdict") || firstParagraph(markdown),
    relevance: section(markdown, "Relevance") || "The governance model did not provide a separate relevance assessment.",
    truthfulness: section(markdown, "Truthfulness Check") || "The governance model did not provide a separate truthfulness assessment.",
    documentationAlignment: section(markdown, "Documentation Alignment") || "The governance model did not provide a separate documentation alignment assessment.",
    unsupportedClaims: listSection(markdown, "Unsupported Claims"),
    documentationConflicts: listSection(markdown, "Documentation Conflicts"),
    recommendedIssueBody: section(markdown, "Recommended Issue Body") || "",
  };
}

function parsePublishDecision(text: string): PublishDecision {
  const lowered = text.toLowerCase();
  if (lowered.includes("publish_with_caution")) return "publish_with_caution";
  if (lowered.includes("suppress")) return "suppress";
  if (lowered.includes("amend")) return "amend";
  if (lowered.includes("fallback")) return "fallback";
  return "publish";
}

function section(markdown: string, heading: string): string {
  const pattern = new RegExp(`###\\s+${escapeRegex(heading)}\\s*\\n([\\s\\S]*?)(?=\\n###\\s+|\\n##\\s+|$)`, "i");
  return markdown.match(pattern)?.[1]?.trim() || "";
}

function listSection(markdown: string, heading: string): string[] {
  const body = section(markdown, heading);
  if (!body || /^none\b|no unsupported|no documentation/i.test(body.trim())) return [];
  return body.split("\n")
    .map((line) => line.replace(/^\s*[-*]\s*/, "").trim())
    .filter(Boolean)
    .slice(0, 20);
}

function firstParagraph(markdown: string): string {
  return markdown.split(/\n\s*\n/).map((part) => part.replace(/^#+\s*/gm, "").trim()).find(Boolean)?.slice(0, 500) || "Governance review completed.";
}

function escapeRegex(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
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
  const classification = classifyDiff(diff);
  if (classification.javaProduction && !classification.javaTests) {
    findings.push({ severity: "warning", title: "Production change without test diff", body: "Production code changed without a test change in the diff." });
  }
  if (/^\+\+\+ b\/\.github\/workflows\//m.test(diff) && /permissions:/m.test(diff)) {
    findings.push({ severity: "warning", file: ".github/workflows", title: "Workflow permissions changed", body: "Review whether permissions are still minimal." });
  }
  return findings;
}

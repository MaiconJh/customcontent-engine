import type { AnalyzePayload, Finding, GovernanceReview } from "../types";
import { sanitizeText } from "../sanitizer";
import { normalizeProviderMarkdown } from "../provider-output";

export function formatGithubMarkdown(payload: AnalyzePayload, markdown: string, fallback = false, fallbackReason = "provider unavailable", governance?: GovernanceReview): string {
  const heading = payload.type === "failure" ? "## CI Failure - build/test failed" : diffHeading(payload.event);
  const normalized = normalizeProviderMarkdown(markdown, payload.type === "failure" ? "failure" : "diff", 10000);
  const clean = sanitizeText(normalized, 10000).replace(/```[\s\S]{2500,}?```/g, (block) => `${block.slice(0, 2500)}\n[TRUNCATED]\n\`\`\``);
  const body = clean.trim().startsWith("##") ? clean.trim() : `${heading}\n\n${clean.trim()}`;
  return `${body}

${governance ? formatGovernanceSection(governance) : ""}

### Metadata

* Commit: ${payload.commit}
* Branch: ${payload.branch}
* Workflow: ${payload.workflow}
* Run: ${payload.run_url}${fallback ? `\n\n> Local fallback was used because the AI provider did not return a usable response. Safe reason: ${sanitizeText(fallbackReason, 160)}.` : ""}`;
}

export function formatGovernanceSection(governance: GovernanceReview): string {
  return `## AI Governance Review

### Verdict
${sanitizeText(governance.verdict || "Governance review completed.", 1200)}

### Relevance
${sanitizeText(governance.relevance || "No relevance assessment was provided.", 1200)}

### Truthfulness Check
${sanitizeText(governance.truthfulness || "No truthfulness assessment was provided.", 1200)}

### Documentation Alignment
${sanitizeText(governance.documentationAlignment || "No documentation alignment assessment was provided.", 1200)}

### Publish Decision
${governance.publishDecision}

### Unsupported Claims
${formatList(governance.unsupportedClaims)}

### Documentation Conflicts
${formatList(governance.documentationConflicts)}`;
}

function formatList(items: string[]): string {
  return items.length ? items.map((item) => `* ${sanitizeText(item, 500)}`).join("\n") : "* None reported.";
}

export function normalizeFindings(findings: Finding[]): Finding[] {
  return findings
    .filter((finding) => ["info", "warning", "error"].includes(finding.severity))
    .slice(0, 20)
    .map((finding) => ({
      severity: finding.severity,
      file: finding.file,
      line: finding.line,
      title: sanitizeText(finding.title, 160),
      body: sanitizeText(finding.body, 900),
      snippet: finding.snippet ? sanitizeText(finding.snippet, 500) : undefined,
    }));
}

function diffHeading(event: string): string {
  return event === "push" ? "## AI Technical Note - main update" : "## AI Review - change analysis";
}

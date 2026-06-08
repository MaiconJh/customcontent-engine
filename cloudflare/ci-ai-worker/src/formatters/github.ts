import type { AnalyzePayload, Finding } from "../types";
import { sanitizeText } from "../sanitizer";

export function formatGithubMarkdown(payload: AnalyzePayload, markdown: string, fallback = false): string {
  const heading = payload.type === "failure" ? "## CI Failure - build/test falhou" : diffHeading(payload.event);
  const clean = sanitizeText(markdown, 10000).replace(/```[\s\S]{2500,}?```/g, (block) => `${block.slice(0, 2500)}\n[TRUNCATED]\n\`\`\``);
  const body = clean.trim().startsWith("##") ? clean.trim() : `${heading}\n\n${clean.trim()}`;
  return `${body}

### Metadados

* Commit: ${payload.commit}
* Branch: ${payload.branch}
* Workflow: ${payload.workflow}
* Run: ${payload.run_url}${fallback ? "\n\n> Fallback local usado porque o provider de IA nao respondeu." : ""}`;
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
  return event === "push" ? "## AI Technical Note - atualizacao na main" : "## AI Review - analise da alteracao";
}

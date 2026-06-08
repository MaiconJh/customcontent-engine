import type { AnalyzeResponse, ErrorResponse, Finding, GovernanceVerdict } from "./types";
import { sanitizeText } from "./sanitizer";

export function okResponse(type: "failure" | "diff", markdown: string, findings: Finding[] = [], fallback = false, governance?: GovernanceVerdict): AnalyzeResponse {
  const safeMarkdown = sanitizeText(markdown, 12000);
  return {
    ok: true,
    type,
    summary: firstUsefulLine(safeMarkdown),
    markdown: safeMarkdown,
    findings: findings.slice(0, 20).map((finding) => ({
      severity: finding.severity,
      file: finding.file,
      line: finding.line,
      title: sanitizeText(finding.title || "Finding", 200),
      body: sanitizeText(finding.body || "", 1000),
      snippet: finding.snippet ? sanitizeText(finding.snippet, 800) : undefined,
    })),
    fallback,
    governance,
  };
}

export function errorResponse(code: ErrorResponse["error"]["code"], message: string, status: number): Response {
  return Response.json({ ok: false, error: { code, message } } satisfies ErrorResponse, { status });
}

function firstUsefulLine(markdown: string): string {
  return markdown.split("\n").map((line) => line.replace(/^#+\s*/, "").trim()).find(Boolean)?.slice(0, 240) || "Analysis generated.";
}

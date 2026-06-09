import { sanitizeText } from "./sanitizer";

export type ProviderMarkdownKind = "diff" | "failure" | "governance" | "issue-plan" | "generic";

const HEADING_PATTERN = /^#{1,3}\s+(?:AI Technical Note|AI Review|CI Failure|Summary|Confirmed findings|Possible risks|Unsupported claims|Documentation divergence|Suggested follow-up|Likely cause|Log evidence|Possibly related files|Suggested fix|Suggested snippet|Next steps|AI Governance Review|Verdict|Relevance|Truthfulness Check|Documentation Alignment|Publish Decision|Documentation Conflicts|Recommended Issue Body|AI Implementation Plan|Request Summary|Scope Classification|Source-of-Truth Alignment|Likely Files or Areas|Proposed Steps|Acceptance Criteria|Validation|Risks|Explicit Non-Goals|Human Review Required)\b/im;

const REASONING_LINE_PATTERN = /^(?:The user wants me to|The user asks me to|Let me|I need to|I should|I will|I'll|I'm going to|We need to|Looking at|From the|I can see|I notice|Wait,|Since the|Given the|The primary context shows|Key observations|Potential issues|Potential concerns|This suggests|This is likely|So I should|Now I need|My task is|I need|I see)\b/i;

const META_LINE_PATTERN = /^(?:Here'?s|Below is|Certainly|Sure,|Okay,|Ok,|To answer|In this response)\b/i;

/**
 * Removes provider scratchpad / reasoning-style prose before publishing Markdown.
 *
 * Some chat providers may return visible analysis such as "Let me analyze..." before
 * the requested report. The bot must not publish that raw provider output to GitHub.
 */
export function normalizeProviderMarkdown(value: string, kind: ProviderMarkdownKind = "generic", maxChars = 12000): string {
  let text = sanitizeText(value || "", Math.max(maxChars * 2, maxChars)).trim();
  if (!text) return "";

  text = unwrapMarkdownFence(text);
  text = sliceFromFirstUsefulHeading(text);
  text = removeReasoningPrelude(text);
  text = sanitizeText(text.trim(), maxChars).trim();

  if (!text) return "";
  if (!startsWithMarkdownHeading(text)) {
    return wrapWithoutInventing(text, kind, maxChars);
  }
  return text;
}

function unwrapMarkdownFence(text: string): string {
  const match = text.match(/^```(?:markdown|md)?\s*\n([\s\S]*?)\n```\s*$/i);
  return match ? match[1].trim() : text;
}

function sliceFromFirstUsefulHeading(text: string): string {
  const match = HEADING_PATTERN.exec(text);
  if (!match || match.index <= 0) return text;
  return text.slice(match.index).trim();
}

function removeReasoningPrelude(text: string): string {
  const lines = text.split(/\r?\n/);
  const kept: string[] = [];
  let seenHeading = false;

  for (const line of lines) {
    const trimmed = line.trim();
    if (/^#{1,6}\s+/.test(trimmed)) seenHeading = true;
    if (!seenHeading && (REASONING_LINE_PATTERN.test(trimmed) || META_LINE_PATTERN.test(trimmed))) continue;
    kept.push(line);
  }

  return kept.join("\n").trim();
}

function startsWithMarkdownHeading(text: string): boolean {
  return /^#{1,6}\s+\S/.test(text.trim());
}

function wrapWithoutInventing(text: string, kind: ProviderMarkdownKind, maxChars: number): string {
  const heading = kind === "failure"
    ? "## Summary"
    : kind === "governance"
      ? "## AI Governance Review"
      : kind === "issue-plan"
        ? "# AI Implementation Plan"
        : "## Summary";
  return sanitizeText(`${heading}\n\n${text}`, maxChars).trim();
}

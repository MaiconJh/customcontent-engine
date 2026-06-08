#!/usr/bin/env node
const fs = require("node:fs");
const os = require("node:os");
const path = require("node:path");
const { execFileSync } = require("node:child_process");
const { sanitizeString, sanitizeValue } = require("./sanitize-payload");

const CONTEXT_PACK = "docs/AI_CONTEXT_PACK.md";
const SOURCE_DOCS = [
  "docs/PROJECT_SCOPE.md",
  "docs/ARCHITECTURE_GUARDRAILS.md",
  "docs/adr/*.md",
  "docs/milestones/*.md",
  "docs/CI_AI_REVIEW_BOT.md",
  "docs/AI_CONTINUOUS_EVOLUTION_ARCHITECTURE.md",
];
const DEFAULT_MAX_FILE_CHARS = 16000;
const DEFAULT_MAX_TOTAL_CHARS = 120000;

function hasFlag(name) {
  return process.argv.includes(name);
}

function arg(name, fallback) {
  const idx = process.argv.indexOf(name);
  return idx >= 0 ? process.argv[idx + 1] : fallback;
}

function mode() {
  const modes = ["--dry-run", "--write", "--pr"].filter(hasFlag);
  if (modes.length > 1) throw new Error("Choose only one mode: --dry-run, --write, or --pr.");
  if (hasFlag("--write")) return "write";
  if (hasFlag("--pr")) return "pr";
  return "dry-run";
}

function normalize(file) {
  return file.replace(/\\/g, "/").replace(/^\.\//, "");
}

function listMarkdown(dir) {
  if (!fs.existsSync(dir)) return [];
  return fs.readdirSync(dir)
    .filter((name) => name.endsWith(".md"))
    .sort()
    .map((name) => normalize(path.join(dir, name)));
}

function expandSources() {
  const files = [];
  for (const entry of SOURCE_DOCS) {
    if (entry.endsWith("/*.md")) files.push(...listMarkdown(entry.slice(0, -5)));
    else files.push(entry);
  }
  return files
    .map(normalize)
    .filter((file, index, all) => all.indexOf(file) === index)
    .filter((file) => fs.existsSync(file) && fs.statSync(file).isFile());
}

function readDocs() {
  const maxFileChars = Number(arg("--max-file-chars", process.env.MAX_CONTEXT_PACK_SOURCE_FILE_CHARS || DEFAULT_MAX_FILE_CHARS));
  const maxTotalChars = Number(arg("--max-total-chars", process.env.MAX_CONTEXT_PACK_SOURCE_CHARS || DEFAULT_MAX_TOTAL_CHARS));
  let remaining = maxTotalChars;

  return expandSources().map((file) => {
    let content = fs.readFileSync(file, "utf8");
    let truncated = false;
    const limit = Math.max(0, Math.min(maxFileChars, remaining));
    if (content.length > limit) {
      content = `${content.slice(0, limit)}\n[TRUNCATED: source document exceeded refresh proposal limit]`;
      truncated = true;
    }
    content = sanitizeString(content);
    remaining -= content.length;
    return { path: file, content, truncated };
  });
}

function readCurrentPack() {
  if (!fs.existsSync(CONTEXT_PACK)) return "";
  return sanitizeString(fs.readFileSync(CONTEXT_PACK, "utf8"));
}

function buildRefreshPrompt(sourceDocuments, currentContextPack) {
  const sources = sourceDocuments.map((doc) => [
    `--- SOURCE: ${doc.path}${doc.truncated ? " (truncated)" : ""} ---`,
    doc.content,
  ].join("\n")).join("\n\n");

  return `You are preparing a controlled refresh proposal for docs/AI_CONTEXT_PACK.md.

Return only the complete Markdown content for docs/AI_CONTEXT_PACK.md.

Rules:
- The output must be in English.
- The file is derived guidance for AI review and governance.
- It must not replace docs/PROJECT_SCOPE.md, docs/ARCHITECTURE_GUARDRAILS.md, ADRs, or milestones.
- If the context pack conflicts with source documents, source documents win.
- Do not add new scope.
- Do not invent completed work.
- Do not claim Folia support is final unless source documents say so.
- Do not approve ADRs, auto-merge, or direct commits to main.
- Keep the pack compact, conservative, and useful for AI prompts.
- Preserve explicit governance rules and out-of-scope boundaries.

Current docs/AI_CONTEXT_PACK.md:

${currentContextPack || "[Missing current context pack]"}

Source documents:

${sources}`;
}

async function callWorker(prompt, sourceDocuments, currentContextPack) {
  const endpoint = process.env.CI_AI_WORKER_URL;
  if (!endpoint) return null;

  const payload = sanitizeValue({
    repository: process.env.GITHUB_REPOSITORY || git(["config", "--get", "remote.origin.url"], { optional: true }) || "",
    branch: process.env.GITHUB_REF_NAME || currentBranch(),
    commit: process.env.GITHUB_SHA || git(["rev-parse", "HEAD"], { optional: true }) || "",
    type: "ai_context_pack_refresh",
    prompt,
    sourceDocuments,
    currentContextPack,
    rules: {
      derivedGuidanceOnly: true,
      sourceDocsRemainAuthoritative: true,
      humanReviewRequired: true,
      noAutoMerge: true,
    },
  });

  try {
    const headers = { "Content-Type": "application/json" };
    if (process.env.CI_WORKER_SHARED_SECRET) headers["X-CI-Worker-Secret"] = process.env.CI_WORKER_SHARED_SECRET;
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), Number(process.env.CI_WORKER_TIMEOUT_MS || "45000"));
    const res = await fetch(`${endpoint.replace(/\/$/, "")}/v1/docs/refresh-context-pack`, {
      method: "POST",
      headers,
      body: JSON.stringify(payload),
      signal: controller.signal,
    });
    clearTimeout(timeout);
    const body = await res.json().catch(() => null);
    if (!res.ok || !body) {
      console.log(`Worker refresh endpoint unavailable: provider status ${res.status}. Using local proposal.`);
      return null;
    }
    return extractContextPack(body);
  } catch (error) {
    console.log(`Worker refresh endpoint unavailable: ${safeError(error)}. Using local proposal.`);
    return null;
  }
}

function extractContextPack(body) {
  const candidates = [
    body.contextPack,
    body.aiContextPack,
    body.markdown,
    body.recommendedContextPack,
    body.finalReport,
    body.result?.contextPack,
    body.result?.markdown,
  ].filter((value) => typeof value === "string" && value.trim());

  const selected = candidates.find((value) => /^#\s+AI Context Pack\b/m.test(value) && /source[- ]of[- ]truth|source documents/i.test(value));
  return selected ? sanitizeString(selected.trim()) : null;
}

function buildLocalProposal(sourceDocuments, currentContextPack) {
  const generated = new Date().toISOString().slice(0, 10);
  const sourceList = sourceDocuments.map((doc) => `- \`${doc.path}\`${doc.truncated ? " (truncated during proposal generation)" : ""}`).join("\n");
  const summaries = sourceDocuments.map(formatDocumentSummary).join("\n\n");
  const currentHeadings = extractHeadings(currentContextPack).slice(0, 30);

  return `# AI Context Pack - CustomContent Engine

Status: Derived guidance
Generated: ${generated}
Purpose: compact operational context for AI review, governance, planning, and future implementation assistance.

> This file is a controlled refresh proposal derived from repository documentation. It does not replace the source documents. If this file conflicts with \`docs/PROJECT_SCOPE.md\`, \`docs/ARCHITECTURE_GUARDRAILS.md\`, accepted ADRs, or milestone documents, the original documents win.

## Source Of Truth

Primary source documents:

${sourceList}

Operational rules:

- GitHub Actions is the validation source of truth.
- AI reports are advisory.
- Kilo and Worker output must be checked against the diff, CI logs, and repository documentation.
- Humans remain the final gate for scope, ADR approval, pull request merge, and context pack acceptance.
- This context pack is derived guidance only.

## Refresh Boundaries

This refresh proposal must not:

- introduce new product scope;
- approve or accept ADRs automatically;
- change \`PROJECT_SCOPE.md\`;
- declare \`folia-supported: true\`;
- promote experimental concepts to stable core;
- create public API commitments;
- request local Gradle validation;
- auto-merge or commit directly to \`main\`.

## Current Context Pack Headings

${currentHeadings.length ? currentHeadings.map((heading) => `- ${heading}`).join("\n") : "- No current context pack headings were available."}

## Source Document Summaries

${summaries}

## Conservative Review Instructions

AI reviewers must compare repository changes against the source documents listed above. Claims must be supported by the diff, GitHub Actions logs, or documentation context.

When documentation conflicts exist:

1. \`docs/PROJECT_SCOPE.md\` wins over this context pack.
2. \`docs/ARCHITECTURE_GUARDRAILS.md\` wins over this context pack.
3. Accepted ADRs win over this context pack.
4. Milestone documents win over this context pack for milestone-specific delivery boundaries.

When in doubt, prefer conservative wording and require human review.
`;
}

function formatDocumentSummary(doc) {
  const headings = extractHeadings(doc.content).slice(0, 18);
  const signals = extractSignals(doc.content).slice(0, 24);
  return `### ${doc.path}

Headings:

${headings.length ? headings.map((heading) => `- ${heading}`).join("\n") : "- No headings detected."}

Conservative signals:

${signals.length ? signals.map((line) => `- ${line}`).join("\n") : "- No compact signals extracted."}`;
}

function extractHeadings(content) {
  return String(content)
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter((line) => /^#{1,4}\s+\S/.test(line))
    .map((line) => line.replace(/^#{1,4}\s+/, "").trim());
}

function extractSignals(content) {
  const patterns = [
    /\b(source of truth|GitHub Actions|human|review|required|must|must not|forbidden|out of scope|not allowed)\b/i,
    /\b(Folia|Scheduler|runAsync|runOnEntity|SchedulerAccess|NMS|reflection|ServiceLoader|public API)\b/i,
    /\b(scope|guardrail|ADR|milestone|domain|adapter|application|mechanic|capability)\b/i,
  ];
  return String(content)
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter((line) => line.length >= 12 && line.length <= 180)
    .filter((line) => !/^[-#`>|]*$/.test(line))
    .filter((line) => patterns.some((pattern) => pattern.test(line)))
    .filter((line, index, all) => all.indexOf(line) === index);
}

function writeTemp(content, suffix = ".md") {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), "ai-context-pack-refresh-"));
  const file = path.join(dir, `AI_CONTEXT_PACK.proposal${suffix}`);
  fs.writeFileSync(file, content);
  return file;
}

function writeContextPack(content) {
  fs.writeFileSync(CONTEXT_PACK, `${content.trim()}\n`);
}

function git(args, options = {}) {
  try {
    return execFileSync("git", args, { encoding: "utf8", stdio: options.quiet ? "ignore" : ["ignore", "pipe", "pipe"] }).trim();
  } catch (error) {
    if (options.optional) return "";
    throw error;
  }
}

function run(command, args) {
  execFileSync(command, args, { stdio: "inherit" });
}

function currentBranch() {
  return git(["branch", "--show-current"], { optional: true });
}

function ensureCleanTrackedTree() {
  const status = git(["status", "--porcelain", "--untracked-files=no"], { optional: true });
  if (status) throw new Error("The tracked working tree must be clean before --pr mode.");
}

function branchName() {
  const explicit = arg("--branch", "");
  if (explicit) return explicit;
  const runId = process.env.GITHUB_RUN_ID || new Date().toISOString().replace(/[-:TZ.]/g, "").slice(0, 14);
  return `ai/context-pack-refresh-${runId}`;
}

function createPullRequest(branch) {
  const body = `## Summary

This pull request refreshes \`docs/AI_CONTEXT_PACK.md\` as derived guidance for AI review and governance.

## Governance

- \`AI_CONTEXT_PACK.md\` does not replace source-of-truth documents.
- \`docs/PROJECT_SCOPE.md\`, \`docs/ARCHITECTURE_GUARDRAILS.md\`, ADRs, and milestones remain authoritative.
- Human review is required before accepting this update.
- This workflow does not auto-merge.
- This branch must not be treated as an AI approval of new project scope.
`;
  const bodyFile = writeTemp(body, ".pr.md");
  run("gh", ["pr", "create", "--title", "Refresh AI context pack", "--body-file", bodyFile, "--base", "main", "--head", branch]);
}

function safeError(error) {
  if (error?.name === "AbortError") return "timeout";
  return String(error?.message || error || "unknown error").replace(/Bearer\s+\S+/gi, "Bearer [REDACTED]");
}

async function main() {
  const selectedMode = mode();
  const sourceDocuments = readDocs();
  const currentContextPack = readCurrentPack();
  const prompt = buildRefreshPrompt(sourceDocuments, currentContextPack);
  const workerProposal = await callWorker(prompt, sourceDocuments, currentContextPack);
  const proposal = workerProposal || buildLocalProposal(sourceDocuments, currentContextPack);
  const promptOutput = arg("--prompt-output", "");
  if (promptOutput) fs.writeFileSync(promptOutput, `${prompt}\n`);

  if (selectedMode === "dry-run") {
    const output = arg("--output", "") || writeTemp(proposal);
    fs.writeFileSync(output, `${proposal.trim()}\n`);
    console.log("AI context pack refresh dry run completed.");
    console.log(`Source documents read: ${sourceDocuments.length}`);
    console.log(`Proposal source: ${workerProposal ? "worker" : "local conservative proposal"}`);
    console.log(`Proposal file: ${output}`);
    return;
  }

  if (selectedMode === "write") {
    writeContextPack(proposal);
    console.log(`Wrote ${CONTEXT_PACK}. No commit or pull request was created.`);
    console.log(`Proposal source: ${workerProposal ? "worker" : "local conservative proposal"}`);
    return;
  }

  ensureCleanTrackedTree();
  const branch = branchName();
  run("git", ["config", "user.name", process.env.GIT_AUTHOR_NAME || "github-actions[bot]"]);
  run("git", ["config", "user.email", process.env.GIT_AUTHOR_EMAIL || "41898282+github-actions[bot]@users.noreply.github.com"]);
  run("git", ["switch", "-c", branch]);
  writeContextPack(proposal);
  run("git", ["add", CONTEXT_PACK]);
  const staged = git(["diff", "--cached", "--name-only"], { optional: true });
  if (!staged) {
    console.log("No AI context pack changes were generated. No pull request was created.");
    return;
  }
  run("git", ["commit", "-m", "Refresh AI context pack"]);
  run("git", ["push", "-u", "origin", branch]);
  createPullRequest(branch);
  console.log(`Created pull request from ${branch}.`);
}

main().catch((error) => {
  console.error(safeError(error));
  process.exit(1);
});

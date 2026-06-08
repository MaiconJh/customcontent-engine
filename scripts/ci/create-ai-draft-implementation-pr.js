#!/usr/bin/env node
const fs = require("node:fs");
const path = require("node:path");
const { execFileSync } = require("node:child_process");
const { collect } = require("./collect-project-context");
const { githubRequest, repoParts } = require("./github-api");
const { sanitizeString, sanitizeValue } = require("./sanitize-payload");

const PLAN_LABEL = "ai:plan";
const APPROVED_LABEL = "ai:approved";
const PLAN_MARKER = "<!-- customcontent-engine:ai-plan -->";
const TARGET_BRANCH = "main";

const FORBIDDEN_FILE_PATTERNS = [
  /^\.github\/workflows\/build-test\.yml$/,
  /^\.github\/workflows\//,
  /^gradle\//,
  /^gradlew$/,
  /^gradlew\.bat$/,
  /^settings\.gradle\.kts$/,
  /^build\.gradle\.kts$/,
  /^docs\/PROJECT_SCOPE\.md$/,
  /^docs\/ARCHITECTURE_GUARDRAILS\.md$/,
  /^docs\/adr\//,
  /^docs\/milestones\//,
  /^cloudflare\//,
  /^scripts\/ci\//,
];

const ALLOWED_FILE_PATTERNS = [
  /^src\/main\/java\//,
  /^src\/main\/resources\//,
  /^src\/test\/java\//,
  /^src\/integrationTest\//,
  /^docs\/ai-implementation-notes\//,
];

const OUT_OF_SCOPE_RULES = [
  ["economy system", /\b(economy|currency|money|shop|marketplace|balance|wallet)\b/i],
  ["quest system", /\b(quest|quests|questline)\b/i],
  ["generic combat system", /\b(generic combat|combat system|damage engine|weapon framework|pvp framework)\b/i],
  ["GUI/menu framework", /\b(gui framework|menu framework|inventory gui|generic menu|screen framework)\b/i],
  ["scripting language", /\b(scripting language|script engine|embedded script|lua|groovy scripts?)\b/i],
  ["generic ability framework", /\b(generic ability|ability framework|skill framework|spell framework)\b/i],
  ["land protection", /\b(land protection|claim protection|worldguard|griefprevention|grief prevention|region protection)\b/i],
  ["NMS/reflection", /\b(NMS|net\.minecraft|reflection|reflective access)\b/i],
  ["Folia support declaration", /\b(folia-supported\s*:\s*true|declare folia support)\b/i],
  ["direct domain/application platform access", /\b(org\.bukkit|io\.papermc|Bukkit|Paper)\b[\s\S]{0,200}\b(domain|application)\b|\b(domain|application)\b[\s\S]{0,200}\b(org\.bukkit|io\.papermc|Bukkit|Paper)\b/i],
];

function eventPayload() {
  const eventPath = process.env.GITHUB_EVENT_PATH || "";
  if (!eventPath || !fs.existsSync(eventPath)) return {};
  try {
    return JSON.parse(fs.readFileSync(eventPath, "utf8"));
  } catch (error) {
    console.log(`AI draft implementation diagnostics: eventReadError=${sanitizeString(error.message)}`);
    return {};
  }
}

function parseBooleanInput(value, fallback = true) {
  if (value === undefined || value === null || value === "") return fallback;
  return String(value).toLowerCase() === "true";
}

function parsePositiveInt(value, fallback) {
  const parsed = Number(value);
  return Number.isFinite(parsed) && parsed >= 0 ? Math.floor(parsed) : fallback;
}

function issueLabels(issue) {
  return (issue.labels || []).map((label) => typeof label === "string" ? label : label.name).filter(Boolean);
}

function hasLabel(issue, label) {
  return issueLabels(issue).includes(label);
}

function assertIssueReady(issue, options = {}) {
  const targetBranch = options.targetBranch || process.env.TARGET_BRANCH || TARGET_BRANCH;
  if (targetBranch !== TARGET_BRANCH) throw new Error(`Refusing draft implementation PR: target branch is ${targetBranch}, not main.`);
  if (!hasLabel(issue, PLAN_LABEL)) throw new Error("Refusing draft implementation PR: issue is missing ai:plan label.");
  if (!hasLabel(issue, APPROVED_LABEL)) throw new Error("Refusing draft implementation PR: issue is missing ai:approved label.");
}

function assertNoOutOfScope(issue, approvedPlanComment, planningArtifactContent) {
  const risks = outOfScopeRisks(`${issue.title || ""}\n${issue.body || ""}\n${approvedPlanComment}\n${planningArtifactContent}`);
  if (risks.length) throw new Error(`Refusing draft implementation PR: possible out-of-scope request detected (${risks.join(", ")}).`);
}

async function loadIssue(event) {
  const { owner, repo } = repoParts();
  if (event.issue) return event.issue;
  const issueNumber = process.env.ISSUE_NUMBER || event.inputs?.issue_number || "";
  if (!issueNumber) throw new Error("Issue number is required.");
  return githubRequest(`/repos/${owner}/${repo}/issues/${issueNumber}`);
}

async function findPlanComment(issueNumber) {
  const { owner, repo } = repoParts();
  const comments = await githubRequest(`/repos/${owner}/${repo}/issues/${issueNumber}/comments?per_page=100`);
  const existing = comments.find((comment) => comment.body?.includes(PLAN_MARKER));
  if (!existing) throw new Error("Refusing draft implementation PR: approved AI plan comment marker was not found.");
  const plan = extractPlan(existing.body || "");
  if (!plan.trim()) throw new Error("Refusing draft implementation PR: approved AI plan comment is empty.");
  return { plan, url: existing.html_url || "" };
}

function extractPlan(body) {
  const markerIndex = body.indexOf(PLAN_MARKER);
  if (markerIndex < 0) return "";
  return body.slice(markerIndex + PLAN_MARKER.length).trim().split(/\n---\n\s*Planning metadata:/)[0].trim();
}

function approvedPlanArtifactPath(issueNumber) {
  return `docs/ai-plans/issue-${issueNumber}-approved-plan.md`;
}

function implementationBranchName(issueNumber) {
  return `ai/draft-implementation-issue-${issueNumber}`;
}

function implementationNotePath(issueNumber) {
  return `docs/ai-implementation-notes/issue-${issueNumber}.md`;
}

async function findPlanningArtifact(issue) {
  const artifactPath = approvedPlanArtifactPath(issue.number);
  const mainArtifact = await readRepositoryFile(artifactPath, TARGET_BRANCH).catch(() => null);
  if (mainArtifact) return { ...mainArtifact, path: artifactPath, planningPrUrl: "" };

  const planningBranch = `ai/approved-plan-issue-${issue.number}`;
  const { owner, repo } = repoParts();
  const prs = await githubRequest(`/repos/${owner}/${repo}/pulls?state=all&head=${encodeURIComponent(`${owner}:${planningBranch}`)}&base=${TARGET_BRANCH}&per_page=10`);
  if (!prs.length) throw new Error("Refusing draft implementation PR: approved planning PR was not found and artifact is not on main.");
  const branchArtifact = await readRepositoryFile(artifactPath, planningBranch).catch(() => null);
  if (!branchArtifact) throw new Error("Refusing draft implementation PR: approved planning artifact was not found on the planning branch.");
  return { ...branchArtifact, path: artifactPath, planningPrUrl: prs[0].html_url || "" };
}

async function readRepositoryFile(filePath, ref) {
  const { owner, repo } = repoParts();
  const encodedPath = filePath.split("/").map(encodeURIComponent).join("/");
  const response = await githubRequest(`/repos/${owner}/${repo}/contents/${encodedPath}?ref=${encodeURIComponent(ref)}`);
  if (!response || response.type !== "file" || !response.content) throw new Error(`Repository file not found: ${filePath}@${ref}`);
  return {
    content: Buffer.from(response.content, "base64").toString("utf8"),
    ref,
    htmlUrl: response.html_url || "",
  };
}

function assertPlanFresh(planComment, artifact) {
  const probe = normalizeWhitespace(planComment.plan).slice(0, 120);
  if (!probe || !normalizeWhitespace(artifact.content).includes(probe)) {
    throw new Error("Refusing draft implementation PR: approved planning artifact does not match the current AI plan comment.");
  }
}

function collectContext() {
  try {
    return collect().files || [];
  } catch (error) {
    console.log(`AI draft implementation diagnostics: projectContextError=${sanitizeString(error.message)}`);
    return [];
  }
}

function buildWorkerPayload(issue, planComment, artifact, options) {
  return sanitizeValue({
    repository: process.env.GITHUB_REPOSITORY || "",
    issueNumber: issue.number,
    issueTitle: issue.title || "",
    issueBody: issue.body || "",
    issueLabels: issueLabels(issue),
    issueUrl: issue.html_url || "",
    approvedPlanComment: planComment.plan,
    planningArtifactContent: artifact.content,
    planningArtifactPath: artifact.path,
    projectContext: collectContext(),
    maxFilesChanged: options.maxFilesChanged,
    maxDiffLines: options.maxDiffLines,
    dryRun: options.dryRun,
    workflow: {
      eventName: process.env.GITHUB_EVENT_NAME || "",
      runId: process.env.GITHUB_RUN_ID || "",
      runUrl: runUrl(),
      commit: process.env.GITHUB_SHA || "",
      ref: process.env.GITHUB_REF || "",
    },
  });
}

function workerUrl() {
  return (process.env.CI_AI_WORKER_URL || "").trim().replace(/\/$/, "");
}

function headers() {
  const out = { "Content-Type": "application/json" };
  if (process.env.CI_WORKER_SHARED_SECRET) out["X-CI-Worker-Secret"] = process.env.CI_WORKER_SHARED_SECRET;
  return out;
}

async function callImplementationWorker(payload) {
  const base = workerUrl();
  const endpoint = "/v1/implement/issue";
  console.log(`AI draft implementation diagnostics: workerUrlConfigured=${Boolean(base)}`);
  console.log(`AI draft implementation diagnostics: endpoint=${endpoint}`);
  if (!base) throw new Error("Refusing draft implementation PR: CI_AI_WORKER_URL is not configured.");

  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), Number(process.env.CI_AI_WORKER_TIMEOUT_MS || "120000"));
  try {
    const res = await fetch(`${base}${endpoint}`, {
      method: "POST",
      headers: headers(),
      body: JSON.stringify(payload),
      signal: controller.signal,
    });
    const text = await res.text();
    console.log(`AI draft implementation diagnostics: workerHttpStatus=${res.status}`);
    if (!res.ok) throw new Error(`Worker returned HTTP ${res.status}: ${safePreview(text)}`);
    let body;
    try {
      body = text ? JSON.parse(text) : null;
    } catch (error) {
      throw new Error(`Worker returned invalid JSON: ${sanitizeString(error.message)}`);
    }
    return validateWorkerResponse(body);
  } finally {
    clearTimeout(timeout);
  }
}

function validateWorkerResponse(body) {
  if (!body || typeof body !== "object" || body.ok !== true) throw new Error("Worker output is invalid: ok=true is required.");
  if (typeof body.summary !== "string") throw new Error("Worker output is invalid: summary is required.");
  if (!Array.isArray(body.proposedFiles)) throw new Error("Worker output is invalid: proposedFiles must be an array.");
  if (!Array.isArray(body.fileEdits)) throw new Error("Worker output is invalid: fileEdits must be an array.");
  if (!Array.isArray(body.safetyNotes)) throw new Error("Worker output is invalid: safetyNotes must be an array.");
  if (!Array.isArray(body.validationNotes)) throw new Error("Worker output is invalid: validationNotes must be an array.");
  return {
    ok: true,
    summary: sanitizeString(body.summary),
    proposedFiles: body.proposedFiles.map((file) => normalizePath(String(file))).filter(Boolean),
    fileEdits: body.fileEdits.map((edit) => ({
      path: normalizePath(String(edit?.path || "")),
      content: String(edit?.content || ""),
    })).filter((edit) => edit.path),
    safetyNotes: body.safetyNotes.map((note) => sanitizeString(String(note))).filter(Boolean),
    validationNotes: body.validationNotes.map((note) => sanitizeString(String(note))).filter(Boolean),
    fallbackUsed: Boolean(body.fallbackUsed),
    fallbackReason: body.fallbackReason ? sanitizeString(String(body.fallbackReason)) : "",
  };
}

function validateSafetyGates({ issue, approvedPlanComment, planningArtifactContent, workerResponse, maxFilesChanged, maxDiffLines, notePath }) {
  assertNoOutOfScope(issue, approvedPlanComment, planningArtifactContent);
  const proposedFiles = [...new Set([
    ...workerResponse.proposedFiles,
    ...workerResponse.fileEdits.map((edit) => edit.path),
    notePath,
  ].map(normalizePath).filter(Boolean))];
  if (proposedFiles.length > maxFilesChanged) throw new Error(`Refusing draft implementation PR: proposed change touches ${proposedFiles.length} files, over limit ${maxFilesChanged}.`);
  for (const file of proposedFiles) {
    if (!isAllowedImplementationPath(file, approvedPlanComment, planningArtifactContent)) {
      throw new Error(`Refusing draft implementation PR: forbidden file edit proposed: ${file}`);
    }
  }
  for (const edit of workerResponse.fileEdits) {
    const reason = unsafeContentReason(edit.path, edit.content);
    if (reason) throw new Error(`Refusing draft implementation PR: ${reason}`);
  }
  const estimatedLines = estimatedDiffLines(workerResponse.fileEdits) + buildImplementationNote({ issue, workerResponse, planComment: { url: "", plan: approvedPlanComment }, artifact: { path: approvedPlanArtifactPath(issue.number), planningPrUrl: "" }, changedFiles: proposedFiles }).split(/\r?\n/).length;
  if (estimatedLines > maxDiffLines) throw new Error(`Refusing draft implementation PR: estimated diff lines ${estimatedLines} exceed limit ${maxDiffLines}.`);
  return { proposedFiles, estimatedLines };
}

function isAllowedImplementationPath(file, approvedPlanComment, planningArtifactContent) {
  const normalized = normalizePath(file);
  if (FORBIDDEN_FILE_PATTERNS.some((pattern) => pattern.test(normalized))) return false;
  if (normalized === "README.md") return /\bREADME\.md\b/i.test(`${approvedPlanComment}\n${planningArtifactContent}`);
  return ALLOWED_FILE_PATTERNS.some((pattern) => pattern.test(normalized));
}

function unsafeContentReason(file, content) {
  const normalized = normalizePath(file);
  if (/folia-supported\s*:\s*true/i.test(content)) return "folia-supported true was proposed without validation";
  if (/\b(net\.minecraft|java\.lang\.reflect|Class\.forName|getDeclaredMethod|getDeclaredField|ServiceLoader)\b/i.test(content)) return "NMS, reflection, or ServiceLoader usage was proposed";
  if (/\b(runAsync|runOnEntity|SchedulerAccess)\b/i.test(content)) return "forbidden scheduler access was proposed";
  if (/\b(getWorlds|getLoadedChunks|getOnlinePlayers)\s*\(/i.test(content)) return "global runtime scans were proposed";
  if (/^src\/main\/java\/.*\/domain\//.test(normalized) && /\b(org\.bukkit|io\.papermc|net\.minecraft|PersistentDataContainer|YamlConfiguration)\b/i.test(content)) return "platform dependency was proposed in domain code";
  if (/^src\/main\/java\/.*\/application\//.test(normalized) && /\b(org\.bukkit|io\.papermc|net\.minecraft|Folia)\b/i.test(content)) return "platform dependency was proposed in application code";
  return "";
}

function outOfScopeRisks(text) {
  const actionableText = String(text || "")
    .split(/\r?\n/)
    .filter((line) => !/\b(do not|must not|no |non-goals?|free from|without|forbidden|guardrail|objective only)\b/i.test(line))
    .join("\n");
  return OUT_OF_SCOPE_RULES.filter(([, pattern]) => pattern.test(actionableText)).map(([label]) => label);
}

function buildImplementationNote({ issue, workerResponse, planComment, artifact, changedFiles }) {
  return `# AI Draft Implementation Notes: Issue #${issue.number}

This file was generated as part of an AI draft implementation PR. It requires human review. It must not be auto-merged.

## Issue

- Issue: ${issue.html_url || `#${issue.number}`}
- Approved plan comment: ${planComment.url || "unavailable"}
- Approved plan artifact: ${artifact.path}
- Planning PR: ${artifact.planningPrUrl || "not found or already merged"}

## Implementation Summary

${workerResponse.summary}

## Files Changed

${changedFiles.length ? changedFiles.map((file) => `- \`${file}\``).join("\n") : "- No implementation files were changed by the AI proposal."}

## Safety Notes

${workerResponse.safetyNotes.length ? workerResponse.safetyNotes.map((note) => `- ${note}`).join("\n") : "- No additional safety notes were returned."}

## Validation Expected

- GitHub Actions build/test/integrationTest.
- CI AI Governance Bot.

## Human Review Required

Review the generated diff, adjust it if needed, and rely on GitHub Actions as the source of truth before merge.`;
}

function buildPrBody({ issue, planComment, artifact, workerResponse, changedFiles, safetyGateSummary }) {
  return `## AI Draft Implementation

Issue: ${issue.html_url || `#${issue.number}`}
Planning PR: ${artifact.planningPrUrl || "not found or already merged"}
Approved plan comment: ${planComment.url || "unavailable"}
Approved plan artifact: \`${artifact.path}\`

This PR was generated as an AI draft implementation. It requires human review. It must not be auto-merged.

## Implementation Summary

${workerResponse.summary}

## Files Changed

${changedFiles.length ? changedFiles.map((file) => `- \`${file}\``).join("\n") : "- No implementation files were changed by the AI proposal."}

## Safety Gates Passed

${safetyGateSummary}

## Validation Checklist

- [ ] GitHub Actions build/test/integrationTest.
- [ ] CI AI Governance Bot.
- [ ] Human maintainer review.

## Notes

Fallback used: ${workerResponse.fallbackUsed ? "yes" : "no"}${workerResponse.fallbackReason ? ` (${workerResponse.fallbackReason})` : ""}`;
}

function printDryRun({ workerResponse, safety, notePath }) {
  console.log("AI draft implementation dry-run result:");
  console.log(`summary=${workerResponse.summary}`);
  console.log(`proposedFiles=${safety.proposedFiles.join(", ") || "none"}`);
  console.log(`implementationNote=${notePath}`);
  console.log(`estimatedDiffLines=${safety.estimatedLines}`);
  console.log(`safetyGate=pass`);
  console.log(`fallbackUsed=${workerResponse.fallbackUsed}`);
  if (workerResponse.fallbackReason) console.log(`fallbackReason=${workerResponse.fallbackReason}`);
  if (workerResponse.safetyNotes.length) console.log(`safetyNotes=${workerResponse.safetyNotes.join(" | ")}`);
}

function configureGit() {
  runGit(["config", "user.name", "github-actions[bot]"]);
  runGit(["config", "user.email", "41898282+github-actions[bot]@users.noreply.github.com"]);
}

function prepareBranch(branch) {
  runGit(["fetch", "origin", TARGET_BRANCH, "--depth=1"]);
  runGit(["checkout", "-B", branch, `origin/${TARGET_BRANCH}`]);
}

function applyFileEdits(edits) {
  for (const edit of edits) {
    fs.mkdirSync(path.dirname(edit.path), { recursive: true });
    fs.writeFileSync(edit.path, edit.content, "utf8");
  }
}

function writeImplementationNote(filePath, content) {
  if (!filePath.startsWith("docs/ai-implementation-notes/") || !filePath.endsWith(".md")) throw new Error(`Refusing to write unexpected implementation note path: ${filePath}`);
  fs.mkdirSync(path.dirname(filePath), { recursive: true });
  fs.writeFileSync(filePath, `${content.trim()}\n`, "utf8");
}

function changedFiles() {
  const status = runGit(["status", "--short", "--untracked-files=all"]);
  return status
    .split(/\r?\n/)
    .map((line) => line.slice(3).trim().split(" -> ").pop())
    .map(normalizePath)
    .filter(Boolean);
}

function actualDiffLines(files) {
  if (!files.length) return 0;
  const tracked = files.filter(isTrackedFile);
  const untracked = files.filter((file) => !isTrackedFile(file) && fs.existsSync(file));
  const trackedDiff = tracked.length ? runGit(["diff", "--unified=0", "--", ...tracked]) : "";
  const trackedLines = trackedDiff.split(/\r?\n/).filter((line) => /^[+-]/.test(line) && !/^\+\+\+|^---/.test(line)).length;
  const untrackedLines = untracked.reduce((sum, file) => sum + fs.readFileSync(file, "utf8").split(/\r?\n/).length, 0);
  return trackedLines + untrackedLines;
}

function isTrackedFile(file) {
  try {
    runGit(["ls-files", "--error-unmatch", file]);
    return true;
  } catch {
    return false;
  }
}

function commitAndPush(branch, files, issueNumber) {
  runGit(["add", "--", ...files]);
  const staged = runGit(["diff", "--cached", "--name-only"]).split(/\r?\n/).map((file) => file.trim()).filter(Boolean);
  if (!staged.length) {
    console.log("AI draft implementation diagnostics: no changes to commit.");
  } else {
    runGit(["commit", "-m", `AI draft implementation for issue #${issueNumber}`]);
  }
  runGit(["push", "--force", "origin", `HEAD:refs/heads/${branch}`]);
}

async function upsertPullRequest({ issue, branch, planComment, artifact, workerResponse, changedFiles, safetyGateSummary }) {
  const { owner, repo } = repoParts();
  const title = `AI draft implementation: issue #${issue.number} - ${slugTitle(issue.title)}`;
  const body = buildPrBody({ issue, planComment, artifact, workerResponse, changedFiles, safetyGateSummary });
  const existing = await githubRequest(`/repos/${owner}/${repo}/pulls?state=open&head=${encodeURIComponent(`${owner}:${branch}`)}&base=${TARGET_BRANCH}&per_page=10`);
  if (existing.length) {
    const updated = await githubRequest(`/repos/${owner}/${repo}/pulls/${existing[0].number}`, {
      method: "PATCH",
      body: JSON.stringify({ title, body, base: TARGET_BRANCH }),
    });
    console.log(`AI draft implementation diagnostics: prResult=updated url=${updated.html_url}`);
    return updated.html_url;
  }
  const created = await githubRequest(`/repos/${owner}/${repo}/pulls`, {
    method: "POST",
    body: JSON.stringify({ title, head: branch, base: TARGET_BRANCH, body, draft: true }),
  });
  console.log(`AI draft implementation diagnostics: prResult=created url=${created.html_url}`);
  return created.html_url;
}

function runGit(args) {
  return execFileSync("git", args, { encoding: "utf8", stdio: ["ignore", "pipe", "pipe"] }).trim();
}

function runUrl() {
  const repository = process.env.GITHUB_REPOSITORY || "";
  const runId = process.env.GITHUB_RUN_ID || "";
  return repository && runId ? `https://github.com/${repository}/actions/runs/${runId}` : "";
}

function safePreview(value) {
  return sanitizeString(String(value || "")).replace(/\s+/g, " ").slice(0, 500);
}

function normalizePath(file) {
  return String(file || "").replace(/\\/g, "/").replace(/^\.\//, "");
}

function normalizeWhitespace(value) {
  return String(value || "").replace(/\s+/g, " ").trim();
}

function estimatedDiffLines(edits) {
  return edits.reduce((sum, edit) => sum + String(edit.content || "").split(/\r?\n/).length, 0);
}

function slugTitle(title) {
  return sanitizeString(title || "Untitled issue").replace(/\s+/g, " ").trim().slice(0, 120);
}

async function main() {
  const event = eventPayload();
  const issue = await loadIssue(event);
  const options = {
    dryRun: parseBooleanInput(process.env.DRY_RUN, true),
    maxFilesChanged: parsePositiveInt(process.env.MAX_FILES_CHANGED, 8),
    maxDiffLines: parsePositiveInt(process.env.MAX_DIFF_LINES, 600),
  };
  const notePath = implementationNotePath(issue.number);
  const branch = implementationBranchName(issue.number);

  console.log(`AI draft implementation diagnostics: issueNumber=${issue.number}`);
  console.log(`AI draft implementation diagnostics: dryRun=${options.dryRun}`);
  console.log(`AI draft implementation diagnostics: maxFilesChanged=${options.maxFilesChanged}`);
  console.log(`AI draft implementation diagnostics: maxDiffLines=${options.maxDiffLines}`);
  console.log(`AI draft implementation diagnostics: branch=${branch}`);

  assertIssueReady(issue);
  const planComment = await findPlanComment(issue.number);
  const artifact = await findPlanningArtifact(issue);
  assertPlanFresh(planComment, artifact);
  assertNoOutOfScope(issue, planComment.plan, artifact.content);

  const payload = buildWorkerPayload(issue, planComment, artifact, options);
  const workerResponse = await callImplementationWorker(payload);
  const safety = validateSafetyGates({
    issue,
    approvedPlanComment: planComment.plan,
    planningArtifactContent: artifact.content,
    workerResponse,
    maxFilesChanged: options.maxFilesChanged,
    maxDiffLines: options.maxDiffLines,
    notePath,
  });

  if (options.dryRun) {
    printDryRun({ workerResponse, safety, notePath });
    return;
  }

  configureGit();
  prepareBranch(branch);
  const note = buildImplementationNote({ issue, workerResponse, planComment, artifact, changedFiles: safety.proposedFiles });
  applyFileEdits(workerResponse.fileEdits);
  writeImplementationNote(notePath, note);
  const changed = changedFiles();
  const actualLines = actualDiffLines(changed);
  if (changed.length > options.maxFilesChanged) throw new Error(`Refusing draft implementation PR: actual changed files ${changed.length} exceed limit ${options.maxFilesChanged}.`);
  if (actualLines > options.maxDiffLines) throw new Error(`Refusing draft implementation PR: actual diff lines ${actualLines} exceed limit ${options.maxDiffLines}.`);
  for (const file of changed) {
    if (!isAllowedImplementationPath(file, planComment.plan, artifact.content)) throw new Error(`Refusing draft implementation PR: unexpected changed file ${file}.`);
  }
  commitAndPush(branch, changed, issue.number);
  const prUrl = await upsertPullRequest({
    issue,
    branch,
    planComment,
    artifact,
    workerResponse,
    changedFiles: changed,
    safetyGateSummary: `Changed files: ${changed.length}. Diff lines: ${actualLines}. Max files: ${options.maxFilesChanged}. Max diff lines: ${options.maxDiffLines}.`,
  });
  console.log(`AI draft implementation diagnostics: prUrl=${prUrl}`);
}

if (require.main === module) {
  main().catch((error) => {
    console.error(sanitizeString(error.message));
    process.exit(1);
  });
}

module.exports = {
  PLAN_MARKER,
  approvedPlanArtifactPath,
  assertIssueReady,
  assertNoOutOfScope,
  buildImplementationNote,
  buildPrBody,
  extractPlan,
  implementationBranchName,
  implementationNotePath,
  isAllowedImplementationPath,
  outOfScopeRisks,
  parseBooleanInput,
  validateSafetyGates,
  validateWorkerResponse,
};

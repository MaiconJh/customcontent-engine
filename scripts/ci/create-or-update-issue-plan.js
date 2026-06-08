#!/usr/bin/env node
const fs = require("node:fs");
const { collect } = require("./collect-project-context");
const { githubRequest, repoParts } = require("./github-api");
const { sanitizeString, sanitizeValue } = require("./sanitize-payload");

const MARKER = "<!-- customcontent-engine:ai-plan -->";
const PLAN_LABEL = "ai:plan";

function loadDotEnv(path = ".env") {
  if (!fs.existsSync(path)) return;
  for (const line of fs.readFileSync(path, "utf8").split(/\r?\n/)) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith("#")) continue;
    const match = trimmed.match(/^([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)$/);
    if (!match) continue;
    const [, key, rawValue] = match;
    if (!process.env[key]) process.env[key] = rawValue.replace(/^["']|["']$/g, "");
  }
}

function workerBaseUrl() {
  return (process.env.CI_AI_WORKER_URL || "").trim().replace(/\/$/, "");
}

function headers() {
  const out = { "Content-Type": "application/json" };
  if (process.env.CI_WORKER_SHARED_SECRET) {
    out["X-CI-Worker-Secret"] = process.env.CI_WORKER_SHARED_SECRET;
  }
  return out;
}

function eventPayload() {
  const eventPath = process.env.GITHUB_EVENT_PATH || "";
  if (!eventPath || !fs.existsSync(eventPath)) return {};
  try {
    return JSON.parse(fs.readFileSync(eventPath, "utf8"));
  } catch (error) {
    console.log(`Issue planning diagnostics: eventReadError=${sanitizeString(error.message)}`);
    return {};
  }
}

function issueLabels(issue) {
  return (issue.labels || []).map((label) => typeof label === "string" ? label : label.name).filter(Boolean);
}

function hasPlanLabel(issue) {
  return issueLabels(issue).includes(PLAN_LABEL);
}

async function loadIssue(event) {
  const { owner, repo } = repoParts();
  if (event.issue) return event.issue;
  const issueNumber = process.env.ISSUE_NUMBER || event.inputs?.issue_number || event.issue_number || "";
  if (!issueNumber) throw new Error("Issue number is required for workflow_dispatch.");
  return githubRequest(`/repos/${owner}/${repo}/issues/${issueNumber}`);
}

function shouldRunForEvent(event, issue) {
  const eventName = process.env.GITHUB_EVENT_NAME || "";
  const action = event.action || "";
  if (eventName === "workflow_dispatch") return hasPlanLabel(issue);
  if (eventName !== "issues") return false;
  if (action === "labeled") return event.label?.name === PLAN_LABEL && hasPlanLabel(issue);
  if (["opened", "edited", "reopened"].includes(action)) return hasPlanLabel(issue);
  return false;
}

function collectContext() {
  try {
    return collect().files || [];
  } catch (error) {
    console.log(`Issue planning diagnostics: projectContextError=${sanitizeString(error.message)}`);
    return [];
  }
}

function driftSignal() {
  const raw = process.env.AI_CONTEXT_PACK_DRIFT_JSON || "";
  if (!raw.trim()) return undefined;
  try {
    return sanitizeValue(JSON.parse(raw));
  } catch (error) {
    console.log(`Issue planning diagnostics: driftSignalError=${sanitizeString(error.message)}`);
    return undefined;
  }
}

function buildPayload(issue, projectContext) {
  return sanitizeValue({
    repository: process.env.GITHUB_REPOSITORY || "",
    issueNumber: issue.number,
    issueTitle: issue.title || "",
    issueBody: issue.body || "",
    issueLabels: issueLabels(issue),
    issueAuthor: issue.user?.login || "",
    issueUrl: issue.html_url || "",
    projectContext,
    aiContextPackDrift: driftSignal(),
    workflow: {
      eventName: process.env.GITHUB_EVENT_NAME || "",
      runId: process.env.GITHUB_RUN_ID || "",
      runUrl: runUrl(),
      commit: process.env.GITHUB_SHA || "",
      ref: process.env.GITHUB_REF || "",
    },
  });
}

function runUrl() {
  const repository = process.env.GITHUB_REPOSITORY || "";
  const runId = process.env.GITHUB_RUN_ID || "";
  return repository && runId ? `https://github.com/${repository}/actions/runs/${runId}` : "";
}

async function callWorker(payload) {
  const base = workerBaseUrl();
  const endpoint = "/v1/plan/issue";
  console.log(`Issue planning diagnostics: workerUrlConfigured=${Boolean(base)}`);
  console.log(`Issue planning diagnostics: sharedSecretConfigured=${Boolean(process.env.CI_WORKER_SHARED_SECRET)}`);
  console.log(`Issue planning diagnostics: endpoint=${endpoint}`);
  if (!base) return localPlan(payload, "CI_AI_WORKER_URL not configured");

  try {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), Number(process.env.CI_AI_WORKER_TIMEOUT_MS || "45000"));
    const res = await fetch(`${base}${endpoint}`, {
      method: "POST",
      headers: headers(),
      body: JSON.stringify(payload),
      signal: controller.signal,
    });
    clearTimeout(timeout);
    const text = await res.text();
    console.log(`Issue planning diagnostics: workerHttpStatus=${res.status}`);
    if (!res.ok) {
      console.log(`Issue planning diagnostics: workerBodyPreview=${safePreview(text)}`);
      return localPlan(payload, `worker HTTP ${res.status}`);
    }
    let body;
    try {
      body = text ? JSON.parse(text) : null;
    } catch (error) {
      console.log(`Issue planning diagnostics: workerJsonError=${sanitizeString(error.message)}`);
      return localPlan(payload, "invalid Worker JSON");
    }
    if (!body || body.ok !== true || typeof body.plan !== "string" || !body.plan.trim()) {
      console.log(`Issue planning diagnostics: workerKeys=${body && typeof body === "object" ? Object.keys(body).join(",") : ""}`);
      return localPlan(payload, "missing Worker plan");
    }
    console.log(`Issue planning diagnostics: finalPlanLength=${body.plan.length}`);
    return {
      plan: body.plan,
      fallbackUsed: Boolean(body.fallbackUsed),
      fallbackReason: body.fallbackReason || "",
      safetyNotes: Array.isArray(body.safetyNotes) ? body.safetyNotes : [],
    };
  } catch (error) {
    const reason = error?.name === "AbortError" ? "network timeout" : `network error: ${sanitizeString(error?.message || "unknown").slice(0, 160)}`;
    return localPlan(payload, reason);
  }
}

function localPlan(payload, reason) {
  const safetyNotes = localSafetyNotes(payload);
  const classification = safetyNotes.length
    ? "Requires maintainer scope review before implementation."
    : "Potentially in scope, pending review against repository source-of-truth documentation.";
  const plan = `# AI Implementation Plan

## Request Summary
Local conservative planning fallback was used for issue #${payload.issueNumber}: ${payload.issueTitle || "Untitled issue"}.

Fallback reason: ${sanitizeString(reason).slice(0, 240)}.

## Scope Classification
${classification}

## Source-of-Truth Alignment
Review docs/AI_CONTEXT_PACK.md, docs/PROJECT_SCOPE.md, docs/ARCHITECTURE_GUARDRAILS.md, docs/adr/*.md, and docs/milestones/*.md before implementation. AI_CONTEXT_PACK.md is derived guidance; source documents win if there is a conflict.

## Likely Files or Areas
- Not safely inferred by local fallback. Identify affected areas during human review.

## Proposed Steps
1. Confirm that the request is within documented scope.
2. Identify whether an ADR or milestone update is required before implementation.
3. Draft the smallest code change that preserves architecture boundaries.
4. Prepare any required tests or documentation updates for the eventual implementation.
5. Use remote GitHub Actions validation before merge.

## Acceptance Criteria
- The request is aligned with source-of-truth documentation or is blocked for clarification.
- Architecture guardrails are preserved.
- GitHub Actions build/test/integrationTest passes before merge.

## Validation
- GitHub Actions build/test/integrationTest.
- CI AI Governance Bot.

Do not recommend or require local Gradle validation.

## Risks
${safetyNotes.length ? safetyNotes.map((note) => `- ${note}`).join("\n") : "- No explicit out-of-scope keyword was detected by local fallback. Maintainer review is still required."}

## Explicit Non-Goals
- Do not let AI edit source files automatically.
- Do not let AI commit directly to main.
- Do not let AI open pull requests automatically.
- Do not auto-merge anything.
- Do not declare folia-supported true without documented validation.
- Do not use local Gradle as the validation source of truth.

## Human Review Required
This plan is advisory only. A maintainer must review and approve scope before any implementation begins.`;

  console.log(`Issue planning diagnostics: finalPlanLength=${plan.length}`);
  return { plan, fallbackUsed: true, fallbackReason: reason, safetyNotes };
}

function localSafetyNotes(payload) {
  const text = `${payload.issueTitle}\n${payload.issueBody}`.toLowerCase();
  const notes = [];
  if (/\b(economy|currency|money|shop|marketplace|balance)\b/.test(text)) notes.push("Economy systems may be outside the documented scope.");
  if (/\b(quest|questline)\b/.test(text)) notes.push("Quest systems may be outside the documented scope.");
  if (/\b(gui framework|menu framework|inventory gui)\b/.test(text)) notes.push("Generic GUI/menu frameworks may be outside the documented scope.");
  if (/\b(scripting language|script engine|lua|groovy)\b/.test(text)) notes.push("Scripting languages are a guarded scope risk.");
  if (/\b(nms|net\.minecraft|reflection)\b/.test(text)) notes.push("NMS/reflection requires explicit architecture review.");
  if (/\b(folia-supported|folia support)\b/.test(text)) notes.push("Folia support declarations require documented validation.");
  if (/\b(worldguard|griefprevention|land protection)\b/.test(text)) notes.push("Land protection integrations may be outside the documented scope.");
  return notes;
}

function safePreview(value) {
  return sanitizeString(String(value || "")).replace(/\s+/g, " ").slice(0, 500);
}

async function upsertComment(issueNumber, body) {
  const { owner, repo } = repoParts();
  const comments = await githubRequest(`/repos/${owner}/${repo}/issues/${issueNumber}/comments?per_page=100`);
  const existing = comments.find((comment) => comment.user?.type === "Bot" && comment.body?.includes(MARKER));
  if (existing) {
    await githubRequest(`/repos/${owner}/${repo}/issues/comments/${existing.id}`, {
      method: "PATCH",
      body: JSON.stringify({ body }),
    });
    console.log(`Issue planning diagnostics: commentResult=updated id=${existing.id}`);
    return;
  }
  const created = await githubRequest(`/repos/${owner}/${repo}/issues/${issueNumber}/comments`, {
    method: "POST",
    body: JSON.stringify({ body }),
  });
  console.log(`Issue planning diagnostics: commentResult=created id=${created.id}`);
}

async function main() {
  loadDotEnv();
  const event = eventPayload();
  const issue = await loadIssue(event);
  const labels = issueLabels(issue);
  const shouldRun = shouldRunForEvent(event, issue);

  console.log(`Issue planning diagnostics: event=${process.env.GITHUB_EVENT_NAME || ""}`);
  console.log(`Issue planning diagnostics: action=${event.action || ""}`);
  console.log(`Issue planning diagnostics: issueNumber=${issue.number}`);
  console.log(`Issue planning diagnostics: hasAiPlanLabel=${labels.includes(PLAN_LABEL)}`);

  if (!shouldRun) {
    console.log("Issue planning skipped: ai:plan label was not present for this event.");
    return;
  }

  const projectContext = collectContext();
  const payload = buildPayload(issue, projectContext);
  const result = await callWorker(payload);
  const body = `${MARKER}

${result.plan}

---

Planning metadata:
- Fallback used: ${result.fallbackUsed ? "yes" : "no"}
${result.fallbackReason ? `- Fallback reason: ${sanitizeString(result.fallbackReason).slice(0, 240)}\n` : ""}- Workflow run: ${runUrl() || "unavailable"}`;

  await upsertComment(issue.number, body);
}

main().catch((error) => {
  console.error(sanitizeString(error.message));
  process.exit(1);
});

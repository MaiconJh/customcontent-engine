#!/usr/bin/env node
const fs = require("node:fs");
const path = require("node:path");
const { execFileSync } = require("node:child_process");
const { githubRequest, repoParts } = require("./github-api");
const { sanitizeString } = require("./sanitize-payload");

const PLAN_LABEL = "ai:plan";
const APPROVED_LABEL = "ai:approved";
const PLAN_MARKER = "<!-- customcontent-engine:ai-plan -->";
const TARGET_BRANCH = "main";

function eventPayload() {
  const eventPath = process.env.GITHUB_EVENT_PATH || "";
  if (!eventPath || !fs.existsSync(eventPath)) return {};
  try {
    return JSON.parse(fs.readFileSync(eventPath, "utf8"));
  } catch (error) {
    console.log(`Approved plan PR diagnostics: eventReadError=${sanitizeString(error.message)}`);
    return {};
  }
}

function issueLabels(issue) {
  return (issue.labels || []).map((label) => typeof label === "string" ? label : label.name).filter(Boolean);
}

function hasLabel(issue, label) {
  return issueLabels(issue).includes(label);
}

async function loadIssue(event) {
  const { owner, repo } = repoParts();
  if (event.issue) return event.issue;
  const issueNumber = process.env.ISSUE_NUMBER || event.inputs?.issue_number || "";
  if (!issueNumber) throw new Error("Issue number is required for workflow_dispatch.");
  return githubRequest(`/repos/${owner}/${repo}/issues/${issueNumber}`);
}

function shouldRunForEvent(event, issue) {
  const eventName = process.env.GITHUB_EVENT_NAME || "";
  if (eventName === "workflow_dispatch") return hasLabel(issue, PLAN_LABEL) && hasLabel(issue, APPROVED_LABEL);
  return eventName === "issues"
    && event.action === "labeled"
    && event.label?.name === APPROVED_LABEL
    && hasLabel(issue, PLAN_LABEL)
    && hasLabel(issue, APPROVED_LABEL);
}

function assertApprovedIssue(event, issue) {
  const targetBranch = process.env.TARGET_BRANCH || TARGET_BRANCH;
  if (targetBranch !== TARGET_BRANCH) {
    throw new Error(`Refusing to create an approved plan PR because target branch is ${targetBranch}, not main.`);
  }
  if (!hasLabel(issue, PLAN_LABEL)) throw new Error("Refusing approved plan PR: issue is missing ai:plan label.");
  if (!hasLabel(issue, APPROVED_LABEL)) throw new Error("Refusing approved plan PR: issue is missing ai:approved label.");
  if (!shouldRunForEvent(event, issue)) {
    throw new Error("Refusing approved plan PR: event did not explicitly approve a planned issue.");
  }
  const risks = outOfScopeRisks(`${issue.title || ""}\n${issue.body || ""}`);
  if (risks.length && !hasHumanScopeNote(issue)) {
    throw new Error(`Refusing approved plan PR: possible out-of-scope request lacks an explicit human scope note (${risks.join(", ")}).`);
  }
}

async function findPlanComment(issueNumber) {
  const { owner, repo } = repoParts();
  const comments = await githubRequest(`/repos/${owner}/${repo}/issues/${issueNumber}/comments?per_page=100`);
  const existing = comments.find((comment) => comment.body?.includes(PLAN_MARKER));
  if (!existing) throw new Error("Refusing approved plan PR: no AI plan comment marker was found.");
  const plan = extractPlan(existing.body || "");
  if (!plan.trim()) throw new Error("Refusing approved plan PR: AI plan comment is empty.");
  return {
    plan,
    url: existing.html_url || "",
  };
}

function extractPlan(body) {
  const markerIndex = body.indexOf(PLAN_MARKER);
  if (markerIndex < 0) return "";
  const afterMarker = body.slice(markerIndex + PLAN_MARKER.length).trim();
  return afterMarker.split(/\n---\n\s*Planning metadata:/)[0].trim();
}

function branchName(issueNumber) {
  return `ai/approved-plan-issue-${issueNumber}`;
}

function artifactPath(issueNumber) {
  return `docs/ai-plans/issue-${issueNumber}-approved-plan.md`;
}

function slugTitle(title) {
  return sanitizeString(title || "Untitled issue").replace(/\s+/g, " ").trim().slice(0, 120);
}

function buildArtifact(issue, planComment) {
  const labels = issueLabels(issue);
  return `# Approved AI Plan Handoff: Issue #${issue.number}

This file is an advisory planning artifact. It does not implement code changes.

## Issue

- Title: ${slugTitle(issue.title)}
- URL: ${issue.html_url || ""}
- Source labels: ${labels.length ? labels.join(", ") : "none"}
- AI plan comment: ${planComment.url || "unavailable"}

## Approved Plan Snapshot

${planComment.plan}

## Implementation Checklist

- [ ] Review the approved plan with a maintainer.
- [ ] Confirm the request still matches PROJECT_SCOPE.md, ARCHITECTURE_GUARDRAILS.md, accepted ADRs, and milestones.
- [ ] Identify the smallest implementation approach in a separate implementation PR.
- [ ] Keep production source changes out of this planning PR.
- [ ] Document any required ADR or milestone follow-up before implementation.

## Validation Checklist

- [ ] GitHub Actions build/test/integrationTest passes on the eventual implementation PR.
- [ ] CI AI Governance Bot review is checked before merge.
- [ ] No local Gradle validation is required for this planning artifact.

## Non-Goals

- Do not implement code in this PR.
- Do not edit Java, YAML definitions, plugin resources, Gradle files, or build-test workflow files in this PR.
- Do not let AI modify production source files automatically.
- Do not auto-merge this PR.
- Do not treat AI_CONTEXT_PACK.md as stronger than PROJECT_SCOPE.md, ARCHITECTURE_GUARDRAILS.md, ADRs, or milestones.

## Human Review Required

Review the plan, adjust it if needed, then implement in a separate commit or PR.`;
}

function buildPrBody(issue, planComment, filePath) {
  return `## Approved Planning Handoff

Issue: ${issue.html_url || `#${issue.number}`}
AI plan comment: ${planComment.url || "unavailable"}
Planning artifact: \`${filePath}\`

This PR is planning-only. It does not implement code changes.

## Summary

This PR captures the approved AI implementation plan for issue #${issue.number} as a reviewed handoff artifact under \`docs/ai-plans/\`.

## Checklist

- [ ] The issue has \`ai:plan\`.
- [ ] The issue has \`ai:approved\`.
- [ ] The AI planning comment was captured.
- [ ] No implementation code was changed.
- [ ] Human review is complete before any implementation work starts.

## Next Human Action

Review the plan, adjust it if needed, then implement in a separate commit/PR.`;
}

function runGit(args) {
  return execFileSync("git", args, { encoding: "utf8", stdio: ["ignore", "pipe", "pipe"] }).trim();
}

function configureGit() {
  runGit(["config", "user.name", "github-actions[bot]"]);
  runGit(["config", "user.email", "41898282+github-actions[bot]@users.noreply.github.com"]);
}

function prepareBranch(name) {
  runGit(["fetch", "origin", TARGET_BRANCH, "--depth=1"]);
  runGit(["checkout", "-B", name, `origin/${TARGET_BRANCH}`]);
}

function writeArtifact(filePath, content) {
  if (!filePath.startsWith("docs/ai-plans/") || !filePath.endsWith(".md")) {
    throw new Error(`Refusing to write unexpected artifact path: ${filePath}`);
  }
  fs.mkdirSync(path.dirname(filePath), { recursive: true });
  fs.writeFileSync(filePath, `${content.trim()}\n`, "utf8");
}

function commitAndPush(branch, filePath) {
  runGit(["add", "--", filePath]);
  const staged = runGit(["diff", "--cached", "--name-only"]);
  const stagedFiles = staged.split(/\r?\n/).map((file) => file.trim()).filter(Boolean);
  assertOnlyPlanningArtifact(stagedFiles, filePath);
  if (!stagedFiles.length) {
    console.log("Approved plan PR diagnostics: no artifact changes to commit.");
  } else {
    runGit(["commit", "-m", `Add approved AI plan for issue ${issueNumberFromArtifact(filePath)}`]);
  }
  runGit(["push", "--force", "origin", `HEAD:refs/heads/${branch}`]);
}

function assertOnlyPlanningArtifact(files, expectedPath) {
  const forbidden = files.filter((file) => file !== expectedPath || isForbiddenPath(file));
  if (forbidden.length) {
    throw new Error(`Refusing to push unexpected files: ${forbidden.join(", ")}`);
  }
}

function isForbiddenPath(file) {
  return file.startsWith("src/")
    || file === "build.gradle.kts"
    || file === "settings.gradle.kts"
    || file.startsWith("gradle/")
    || file === ".github/workflows/build-test.yml"
    || file.endsWith("/plugin.yml")
    || file.endsWith("/definitions.yml");
}

function issueNumberFromArtifact(filePath) {
  return filePath.match(/issue-(\d+)-approved-plan\.md$/)?.[1] || "";
}

async function upsertPullRequest(issue, branch, planComment, filePath) {
  const { owner, repo } = repoParts();
  const title = `AI approved plan: issue #${issue.number} - ${slugTitle(issue.title)}`;
  const body = buildPrBody(issue, planComment, filePath);
  const encodedHead = encodeURIComponent(`${owner}:${branch}`);
  const existing = await githubRequest(`/repos/${owner}/${repo}/pulls?state=open&head=${encodedHead}&base=${TARGET_BRANCH}&per_page=10`);
  if (existing.length) {
    const pr = existing[0];
    const updated = await githubRequest(`/repos/${owner}/${repo}/pulls/${pr.number}`, {
      method: "PATCH",
      body: JSON.stringify({ title, body, base: TARGET_BRANCH }),
    });
    console.log(`Approved plan PR diagnostics: prResult=updated url=${updated.html_url}`);
    return updated.html_url;
  }
  const created = await githubRequest(`/repos/${owner}/${repo}/pulls`, {
    method: "POST",
    body: JSON.stringify({
      title,
      head: branch,
      base: TARGET_BRANCH,
      body,
      draft: true,
    }),
  });
  console.log(`Approved plan PR diagnostics: prResult=created url=${created.html_url}`);
  return created.html_url;
}

function outOfScopeRisks(text) {
  const rules = [
    ["economy system", /\b(economy|currency|money|shop|marketplace|balance|wallet)\b/i],
    ["quest system", /\b(quest|quests|questline)\b/i],
    ["generic combat system", /\b(generic combat|combat system|damage engine|weapon framework|pvp framework)\b/i],
    ["GUI/menu framework", /\b(gui framework|menu framework|inventory gui|generic menu|screen framework)\b/i],
    ["scripting language", /\b(scripting language|script engine|embedded script|lua|groovy scripts?)\b/i],
    ["generic ability framework", /\b(generic ability|ability framework|skill framework|spell framework)\b/i],
    ["land protection", /\b(land protection|claim protection|worldguard|griefprevention|grief prevention|region protection)\b/i],
    ["NMS/reflection", /\b(NMS|net\.minecraft|reflection|reflective access)\b/i],
    ["Folia support declaration", /\b(folia-supported\s*:\s*true|declare folia support|Folia support)\b/i],
  ];
  return rules.filter(([, pattern]) => pattern.test(text)).map(([label]) => label);
}

function hasHumanScopeNote(issue) {
  const labels = issueLabels(issue).map((label) => label.toLowerCase());
  const body = `${issue.title || ""}\n${issue.body || ""}`.toLowerCase();
  return labels.some((label) => ["scope:approved", "adr:required", "maintainer:approved"].includes(label))
    || /maintainer approved|scope reviewed|human approved|explicit approval|adr required|approved scope/i.test(body);
}

async function main() {
  const event = eventPayload();
  const issue = await loadIssue(event);
  const branch = branchName(issue.number);
  const filePath = artifactPath(issue.number);

  console.log(`Approved plan PR diagnostics: event=${process.env.GITHUB_EVENT_NAME || ""}`);
  console.log(`Approved plan PR diagnostics: action=${event.action || ""}`);
  console.log(`Approved plan PR diagnostics: issueNumber=${issue.number}`);
  console.log(`Approved plan PR diagnostics: hasAiPlan=${hasLabel(issue, PLAN_LABEL)}`);
  console.log(`Approved plan PR diagnostics: hasAiApproved=${hasLabel(issue, APPROVED_LABEL)}`);
  console.log(`Approved plan PR diagnostics: targetBranch=${process.env.TARGET_BRANCH || TARGET_BRANCH}`);
  console.log(`Approved plan PR diagnostics: branch=${branch}`);
  console.log(`Approved plan PR diagnostics: artifact=${filePath}`);

  assertApprovedIssue(event, issue);
  const planComment = await findPlanComment(issue.number);
  const artifact = buildArtifact(issue, planComment);

  configureGit();
  prepareBranch(branch);
  writeArtifact(filePath, artifact);
  commitAndPush(branch, filePath);
  const prUrl = await upsertPullRequest(issue, branch, planComment, filePath);
  console.log(`Approved plan PR diagnostics: prUrl=${prUrl}`);
}

if (require.main === module) {
  main().catch((error) => {
    console.error(sanitizeString(error.message));
    process.exit(1);
  });
}

module.exports = {
  PLAN_MARKER,
  artifactPath,
  branchName,
  buildArtifact,
  buildPrBody,
  extractPlan,
  outOfScopeRisks,
};

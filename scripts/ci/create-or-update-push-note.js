#!/usr/bin/env node
const fs = require("node:fs");
const { githubRequest, repoParts } = require("./github-api");

const CONFIG_PATH = ".github/ai-review/config.yml";

function arg(name, fallback) {
  const idx = process.argv.indexOf(name);
  return idx >= 0 ? process.argv[idx + 1] : fallback;
}

function fileExists(file) {
  return Boolean(file && fs.existsSync(file));
}

function loadPushNoteConfig() {
  if (!fs.existsSync(CONFIG_PATH)) return { enabled: true, reopenClosed: true, title: "AI Technical Note: main" };
  const raw = fs.readFileSync(CONFIG_PATH, "utf8");
  const section = extractYamlSection(raw, "push_notes");
  return {
    enabled: readBoolean(section, "enabled", true),
    reopenClosed: readBoolean(section, "reopen_closed", true),
    title: readString(section, "title", "AI Technical Note: main"),
  };
}

function extractYamlSection(raw, name) {
  const lines = raw.split(/\r?\n/);
  const start = lines.findIndex((line) => line.trim() === `${name}:`);
  if (start < 0) return "";
  const out = [];
  for (const line of lines.slice(start + 1)) {
    if (/^\S/.test(line) && line.trim().endsWith(":")) break;
    out.push(line);
  }
  return out.join("\n");
}

function readBoolean(section, key, fallback) {
  const match = section.match(new RegExp(`^\\s*${key}:\\s*(true|false)\\s*$`, "im"));
  return match ? match[1].toLowerCase() === "true" : fallback;
}

function readString(section, key, fallback) {
  const match = section.match(new RegExp(`^\\s*${key}:\\s*["']?([^"'\r\n]+)["']?\\s*$`, "im"));
  return match ? match[1].trim() : fallback;
}

function responseMetadata(responsePath) {
  if (!fileExists(responsePath)) return {};
  try {
    const response = JSON.parse(fs.readFileSync(responsePath, "utf8"));
    const governance = response.governanceReview || {};
    return {
      hasFinalReport: Boolean(response.finalReport),
      hasInitialReport: Boolean(response.initialReport),
      hasGovernanceReview: Boolean(response.governanceReview),
      publishDecision: governance.publishDecision || response.publishDecision || "unknown",
      fallbackUsed: Boolean(response.fallbackUsed),
      fallbackReason: response.fallbackReason || "",
      finalReportLength: String(response.finalReport || response.markdown || "").length,
    };
  } catch (error) {
    return { responseReadError: error.message };
  }
}

async function listIssues(owner, repo) {
  const issues = [];
  for (const state of ["open", "closed"]) {
    const page = await githubRequest(`/repos/${owner}/${repo}/issues?state=${state}&per_page=100`);
    issues.push(...page.filter((issue) => !issue.pull_request));
  }
  return issues;
}

function findPushNote(issues, title, marker) {
  const matching = issues.filter((issue) => issue.title === title || issue.body?.includes(marker));
  return matching.find((issue) => issue.state === "open") || matching[0] || null;
}

async function setLabels(owner, repo, issueNumber, labels) {
  if (!labels.length) return;
  await githubRequest(`/repos/${owner}/${repo}/issues/${issueNumber}/labels`, {
    method: "PUT",
    body: JSON.stringify({ labels }),
  });
}

async function main() {
  const marker = "<!-- ai-ci-review-bot:push-note -->";
  const bodyPath = arg("--body-file", null);
  const responsePath = arg("--response-file", null);
  const config = loadPushNoteConfig();
  const labels = (arg("--labels", "automated,ai-analysis") || "").split(",").map((label) => label.trim()).filter(Boolean);
  const sha = process.env.GITHUB_SHA || "";
  const runId = process.env.GITHUB_RUN_ID || "";
  const branch = process.env.GITHUB_REF_NAME || "";
  const reportExists = fileExists(bodyPath);
  const reportBody = reportExists ? fs.readFileSync(bodyPath, "utf8") : fs.readFileSync(0, "utf8");
  const metadata = responseMetadata(responsePath);

  console.log(`Push note diagnostics: event=${process.env.GITHUB_EVENT_NAME || ""}`);
  console.log(`Push note diagnostics: ref=${process.env.GITHUB_REF || ""}`);
  console.log(`Push note diagnostics: branch=${branch}`);
  console.log(`Push note diagnostics: commit=${sha}`);
  console.log(`Push note diagnostics: enabled=${config.enabled}`);
  console.log(`Push note diagnostics: reportPath=${bodyPath || "stdin"}`);
  console.log(`Push note diagnostics: reportExists=${reportExists}`);
  console.log(`Push note diagnostics: reportLength=${reportBody.length}`);
  console.log(`Push note diagnostics: hasFinalReport=${metadata.hasFinalReport ?? "unknown"}`);
  console.log(`Push note diagnostics: hasInitialReport=${metadata.hasInitialReport ?? "unknown"}`);
  console.log(`Push note diagnostics: hasGovernanceReview=${metadata.hasGovernanceReview ?? "unknown"}`);
  console.log(`Push note diagnostics: publishDecision=${metadata.publishDecision || "unknown"}`);
  console.log(`Push note diagnostics: fallbackUsed=${metadata.fallbackUsed ?? "unknown"}`);
  if (metadata.fallbackReason) console.log(`Push note diagnostics: fallbackReason=${metadata.fallbackReason}`);
  if (metadata.responseReadError) console.log(`Push note diagnostics: responseReadError=${metadata.responseReadError}`);

  if (!config.enabled) {
    console.log("Push note publishing is disabled by .github/ai-review/config.yml.");
    return;
  }

  const body = `${marker}\n<!-- ai-ci-review-bot:sha:${sha} -->\n<!-- ai-ci-review-bot:run:${runId} -->\n\n${reportBody}`;
  const { owner, repo } = repoParts();
  const title = config.title || "AI Technical Note: main";
  const issues = await listIssues(owner, repo);
  const existing = findPushNote(issues, title, marker);
  if (existing) {
    const wasClosed = existing.state !== "open";
    await githubRequest(`/repos/${owner}/${repo}/issues/${existing.number}`, {
      method: "PATCH",
      body: JSON.stringify({
        body,
        title,
        state: config.reopenClosed ? "open" : existing.state,
      }),
    });
    await setLabels(owner, repo, existing.number, labels);
    const url = `https://github.com/${owner}/${repo}/issues/${existing.number}`;
    console.log(`${wasClosed && config.reopenClosed ? "Reopened and updated" : "Updated"} push note issue #${existing.number}: ${url}`);
    return;
  }
  const created = await githubRequest(`/repos/${owner}/${repo}/issues`, {
    method: "POST",
    body: JSON.stringify({ title, body, labels }),
  });
  console.log(`Created push note issue #${created.number}: ${created.html_url}`);
}

main().catch((error) => {
  console.error(error.message);
  process.exit(1);
});

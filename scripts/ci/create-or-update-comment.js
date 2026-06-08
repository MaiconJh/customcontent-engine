#!/usr/bin/env node
const fs = require("node:fs");
const { githubRequest, repoParts } = require("./github-api");

const MARKERS = new Set([
  "<!-- ai-ci-review-bot:summary -->",
  "<!-- ai-ci-review-bot:diff -->",
  "<!-- ai-ci-review-bot:push-note -->",
]);

function arg(name, fallback) {
  const idx = process.argv.indexOf(name);
  return idx >= 0 ? process.argv[idx + 1] : fallback;
}

async function main() {
  const marker = arg("--marker", "<!-- ai-ci-review-bot:summary -->");
  if (!MARKERS.has(marker)) throw new Error("Unsupported marker");
  const bodyPath = arg("--body-file", null);
  const body = `${marker}\n\n${bodyPath ? fs.readFileSync(bodyPath, "utf8") : fs.readFileSync(0, "utf8")}`;
  const prNumber = arg("--pr", process.env.PR_NUMBER || "");
  const { owner, repo } = repoParts();

  if (!prNumber) {
    console.log("No pull request number available; skipping PR comment.");
    return;
  }

  const comments = await githubRequest(`/repos/${owner}/${repo}/issues/${prNumber}/comments?per_page=100`);
  const existing = comments.find((comment) => comment.user?.type === "Bot" && comment.body?.includes(marker));
  if (existing) {
    await githubRequest(`/repos/${owner}/${repo}/issues/comments/${existing.id}`, {
      method: "PATCH",
      body: JSON.stringify({ body }),
    });
    console.log(`Updated PR comment ${existing.id}.`);
  } else {
    await githubRequest(`/repos/${owner}/${repo}/issues/${prNumber}/comments`, {
      method: "POST",
      body: JSON.stringify({ body }),
    });
    console.log("Created PR comment.");
  }

  const findingsPath = arg("--findings-file", "");
  if (findingsPath && fs.existsSync(findingsPath)) {
    await createInlineComments({ owner, repo, prNumber, findings: JSON.parse(fs.readFileSync(findingsPath, "utf8")) });
  }
}

async function createInlineComments({ owner, repo, prNumber, findings }) {
  const pr = await githubRequest(`/repos/${owner}/${repo}/pulls/${prNumber}`);
  const files = await githubRequest(`/repos/${owner}/${repo}/pulls/${prNumber}/files?per_page=100`);
  const changed = new Map(files.map((file) => [file.filename, file.patch || ""]));
  const ordered = findings
    .filter((finding) => ["warning", "error"].includes(finding.severity) && finding.file && finding.line && changed.has(finding.file))
    .sort((a, b) => (a.severity === "error" ? -1 : 1) - (b.severity === "error" ? -1 : 1))
    .slice(0, Number(process.env.MAX_INLINE_COMMENTS || "10"));

  for (const finding of ordered) {
    try {
      await githubRequest(`/repos/${owner}/${repo}/pulls/${prNumber}/comments`, {
        method: "POST",
        body: JSON.stringify({
          body: `**${finding.severity.toUpperCase()}: ${finding.title || "AI finding"}**\n\n${finding.body || ""}`,
          commit_id: pr.head.sha,
          path: finding.file,
          line: Number(finding.line),
          side: "RIGHT",
        }),
      });
    } catch (error) {
      console.log(`Inline comment skipped for ${finding.file}:${finding.line}: ${error.message}`);
    }
  }
}

main().catch((error) => {
  console.error(error.message);
  process.exit(1);
});

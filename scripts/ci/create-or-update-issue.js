#!/usr/bin/env node
const fs = require("node:fs");
const { githubRequest, repoParts } = require("./github-api");

function arg(name, fallback) {
  const idx = process.argv.indexOf(name);
  return idx >= 0 ? process.argv[idx + 1] : fallback;
}

async function main() {
  const marker = "<!-- ai-ci-review-bot:failure -->";
  const bodyPath = arg("--body-file", null);
  const labels = (arg("--labels", "ci-failure,automated,ai-analysis") || "").split(",").map((label) => label.trim()).filter(Boolean);
  const sha = process.env.GITHUB_SHA || "";
  const runId = process.env.GITHUB_RUN_ID || "";
  const branch = process.env.GITHUB_REF_NAME || "";
  const title = `CI Failure: ${branch || "unknown branch"} @ ${sha.slice(0, 7) || "unknown"}`;
  const body = `${marker}\n<!-- ai-ci-review-bot:sha:${sha} -->\n<!-- ai-ci-review-bot:run:${runId} -->\n\n${bodyPath ? fs.readFileSync(bodyPath, "utf8") : fs.readFileSync(0, "utf8")}`;
  const { owner, repo } = repoParts();

  const issues = await githubRequest(`/repos/${owner}/${repo}/issues?state=open&labels=${encodeURIComponent(labels[0] || "ci-failure")}&per_page=100`);
  const sameSha = issues.find((issue) => issue.body?.includes(`ai-ci-review-bot:sha:${sha}`));
  if (sameSha) {
    console.log(`Issue already exists for SHA ${sha}: #${sameSha.number}.`);
    return;
  }

  const reusable = issues.find((issue) => issue.body?.includes(marker) && issue.title.includes(branch || "unknown branch"));
  if (reusable) {
    await githubRequest(`/repos/${owner}/${repo}/issues/${reusable.number}/comments`, {
      method: "POST",
      body: JSON.stringify({ body }),
    });
    console.log(`Commented on existing issue #${reusable.number}.`);
    return;
  }

  await githubRequest(`/repos/${owner}/${repo}/issues`, {
    method: "POST",
    body: JSON.stringify({ title, body, labels }),
  });
  console.log("Created CI failure issue.");
}

main().catch((error) => {
  console.error(error.message);
  process.exit(1);
});

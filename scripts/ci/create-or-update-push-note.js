#!/usr/bin/env node
const fs = require("node:fs");
const { githubRequest, repoParts } = require("./github-api");

function arg(name, fallback) {
  const idx = process.argv.indexOf(name);
  return idx >= 0 ? process.argv[idx + 1] : fallback;
}

async function main() {
  const marker = "<!-- ai-ci-review-bot:push-note -->";
  const bodyPath = arg("--body-file", null);
  const labels = (arg("--labels", "automated,ai-analysis") || "").split(",").map((label) => label.trim()).filter(Boolean);
  const body = `${marker}\n\n${bodyPath ? fs.readFileSync(bodyPath, "utf8") : fs.readFileSync(0, "utf8")}`;
  const { owner, repo } = repoParts();
  const title = "AI Technical Note: main";
  const issues = await githubRequest(`/repos/${owner}/${repo}/issues?state=open&labels=${encodeURIComponent(labels[0] || "automated")}&per_page=100`);
  const existing = issues.find((issue) => issue.title === title || issue.body?.includes(marker));
  if (existing) {
    await githubRequest(`/repos/${owner}/${repo}/issues/${existing.number}`, {
      method: "PATCH",
      body: JSON.stringify({ body, title, labels }),
    });
    console.log(`Updated push note issue #${existing.number}.`);
    return;
  }
  await githubRequest(`/repos/${owner}/${repo}/issues`, {
    method: "POST",
    body: JSON.stringify({ title, body, labels }),
  });
  console.log("Created push note issue.");
}

main().catch((error) => {
  console.error(error.message);
  process.exit(1);
});

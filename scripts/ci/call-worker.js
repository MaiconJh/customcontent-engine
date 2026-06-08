#!/usr/bin/env node
const fs = require("node:fs");
const { sanitizeValue } = require("./sanitize-payload");

function arg(name, fallback) {
  const idx = process.argv.indexOf(name);
  return idx >= 0 ? process.argv[idx + 1] : fallback;
}

function fallbackMarkdown(type, payload) {
  if (type === "failure") {
    return `## CI Failure - build/test failed

AI analysis was not generated because the Worker is not configured or failed.

### Data

* Repository: ${payload.repository}
* Branch: ${payload.branch}
* Commit: ${payload.commit}
* Workflow: ${payload.workflow}
* Run URL: ${payload.run_url}

### Next steps

* Check the build.log artifact.
* Fix the build/test failure.
* Re-run the workflow.`;
  }
  return `## AI Technical Note - analysis unavailable

The Worker is not configured or did not respond. Build/test remains the source of truth for this workflow.

### Data

* Repository: ${payload.repository}
* Branch: ${payload.branch}
* Commit: ${payload.commit}
* Workflow: ${payload.workflow}
* Run URL: ${payload.run_url}`;
}

async function main() {
  const endpoint = process.env.CI_AI_WORKER_URL;
  const type = arg("--type", "diff");
  const inputFile = arg("--input-file", null);
  const outputFile = arg("--output-file", "ai-response.json");
  const contentField = type === "failure" ? "log" : "diff";
  const content = inputFile && fs.existsSync(inputFile) ? fs.readFileSync(inputFile, "utf8") : "";
  const payload = sanitizeValue({
    type,
    repository: process.env.GITHUB_REPOSITORY || "",
    event: process.env.GITHUB_EVENT_NAME || "",
    branch: process.env.GITHUB_HEAD_REF || process.env.GITHUB_REF_NAME || "",
    base: process.env.GITHUB_BASE_REF || "",
    head: process.env.GITHUB_SHA || "",
    commit: process.env.GITHUB_SHA || "",
    workflow: process.env.GITHUB_WORKFLOW || "",
    run_id: process.env.GITHUB_RUN_ID || "",
    run_url: `${process.env.GITHUB_SERVER_URL || "https://github.com"}/${process.env.GITHUB_REPOSITORY || ""}/actions/runs/${process.env.GITHUB_RUN_ID || ""}`,
    [contentField]: content,
    metadata: {
      actor: process.env.GITHUB_ACTOR || "",
      ref: process.env.GITHUB_REF || "",
    },
  });

  let response = null;
  if (endpoint) {
    try {
      const headers = { "Content-Type": "application/json" };
      if (process.env.CI_WORKER_SHARED_SECRET) headers["X-CI-Worker-Secret"] = process.env.CI_WORKER_SHARED_SECRET;
      const controller = new AbortController();
      const timeout = setTimeout(() => controller.abort(), Number(process.env.CI_WORKER_TIMEOUT_MS || "45000"));
      const res = await fetch(`${endpoint.replace(/\/$/, "")}/v1/analyze/${type}`, {
        method: "POST",
        headers,
        body: JSON.stringify(payload),
        signal: controller.signal,
      });
      clearTimeout(timeout);
      response = await res.json().catch(() => null);
      if (!res.ok || !response?.ok) response = null;
    } catch (error) {
      console.log(`Worker unavailable: ${error.message}`);
    }
  } else {
    console.log("CI_AI_WORKER_URL is not configured; using local fallback message.");
  }

  if (!response) {
    response = { ok: true, type, summary: "Worker unavailable", markdown: fallbackMarkdown(type, payload), findings: [] };
  }

  fs.writeFileSync(outputFile, JSON.stringify(response, null, 2));
  fs.writeFileSync(arg("--markdown-file", "ai-response.md"), response.markdown || fallbackMarkdown(type, payload));
  fs.writeFileSync(arg("--findings-file", "ai-findings.json"), JSON.stringify(response.findings || [], null, 2));
}

main().catch((error) => {
  console.error(error.message);
  process.exit(1);
});

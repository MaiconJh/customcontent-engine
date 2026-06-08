#!/usr/bin/env node
const fs = require("node:fs");

function loadDotEnv(path = ".env") {
  if (!fs.existsSync(path)) return;
  for (const line of fs.readFileSync(path, "utf8").split(/\r?\n/)) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith("#")) continue;
    const match = trimmed.match(/^([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)$/);
    if (!match) continue;
    const [, key, rawValue] = match;
    if (!process.env[key]) {
      process.env[key] = rawValue.replace(/^["']|["']$/g, "");
    }
  }
}

function workerUrl() {
  const url = process.env.CI_AI_WORKER_URL;
  if (!url) throw new Error("CI_AI_WORKER_URL is required. Add it to .env or the environment.");
  return url.replace(/\/$/, "");
}

async function requestJson(url, options = {}) {
  const res = await fetch(url, options);
  const body = await res.json().catch(() => null);
  return { status: res.status, ok: res.ok, body };
}

function headers() {
  const out = { "Content-Type": "application/json" };
  if (process.env.CI_WORKER_SHARED_SECRET) {
    out["X-CI-Worker-Secret"] = process.env.CI_WORKER_SHARED_SECRET;
  }
  return out;
}

function payload(type) {
  const projectContext = [
    {
      path: "docs/PROJECT_SCOPE.md",
      content: "The project scope is conservative. Public API is not stable. Advanced Folia support is not promised.",
    },
    {
      path: "docs/ARCHITECTURE_GUARDRAILS.md",
      content: "Domain code must not depend on Bukkit, Paper, Folia, YAML, PDC, NMS, or adapter implementation details.",
    },
  ];
  const base = {
    repository: "MaiconJh/customcontent-engine",
    event: "workflow_dispatch",
    branch: "local-api-test",
    commit: "local-test",
    workflow: "local-worker-api-test",
    run_id: "local",
    run_url: "https://github.com/MaiconJh/customcontent-engine/actions",
    ciLogs: "GitHub Actions build-test result: success.",
    projectContext,
    metadata: { source: "scripts/ci/test-worker-api.js" },
  };
  if (type === "failure") {
    return {
      ...base,
      type,
      ciLogs: "BUILD FAILED\nExecution failed for task ':test'.\nThere were failing tests.",
      log: "BUILD FAILED\nExecution failed for task ':test'.\nThere were failing tests.",
    };
  }
  return {
    ...base,
    type,
    base: "main",
    head: "local-test",
    diff: [
      "diff --git a/.github/workflows/example.yml b/.github/workflows/example.yml",
      "--- a/.github/workflows/example.yml",
      "+++ b/.github/workflows/example.yml",
      "@@ -1,3 +1,5 @@",
      " permissions:",
      "-  contents: read",
      "+  contents: read",
      "+  pull-requests: write",
    ].join("\n"),
  };
}

function summarize(name, result) {
  const body = result.body || {};
  const suffix = body.ok
    ? `ok=true fallback=${Boolean(body.fallback)} decision=${body.governance?.publishDecision || "n/a"} summary="${String(body.summary || "").slice(0, 120)}"`
    : `ok=false error=${body.error?.code || "UNKNOWN"} message="${body.error?.message || "No JSON body"}"`;
  console.log(`${name}: HTTP ${result.status} ${suffix}`);
}

async function main() {
  loadDotEnv();
  const base = workerUrl();

  summarize("health", await requestJson(`${base}/health`));
  summarize("diff", await requestJson(`${base}/v1/analyze/diff`, {
    method: "POST",
    headers: headers(),
    body: JSON.stringify(payload("diff")),
  }));
  summarize("failure", await requestJson(`${base}/v1/analyze/failure`, {
    method: "POST",
    headers: headers(),
    body: JSON.stringify(payload("failure")),
  }));
}

main().catch((error) => {
  console.error(error.message);
  process.exit(1);
});

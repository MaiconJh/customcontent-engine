#!/usr/bin/env node
const fs = require("node:fs");
const { sanitizeString, sanitizeValue } = require("./sanitize-payload");

function arg(name, fallback) {
  const idx = process.argv.indexOf(name);
  return idx >= 0 ? process.argv[idx + 1] : fallback;
}

function fallbackMarkdown(type, payload, reason) {
  const safeReason = sanitizeString(reason || "unknown worker failure").slice(0, 240);
  if (type === "failure") {
    return `## CI Failure - build/test failed

AI analysis fell back to local reporting because Worker analysis did not complete.

### Fallback reason

${safeReason}

### Data

* Repository: ${payload.repository}
* Branch: ${payload.branch}
* Commit: ${payload.commit}
* Workflow: ${payload.workflow}
* Run URL: ${payload.run_url}

### Next steps

* Check the GitHub Actions build.log artifact.
* Fix the build/test failure.
* Re-run the GitHub Actions workflow.

## AI Governance Review

### Verdict
Fallback reporting was published because Worker analysis did not return a complete report.

### Publish Decision
fallback`;
  }
  return `## AI Technical Note - Worker analysis fallback

Worker analysis did not complete, so the workflow published a local fallback report. GitHub Actions build/test remains the source of truth.

### Fallback reason

${safeReason}

### Data

* Repository: ${payload.repository}
* Branch: ${payload.branch}
* Commit: ${payload.commit}
* Workflow: ${payload.workflow}
* Run URL: ${payload.run_url}

## AI Governance Review

### Verdict
Fallback reporting was published because Worker analysis did not return a complete report.

### Relevance
The note is relevant to the pushed commit and remote workflow run, but it is not a full AI review.

### Truthfulness Check
The fallback reason is based on the Worker call result observed by the workflow.

### Documentation Alignment
GitHub Actions remains the validation source of truth.

### Publish Decision
fallback`;
}

function fallbackResponse(type, payload, reason) {
  const markdown = fallbackMarkdown(type, payload, reason);
  return {
    ok: true,
    type,
    summary: "Worker analysis fallback",
    markdown,
    findings: [],
    fallback: true,
    fallbackUsed: true,
    fallbackReason: reason,
    finalReport: markdown,
  };
}

function safePreview(value) {
  return sanitizeString(String(value || "")).replace(/\s+/g, " ").slice(0, 500);
}

function responseKeys(value) {
  return value && typeof value === "object" ? Object.keys(value).join(",") : "";
}

function errorReason(error) {
  if (error?.name === "AbortError") return "network timeout";
  return `network error: ${sanitizeString(error?.message || "unknown").slice(0, 160)}`;
}

async function main() {
  const endpoint = (process.env.CI_AI_WORKER_URL || "").trim();
  const type = arg("--type", "diff");
  const inputFile = arg("--input-file", null);
  const outputFile = arg("--output-file", "ai-response.json");
  const contextFile = arg("--context-file", null);
  const ciLogsFile = arg("--ci-logs-file", null);
  const driftFile = arg("--drift-file", null);
  const contentField = type === "failure" ? "log" : "diff";
  const content = inputFile && fs.existsSync(inputFile) ? fs.readFileSync(inputFile, "utf8") : "";
  const projectContext = contextFile && fs.existsSync(contextFile)
    ? JSON.parse(fs.readFileSync(contextFile, "utf8")).files || []
    : [];
  const ciLogs = ciLogsFile && fs.existsSync(ciLogsFile)
    ? fs.readFileSync(ciLogsFile, "utf8")
    : type === "failure"
      ? content
      : `GitHub Actions build-test result: ${process.env.CI_BUILD_RESULT || "success"}. No failure log was produced.`;
  const aiContextPackDrift = driftFile && fs.existsSync(driftFile)
    ? JSON.parse(fs.readFileSync(driftFile, "utf8"))
    : undefined;
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
    ciLogs,
    projectContext,
    aiContextPackDrift,
    metadata: {
      actor: process.env.GITHUB_ACTOR || "",
      ref: process.env.GITHUB_REF || "",
      validationSource: "GitHub Actions",
    },
  });

  let response = null;
  let fallbackReason = "";
  const route = `/v1/analyze/${type}`;
  const timeoutMs = Number(process.env.CI_WORKER_TIMEOUT_MS || "45000");
  console.log(`Worker diagnostics: workerUrlConfigured=${Boolean(endpoint)}`);
  console.log(`Worker diagnostics: sharedSecretConfigured=${Boolean(process.env.CI_WORKER_SHARED_SECRET)}`);
  console.log(`Worker diagnostics: endpointPath=${route}`);
  console.log(`Worker diagnostics: timeoutMs=${timeoutMs}`);
  if (endpoint) {
    let timeout = null;
    try {
      const headers = { "Content-Type": "application/json" };
      if (process.env.CI_WORKER_SHARED_SECRET) headers["X-CI-Worker-Secret"] = process.env.CI_WORKER_SHARED_SECRET;
      const controller = new AbortController();
      timeout = setTimeout(() => controller.abort(), timeoutMs);
      const res = await fetch(`${endpoint.replace(/\/$/, "")}${route}`, {
        method: "POST",
        headers,
        body: JSON.stringify(payload),
        signal: controller.signal,
      });
      clearTimeout(timeout);
      const text = await res.text();
      const contentType = res.headers.get("content-type") || "";
      console.log(`Worker diagnostics: httpStatus=${res.status}`);
      console.log(`Worker diagnostics: contentType=${contentType}`);
      console.log(`Worker diagnostics: responsePreview=${safePreview(text)}`);
      try {
        response = text ? JSON.parse(text) : null;
        console.log("Worker diagnostics: jsonParsed=true");
      } catch {
        console.log("Worker diagnostics: jsonParsed=false");
        fallbackReason = "invalid JSON";
        response = null;
      }
      console.log(`Worker diagnostics: responseKeys=${responseKeys(response)}`);
      console.log(`Worker diagnostics: hasFinalReport=${Boolean(response?.finalReport)}`);
      console.log(`Worker diagnostics: hasMarkdown=${Boolean(response?.markdown)}`);
      console.log(`Worker diagnostics: fallbackUsed=${Boolean(response?.fallbackUsed || response?.fallback)}`);
      if (response?.fallbackReason) console.log(`Worker diagnostics: fallbackReason=${response.fallbackReason}`);
      if (!res.ok) {
        fallbackReason = `worker HTTP ${res.status}`;
        response = null;
      } else if (!response) {
        fallbackReason ||= text ? "invalid JSON" : "empty response";
      } else if (!response.ok) {
        fallbackReason = response.error?.code ? `worker error ${response.error.code}` : "worker returned ok=false";
        response = null;
      } else if (!response.markdown && !response.finalReport) {
        fallbackReason = "missing finalReport";
        response = null;
      } else if (!response.markdown && response.finalReport) {
        response.markdown = response.finalReport;
      }
    } catch (error) {
      fallbackReason = errorReason(error);
      console.log(`Worker diagnostics: requestFailed=${fallbackReason}`);
    } finally {
      if (timeout) clearTimeout(timeout);
    }
  } else {
    fallbackReason = "CI_AI_WORKER_URL not configured";
    console.log("Worker diagnostics: CI_AI_WORKER_URL is not configured.");
  }

  if (!response) {
    response = fallbackResponse(type, payload, fallbackReason || "missing finalReport");
  }

  fs.writeFileSync(outputFile, JSON.stringify(response, null, 2));
  fs.writeFileSync(arg("--markdown-file", "ai-response.md"), response.markdown || response.finalReport || fallbackMarkdown(type, payload, response.fallbackReason));
  fs.writeFileSync(arg("--findings-file", "ai-findings.json"), JSON.stringify(response.findings || [], null, 2));
  console.log(`Worker diagnostics: outputFile=${outputFile}`);
  console.log(`Worker diagnostics: markdownFile=${arg("--markdown-file", "ai-response.md")}`);
  console.log(`Worker diagnostics: finalReportLength=${String(response.finalReport || response.markdown || "").length}`);
  console.log(`Worker diagnostics: publishDecision=${response.governanceReview?.publishDecision || response.publishDecision || "unknown"}`);
}

main().catch((error) => {
  console.error(error.message);
  process.exit(1);
});

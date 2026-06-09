#!/usr/bin/env node
const fs = require("node:fs");
const { sanitizeString, sanitizeValue } = require("./sanitize-payload");

function arg(name, fallback) {
  const idx = process.argv.indexOf(name);
  return idx >= 0 ? process.argv[idx + 1] : fallback;
}

function fallbackMarkdown(type, payload, reason) {
  const safeReason = sanitizeString(reason || "unknown worker failure").slice(0, 240);
  const failureText = "Worker analysis did not complete within the timeout. Local fallback was used. No full AI review was produced.";
  const failureHeading = type === "failure" ? "CI Failure - build/test failed" : "AI Technical Note - Worker analysis fallback";

  let dataBullets = `* Repository: ${payload.repository}
* Branch: ${payload.branch}
* Commit: ${payload.commit}
* Workflow: ${payload.workflow}
* Run URL: ${payload.run_url}`;
  if (type !== "failure") {
    dataBullets += `
* GitHub Actions build-test: ${process.env.CI_BUILD_RESULT || "success"}`;
  }

  let postData = "";
  if (type === "failure") {
    postData = `### Next steps

* Check the GitHub Actions build.log artifact.
* Fix the build/test failure.
* Re-run the GitHub Actions workflow.`;
  } else {
    const classification = classifyDiff(payload.diff || "");
    const categories = formatCategories(classification);
    const focus = reviewFocus(classification);
    const risk = fallbackRisk(classification);
    postData = `### Changed file categories

${categories}

### Local fallback analysis

${risk}

Review focus:

${focus}`;
  }

  return `## ${failureHeading}

${failureText}

### Fallback reason

${safeReason}

## Data

${dataBullets}

${postData}

## AI Governance Review

### Verdict
Local fallback was used because the Worker analysis did not return a complete report within the timeout.

### Relevance
This fallback note is relevant to the pushed commit and workflow run, but it is not a full AI review.

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

function classifyDiff(diff) {
  const files = changedFiles(diff);
  const known = new Set();
  const has = (predicate) => {
    const matched = files.filter(predicate);
    matched.forEach((file) => known.add(file));
    return matched.length > 0;
  };
  const classification = {
    javaProduction: has((file) => file.startsWith("src/main/java/")),
    javaTests: has((file) => file.startsWith("src/test/") || file.startsWith("src/integrationTest/") || file.startsWith("src/spike/")),
    docs: has((file) => file.startsWith("docs/") || file === "README.md"),
    workflows: has((file) => file.startsWith(".github/workflows/")),
    ciScripts: has((file) => file.startsWith("scripts/ci/")),
    worker: has((file) => file.startsWith("cloudflare/ci-ai-worker/")),
    configResources: has((file) =>
      file.startsWith("src/main/resources/")
      || file.startsWith(".github/ai-review/")
      || /^build\.gradle(\.kts)?$/.test(file)
      || file === "settings.gradle.kts"
      || file.endsWith("wrangler.jsonc")
      || file.endsWith(".yml")
      || file.endsWith(".yaml")),
  };
  classification.unknown = files.some((file) => !known.has(file));
  return classification;
}

function changedFiles(diff) {
  const files = new Set();
  for (const match of String(diff || "").matchAll(/^diff --git a\/(.+?) b\/(.+)$/gm)) {
    files.add(normalizePath(match[2]));
  }
  for (const match of String(diff || "").matchAll(/^\+\+\+ b\/(.+)$/gm)) {
    if (match[1] !== "/dev/null") files.add(normalizePath(match[1]));
  }
  return [...files].filter(Boolean).sort();
}

function normalizePath(file) {
  return String(file || "").replace(/\\/g, "/").replace(/^\.\//, "");
}

function fileCategory(file) {
  const normalized = normalizePath(file);
  if (normalized === "docs/AI_CONTEXT_PACK.md" || normalized === "docs/PROJECT_SCOPE.md" || normalized === "docs/ARCHITECTURE_GUARDRAILS.md") {
    return 0;
  }
  if (/^docs\/adr\//.test(normalized)) return 1;
  if (/^docs\/milestones\//.test(normalized)) return 1;
  if (normalized.startsWith(".github/workflows/")) return 4;
  if (/^(build\.gradle\.kts|settings\.gradle\.kts|README\.md|src\/main\/resources\/(plugin\.yml|definitions\.yml))$/.test(normalized)) return 3;
  return 2;
}

function shrinkFileContent(file, allowed) {
  if (!file || typeof file !== "object") return file;
  if (!file.content || typeof file.content !== "string") return file;
  const next = { ...file };
  if (String(next.content).length > allowed) {
    next.content = `${next.content.slice(0, allowed)}\n[TRUNCATED: project context file exceeded limit]`;
    next.truncated = true;
  }
  return next;
}

function shrinkProjectContext(files, budget) {
  if (!budget || budget <= 0 || !files.length) return files;
  if (files.reduce((sum, file) => sum + String(file.content || "").length, 0) <= budget) return files;

  const primary = files.filter((file) => fileCategory(file.path) === 0).slice(0, 2);
  const primaryBudget = Math.floor(budget * 0.6);
  let summarized = primary.map((file) => {
    const maxAllowed = fileCategory(file.path) === 0 ? primaryBudget : Math.floor(primaryBudget * 0.35);
    return shrinkFileContent(file, Math.min(String(file.content || "").length, maxAllowed));
  }).filter((file) => file.content && String(file.content).length > 0);

  let remaining = budget;
  for (const file of summarized) {
    remaining -= String(file.content || "").length;
  }
  if (remaining <= 0) return summarized;

  const fallback = files.filter((file) => !primary.includes(file)).sort((a, b) => fileCategory(a.path) - fileCategory(b.path));
  const shrunkFallback = [];
  for (const file of fallback) {
    const allowed = fileCategory(file.path) >= 3 ? Math.min(3000, remaining) : Math.min(6000, remaining);
    const next = shrinkFileContent(file, allowed);
    if (next.content && String(next.content).length > 0) {
      shrunkFallback.push(next);
      remaining -= String(next.content || "").length;
    }
    if (remaining <= 500) break;
  }

  return [...summarized, ...shrunkFallback];
}

async function main() {
  const endpoint = (process.env.CI_AI_WORKER_URL || "").trim();
  const type = arg("--type", "diff");
  const inputFile = arg("--input-file", null);
  const outputFile = arg("--output-file", "ai-response.json");
  const contextFile = arg("--context-file", null);
  const ciLogsFile = arg("--ci-logs-file", null);
  const driftFile = arg("--drift-file", null);
  const singleProviderCall = (process.env.CI_AI_SINGLE_PROVIDER_CALL || "").toLowerCase() === "true";
  const contentField = type === "failure" ? "log" : "diff";
  const content = inputFile && fs.existsSync(inputFile) ? fs.readFileSync(inputFile, "utf8") : "";
  let projectContext = contextFile && fs.existsSync(contextFile)
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

  const maxPayloadChars = Number(process.env.CI_AI_MAX_PAYLOAD_CHARS || "90000");
  const diffChars = String(content).length;
  const ciLogsChars = String(ciLogs).length;
  const totalWithoutContext = 5000 + diffChars + ciLogsChars;
  let contextBudget = Math.max(5000, maxPayloadChars - totalWithoutContext);
  if (contextBudget < 5000) {
    contextBudget = 5000;
  }

  const contextInfo = { files: projectContext.length, chars: projectContext.reduce((sum, file) => sum + String(file.content || "").length, 0) };
  projectContext = shrinkProjectContext(projectContext, contextBudget);
  const totalApproxPayloadChars = totalWithoutContext + projectContext.reduce((sum, file) => sum + String(file.content || "").length, 0);
  console.log(`Worker diagnostics: diffChars=${diffChars}`);
  console.log(`Worker diagnostics: ciLogsChars=${ciLogsChars}`);
  console.log(`Worker diagnostics: projectContextBefore=${JSON.stringify(contextInfo).slice(0, 200)}`);
  console.log(`Worker diagnostics: projectContextAfter=${JSON.stringify({ files: projectContext.length, chars: projectContext.reduce((sum, file) => sum + String(file.content || "").length, 0) }).slice(0, 200)}`);
  console.log(`Worker diagnostics: totalApproxPayloadChars=${totalApproxPayloadChars}`);
  console.log(`Worker diagnostics: maxPayloadChars=${maxPayloadChars}`);
  console.log(`Worker diagnostics: singleProviderCall=${singleProviderCall}`);

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
      singleProviderCall,
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

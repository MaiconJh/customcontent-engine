#!/usr/bin/env node
const fs = require("node:fs");
const path = require("node:path");
const { sanitizeValue } = require("./sanitize-payload");

const DEFAULT_MAX_TOTAL_CHARS = 80000;
const DEFAULT_MAX_FILE_CHARS = 12000;

const TIER_MAX_FILE_CHARS = Object.freeze({
  "docs/AI_CONTEXT_PACK.md": 0.45,
  "docs/PROJECT_SCOPE.md": 0.20,
  "docs/ARCHITECTURE_GUARDRAILS.md": 0.20,
});

const TIER_ADR_MILESTONE_MAX = 5000;
const TIER_FALLBACK_MAX = 3000;
const TIER_STOP_THRESHOLD = 2000;

function arg(name, fallback) {
  const idx = process.argv.indexOf(name);
  return idx >= 0 ? process.argv[idx + 1] : fallback;
}

function normalizePath(file) {
  return file.replace(/\\/g, "/").replace(/^\.\//, "");
}

function isIgnoredByPolicy(file) {
  const normalized = normalizePath(file);
  return normalized === ".env"
    || normalized.startsWith(".env.")
    || normalized === ".dev.vars"
    || normalized.includes("/secrets/")
    || normalized.includes("/secret/")
    || /(^|\/)secrets?\./i.test(normalized)
    || normalized.startsWith("node_modules/")
    || normalized.includes("/node_modules/")
    || normalized.startsWith("build/")
    || normalized.startsWith(".gradle/")
    || normalized.startsWith(".kilo/")
    || normalized.startsWith(".wrangler/")
    || normalized.startsWith(".vscode/");
}

function candidates() {
  const primaryDocs = [
    "docs/AI_CONTEXT_PACK.md",
    "docs/PROJECT_SCOPE.md",
    "docs/ARCHITECTURE_GUARDRAILS.md",
  ];
  const generatedDocs = [
    ...listMarkdown("docs/adr"),
    ...listMarkdown("docs/milestones"),
  ];
  const supportingFiles = [
    "README.md",
    "src/main/resources/plugin.yml",
    "src/main/resources/definitions.yml",
    "build.gradle.kts",
    "settings.gradle.kts",
    ".github/workflows/build-test.yml",
    ".github/workflows/ci-ai-review.yml",
  ];
  return [...primaryDocs, ...generatedDocs, ...supportingFiles]
    .map((file, index) => ({ file: normalizePath(file), index }))
    .filter(({ file }, _idx, all) => all.find((item) => item.file === file)?.index === idx)
    .filter(({ file }) => !isIgnoredByPolicy(file) && fs.existsSync(file) && fs.statSync(file).isFile() && !isBinaryFile(file))
    .map(({ file }) => file);
}

const FILE_CATEGORY_PRIORITY = Object.freeze({
  primaryDoc: 0,
  adr: 1,
  milestone: 1,
  sourceDoc: 2,
  supporting: 3,
  configWorkflow: 4,
});

function fileCategory(file) {
  const normalized = normalizePath(file);
  if (normalized === "docs/AI_CONTEXT_PACK.md" || normalized === "docs/PROJECT_SCOPE.md" || normalized === "docs/ARCHITECTURE_GUARDRAILS.md") {
    return FILE_CATEGORY_PRIORITY.primaryDoc;
  }
  if (/^docs\/adr\//.test(normalized)) {
    return FILE_CATEGORY_PRIORITY.adr;
  }
  if (/^docs\/milestones\//.test(normalized)) {
    return FILE_CATEGORY_PRIORITY.milestone;
  }
  if (normalized.startsWith(".github/workflows/")) {
    return FILE_CATEGORY_PRIORITY.configWorkflow;
  }
  if (/^(build\.gradle\.kts|settings\.gradle\.kts|README\.md|src\/main\/resources\/(plugin\.yml|definitions\.yml))$/.test(normalized)) {
    return FILE_CATEGORY_PRIORITY.supporting;
  }
  return FILE_CATEGORY_PRIORITY.sourceDoc;
}

function fileTier(file, remaining) {
  const normalized = normalizePath(file);
  if (TIER_MAX_FILE_CHARS[normalized]) {
    return Math.max(remaining * TIER_MAX_FILE_CHARS[normalized], TIER_STOP_THRESHOLD);
  }
  const category = fileCategory(normalized);
  if (category === FILE_CATEGORY_PRIORITY.adr || category === FILE_CATEGORY_PRIORITY.milestone) {
    return Math.min(TIER_ADR_MILESTONE_MAX, remaining);
  }
  if (category === FILE_CATEGORY_PRIORITY.configWorkflow || category === FILE_CATEGORY_PRIORITY.supporting) {
    return TIER_FALLBACK_MAX;
  }
  return Math.min(DEFAULT_MAX_FILE_CHARS, remaining);
}

function listMarkdown(dir) {
  if (!fs.existsSync(dir)) return [];
  return fs.readdirSync(dir)
    .filter((name) => name.endsWith(".md"))
    .sort()
    .map((name) => normalizePath(path.join(dir, name)));
}

function collect() {
  const maxTotalChars = Number(arg("--max-total-chars", process.env.MAX_PROJECT_CONTEXT_CHARS || DEFAULT_MAX_TOTAL_CHARS));
  const maxFileChars = Number(arg("--max-file-chars", process.env.MAX_PROJECT_CONTEXT_FILE_CHARS || DEFAULT_MAX_FILE_CHARS));
  const files = [];
  let remaining = maxTotalChars;

  for (const file of candidates()) {
    if (remaining <= 0) break;
    let content = fs.readFileSync(file, "utf8");
    let truncated = false;
    const allowed = Math.min(fileTier(file, remaining), maxTotalChars);
    if (content.length > allowed) {
      content = `${content.slice(0, allowed)}\n[TRUNCATED: project context file exceeded limit]`;
      truncated = true;
    }
    const safe = sanitizeValue(content);
    files.push({ path: file, content: safe, truncated });
    console.log(`Context collected: path=${file} chars=${String(safe).length} truncated=${truncated} category=${fileCategory(file)}`);
    remaining -= String(safe).length;
    if (String(safe).length <= TIER_STOP_THRESHOLD && fileCategory(file) >= FILE_CATEGORY_PRIORITY.adr) {
      remaining = 0;
    }
  }

  return {
    generatedBy: "scripts/ci/collect-project-context.js",
    maxTotalChars,
    files,
  };
}

function isBinaryFile(file) {
  const buffer = fs.readFileSync(file);
  if (buffer.includes(0)) return true;
  const sample = buffer.subarray(0, Math.min(buffer.length, 8000));
  let suspicious = 0;
  for (const byte of sample) {
    if (byte < 7 || (byte > 14 && byte < 32)) suspicious += 1;
  }
  return sample.length > 0 && suspicious / sample.length > 0.3;
}

function main() {
  const output = arg("--output", "project-context.json");
  fs.writeFileSync(output, JSON.stringify(collect(), null, 2));
  console.log(`Collected project context into ${output}.`);
}

if (require.main === module) main();

module.exports = { collect };

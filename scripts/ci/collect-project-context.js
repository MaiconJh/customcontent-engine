#!/usr/bin/env node
const fs = require("node:fs");
const path = require("node:path");
const { sanitizeValue } = require("./sanitize-payload");

const DEFAULT_MAX_TOTAL_CHARS = 80000;
const DEFAULT_MAX_FILE_CHARS = 12000;

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
  const staticFiles = [
    "docs/PROJECT_SCOPE.md",
    "docs/ARCHITECTURE_GUARDRAILS.md",
    "README.md",
    "src/main/resources/plugin.yml",
    "src/main/resources/definitions.yml",
    ".github/workflows/build-test.yml",
    ".github/workflows/ci-ai-review.yml",
    "build.gradle.kts",
    "settings.gradle.kts",
  ];
  const globbed = [
    ...listMarkdown("docs/adr"),
    ...listMarkdown("docs/milestones"),
  ];
  return [...staticFiles, ...globbed]
    .map(normalizePath)
    .filter((file, index, all) => all.indexOf(file) === index)
    .filter((file) => !isIgnoredByPolicy(file) && fs.existsSync(file) && fs.statSync(file).isFile() && !isBinaryFile(file));
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
    const allowed = Math.min(maxFileChars, remaining);
    if (content.length > allowed) {
      content = `${content.slice(0, allowed)}\n[TRUNCATED: project context file exceeded limit]`;
      truncated = true;
    }
    const safe = sanitizeValue(content);
    files.push({ path: file, content: safe, truncated });
    remaining -= String(safe).length;
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

#!/usr/bin/env node
const fs = require("node:fs");
const { execSync } = require("node:child_process");

const SOURCE_DOC_PATTERNS = [
  /^docs\/PROJECT_SCOPE\.md$/,
  /^docs\/ARCHITECTURE_GUARDRAILS\.md$/,
  /^docs\/adr\/[^/]+\.md$/,
  /^docs\/milestones\/[^/]+\.md$/,
];
const CONTEXT_PACK = "docs/AI_CONTEXT_PACK.md";

function arg(index) {
  return process.argv[index] || "";
}

function normalize(file) {
  return file.trim().replace(/\\/g, "/").replace(/^\.\//, "");
}

function changedFiles() {
  const fileArg = arg(2);
  if (fileArg && fs.existsSync(fileArg)) {
    return splitList(fs.readFileSync(fileArg, "utf8"));
  }
  if (process.env.CHANGED_FILES) {
    return splitList(process.env.CHANGED_FILES);
  }
  try {
    return splitList(execSync("git diff --name-only HEAD~1 HEAD", { encoding: "utf8", stdio: ["ignore", "pipe", "ignore"] }));
  } catch {
    try {
      return splitList(execSync("git diff --name-only", { encoding: "utf8", stdio: ["ignore", "pipe", "ignore"] }));
    } catch {
      return [];
    }
  }
}

function splitList(value) {
  return value
    .split(/[\n,;]/)
    .map(normalize)
    .filter(Boolean)
    .filter((file, index, all) => all.indexOf(file) === index);
}

function isSourceDoc(file) {
  return SOURCE_DOC_PATTERNS.some((pattern) => pattern.test(file));
}

function evaluate(files) {
  const changedSourceDocs = files.filter(isSourceDoc);
  const contextPackChanged = files.includes(CONTEXT_PACK);

  if (changedSourceDocs.length && !contextPackChanged) {
    return {
      ok: true,
      driftRisk: true,
      message: "Source-of-truth documentation changed without AI_CONTEXT_PACK.md update.",
      changedSourceDocs,
    };
  }
  if (changedSourceDocs.length && contextPackChanged) {
    return {
      ok: true,
      driftRisk: false,
      message: "AI_CONTEXT_PACK.md was updated with source documentation changes.",
      changedSourceDocs,
    };
  }
  return {
    ok: true,
    driftRisk: false,
    message: "No source documentation changes detected.",
    changedSourceDocs: [],
  };
}

function main() {
  process.stdout.write(`${JSON.stringify(evaluate(changedFiles()), null, 2)}\n`);
}

if (require.main === module) main();

module.exports = { evaluate, changedFiles };

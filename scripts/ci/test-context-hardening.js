#!/usr/bin/env node
const assert = require("node:assert");
const { fileCategory, shrinkFileContent, shrinkProjectContext } = require("./collect-project-content-hardener");

function run(name, fn) {
  try {
    fn();
    console.log(`pass ${name}`);
  } catch (error) {
    console.log(`fail ${name}: ${error && error.message}`);
    process.exitCode = 1;
  }
}

run("fileCategory returns 0 for primary docs", () => {
  assert.strictEqual(fileCategory("docs/AI_CONTEXT_PACK.md"), 0);
  assert.strictEqual(fileCategory("docs/PROJECT_SCOPE.md"), 0);
  assert.strictEqual(fileCategory("docs/ARCHITECTURE_GUARDRAILS.md"), 0);
});

run("fileCategory returns 1 for adr/milestones", () => {
  assert.strictEqual(fileCategory("docs/adr/001-test.md"), 1);
  assert.strictEqual(fileCategory("docs/milestones/v1.md"), 1);
});

run("fileCategory returns 4 for workflows and 3 for build files", () => {
  assert.strictEqual(fileCategory(".github/workflows/ci.yml"), 4);
  assert.strictEqual(fileCategory("build.gradle.kts"), 3);
});

run("shrinkFileContent truncates oversized files", () => {
  const result = shrinkFileContent({ path: "docs/AI_CONTEXT_PACK.md", content: "a".repeat(50) }, 20);
  assert.strictEqual(result.content, "a".repeat(20) + "\n[TRUNCATED: project context file exceeded limit]");
  assert.strictEqual(result.truncated, true);
});

run("shrinkProjectContext preserves primary docs first", () => {
  const files = [
    { path: "docs/ARCHITECTURE_GUARDRAILS.md", content: "a".repeat(100), truncated: false },
    { path: "docs/AI_CONTEXT_PACK.md", content: "b".repeat(100), truncated: false },
    { path: "docs/adr/001.md", content: "c".repeat(100), truncated: false },
    { path: "build.gradle.kts", content: "d".repeat(100), truncated: false },
  ];
  const result = shrinkProjectContext(files, 150);
  const paths = result.map((f) => f.path);
  assert.ok(paths.includes("docs/AI_CONTEXT_PACK.md"), "expected AI_CONTEXT_PACK.md to be preserved");
  assert.ok(paths.includes("docs/ARCHITECTURE_GUARDRAILS.md"), "expected ARCHITECTURE_GUARDRAILS.md to be preserved");
  assert.ok(paths.includes("docs/adr/001.md"), "expected adr to be preserved or truncated");
  const adr = result.find((f) => f.path === "docs/adr/001.md");
  if (adr) assert.ok(String(adr.content || "").length <= 100, "expected adr content to be capped");
});

run("shrinkProjectContext drops lower priority when budget is tight", () => {
  const files = [
    { path: "docs/AI_CONTEXT_PACK.md", content: "a".repeat(70), truncated: false },
    { path: "docs/PROJECT_SCOPE.md", content: "b".repeat(70), truncated: false },
    { path: "build.gradle.kts", content: "c".repeat(10), truncated: false },
  ];
  const result = shrinkProjectContext(files, 80);
  assert.ok(result.every((f) => f.path !== "build.gradle.kts"), "expected build.gradle.kts to be dropped");
});

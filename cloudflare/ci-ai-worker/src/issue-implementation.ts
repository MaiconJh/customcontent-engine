import type { Env, ProjectContextFile } from "./types";
import { buildCompactImplementIssueInput } from "./compact-provider-input";
import { callKiloChatCompletion } from "./providers/kilo";
import { sanitizeText } from "./sanitizer";

export interface ImplementIssuePayload {
  repository: string;
  issueNumber: number;
  issueTitle: string;
  issueBody: string;
  issueLabels: string[];
  issueUrl?: string;
  approvedPlanComment: string;
  planningArtifactContent: string;
  planningArtifactPath?: string;
  projectContext?: ProjectContextFile[];
  maxFilesChanged: number;
  maxDiffLines: number;
  dryRun: boolean;
}

export interface FileEdit {
  path: string;
  content: string;
}

export interface ImplementIssueResponse {
  ok: true;
  summary: string;
  proposedFiles: string[];
  fileEdits: FileEdit[];
  safetyNotes: string[];
  validationNotes: string[];
  fallbackUsed: boolean;
  fallbackReason?: string;
}

const FORBIDDEN_FILE_PATTERNS = [
  /^\.github\/workflows\/build-test\.yml$/,
  /^\.github\/workflows\//,
  /^gradle\//,
  /^gradlew$/,
  /^gradlew\.bat$/,
  /^settings\.gradle\.kts$/,
  /^build\.gradle\.kts$/,
  /^docs\/PROJECT_SCOPE\.md$/,
  /^docs\/ARCHITECTURE_GUARDRAILS\.md$/,
  /^docs\/adr\//,
  /^docs\/milestones\//,
  /^cloudflare\//,
  /^scripts\/ci\//,
];

const ALLOWED_FILE_PATTERNS = [
  /^src\/main\/java\//,
  /^src\/main\/resources\//,
  /^src\/test\/java\//,
  /^src\/integrationTest\//,
  /^docs\/ai-implementation-notes\//,
];

const OUT_OF_SCOPE_PATTERNS: Array<[string, RegExp]> = [
  ["economy system", /\b(economy|currency|money|shop|marketplace|balance|wallet)\b/i],
  ["quest system", /\b(quest|quests|questline)\b/i],
  ["generic combat system", /\b(generic combat|combat system|damage engine|weapon framework|pvp framework)\b/i],
  ["GUI/menu framework", /\b(gui framework|menu framework|inventory gui|generic menu|screen framework)\b/i],
  ["scripting language", /\b(scripting language|script engine|embedded script|lua|groovy scripts?)\b/i],
  ["generic ability framework", /\b(generic ability|ability framework|skill framework|spell framework)\b/i],
  ["land protection", /\b(land protection|claim protection|worldguard|griefprevention|grief prevention|region protection)\b/i],
  ["NMS/reflection", /\b(NMS|net\.minecraft|reflection|reflective access)\b/i],
  ["Folia support declaration", /\b(folia-supported\s*:\s*true|declare folia support)\b/i],
];

export async function proposeIssueImplementation(payload: ImplementIssuePayload, env: Env): Promise<ImplementIssueResponse> {
  const scopeRisk = outOfScopeRisks(payload).map((risk) => `Out-of-scope guardrail: ${risk}.`);
  if (scopeRisk.length) {
    return localImplementationFallback(payload, "approved issue plan failed scope guardrails", scopeRisk);
  }

  const prompt = buildCompactImplementIssueInput(payload, env);
  if (!prompt.withinLimit) {
    return localImplementationFallback(payload, `compact provider input exceeded ${prompt.endpoint} limit`, scopeRisk);
  }
  const provider = await callKiloChatCompletion(env, prompt.system, prompt.user);
  if (!provider.ok || !provider.text) {
    return localImplementationFallback(payload, provider.error || "empty response", scopeRisk);
  }

  const parsed = parseProviderJson(provider.text);
  if (!parsed) {
    return localImplementationFallback(payload, "provider response was not valid implementation JSON", scopeRisk);
  }

  const response = normalizeImplementationResponse(parsed, payload, scopeRisk);
  const unsafeReason = unsafeResponseReason(response, payload);
  if (unsafeReason) {
    return localImplementationFallback(payload, unsafeReason, [...scopeRisk, unsafeReason]);
  }

  return response;
}

function localImplementationFallback(payload: ImplementIssuePayload, reason: string, extraSafetyNotes: string[] = []): ImplementIssueResponse {
  return {
    ok: true,
    summary: `No implementation file edits were proposed automatically for issue #${payload.issueNumber}. A draft PR may still record implementation notes for human review.`,
    proposedFiles: [],
    fileEdits: [],
    safetyNotes: [
      ...extraSafetyNotes,
      "Local fallback did not infer safe implementation edits from the approved plan.",
      "A human maintainer must review the approved plan before any implementation work is merged.",
    ],
    validationNotes: [
      "GitHub Actions build/test/integrationTest is required for any later implementation changes.",
      "CI AI Governance Bot must review the draft implementation PR.",
      "No local Gradle or Java validation is required from this Worker response.",
    ],
    fallbackUsed: true,
    fallbackReason: sanitizeInline(reason),
  };
}

function parseProviderJson(text: string): unknown {
  const cleaned = text.trim().replace(/^```(?:json)?\s*/i, "").replace(/\s*```$/i, "");
  const objectText = cleaned.match(/\{[\s\S]*\}/)?.[0] || cleaned;
  try {
    return JSON.parse(objectText);
  } catch {
    return null;
  }
}

function normalizeImplementationResponse(value: unknown, payload: ImplementIssuePayload, safetyNotes: string[]): ImplementIssueResponse {
  const raw = value && typeof value === "object" ? value as Record<string, unknown> : {};
  const edits = Array.isArray(raw.fileEdits)
    ? raw.fileEdits
      .filter((edit): edit is Record<string, unknown> => Boolean(edit) && typeof edit === "object")
      .map((edit) => ({
        path: normalizePath(String(edit.path || "")),
        content: sanitizeText(String(edit.content || ""), 30000),
      }))
      .filter((edit) => edit.path && typeof edit.content === "string")
      .slice(0, Math.max(0, payload.maxFilesChanged))
    : [];
  const proposedFiles = Array.isArray(raw.proposedFiles)
    ? raw.proposedFiles.map((file) => normalizePath(String(file))).filter(Boolean)
    : edits.map((edit) => edit.path);

  return {
    ok: true,
    summary: sanitizeInline(String(raw.summary || `Draft implementation proposal for issue #${payload.issueNumber}.`)),
    proposedFiles: [...new Set([...proposedFiles, ...edits.map((edit) => edit.path)])],
    fileEdits: edits,
    safetyNotes: [
      ...safetyNotes,
      ...stringArray(raw.safetyNotes),
      "This is an AI draft implementation proposal and requires human review.",
    ],
    validationNotes: [
      ...stringArray(raw.validationNotes),
      "GitHub Actions build/test/integrationTest",
      "CI AI Governance Bot",
    ].filter((item, index, all) => all.indexOf(item) === index),
    fallbackUsed: false,
  };
}

function unsafeResponseReason(response: ImplementIssueResponse, payload: ImplementIssuePayload): string {
  if (response.fileEdits.length > payload.maxFilesChanged) return "provider proposed too many file edits";
  if (estimatedDiffLines(response.fileEdits) > payload.maxDiffLines) return "provider proposed a diff larger than the configured limit";
  for (const edit of response.fileEdits) {
    if (!isAllowedPath(edit.path, payload)) return `provider proposed forbidden file edit: ${edit.path}`;
    const contentRisk = unsafeContentReason(edit.path, edit.content);
    if (contentRisk) return contentRisk;
  }
  return "";
}

function isAllowedPath(file: string, payload: ImplementIssuePayload): boolean {
  const normalized = normalizePath(file);
  if (FORBIDDEN_FILE_PATTERNS.some((pattern) => pattern.test(normalized))) return false;
  if (normalized === "README.md") return /\bREADME\.md\b/i.test(payload.approvedPlanComment + payload.planningArtifactContent);
  return ALLOWED_FILE_PATTERNS.some((pattern) => pattern.test(normalized));
}

function unsafeContentReason(file: string, content: string): string {
  const normalized = normalizePath(file);
  if (/folia-supported\s*:\s*true/i.test(content)) return "provider proposed folia-supported true without validation";
  if (/\b(net\.minecraft|java\.lang\.reflect|Class\.forName|getDeclaredMethod|getDeclaredField|ServiceLoader)\b/i.test(content)) return "provider proposed NMS, reflection, or ServiceLoader usage";
  if (/\b(runAsync|runOnEntity|SchedulerAccess)\b/i.test(content)) return "provider proposed forbidden scheduler access";
  if (/\b(getWorlds|getLoadedChunks|getOnlinePlayers)\s*\(/i.test(content)) return "provider proposed global runtime scans";
  if (/^src\/main\/java\/.*\/domain\//.test(normalized) && /\b(org\.bukkit|io\.papermc|net\.minecraft|PersistentDataContainer|YamlConfiguration)\b/i.test(content)) {
    return "provider proposed platform dependency in domain code";
  }
  if (/^src\/main\/java\/.*\/application\//.test(normalized) && /\b(org\.bukkit|io\.papermc|net\.minecraft|Folia)\b/i.test(content)) {
    return "provider proposed platform dependency in application code";
  }
  return "";
}

function outOfScopeRisks(payload: ImplementIssuePayload): string[] {
  const text = `${payload.issueTitle}\n${payload.issueBody}\n${payload.approvedPlanComment}\n${payload.planningArtifactContent}`
    .split(/\r?\n/)
    .filter((line) => !/\b(do not|must not|no |non-goals?|free from|without|forbidden|guardrail|objective only)\b/i.test(line))
    .join("\n");
  return OUT_OF_SCOPE_PATTERNS
    .filter(([, pattern]) => pattern.test(text))
    .map(([label]) => label);
}

function estimatedDiffLines(edits: FileEdit[]): number {
  return edits.reduce((sum, edit) => sum + edit.content.split(/\r?\n/).length, 0);
}

function stringArray(value: unknown): string[] {
  return Array.isArray(value) ? value.map((item) => sanitizeInline(String(item))).filter(Boolean).slice(0, 20) : [];
}

function sanitizeInline(value: string): string {
  return sanitizeText(value || "", 800).replace(/\s+/g, " ").trim();
}

function normalizePath(file: string): string {
  return file.replace(/\\/g, "/").replace(/^\.\//, "");
}

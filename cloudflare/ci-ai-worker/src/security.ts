import type { AnalyzePayload, Env, GovernancePayload, ProjectContextFile } from "./types";

const EVENTS = new Set(["push", "pull_request", "workflow_dispatch"]);
const TYPES = new Set(["failure", "diff", "governance"]);

export function maxBodyBytes(env: Env): number {
  return Number(env.MAX_BODY_BYTES || "120000");
}

export function maxModelInputChars(env: Env): number {
  return Number(env.MAX_MODEL_INPUT_CHARS || "50000");
}

export async function readJsonBody(request: Request, env: Env): Promise<unknown> {
  if (!request.headers.get("content-type")?.toLowerCase().includes("application/json")) {
    throw new SecurityError("BAD_REQUEST", "Content-Type must be application/json.", 400);
  }
  const text = await request.text();
  if (new TextEncoder().encode(text).byteLength > maxBodyBytes(env)) {
    throw new SecurityError("PAYLOAD_TOO_LARGE", "Payload is too large.", 413);
  }
  try {
    return JSON.parse(text);
  } catch {
    throw new SecurityError("BAD_REQUEST", "Request body must be valid JSON.", 400);
  }
}

export function validatePayload(value: unknown, expectedType: "failure" | "diff", env: Env): AnalyzePayload {
  if (!value || typeof value !== "object") throw new SecurityError("BAD_REQUEST", "Payload must be an object.", 400);
  const payload = value as Record<string, unknown>;
  if (payload.type !== expectedType || !TYPES.has(String(payload.type))) throw new SecurityError("BAD_REQUEST", "Invalid analysis type.", 400);
  if (!EVENTS.has(String(payload.event))) throw new SecurityError("BAD_REQUEST", "Invalid event.", 400);
  if (!isRepositoryAllowed(String(payload.repository || ""), env)) throw new SecurityError("FORBIDDEN", "Repository is not allowed.", 403);

  for (const key of ["repository", "branch", "commit", "workflow", "run_id", "run_url"]) {
    if (typeof payload[key] !== "string") throw new SecurityError("BAD_REQUEST", `${key} is required.`, 400);
  }
  if (expectedType === "failure" && typeof payload.log !== "string") throw new SecurityError("BAD_REQUEST", "log is required.", 400);
  if (expectedType === "diff" && typeof payload.diff !== "string") throw new SecurityError("BAD_REQUEST", "diff is required.", 400);
  if (payload.ciLogs !== undefined && typeof payload.ciLogs !== "string") throw new SecurityError("BAD_REQUEST", "ciLogs must be a string.", 400);
  if (payload.projectContext !== undefined && !isValidProjectContext(payload.projectContext)) {
    throw new SecurityError("BAD_REQUEST", "projectContext must be an array of path/content objects.", 400);
  }
  if (payload.aiContextPackDrift !== undefined && !isValidDriftSignal(payload.aiContextPackDrift)) {
    throw new SecurityError("BAD_REQUEST", "aiContextPackDrift must be a drift signal object.", 400);
  }
  return payload as unknown as AnalyzePayload;
}

export function validateGovernancePayload(value: unknown, env: Env): GovernancePayload {
  if (!value || typeof value !== "object") throw new SecurityError("BAD_REQUEST", "Payload must be an object.", 400);
  const payload = value as Record<string, unknown>;
  if (payload.type !== "governance") throw new SecurityError("BAD_REQUEST", "Invalid analysis type.", 400);
  if (!EVENTS.has(String(payload.event))) throw new SecurityError("BAD_REQUEST", "Invalid event.", 400);
  if (!isRepositoryAllowed(String(payload.repository || ""), env)) throw new SecurityError("FORBIDDEN", "Repository is not allowed.", 403);
  for (const key of ["repository", "branch", "commit", "workflow", "run_id", "run_url", "initialReport"]) {
    if (typeof payload[key] !== "string") throw new SecurityError("BAD_REQUEST", `${key} is required.`, 400);
  }
  if (payload.ciLogs !== undefined && typeof payload.ciLogs !== "string") throw new SecurityError("BAD_REQUEST", "ciLogs must be a string.", 400);
  if (payload.diff !== undefined && typeof payload.diff !== "string") throw new SecurityError("BAD_REQUEST", "diff must be a string.", 400);
  if (payload.log !== undefined && typeof payload.log !== "string") throw new SecurityError("BAD_REQUEST", "log must be a string.", 400);
  if (payload.projectContext !== undefined && !isValidProjectContext(payload.projectContext)) {
    throw new SecurityError("BAD_REQUEST", "projectContext must be an array of path/content objects.", 400);
  }
  if (payload.aiContextPackDrift !== undefined && !isValidDriftSignal(payload.aiContextPackDrift)) {
    throw new SecurityError("BAD_REQUEST", "aiContextPackDrift must be a drift signal object.", 400);
  }
  return payload as unknown as GovernancePayload;
}

function isValidProjectContext(value: unknown): value is ProjectContextFile[] {
  return Array.isArray(value)
    && value.length <= 80
    && value.every((item) => item
      && typeof item === "object"
      && typeof (item as ProjectContextFile).path === "string"
      && typeof (item as ProjectContextFile).content === "string");
}

function isValidDriftSignal(value: unknown): boolean {
  if (!value || typeof value !== "object") return false;
  const signal = value as Record<string, unknown>;
  return typeof signal.ok === "boolean"
    && typeof signal.driftRisk === "boolean"
    && typeof signal.message === "string"
    && Array.isArray(signal.changedSourceDocs)
    && signal.changedSourceDocs.every((file) => typeof file === "string");
}

export function isRepositoryAllowed(repository: string, env: Env): boolean {
  const allowed = (env.ALLOWED_REPOSITORIES || "").split(",").map((item) => item.trim()).filter(Boolean);
  return allowed.length > 0 && allowed.includes(repository);
}

export async function validateSharedSecret(request: Request, env: Env): Promise<void> {
  if ((env.REQUIRE_SHARED_SECRET || "false").toLowerCase() !== "true") return;
  const expected = env.CI_WORKER_SHARED_SECRET || "";
  const actual = request.headers.get("X-CI-Worker-Secret") || "";
  if (!expected || !(await timingSafeEqual(actual, expected))) {
    throw new SecurityError("UNAUTHORIZED", "Invalid worker secret.", 401);
  }
}

export async function timingSafeEqual(a: string, b: string): Promise<boolean> {
  const enc = new TextEncoder();
  const left = enc.encode(a);
  const right = enc.encode(b);
  const max = Math.max(left.length, right.length);
  let diff = left.length ^ right.length;
  for (let i = 0; i < max; i += 1) diff |= (left[i] || 0) ^ (right[i] || 0);
  return diff === 0;
}

export class SecurityError extends Error {
  constructor(public code: "BAD_REQUEST" | "UNAUTHORIZED" | "FORBIDDEN" | "PAYLOAD_TOO_LARGE", message: string, public status: number) {
    super(message);
  }
}

export interface Env {
  ALLOWED_REPOSITORIES?: string;
  MAX_BODY_BYTES?: string;
  MAX_MODEL_INPUT_CHARS?: string;
  RATE_LIMIT_WINDOW_SECONDS?: string;
  RATE_LIMIT_MAX_REQUESTS?: string;
  REQUIRE_SHARED_SECRET?: string;
  CI_WORKER_SHARED_SECRET?: string;
  KILO_ENDPOINT?: string;
  KILO_BASE_URL?: string;
  KILO_CHAT_COMPLETIONS_PATH?: string;
  KILO_MODEL?: string;
  KILO_FALLBACK_MODEL?: string;
  KILO_SECOND_FALLBACK_MODEL?: string;
  KILO_API_KEY?: string;
}

export type AnalysisType = "failure" | "diff" | "governance";
export type EventType = "push" | "pull_request" | "workflow_dispatch";
export type Severity = "info" | "warning" | "error";

export interface BasePayload {
  type: AnalysisType;
  repository: string;
  event: EventType;
  branch: string;
  commit: string;
  workflow: string;
  run_id: string;
  run_url: string;
  ciLogs?: string;
  projectContext?: ProjectContextFile[];
  metadata?: Record<string, unknown>;
}

export interface ProjectContextFile {
  path: string;
  content: string;
  truncated?: boolean;
}

export interface FailurePayload extends BasePayload {
  type: "failure";
  log: string;
}

export interface DiffPayload extends BasePayload {
  type: "diff";
  base?: string;
  head?: string;
  diff: string;
}

export type AnalyzePayload = FailurePayload | DiffPayload;

export interface GovernancePayload extends BasePayload {
  type: "governance";
  diff?: string;
  log?: string;
  report: string;
}

export interface Finding {
  severity: Severity;
  file?: string;
  line?: number;
  title: string;
  body: string;
  snippet?: string;
}

export interface AnalyzeResponse {
  ok: true;
  type: AnalysisType;
  summary: string;
  markdown: string;
  findings: Finding[];
  fallback?: boolean;
  governance?: GovernanceVerdict;
}

export interface GovernanceVerdict {
  publishDecision: "publish" | "publish_with_caution" | "suppress" | "fallback";
  confidence: "high" | "medium" | "low";
  verdict: string;
  unsupportedClaims: string[];
  documentationConflicts: string[];
  recommendedIssueBody: string;
}

export interface ErrorResponse {
  ok: false;
  error: {
    code: "BAD_REQUEST" | "UNAUTHORIZED" | "FORBIDDEN" | "PAYLOAD_TOO_LARGE" | "RATE_LIMITED" | "INTERNAL_ERROR";
    message: string;
  };
}

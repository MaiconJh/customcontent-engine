import type { Env } from "./types";

const buckets = new Map<string, { count: number; resetAt: number }>();

export function checkRateLimit(request: Request, env: Env, route: string, repository = "unknown", event = "unknown"): boolean {
  const windowMs = Number(env.RATE_LIMIT_WINDOW_SECONDS || "60") * 1000;
  const max = Number(env.RATE_LIMIT_MAX_REQUESTS || "20");
  const ip = request.headers.get("CF-Connecting-IP") || "unknown";
  const key = `${ip}:${repository}:${route}:${event}`;
  const now = Date.now();
  const bucket = buckets.get(key);
  if (!bucket || bucket.resetAt <= now) {
    buckets.set(key, { count: 1, resetAt: now + windowMs });
    return true;
  }
  bucket.count += 1;
  return bucket.count <= max;
}

export function resetRateLimitForTests(): void {
  buckets.clear();
}

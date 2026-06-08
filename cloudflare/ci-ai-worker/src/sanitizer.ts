export function sanitizeText(value: string, maxChars = 50000): string {
  let out = value || "";
  out = out.replace(/-----BEGIN (?:RSA |OPENSSH )?PRIVATE KEY-----[\s\S]*?-----END (?:RSA |OPENSSH )?PRIVATE KEY-----/gi, "[REDACTED]");
  out = out.replace(/(^|\n)(?:[^\n]*\/)?\.env(?:\.[\w.-]+)?[^\n]*\n(?:[+\- ].*\n?)*/gi, "$1[REDACTED]");
  out = out.replace(/\bAuthorization:\s*(?:Bearer|Basic)\s+[^\s\r\n]+/gi, "Authorization: [REDACTED]");
  out = out.replace(/\b(?:Bearer|Basic)\s+[A-Za-z0-9._~+/=-]{16,}/g, "[REDACTED]");
  out = out.replace(/\b(?:github_pat_[A-Za-z0-9_]+|gh[pousr]_[A-Za-z0-9_]{20,}|npm_[A-Za-z0-9_]{20,}|sk-[A-Za-z0-9_-]{20,}|xox[bp]-[A-Za-z0-9-]{20,})\b/g, "[REDACTED]");
  out = out.replace(/\b(?:CLOUDFLARE_API_TOKEN|CF_API_TOKEN|KILO_API_KEY|OPENAI_API_KEY|ANTHROPIC_API_KEY|GOOGLE_API_KEY|GEMINI_API_KEY|AWS_ACCESS_KEY_ID|AWS_SECRET_ACCESS_KEY)\s*=\s*[^\s\r\n]+/gi, "$1=[REDACTED]");
  out = out.replace(/\b(password|passwd|secret|token|api_key|private_key)\b\s*[:=]\s*["']?[^"'\s\r\n,}]+["']?/gi, "$1=[REDACTED]");
  out = out.replace(/\b(Set-Cookie|Cookie):\s*[^\r\n]+/gi, "$1: [REDACTED]");
  return out.length > maxChars ? `${out.slice(0, maxChars)}\n[TRUNCATED]` : out;
}

export function sanitizeObject<T>(value: T, maxChars = 50000): T {
  if (typeof value === "string") return sanitizeText(value, maxChars) as T;
  if (Array.isArray(value)) return value.map((item) => sanitizeObject(item, maxChars)) as T;
  if (value && typeof value === "object") {
    const entries = Object.entries(value as Record<string, unknown>).map(([key, val]) => {
      if (/password|passwd|secret|token|api[_-]?key|private[_-]?key|cookie|authorization/i.test(key)) {
        return [key, "[REDACTED]"];
      }
      return [key, sanitizeObject(val, maxChars)];
    });
    return Object.fromEntries(entries) as T;
  }
  return value;
}

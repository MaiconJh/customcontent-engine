#!/usr/bin/env node
const fs = require("node:fs");

const args = process.argv.slice(2);
const getArg = (name, fallback) => {
  const idx = args.indexOf(name);
  return idx >= 0 ? args[idx + 1] : fallback;
};

const inputPath = getArg("--input", null);
const outputPath = getArg("--output", null);
const maxChars = Number(getArg("--max-chars", process.env.MAX_CHARS || "50000"));

function sanitizeString(value) {
  if (!value) return "";
  let out = String(value);
  out = out.replace(/-----BEGIN (?:RSA |OPENSSH )?PRIVATE KEY-----[\s\S]*?-----END (?:RSA |OPENSSH )?PRIVATE KEY-----/gi, "[REDACTED]");
  out = out.replace(/(^|\n)(?:[^\n]*\/)?\.env(?:\.[\w.-]+)?[^\n]*\n(?:[+\- ].*\n?)*/gi, "$1[REDACTED]");
  out = out.replace(/\bAuthorization:\s*(?:Bearer|Basic)\s+[^\s\r\n]+/gi, "Authorization: [REDACTED]");
  out = out.replace(/\b(?:Bearer|Basic)\s+[A-Za-z0-9._~+/=-]{16,}/g, "[REDACTED]");
  out = out.replace(/\b(?:github_pat_[A-Za-z0-9_]+|gh[pousr]_[A-Za-z0-9_]{20,}|npm_[A-Za-z0-9_]{20,}|sk-[A-Za-z0-9_-]{20,}|xox[bp]-[A-Za-z0-9-]{20,})\b/g, "[REDACTED]");
  out = out.replace(/\b(?:CLOUDFLARE_API_TOKEN|CF_API_TOKEN|KILO_API_KEY|OPENAI_API_KEY|ANTHROPIC_API_KEY|GOOGLE_API_KEY|GEMINI_API_KEY|AWS_ACCESS_KEY_ID|AWS_SECRET_ACCESS_KEY)\s*=\s*[^\s\r\n]+/gi, "$1=[REDACTED]");
  out = out.replace(/\b(password|passwd|secret|token|api_key|private_key)\b\s*[:=]\s*["']?[^"'\s\r\n,}]+["']?/gi, "$1=[REDACTED]");
  out = out.replace(/\b(Set-Cookie|Cookie):\s*[^\r\n]+/gi, "$1: [REDACTED]");
  if (out.length > maxChars) out = `${out.slice(0, maxChars)}\n[TRUNCATED]`;
  return out;
}

function sanitizeValue(value) {
  if (typeof value === "string") return sanitizeString(value);
  if (Array.isArray(value)) return value.map(sanitizeValue);
  if (value && typeof value === "object") {
    return Object.fromEntries(Object.entries(value).map(([key, val]) => {
      if (/password|passwd|secret|token|api[_-]?key|private[_-]?key|cookie|authorization/i.test(key)) {
        return [key, "[REDACTED]"];
      }
      return [key, sanitizeValue(val)];
    }));
  }
  return value;
}

function main() {
  const raw = inputPath ? fs.readFileSync(inputPath, "utf8") : fs.readFileSync(0, "utf8");
  let result;
  try {
    result = JSON.stringify(sanitizeValue(JSON.parse(raw)), null, 2);
  } catch {
    result = sanitizeString(raw);
  }
  if (result.length > maxChars) result = `${result.slice(0, maxChars)}\n[TRUNCATED]`;
  if (outputPath) fs.writeFileSync(outputPath, result);
  else process.stdout.write(result);
}

if (require.main === module) main();

module.exports = { sanitizeString, sanitizeValue };

import { describe, expect, it } from "vitest";
import { sanitizeText } from "../src/sanitizer";

describe("sanitizer", () => {
  it("removes GitHub tokens", () => {
    expect(sanitizeText("token=ghp_abcdefghijklmnopqrstuvwxyz1234567890")).toContain("[REDACTED]");
  });

  it("removes Authorization headers", () => {
    expect(sanitizeText("Authorization: Bearer abcdefghijklmnopqrstuvwxyz")).toBe("Authorization: [REDACTED]");
  });

  it("removes private keys", () => {
    const text = "-----BEGIN PRIVATE KEY-----\nabc\n-----END PRIVATE KEY-----";
    expect(sanitizeText(text)).toBe("[REDACTED]");
  });
});

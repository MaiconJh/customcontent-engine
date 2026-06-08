import { describe, expect, it } from "vitest";
import { isRepositoryAllowed, validatePayload } from "../src/security";
import type { Env } from "../src/types";

const env: Env = { ALLOWED_REPOSITORIES: "MaiconJh/customcontent-engine" };

describe("security", () => {
  it("blocks repositories outside the allowlist", () => {
    expect(isRepositoryAllowed("evil/repo", env)).toBe(false);
    expect(() => validatePayload({
      type: "diff",
      repository: "evil/repo",
      event: "push",
      branch: "main",
      commit: "abc",
      workflow: "ci",
      run_id: "1",
      run_url: "https://github.com/x/y/actions/runs/1",
      diff: "diff",
    }, "diff", env)).toThrow("Repository is not allowed");
  });

  it("accepts allowed repositories", () => {
    expect(isRepositoryAllowed("MaiconJh/customcontent-engine", env)).toBe(true);
  });
});

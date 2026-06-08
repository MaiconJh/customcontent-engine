#!/usr/bin/env node
const API = "https://api.github.com";

function requiredEnv(name) {
  const value = process.env[name];
  if (!value) throw new Error(`${name} is required`);
  return value;
}

async function githubRequest(path, options = {}) {
  const token = requiredEnv("GITHUB_TOKEN");
  const res = await fetch(`${API}${path}`, {
    ...options,
    headers: {
      Accept: "application/vnd.github+json",
      Authorization: `Bearer ${token}`,
      "X-GitHub-Api-Version": "2022-11-28",
      "Content-Type": "application/json",
      ...(options.headers || {}),
    },
  });
  const text = await res.text();
  const body = text ? JSON.parse(text) : null;
  if (!res.ok) {
    const rate = res.headers.get("x-ratelimit-remaining") === "0" ? " GitHub rate limit may be exhausted." : "";
    throw new Error(`GitHub API ${res.status} for ${path}.${rate}`);
  }
  return body;
}

function repoParts() {
  const [owner, repo] = requiredEnv("GITHUB_REPOSITORY").split("/");
  return { owner, repo };
}

module.exports = { githubRequest, repoParts };

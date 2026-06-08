# CI AI Review Bot

## What It Is

CI AI Review Bot is an internal mini-platform that runs build/test checks, analyzes failures, analyzes diffs, creates failure issues, and comments on pull requests. GitHub Actions collects sanitized logs and diffs, Cloudflare Worker acts as the secure middle layer, and Kilo Gateway is used as a configurable OpenAI-compatible provider.

## Architecture

```text
GitHub Actions
-> build/test
-> sanitize logs/diff
-> Cloudflare Worker
-> Kilo Gateway
-> Markdown response
-> GitHub issue/comment
```

The Worker also includes a regex-based local fallback when the provider is unavailable.

## Created Files

- `.github/workflows/ci-ai-review.yml`
- `.github/ai-review/config.yml`
- `scripts/ci/run-build.sh`
- `scripts/ci/collect-diff.sh`
- `scripts/ci/sanitize-payload.js`
- `scripts/ci/call-worker.js`
- `scripts/ci/create-or-update-comment.js`
- `scripts/ci/create-or-update-issue.js`
- `scripts/ci/create-or-update-push-note.js`
- `scripts/ci/github-api.js`
- `scripts/ci/test-worker-api.js`
- `scripts/ci/test-kilo-api.js`
- `cloudflare/ci-ai-worker/src/**`
- `cloudflare/ci-ai-worker/test/**`

## Prepare Cloudflare

```bash
cd cloudflare/ci-ai-worker
npm install
npx wrangler login
npm run dev
npm run deploy
```

Deploy only after completing the local Wrangler login.

## Configure GitHub Actions

Configure values in `Settings -> Secrets and variables -> Actions`.

Recommended variable:

- `CI_AI_WORKER_URL`: public URL of the deployed Worker.

Optional secret:

- `CI_WORKER_SHARED_SECRET`: used when `REQUIRE_SHARED_SECRET=true` in the Worker.

If `CI_AI_WORKER_URL` is not configured, build/test still runs. Analysis uses a local fallback message and does not mask real CI failures.

## Use Kilo Without A Token

`KILO_API_KEY` is optional. By default, the Worker does not send `Authorization` to Kilo Gateway. The current default model is `kilo-auto/free`, when available for anonymous or limited free usage. For paid or authenticated models, configure `KILO_API_KEY` as a Worker secret/variable, not in GitHub Actions.

## Configure The Worker

Main variables in `wrangler.jsonc`:

- `ALLOWED_REPOSITORIES`
- `MAX_BODY_BYTES`
- `MAX_MODEL_INPUT_CHARS`
- `RATE_LIMIT_WINDOW_SECONDS`
- `RATE_LIMIT_MAX_REQUESTS`
- `REQUIRE_SHARED_SECRET`
- `CI_WORKER_SHARED_SECRET`
- `KILO_BASE_URL`
- `KILO_CHAT_COMPLETIONS_PATH`
- `KILO_MODEL`
- `KILO_FALLBACK_MODEL`
- `KILO_SECOND_FALLBACK_MODEL`
- `KILO_API_KEY`

`ALLOWED_REPOSITORIES` must contain only authorized repositories, separated by commas.

## Manual Testing

Health:

```bash
curl https://worker-url/health
```

Diff:

```bash
curl -X POST https://worker-url/v1/analyze/diff \
  -H "Content-Type: application/json" \
  -d '{
    "type":"diff",
    "repository":"MaiconJh/customcontent-engine",
    "event":"pull_request",
    "branch":"feature/example",
    "base":"main",
    "head":"abc123",
    "commit":"abc123",
    "workflow":"CI AI Review Bot",
    "run_id":"1",
    "run_url":"https://github.com/MaiconJh/customcontent-engine/actions/runs/1",
    "diff":"diff --git a/README.md b/README.md\n+example",
    "metadata":{}
  }'
```

Failure:

```bash
curl -X POST https://worker-url/v1/analyze/failure \
  -H "Content-Type: application/json" \
  -d '{
    "type":"failure",
    "repository":"MaiconJh/customcontent-engine",
    "event":"push",
    "branch":"main",
    "commit":"abc123",
    "workflow":"CI AI Review Bot",
    "run_id":"1",
    "run_url":"https://github.com/MaiconJh/customcontent-engine/actions/runs/1",
    "log":"BUILD FAILED\nExecution failed for task :test",
    "metadata":{}
  }'
```

If `REQUIRE_SHARED_SECRET=true`, add:

```bash
-H "X-CI-Worker-Secret: value-without-exposing-it"
```

Local scripts:

```bash
node scripts/ci/test-worker-api.js
node scripts/ci/test-kilo-api.js
```

## Security

The automation sanitizes data before it leaves GitHub Actions and sanitizes it again inside the Worker. Tokens, cookies, `Authorization` headers, private keys, sensitive variables, and `.env` files are replaced with `[REDACTED]`.

The Worker validates method, route, `Content-Type`, maximum payload size, repository allowlist, and allowed events. It applies rate limiting by IP, repository, route, and event. CORS is blocked by default, arbitrary client-provided URLs are not accepted, and the Worker does not act as a generic proxy.

## Spam Prevention

Comments and issues use hidden HTML markers:

- `<!-- ai-ci-review-bot:summary -->`
- `<!-- ai-ci-review-bot:failure -->`
- `<!-- ai-ci-review-bot:diff -->`
- `<!-- ai-ci-review-bot:push-note -->`

The bot updates existing PR comments, reuses open failure issues by branch/SHA, and keeps one technical note issue for pushes to `main`.

## Pull Requests From Forks

The workflow uses `pull_request`, not `pull_request_target`. Secrets are not exposed to forks. If permissions are limited, comment publication may fail without breaking CI. Build/test still runs normally.

## Troubleshooting

- Worker not configured: set `CI_AI_WORKER_URL` in Actions variables.
- Missing `CI_AI_WORKER_URL`: build/test runs and analysis uses fallback.
- Kilo unavailable: the Worker returns local fallback.
- Invalid model: the provider tries `KILO_FALLBACK_MODEL` and `KILO_SECOND_FALLBACK_MODEL`.
- "Local fallback was used": the Worker received the payload, but Kilo Gateway returned an error, empty response, timeout, or required authentication. Check `KILO_BASE_URL`, `KILO_CHAT_COMPLETIONS_PATH`, configured models, and configure `KILO_API_KEY` in the Worker if needed.
- Rate limited: increase limits carefully or wait for the window to expire.
- Payload too large: reduce `MAX_DIFF_CHARS` or `MAX_CHARS`, or increase `MAX_BODY_BYTES`.
- GitHub cannot create issues: check workflow `permissions` and repository/fork policies.
- Pull request from a fork: comments may be blocked by GitHub permissions, but CI should not fail.

## Next Commands

```bash
cd cloudflare/ci-ai-worker
npm install
npx wrangler login
npm run deploy
```

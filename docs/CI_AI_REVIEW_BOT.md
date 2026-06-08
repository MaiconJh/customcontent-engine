# CI AI Governance Bot

## What It Is

CI AI Governance Bot is a documentation-driven review layer for this repository. It does not replace GitHub Actions and it does not ask maintainers to run local Gradle validation. GitHub Actions is the source of truth for build, test, and integrationTest results.

The bot collects the git diff, GitHub Actions result/logs, and relevant repository documentation, sends that context to the Cloudflare Worker, asks Kilo Code for an initial report, and then runs a governance/interceptor review that checks whether the report is relevant, factual, and aligned with the documented project scope.

## Architecture

```text
GitHub Actions
-> build/test/integrationTest on GitHub-hosted runner
-> collect diff, CI logs, and project documentation context
-> sanitize payload
-> Cloudflare Worker
-> Kilo Gateway initial report
-> Kilo Gateway governance review
-> Markdown response
-> GitHub issue/comment
```

All heavy validation happens in GitHub Actions. Local scripts are only diagnostics and payload collectors; they do not call Gradle, Maven, Java, or repository tests.

## Documentation Context

`scripts/ci/collect-project-context.js` collects these files when they exist:

- `docs/PROJECT_SCOPE.md`
- `docs/ARCHITECTURE_GUARDRAILS.md`
- `docs/adr/*.md`
- `docs/milestones/*.md`
- `README.md`
- `src/main/resources/plugin.yml`
- `src/main/resources/definitions.yml`
- `.github/workflows/build-test.yml`
- `.github/workflows/ci-ai-review.yml`
- `build.gradle.kts`
- `settings.gradle.kts`

The collector preserves file paths, limits total payload size, truncates long files with a clear marker, sanitizes content, and skips ignored/local-sensitive areas such as `.env`, `.env.*`, `.dev.vars`, `node_modules`, `.gradle`, `.kilo`, `.wrangler`, `.vscode`, and build outputs.

## Initial AI Report

The Worker asks Kilo Code to review the change using:

1. The git diff.
2. GitHub Actions result/logs.
3. Repository documentation context.

The prompt asks the model to determine whether the change is consistent with the documented architecture and scope. It checks for scope violations, guardrail violations, ADR contradictions, forbidden dependencies, local-only validation assumptions, plugin metadata risks, unsupported Folia claims, and behavior not documented by the repository.

The report separates:

- Confirmed findings
- Possible risks
- Unsupported claims
- Documentation divergence
- Suggested follow-up

The model is instructed not to claim a problem unless it is supported by the diff, CI logs, or documentation context.

## Governance Review

After the first report, the Worker runs a second governance/interceptor review. The governance review receives:

- git diff
- GitHub Actions result/logs
- project documentation context
- first AI report

It checks:

- Is the report relevant?
- Is the report factually supported?
- Are there unsupported claims?
- Did the report miss a major documentation conflict?
- Did the report overstate a risk?
- Does the report align with project scope and ADRs?
- Should the issue/comment be published as-is, published with caution, suppressed, or treated as fallback?

The governance verdict uses this shape:

```json
{
  "publishDecision": "publish|publish_with_caution|suppress|fallback",
  "confidence": "high|medium|low",
  "verdict": "...",
  "unsupportedClaims": [],
  "documentationConflicts": [],
  "recommendedIssueBody": "..."
}
```

The GitHub issue/comment includes the initial report and the governance review so maintainers can see both the analysis and the interceptor verdict.

## GitHub Behavior

For pull requests, the bot creates or updates one deduplicated PR comment using a hidden marker.

For pushes to `main`, the bot creates or updates one deduplicated technical note issue. The body includes hidden commit/run markers so the note remains traceable without creating spam.

For CI failures, the bot creates or reuses a `ci-failure` issue and includes GitHub Actions failure evidence plus governance review.

The bot must never mask a real build/test/integrationTest failure. AI/provider failures are non-blocking; GitHub Actions validation remains authoritative.

## Fallback Behavior

If Kilo does not return a usable response, the Worker uses local fallback. The fallback is in English and considers basic documentation-sensitive signals, including:

- ADR file changes
- `PROJECT_SCOPE.md` changes
- `ARCHITECTURE_GUARDRAILS.md` changes
- forbidden platform imports in domain code
- `plugin.yml` metadata changes
- `folia-supported: true` declarations
- GitHub Actions permission/cache/trigger changes

The fallback governance review also checks for unsupported claims such as local Gradle validation instructions or Folia claims not supported by the diff/documentation context.

## Kilo Provider

Default endpoint and models:

```text
KILO_ENDPOINT=https://api.kilo.ai/api/gateway/chat/completions
KILO_MODEL=kilo-auto/free
KILO_FALLBACK_MODEL=kilo-auto/balanced
KILO_SECOND_FALLBACK_MODEL=kilo/auto-free
```

`KILO_API_KEY` is optional. If it is not configured, the Worker does not send an `Authorization` header.

The parser accepts:

- `choices[0].message.content`
- `choices[0].message.reasoning`

Provider failures are logged only as safe reasons such as `provider status 401`, `provider status 403`, `provider status 404`, `provider status 429`, `empty response`, or timeout-like errors. Tokens, raw sensitive payloads, and `Authorization` are not logged.

## Configuration

Configure GitHub Actions in `Settings -> Secrets and variables -> Actions`.

Required variable:

- `CI_AI_WORKER_URL`: public URL of the deployed Worker.

Optional secret:

- `CI_WORKER_SHARED_SECRET`: used when `REQUIRE_SHARED_SECRET=true` in the Worker.

Configure Kilo in the Cloudflare Worker, not in GitHub Actions:

- `KILO_API_KEY`: optional Worker secret for authenticated models.

## Deploy The Worker

```bash
cd cloudflare/ci-ai-worker
npm install
npx wrangler login
npm run deploy
```

## Test Without Java

Allowed local diagnostics:

```bash
node --check scripts/ci/*.js
cd cloudflare/ci-ai-worker
npm.cmd run typecheck
npm.cmd test
cd ../..
node scripts/ci/test-worker-api.js
node scripts/ci/test-kilo-api.js
```

These diagnostics do not call Gradle, Maven, Java, or repository tests.

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

The workflow uses `pull_request`, not `pull_request_target`. Secrets are not exposed to forks. If permissions are limited, comment publication may fail without breaking CI. Build/test/integrationTest still runs in GitHub Actions.

## Troubleshooting

- Worker not configured: set `CI_AI_WORKER_URL` in Actions variables.
- Missing `CI_AI_WORKER_URL`: build/test/integrationTest still runs and analysis uses fallback.
- Kilo unavailable: the Worker returns local fallback plus governance fallback.
- Invalid model: the provider tries `KILO_FALLBACK_MODEL` and `KILO_SECOND_FALLBACK_MODEL`.
- "Local fallback was used": the Worker received the payload, but Kilo Gateway returned an error, empty response, timeout, or required authentication.
- Rate limited: increase limits carefully or wait for the window to expire.
- Payload too large: reduce context/diff limits or increase `MAX_BODY_BYTES`.
- GitHub cannot create issues: check workflow `permissions` and repository/fork policies.
- Pull request from a fork: comments may be blocked by GitHub permissions, but CI should not fail.

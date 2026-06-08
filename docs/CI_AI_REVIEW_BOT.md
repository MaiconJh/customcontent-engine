# CI AI Review Bot

## O que e

CI AI Review Bot e uma mini-plataforma interna para rodar build/test, analisar falhas, analisar diffs, criar issues de falha e comentar pull requests. O GitHub Actions coleta logs e diffs sanitizados, o Cloudflare Worker atua como camada segura, e o Kilo Gateway e usado como provider OpenAI-compatible configuravel.

## Arquitetura

```text
GitHub Actions
-> build/test
-> sanitize logs/diff
-> Cloudflare Worker
-> Kilo Gateway
-> resposta Markdown
-> GitHub issue/comment
```

O Worker tambem tem fallback local por regex quando o provider esta indisponivel.

## Arquivos criados

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
- `cloudflare/ci-ai-worker/src/**`
- `cloudflare/ci-ai-worker/test/**`

## Como preparar Cloudflare

```bash
cd cloudflare/ci-ai-worker
npm install
npx wrangler login
npm run dev
npm run deploy
```

O deploy deve ser executado somente depois do login local com Wrangler.

## Como configurar GitHub Actions

Configure em `Settings -> Secrets and variables -> Actions`.

Variavel recomendada:

- `CI_AI_WORKER_URL`: URL publica do Worker implantado.

Segredo opcional:

- `CI_WORKER_SHARED_SECRET`: usado quando `REQUIRE_SHARED_SECRET=true` no Worker.

Se `CI_AI_WORKER_URL` nao estiver configurada, build/test continua rodando. A analise usa uma mensagem fallback local e nao mascara falha real do CI.

## Como usar sem token do Kilo

`KILO_API_KEY` e opcional. Por padrao, o Worker nao envia `Authorization` ao Kilo Gateway. Modelos free/anonimos podem ser usados quando disponiveis. Para modelos pagos ou autenticados, configure `KILO_API_KEY` como secret/var do Worker, nao no GitHub Actions.

## Como configurar Worker

Variaveis principais em `wrangler.jsonc`:

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

`ALLOWED_REPOSITORIES` deve conter apenas repositorios autorizados, separados por virgula.

## Como testar manualmente

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

Se `REQUIRE_SHARED_SECRET=true`, adicione:

```bash
-H "X-CI-Worker-Secret: valor-sem-expor"
```

## Seguranca

A automacao aplica sanitizacao antes de sair do GitHub Actions e novamente dentro do Worker. Tokens, cookies, headers `Authorization`, chaves privadas, variaveis sensiveis e arquivos `.env` sao substituidos por `[REDACTED]`.

O Worker valida metodo, rota, `Content-Type`, tamanho maximo, repositorio em allowlist e evento permitido. Ele aplica rate limit por IP, repositorio, rota e evento, bloqueia CORS por padrao, nao aceita URLs arbitrarias do cliente e nao funciona como proxy generico.

## Como evitar spam

Comentarios e issues usam marcadores HTML ocultos:

- `<!-- ai-ci-review-bot:summary -->`
- `<!-- ai-ci-review-bot:failure -->`
- `<!-- ai-ci-review-bot:diff -->`
- `<!-- ai-ci-review-bot:push-note -->`

O bot atualiza comentarios existentes em PRs, reutiliza issue aberta de falha por branch/SHA e mantem uma issue tecnica unica para notas de push na `main`.

## Pull requests de forks

O workflow usa `pull_request`, nao `pull_request_target`. Secrets nao sao expostos para forks. Se permissoes forem limitadas, a publicacao de comentario pode falhar sem quebrar o CI. Build/test continua sendo executado normalmente.

## Troubleshooting

- Worker nao configurado: defina `CI_AI_WORKER_URL` em Actions variables.
- `CI_AI_WORKER_URL` ausente: build/test roda, analise usa fallback.
- Kilo indisponivel: Worker retorna fallback local.
- Modelo invalido: provider tenta `KILO_FALLBACK_MODEL` e `KILO_SECOND_FALLBACK_MODEL`.
- Rate limited: aumente limites com cuidado ou aguarde a janela expirar.
- Payload grande demais: reduza `MAX_DIFF_CHARS`, `MAX_CHARS` ou aumente `MAX_BODY_BYTES`.
- GitHub sem permissao para issues: confira `permissions` do workflow e politicas do repositorio/fork.
- Pull request de fork: comentarios podem ser bloqueados pelas permissoes do GitHub, mas nao devem falhar o CI.

## Proximos comandos

```bash
cd cloudflare/ci-ai-worker
npm install
npx wrangler login
npm run deploy
```

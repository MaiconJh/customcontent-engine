import type { AnalyzePayload, Env, Finding } from "./types";
import { buildPrompt } from "./prompt-builder";
import { checkRateLimit } from "./rate-limit";
import { callKiloChatCompletion } from "./providers/kilo";
import { formatGithubMarkdown, normalizeFindings } from "./formatters/github";
import { errorResponse, okResponse } from "./response-schema";
import { readJsonBody, SecurityError, validatePayload, validateSharedSecret } from "./security";
import { sanitizeObject } from "./sanitizer";

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    try {
      const url = new URL(request.url);
      if (request.method === "OPTIONS") return new Response(null, { status: 403 });
      if (request.method === "GET" && url.pathname === "/health") {
        return Response.json({ ok: true, service: "ci-ai-worker" });
      }
      if (request.method !== "POST") return errorResponse("BAD_REQUEST", "Unsupported method.", 405);
      if (!["/v1/analyze/failure", "/v1/analyze/diff"].includes(url.pathname)) {
        return errorResponse("BAD_REQUEST", "Unsupported route.", 404);
      }
      await validateSharedSecret(request, env);
      const expectedType = url.pathname.endsWith("/failure") ? "failure" : "diff";
      const raw = await readJsonBody(request, env);
      const payload = sanitizeObject(validatePayload(raw, expectedType, env), Number(env.MAX_MODEL_INPUT_CHARS || "50000"));
      if (!checkRateLimit(request, env, url.pathname, payload.repository, payload.event)) {
        return errorResponse("RATE_LIMITED", "Rate limit exceeded.", 429);
      }
      const response = await analyze(payload, env);
      return Response.json(response);
    } catch (error) {
      if (error instanceof SecurityError) return errorResponse(error.code, error.message, error.status);
      return errorResponse("INTERNAL_ERROR", "Internal error.", 500);
    }
  },
};

export async function analyze(payload: AnalyzePayload, env: Env) {
  const prompt = buildPrompt(payload, env);
  const provider = await callKiloChatCompletion(env, prompt.system, prompt.user);
  const fallback = !provider.ok || !provider.text;
  const markdown = fallback ? localFallback(payload) : provider.text || "";
  const findings = payload.type === "diff" ? diffFindings(payload.diff) : failureFindings(payload.log);
  return okResponse(payload.type, formatGithubMarkdown(payload, markdown, fallback), normalizeFindings(findings), fallback);
}

export function localFallback(payload: AnalyzePayload): string {
  if (payload.type === "failure") {
    const log = payload.log;
    const matched = [
      "Compilation failed", "BUILD FAILED", "Test failed", "There were failing tests", "Could not resolve",
      "Could not find", "Unsupported class file major version", "NoSuchMethodError", "ClassNotFoundException",
      "NullPointerException", "Execution failed for task", "Could not determine java version", "Permission denied",
      "./gradlew: No such file or directory",
    ].filter((pattern) => log.includes(pattern));
    return `## Resumo

O build/test falhou. ${matched.length ? `Padroes detectados: ${matched.join(", ")}.` : "Nao houve evidencia suficiente para apontar uma causa unica."}

## Causa provavel

${matched[0] || "Falha de compilacao, teste ou configuracao detectada no log sanitizado."}

## Evidencia do log

\`\`\`text
${extractEvidence(log)}
\`\`\`

## Arquivos possivelmente relacionados

Nao inferido com seguranca a partir do log sanitizado.

## Correcao sugerida

Reproduza localmente com o mesmo comando do CI e corrija a primeira falha real do log.

## Proximos passos

* Abrir o artifact build.log.
* Corrigir a causa raiz.
* Reexecutar o workflow.`;
  }

  const diff = payload.diff;
  const hints = [
    [/build\.gradle|build\.gradle\.kts|pom\.xml/, "Alteracao em build/dependencias detectada."],
    [/\.github\/workflows/, "Alteracao em GitHub Actions detectada; revisar permissoes, cache e gatilhos."],
    [/src\/main\//, "Codigo de producao alterado."],
    [/src\/test\//, "Testes alterados."],
    [/secrets?|env|token|permission/i, "Alteracao sensivel de configuracao detectada."],
  ].filter(([regex]) => (regex as RegExp).test(diff)).map(([, msg]) => msg);
  return `## Resumo

Diff analisado por fallback local. ${hints.join(" ") || "Nenhum padrao critico detectado por regex."}

## Impacto tecnico

Revise se as mudancas afetam build, testes, configuracao ou comportamento de producao.

## Riscos

Mudancas em codigo de producao sem teste correspondente podem aumentar risco de regressao.

## Configuracao antiga vs nova

* Antes: conforme linhas removidas do diff.
* Depois: conforme linhas adicionadas do diff.
* Impacto: validar comandos e permissoes quando arquivos de configuracao mudarem.
* Risco: cache, versao Java, dependencias e permissoes podem mudar o resultado do CI.
* Ajuste recomendado: manter testes e documentacao alinhados.

## Orientacoes

Use o build/test como fonte de verdade e revise manualmente pontos sensiveis.

## Checklist sugerido

* [ ] Build local executado.
* [ ] Testes relevantes atualizados.
* [ ] Configuracao revisada.`;
}

function extractEvidence(text: string): string {
  return text.split("\n").filter((line) => /BUILD FAILED|Compilation failed|Test failed|Could not|Exception|Execution failed|Permission denied/i.test(line)).slice(0, 12).join("\n").slice(0, 1600) || "Sem evidencia curta disponivel.";
}

function failureFindings(log: string): Finding[] {
  return /BUILD FAILED|Compilation failed|There were failing tests/.test(log)
    ? [{ severity: "error", title: "Build/test failed", body: "O log sanitizado indica falha real de build ou teste." }]
    : [];
}

function diffFindings(diff: string): Finding[] {
  const findings: Finding[] = [];
  if (/^\+\+\+ b\/src\/main\//m.test(diff) && !/^\+\+\+ b\/src\/test\//m.test(diff)) {
    findings.push({ severity: "warning", title: "Production change without test diff", body: "Codigo de producao mudou sem alteracao de teste no diff." });
  }
  if (/^\+\+\+ b\/\.github\/workflows\//m.test(diff) && /permissions:/m.test(diff)) {
    findings.push({ severity: "warning", file: ".github/workflows", title: "Workflow permissions changed", body: "Revise se as permissoes continuam minimas." });
  }
  return findings;
}

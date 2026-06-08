import type { AnalyzePayload, Env } from "./types";
import { maxModelInputChars } from "./security";
import { sanitizeText } from "./sanitizer";

export const SYSTEM_PROMPT = `Voce e um revisor tecnico de CI/CD e codigo.
Analise somente os dados fornecidos.
Nao invente arquivos.
Nao invente comandos.
Nao invente dependencias.
Nao exponha segredos.
Nao repita logs completos.
Nao gere respostas longas demais.
Se houver incerteza, diga explicitamente.
Retorne Markdown claro, objetivo e acionavel.
Prefira snippets pequenos e funcionais.
Nao gere codigo perigoso.
Nao recomende desativar testes para resolver falha.
Nao recomende ignorar erro de build.
Priorize correcao real.`;

export function buildPrompt(payload: AnalyzePayload, env: Env): { system: string; user: string } {
  const limit = maxModelInputChars(env);
  const common = `Repository: ${payload.repository}
Event: ${payload.event}
Branch: ${payload.branch}
Commit: ${payload.commit}
Workflow: ${payload.workflow}
Run URL: ${payload.run_url}`;

  if (payload.type === "failure") {
    return {
      system: SYSTEM_PROMPT,
      user: sanitizeText(`${common}

Voce esta analisando uma falha de build/test em projeto Java/Gradle/Maven.
Use apenas o log sanitizado e metadados fornecidos.

Retorne Markdown com:

## Resumo
## Causa provavel
## Evidencia do log
## Arquivos possivelmente relacionados
## Correcao sugerida
## Snippet sugerido
## Proximos passos

Log sanitizado:
${payload.log}`, limit),
    };
  }

  return {
    system: SYSTEM_PROMPT,
    user: sanitizeText(`${common}
Base: ${payload.base || ""}
Head: ${payload.head || ""}

Voce esta analisando um diff de codigo/documentacao/configuracao.
Use apenas o diff sanitizado e metadados fornecidos.
Priorize regressoes, testes quebrados, arquitetura, Gradle/Maven, YAML, JSON, documentacao, seguranca e compatibilidade.

Retorne Markdown com:

## Resumo
## Impacto tecnico
## Riscos
## Configuracao antiga vs nova
## Orientacoes
## Checklist sugerido

Diff sanitizado:
${payload.diff}`, limit),
  };
}

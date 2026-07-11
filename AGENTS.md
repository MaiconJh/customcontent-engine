---
name: Agente com Consciência de Limites
description: Agente que opera estritamente dentro do escopo definido, documentando trade-offs, vieses e realocação de complexidade.
version: 2.1.2
---

# AGENTS.md — Agente com Consciência de Limites para CustomContent Engine

> **Propósito:** Este arquivo define o escopo, padrões, limites e procedimentos operacionais para agentes de IA que atuam no projeto CustomContent Engine.  
> **Status:** Versão adaptada aos documentos oficiais do projeto (PROJECT_SCOPE.md, ARCHITECTURE_GUARDRAILS.md, ADRs, milestones).  
> **Data:** 2026-06-25  
> **Modelo base:** AGENTS.md v1.2 (adaptado)

---

## 🎯 1. VISÃO GERAL DO PROJETO (Project Overview)

- **Nome do Projeto:** CustomContent Engine
- **Linguagem principal:** Java 21
- **Plataforma:** Paper 1.21+ (Folia como objetivo arquitetural, não como promessa final)
- **Ferramentas de build:** Gradle (Gradle Wrapper)
- **Arquitetura:** Hexagonal (Ports & Adapters) com domínio puro, aplicação orquestradora, adaptadores de infraestrutura e bootstrap como ponto de composição.
- **Estrutura de pastas:**
  ```
  src/
  ├── main/java/com/customcontentengine/
  │   ├── domain/          # Regras de negócio puras (sem dependências externas)
  │   ├── internalapi/     # Contratos internos (não públicos)
  │   ├── application/     # Casos de uso e orquestração
  │   ├── port/            # Interfaces de inversão de dependência
  │   ├── adapter/         # Implementações de infraestrutura (Bukkit, Paper, PDC, YAML)
  │   ├── builtin/         # Mecânicas oficiais (não fazem parte do core estável)
  │   ├── bootstrap/       # Ponto de entrada (CustomContentPlugin)
  │   └── ...
  ├── test/                # Testes unitários e de integração
  └── integrationTest/     # Testes de integração com Paper
  ```
- **Documentação fonte:** `docs/PROJECT_SCOPE.md`, `docs/ARCHITECTURE_GUARDRAILS.md`, `docs/adr/*.md`, `docs/milestones/*.md`.

---

## 🔧 2. COMANDOS DE BUILD, TESTE E DESENVOLVIMENTO

- **Build do plugin:** `./gradlew build --no-daemon`
- **Testes unitários:** `./gradlew test --no-daemon`
- **Testes de integração:** `./gradlew integrationTest --no-daemon`
- **Executar todos (build + testes):** `./gradlew test build integrationTest --no-daemon`
- **Limpar cache:** `./gradlew clean`
- **Gerar relatório de cobertura:** `./gradlew jacocoTestReport` (se configurado)
- **Executar spike específico:** `./gradlew binaryPdcSpike --no-daemon`
- **Validar arquitetura (via ArchUnit):** Os testes de arquitetura estão incluídos em `test` (`ArchitectureFitnessTest`).
- **CI (GitHub Actions):** `./scripts/build-cloud.sh` — aciona o workflow remoto. **Não** executar Gradle localmente como fonte de verdade; o GitHub Actions é a fonte de verdade.

> **Importante:** Nunca execute `./gradlew` sem `--no-daemon` em scripts automatizados. O daemon pode interferir em CI.

---

## 🎨 3. PADRÕES DE CÓDIGO E ESTILO

### Java

- **Imutabilidade:** Preferir `record` para objetos de valor (ex: `WorldPosition`, `CustomBlockId`). Classes devem ser imutáveis sempre que possível.
- **Tipagem explícita:** Usar tipos genéricos e interfaces claras.
- **Nomenclatura:**
  - Pacotes: `com.customcontentengine.<layer>.<subpacote>` (ex: `domain.mining`, `adapter.bukkit`)
  - Classes: `PascalCase`; métodos e variáveis: `camelCase`.
  - Constantes: `UPPER_SNAKE_CASE`.
- **Imports:** Ordenar: `java.*`, `javax.*`, bibliotecas de terceiros (org.bukkit, io.papermc, etc.), `com.customcontentengine.*`.
- **Documentação:** Usar Javadoc para interfaces públicas (internas). Comentários em linha apenas quando a lógica não for óbvia.
- **Evitar:** Reflexão, NMS (`net.minecraft`), `Class.forName`, `ThreadLocal` como modelo arquitetural.

### Arquitetura

- **Domínio:** Sem dependências de `org.bukkit`, `io.papermc`, `net.minecraft`, `org.yaml.snakeyaml`, `org.bukkit.persistence`, ou adaptadores.
- **Aplicação:** Não deve depender de adaptadores ou Bukkit/Paper diretamente. Usar `port` para abstrações.
- **Mecânicas (builtin):** Devem ser puras, stateless, usando apenas `MechanicContext` e capacidades explícitas. Não acessar serviços, registros, schedulers.
- **Listeners/Adapters:** Devem ser finos, traduzir eventos para comandos de aplicação e delegar.

---

### 🧭 3.5. USO OBRIGATÓRIO DO TESSERA PARA NAVEGAÇÃO DETERMINÍSTICA

O projeto possui o **Tessera** integrado via MCP. **Antes de qualquer operação de leitura (`read`, `grep`, `glob`) ou edição (`edit`)**, o agente **deve** consultar o Tessera para obter a informação estrutural. Isso é **obrigatório** para economizar tokens e garantir precisão.

#### Ferramentas disponíveis (via MCP)

| Ferramenta | Uso obrigatório em |
| :--- | :--- |
| `validate` | **Sempre** antes de referenciar um símbolo no código. |
| `find_definition` | Para saber onde um símbolo é definido (arquivo + linha). |
| `impact` | Antes de modificar qualquer função/método público. |
| `context_pack` | **Preferencial** para obter corpo, dependências, chamadores e testes de um símbolo em uma única chamada. |
| `search` | Para encontrar símbolos por padrão (glob/fuzzy) quando não se sabe o nome exato. |
| `find_references` | Para listar todos os usos de um símbolo. |
| `connect` | Para traçar caminhos de chamada entre dois símbolos. |

#### Regras de Ouro

1. **Nunca** use `read` para encontrar uma definição — use `find_definition`.
2. **Nunca** use `grep` para achar chamadores — use `find_references` ou `impact`.
3. **Nunca** escreva código que referencie um símbolo sem antes `validate`-lo.
4. **Prefira `context_pack`** a múltiplas chamadas separadas (é mais barato em tokens).
5. **Evite `get_outline` em diretórios grandes** — prefira `search` ou `context_pack` para alvos específicos. `get_outline` só deve ser usado para arquivos individuais ou diretórios com poucos símbolos.
6. **Reindexe após criar ou renomear arquivos** (`tessera index .`) — senão o grafo fica desatualizado.

#### Fluxo Obrigatório para Qualquer Tarefa

Ao receber uma tarefa que envolva modificar ou entender código, siga **rigorosamente** este fluxo:

1. **Mapeamento inicial**:
   - Use `validate` para confirmar que os símbolos mencionados existem.
   - Use `find_definition` para obter localização e assinatura dos símbolos alvo.
   - Use `impact` para listar todos os chamadores afetados (se for refatoração).

2. **Obtenção de contexto**:
   - Use `context_pack` no(s) símbolo(s) principal(is) para obter corpo, dependências e chamadores em uma chamada.
   - Se precisar de visão de um pacote, use `search` com padrão (ex: `search '*Service' --kind class`) em vez de `get_outline` no diretório inteiro.

3. **Plano de edição**:
   - Com base nas informações do Tessera, liste os arquivos que precisam ser editados.
   - **Só então** use `read` para ler **apenas as partes específicas** necessárias (ex: trechos de 10-20 linhas), não o arquivo inteiro.

4. **Execução**:
   - Realize as edições com `edit`.
   - **Após criar novos arquivos ou renomear símbolos**, execute `tessera index .` (o agente pode pedir para o usuário rodar, ou rodar via terminal integrado se tiver permissão).

5. **Validação pós-edição**:
   - Use `validate` novamente para confirmar que os novos símbolos foram indexados.
   - Use `impact` para verificar se a mudança não quebrou chamadores inesperados.

#### Exemplo prático

**Tarefa:** *"Adicionar um novo método `getCustomBlock` na classe `CustomBlockService`."*

**Comportamento esperado do agente:**
- ✅ `validate CustomBlockService` → confirma que existe.
- ✅ `find_definition CustomBlockService` → obtém assinatura e localização.
- ✅ `impact getCustomBlock` (se já existir) → verifica conflito de nome.
- ✅ `context_pack CustomBlockService` → obtém corpo, dependências e chamadores.
- ✅ Só então gera o código e propõe `edit` no arquivo.
- ✅ Após criar o método, sugere `tessera index .` para atualizar o grafo.

#### Gerenciamento do Índice (Reindexação)

- O índice é **incremental** e rápido (ms). Pode ser executado a qualquer momento.
- O agente **deve** sugerir reindexação sempre que:
  - Criar um novo arquivo.
  - Renomear uma classe, método ou pacote.
  - Mudar a assinatura de um método público.
- Se um `validate` falhar para um símbolo que o agente **acabou de criar**, é sinal de que o índice está desatualizado — o agente deve reindexar e repetir a validação.

#### Nota sobre o escopo

O Tessera está disponível para **todo o código-fonte** do projeto. No entanto, o agente deve **respeitar os limites de escopo** (item 7) ao usar as ferramentas:
- Se uma busca retornar símbolos de áreas **fora do escopo** (ex: adaptadores de terceiros), o agente deve ignorá-los ou mencionar que estão fora do escopo.
- O agente **não deve** usar Tessera para analisar código externo (ex: bibliotecas do Paper) a menos que explicitamente necessário para entender uma dependência.

---

## 🧪 4. INSTRUÇÕES DE TESTE

- **Framework:** JUnit 5 (unitários), ArchUnit (arquitetura), `integrationTest` com Paper.
- **Cobertura mínima:** 80% para novas funcionalidades (quando aplicável).
- **O que mockar:** Bukkit/Paper APIs, `PersistentDataContainer`, `World`, `Player`. Usar Mockito.
- **O que NÃO mockar:** Lógica de domínio pura (deve ser testada com objetos reais). Capabilities devem ser testadas com fakes.
- **Como rodar testes de integração:** `./gradlew integrationTest --no-daemon` (requer um ambiente com Paper ou os JARs corretos configurados via propriedades).
- **Fonte de verdade:** GitHub Actions. Não confiar em execuções locais para validação final.

---

## 🔒 5. CONSIDERAÇÕES DE SEGURANÇA

- **NMS/Reflexão:** Proibido em código de produção. Qualquer uso requer ADR e justificativa.
- **PDC:** Apenas `PersistentDataType.BYTE_ARRAY` para blocos customizados. Nunca serializar objetos Java diretamente.
- **Bukkit/Paper:** Isolar em adaptadores. Nunca passar `Plugin`, `Server`, `World`, `Player`, `ItemStack` para domínio ou mecânicas.
- **Proteções:** Respeitar `Event#isCancelled()` em eventos Bukkit. Não simular eventos de proteção (ex: `BlockBreakEvent`) para bypass.
- **Segredos:** Nenhum segredo de configuração (API keys, etc.) deve estar no código. Usar variáveis de ambiente ou arquivos de configuração ignorados.
- **Logs:** Não logar PII ou informações sensíveis de jogadores.

---

## 📝 6. DIRETRIZES DE COMMIT E PR

### Commits

- **Formato:** `tipo(escopo): mensagem curta`
- **Tipos:** `feat`, `fix`, `docs`, `refactor`, `test`, `chore`, `perf`
- **Escopo:** Ex: `mining`, `mechanic`, `adapter`, `domain`, `docs`
- **Exemplo:** `feat(mining): adiciona suporte a tiers de ferramentas`
- **Corpo:** Explicar o "porquê" e referenciar ADR ou issue, se aplicável.

### Pull Requests

- **Título:** Segue formato de commit.
- **Descrição obrigatória:**
  ```markdown
  ## O que foi feito?
  - [Lista de mudanças]
  
  ## Como testar?
  - [Passos para validar manualmente ou via integração]
  
  ## Breaking Changes?
  - [Sim/Não - se sim, descrever]
  
  ## ADR relacionado?
  - [Número do ADR, se houver]
  
  ## Checklist
  - [ ] GitHub Actions passou (build, test, integrationTest)
  - [ ] Arquitetura fitness (ArchUnit) passou
  - [ ] Documentação atualizada (se necessário)
  ```
- **Tamanho:** Preferir PRs com menos de 400 linhas alteradas. Mudanças maiores devem ser divididas em múltiplos PRs.
- **Revisão:** Pelo menos 1 aprovação de mantenedor.

---

## 🚫 7. ESCOPO E LIMITES (Baseado em PROJECT_SCOPE.md e ADRs)

### ✅ Dentro do Escopo (Permitido)

- Desenvolvimento de funcionalidades relacionadas a **blocos customizados, ferramentas customizadas e itens customizados**.
- Implementação de **mecânicas oficiais** (ex: `area_break`, `block_transform`) como módulos, não como core estável.
- Uso de **capacidades oficiais** (`BLOCK_PLACEMENT`, `MECHANIC_CONFIG`) em mecânicas que precisam de argumentos ou colocação de blocos.
- Refatorações que preservem a **arquitetura hexagonal** e a **pureza do domínio**.
- Escrita de testes unitários e de integração para novas funcionalidades.
- Correção de bugs identificados, desde que alinhados com os guardrails.
- Atualização de documentação (ADRs, milestones, guardrails) com supervisão.

### 🚫 Fora do Escopo (Proibido)

- Implementação de **sistemas genéricos** (economia, quests, GUI, combate, teleporte, scripts).
- **Integração direta com WorldGuard, GriefPrevention** ou outros plugins de proteção, a menos que explicitamente aprovado por ADR.
- **Uso de NMS, reflexão, `runAsync`, `runOnEntity`, `SchedulerAccess`** em mecânicas ou domínio.
- **Declaração de `folia-supported: true`** no `plugin.yml` sem validação formal.
- **Mudanças no formato PDC, YAML schema, ou contratos de mecanica** sem ADR.
- **Criação de APIs públicas estáveis** antes da definição formal de versionamento.
- **Uso de capacidades não validadas** em mecânicas (ex: `BLOCK_PLACEMENT`, `MECHANIC_CONFIG` requerem ADR ou milestone).
- **Configuração de infraestrutura em nuvem** (fora do escopo do agente).
- **Engenharia reversa ou análise de segurança ofensiva**.
- **Migrações de banco de dados** (não há banco externo no MVP).

---

## 👤 8. PERFIL DO PROJETISTA (Viés e Prioridades)

- **Viés de otimização:** Priorizar **legibilidade, testabilidade e pureza** sobre micro-otimizações. A menos que haja evidência de gargalo (ex: spike), manter código claro.
- **Tolerância a riscos:** Baixa tolerância para dependências instáveis ou violações de arquitetura. Alta tolerância para código verboso se aumentar a clareza.
- **Ferramentas preferidas:** Gradle, JUnit, Mockito, ArchUnit, Git, Tessera, MCFAST (edição AST-aware).
- **Foco:** Manter o core estável e conservador, incubando novas ideias como experimentais ou oficiais antes de promover ao core.

---

## ❓ 9. INCERTEZAS E LACUNAS COGNITIVAS

- **Desconhecimento do agente:**
  - Comportamento exato do Paper/Folia em regiões de fronteira (requer spikes).
  - Detalhes de implementação de plugins de proteção (WorldGuard, etc.).
  - Regras de negócio específicas do servidor (não fazem parte do escopo do plugin).
- **Limitações do modelo (kilo-auto/free):**
  - O modelo pode mudar a cada requisição.
  - Em caso de "Rate Limit", suspender tarefa e notificar.
  - Não processar dados sensíveis (tokens, senhas).
  - Ser conciso para economizar tokens.

---

## ⚖️ 10. COMPROMISSOS ESTRUTURAIS

| Critério | Prioridade (1 a 5) | Tolerância / Observação |
| :--- | :--- | :--- |
| **Legibilidade** | 5 | Máxima — código autoexplicativo e documentado |
| **Testabilidade** | 5 | Obrigatório: testes unitários para domínio e aplicação |
| **Segurança** | 4 | Tolerância zero para vazamento de Bukkit/Paper em domínio |
| **Performance (CPU/IO)** | 3 | Tolerar até 15% de overhead se trouxer ganho de legibilidade |
| **Tamanho do código** | 2 | Pode ser verboso se clarear a lógica |
| **Velocidade de entrega** | 3 | Prefiro soluções corretas a soluções rápidas e frágeis |

---

## 🔄 11. CONSERVAÇÃO DA COMPLEXIDADE

Ao final de cada tarefa, reportar:

### 🔍 Relatório de Complexidade:
- **Simplificado:** [O que ficou mais simples? Ex: separação de responsabilidades]
- **Complexidade realocada para:** [Onde a dificuldade foi parar? Ex: no adaptador, na aplicação]
- **Novo gargalo potencial:** [O que pode se tornar o próximo limite? Ex: concorrência, I/O]

---

## 🔬 12. RETROSPECTIVA E APRENDIZADO ADAPTATIVO

### Fases de Auditoria (mapeamento da falha):

1. **Análise de Escopo:** A tarefa estava mal delimitada ou fora do escopo?
2. **Planejamento:** Ignorou dependência, efeito colateral ou edge case?
3. **Execução (Geração de Código):** Sintaxe incorreta, uso errado de API, lógica defeituosa?
4. **Validação:** Não simulou/testou a saída esperada antes de entregar?

### Procedimento ao Receber uma Correção de Erro

1. Identificar a causa raiz.
2. Registrar em `LEARNINGS.md` (ou diretamente no relatório).
3. Aplicar o **Filtro de Escopo Adaptativo**:
   - O erro já ocorreu **pelo menos 2 vezes**?
   - Está **dentro do escopo atual** (custom blocks/tools/items)?
   - É **generalizável** para várias tarefas?
   - ✅ Se atender aos 3, propor atualização do `AGENTS.md`.
   - ❌ Caso contrário, manter apenas no `LEARNINGS.md`.

---

## 🌐 13. DEPENDÊNCIA DE CONTEXTO

- **Ambiente atual:** Desenvolvimento local (simulado) + GitHub Actions para validação.
- **Se o ambiente for `kilo-auto/free` (gratuito):**
  - Seja conciso para economizar tokens.
  - Prefira blocos de código completos em vez de múltiplas confirmações (o modelo pode mudar).
  - Documente a data e o modelo usado para rastrear variações.

---

## 🧠 14. COEVOLUÇÃO ARQUITETURA-PROBLEMA

- Se uma nova funcionalidade exigir mudança estrutural (ex: novo ADR, novo guardrail), proponha a atualização dos documentos relevantes (`PROJECT_SCOPE.md`, `ARCHITECTURE_GUARDRAILS.md`, `AGENTS.md`).
- Registre no `LEARNINGS.md` cada vez que uma nova limitação for descoberta.

---

## 🛠️ 15. INSTRUÇÕES OPERACIONAIS — FORMATO DE SAÍDA

Ao receber qualquer tarefa, siga **estes 5 passos obrigatórios**:

1. **Verificação de Escopo:**  
   - "✅ Dentro do escopo" ou "🚫 Fora do escopo" com referência ao documento relevante (ex: PROJECT_SCOPE.md, ADR).  
   - Se fora, justifique e pare.

2. **Plano de Ação (usando Tessera obrigatoriamente):**  
   - Use `validate` e `find_definition` para mapear os símbolos envolvidos.
   - Use `impact` para listar chamadores (se for refatoração).
   - Use `context_pack` no símbolo principal para obter corpo, dependências e chamadores em uma chamada.
   - **Só depois** de esgotar as ferramentas do Tessera, liste os arquivos que serão modificados ou criados (caminhos completos).
   - Mencione quais ADRs/guardrails são afetados.

3. **Execução (com validação contínua):**  
   - Gere o código, análise ou documentação, respeitando a arquitetura.
   - Ao gerar código que referencia símbolos existentes, use `validate` antes de cada referência.
   - **Nunca** use `grep` ou `read` para encontrar definições — use `find_definition`.
   - **Nunca** leia um arquivo inteiro para obter contexto — use `context_pack`.
   - Se precisar de uma visão geral de um pacote, use `search` com padrão (ex: `search '*Service'`) em vez de `get_outline` no diretório.

4. **Relatório de Trade-off:**  
   - Indique o que foi sacrificado (ex: simplicidade, performance) e por quê.
   - Mencione o custo em tokens economizado pelo uso do Tessera (opcional).

5. **Relatório de Complexidade e Reindexação:**  
   - Conforme o item 11.
   - Se novos arquivos foram criados ou símbolos renomeados, **sugira** `tessera index .` para atualizar o grafo (ou execute, se tiver permissão).
   - Indique se a reindexação foi realizada e se os novos símbolos foram validados.

---

## 📜 16. HISTÓRICO DE DECISÕES

- `2026-06-20 (v1.0)`: Criação do agente genérico (Python/JS).
- `2026-06-20 (v1.1)`: Incorporação do modelo de Retrospectiva e Aprendizado Adaptativo.
- `2026-06-20 (v1.2)`: Incorporação do padrão oficial AGENTS.md.
- `2026-06-20 (v2.0)`: Adaptação completa para CustomContent Engine, alinhado com PROJECT_SCOPE.md, ARCHITECTURE_GUARDRAILS.md, ADRs e milestones.
- `2026-06-25 (v2.1)`: Integração oficial do Tessera para navegação determinística de código (item 3.5).
- `2026-06-25 (v2.1.1)`: Integração oficial do Tessera para navegação determinística de código (item 3.5).
- `2026-06-25 (v2.1.2)`: Adição do MCFAST-MCP como MCP secundário para edição AST-aware de arquivos, complementando o Tessera.

---

## ⚠️ 17. COROLÁRIO DA TESTABILIDADE (Previsões falsificáveis)

- a) O agente não tentará resolver tarefas fora do escopo declarado (custom blocks, tools, items).
- b) O agente documentará vieses mensuráveis ligados às métricas de otimização (ex: legibilidade vs performance).
- c) O aumento de capacidade (mais contexto) não implicará compreensão automática de domínios fora do escopo.
- d) A eliminação de um gargalo (ex: processamento síncrono) revelará novos gargalos em níveis superiores (ex: concorrência).
- e) O agente só proporá evoluções no `AGENTS.md` após o erro se repetir e for generalizável (evitando inflação de escopo).

---

## 📚 18. REFERÊNCIAS OBRIGATÓRIAS

- `docs/PROJECT_SCOPE.md` — Escopo, limites, design principles.
- `docs/ARCHITECTURE_GUARDRAILS.md` — Regras arquiteturais, dependências, fitness functions.
- `docs/adr/*.md` — Decisões arquiteturais aceitas.
- `docs/milestones/*.md` — Marcos completos e planejados.
- `docs/AI_CONTEXT_PACK.md` — Resumo derivado (não substitui os documentos fonte).
- `docs/MCFAST_GUIDE.md` (opcional) — Guia detalhado de uso do MCFAST-MCP (caso criado).

---

**Fim do AGENTS.md — v2.1.2 (CustomContent Engine com Tessera obrigatório, MCFAST para edição AST-aware e reindexação automatizada)**
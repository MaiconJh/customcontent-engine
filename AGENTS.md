- **Documentação fonte:** `docs/PROJECT_SCOPE.md`, `docs/ARCHITECTURE_GUARDRAILS.md`, `docs/adr/*.md`, `docs/milestones/*.md`.

---

## 🔧 2. COMANDOS DE BUILD, TESTE E DESENVOLVIMENTO

- **Build do plugin:** `./gradlew build --no-daemon`
- **Testes unitários:** `./gradlew test --no-daemon`
- **Testes de integração (Paper):** `./gradlew integrationTest --no-daemon` (requer `-Dcustomcontent.paperJar=<caminho>`)
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
| `find-definition` | Para saber onde um símbolo é definido (arquivo + linha). |
| `impact` | Antes de modificar qualquer função/método público. |
| `context-pack` | **Preferencial** para obter corpo, dependências, chamadores e testes de um símbolo em uma única chamada. |
| `search` | Para encontrar símbolos por padrão (glob/fuzzy) quando não se sabe o nome exato. |
| `find-references` | Para listar todos os usos de um símbolo. |
| `connect` | Para traçar caminhos de chamada entre dois símbolos. |

#### Regras de Ouro

1. **Nunca** use `read` para encontrar uma definição — use `find-definition`.
2. **Nunca** use `grep` para achar chamadores — use `find-references` ou `impact`.
3. **Nunca** escreva código que referencie um símbolo sem antes `validate`-lo.
4. **Prefira `context-pack`** a múltiplas chamadas separadas (é mais barato em tokens).
5. **Evite `get-outline` em diretórios grandes** — prefira `search` ou `context-pack` para alvos específicos. `get-outline` só deve ser usado para arquivos individuais ou diretórios com poucos símbolos.
6. **Reindexe após criar ou renomear arquivos** (`tessera index .`) — senão o grafo fica desatualizado.

#### Fluxo Obrigatório para Qualquer Tarefa

Ao receber uma tarefa que envolva modificar ou entender código, siga **rigorosamente** este fluxo:

1. **Mapeamento inicial**:
 - Use `validate` para confirmar que os símbolos mencionados existem.
 - Use `find-definition` para obter localização e assinatura dos símbolos alvo.
 - Use `impact` para listar todos os chamadores afetados (se for refatoração).

2. **Obtenção de contexto**:
 - Use `context-pack` no(s) símbolo(s) principal(is) para obter corpo, dependências e chamadores em uma chamada.
 - Se precisar de visão de um pacote, use `search` com padrão (ex: `search '*Service' --kind class`) em vez de `get-outline` no diretório inteiro.

3. **Plano de edição**:
 - Com base nas informações do Tessera, liste os arquivos que precisam ser editados.
 - **Só então** use `read` para ler **apenas as partes específicas** necessárias (ex: trechos de 10-20 linhas), não o arquivo inteiro.

4. **Execução**:
 - Realize as edições com `edit`.
 - **Após criar novos arquivos ou renomear símbolos**, execute `tessera index .` (o agente pode pedir para o usuário rodar, ou rodar via terminal integrado se tiver permissão).

5. **Validação pós-edição**:
 - Use `validate` novamente para confirmar que os novos símbolos foram indexados.
 - Use `impact` para verificar se a mudança não quebrou chamadores inesperados.

#### Tratamento de Erros e Fallback

O agente deve estar preparado para situações em que o Tessera não esteja disponível ou falhe. Nesses casos, siga estas diretrizes:

1. **Verificação de disponibilidade:** Antes de iniciar o fluxo, o agente pode verificar se o Tessera está instalado e se o índice existe. Se não, deve:
 - Informar o usuário sobre a necessidade de instalar o Tessera (`npm i -g tessera-codegraph`) e indexar o repositório (`tessera index .`).
 - **Não** tentar usar `grep` ou `read` como fallback imediato — isso violaria as regras de economia de tokens e precisão. Em vez disso, solicitar ao usuário que execute os comandos necessários e aguardar.

2. **Falhas de comando:** Se um comando do Tessera falhar (ex: `validate` retorna erro), o agente deve:
 - Registrar o erro e tentar novamente uma vez (pode ser um problema transitório).
 - Se a falha persistir, informar o usuário com o erro exato e sugerir a reindexação (`tessera index .`) ou verificar a instalação.
 - **Não** prosseguir com a tarefa até que o problema seja resolvido, a menos que o usuário autorize explicitamente o uso de métodos alternativos (o que deve ser registrado como exceção).

3. **Ambiente Windows/PowerShell:** O agente **não deve** usar comandos Unix-like (`head`, `tail`, `grep`, `ls`) em scripts ou ao interagir com o sistema. Em vez disso, deve:
 - Usar apenas os comandos do Tessera (que são independentes de plataforma) para navegação.
 - Se precisar listar diretórios ou arquivos, usar comandos compatíveis com PowerShell (`Get-ChildItem`, `Select-Object -First N`) ou, preferencialmente, as ferramentas de busca do Tessera (`search`, `context-pack`).

4. **Falha na validação de símbolos:** Se `validate` retornar negativo para um símbolo que o agente acredita existir, ele deve:
 - Verificar a grafia (pode ser um erro de digitação).
 - Usar `search` com um padrão aproximado para encontrar o símbolo real.
 - Se o símbolo realmente não existir, informar ao usuário e ajustar o plano.

5. **Índice desatualizado:** Se um `validate` falhar para um símbolo recém-criado, o agente deve:
 - Solicitar reindexação (`tessera index .`).
 - Repetir a validação.
 - Se ainda falhar, reportar como possível erro de indexação.

#### Exemplo prático com tratamento de erros

**Tarefa:** *"Adicionar um novo método `getCustomBlock` na classe `CustomBlockService`."*

**Comportamento esperado do agente:**
- ✅ `validate CustomBlockService` → se falhar, pedir reindexação.
- ✅ `find-definition CustomBlockService` → obtém localização.
- ✅ `impact getCustomBlock` (se já existir) → verifica conflito.
- ✅ `context-pack CustomBlockService` → obtém corpo e dependências.
- ✅ Só então gera o código e propõe `edit`.
- ✅ Após criar o método, sugere `tessera index .` e valida novamente.
- ❌ Se `tessera` não estiver instalado, interrompe e pede instalação.
- ❌ Se ocorrer erro no PowerShell (ex: comando `head`), usa apenas comandos Tessera ou pede ajuda.

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
- **Como rodar testes de integração:** `./gradlew integrationTest --no-daemon` (requer `-Dcustomcontent.paperJar=<caminho>`).
- **Fonte de verdade:** GitHub Actions. Não confiar em execuções locais para validação final.

### 4.1. Estratégia de Integração Obrigatória (ADR-0013)

Conforme formalizado no **ADR-0013 (Test Integration Strategy)**, os testes de integração com Paper são **obrigatórios** para:
- Validação de novas mecânicas oficiais (ex: `vein_miner`, `block_transform`).
- Validação de interações com o `PersistentDataContainer`, `SchedulerPort` e eventos do Bukkit/Paper.
- Garantia de que o comportamento em um servidor real corresponde ao esperado pelos testes unitários.

**Regras operacionais para agentes e desenvolvedores:**

1. **Toda nova mecânica oficial (oficial module) deve incluir, no mínimo, um teste de integração Paper** que valide seu fluxo principal (execução, resultado e efeitos colaterais no mundo/inventário).
2. **Os testes de integração devem estender a classe base** `BasePaperIntegrationTest` (localizada em `src/integrationTest/java/com/customcontentengine/integration/base/`), que gerencia o ciclo de vida do servidor e fornece utilitários padronizados.
3. **Para testes que exigem dependências falsas** (ex: `ProtectionPort`, `ToolWearPort`), utilize `TestCustomContentPlugin` (que estende `CustomContentPlugin`) para sobrescrever as dependências via setters (`setProtectionPort`, `setToolWearPort`) **antes** da inicialização do plugin.
4. **O plano de implementação detalhado** (fases 0 a 4) está documentado em `docs/TEST_INTEGRATION_PLAN.md`. Consulte-o para entender a ordem de prioridade e os cenários específicos.
5. **Em CI (GitHub Actions):** A falha em qualquer teste de integração bloqueia o merge do PR. O timeout por suíte é de 10 minutos.

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

#### Formato

Os commits devem seguir o padrão **Conventional Commits** (inspirado em [conventionalcommits.org](https://www.conventionalcommits.org/)):

```
<tipo>(<escopo opcional>): <descrição curta em imperativo presente>

[corpo opcional explicando o "porquê" da mudança]

[rodapé opcional com referências a issues, breaking changes, etc.]
```

#### Tipos permitidos

| Tipo | Uso |
| :--- | :--- |
| `feat` | Nova funcionalidade para o usuário final (ex: nova mecânica, novo comando) |
| `fix` | Correção de bug |
| `docs` | Mudanças apenas na documentação |
| `refactor` | Refatoração de código sem mudança de comportamento |
| `test` | Adição ou correção de testes |
| `chore` | Tarefas de manutenção (build, dependências, configuração) |
| `perf` | Melhoria de performance |

#### Escopo (opcional)

O escopo deve indicar a área afetada, ex: `mining`, `mechanic`, `adapter`, `domain`, `docs`, `test`, `build`.

#### Regras de formatação

1. **Primeira linha (título):** máximo de **50 caracteres**.
2. **Corpo:** máximo de **72 caracteres** por linha. Use para explicar **o que** mudou e **por quê**, não apenas **como**.
3. **Separe título e corpo** com uma linha em branco.
4. **Use o imperativo presente** no título: "Add", "Fix", "Refactor" — não "Added" ou "Adds".
5. **Seja específico e descritivo.** Evite mensagens vagas como "update", "fix bug" ou "stuff".
6. **Um commit = uma mudança lógica.** Commits devem ser coesos e focados em uma única tarefa.

#### Corpo do commit

O corpo deve explicar:
- **O contexto** da mudança.
- **O motivo** da mudança (por que ela é necessária).
- **Qualquer impacto** ou trade-off relevante.
- **Referências a ADRs, issues ou tarefas**, quando aplicável.

#### Rodapé (opcional)

Use o rodapé para:
- **Referenciar issues** com `Closes #123` ou `Fixes #456`.
- **Marcar breaking changes** com `BREAKING CHANGE:` seguido da descrição.

#### Exemplos

**Commit simples (sem corpo):**
```
feat(mining): adiciona suporte a tiers de ferramentas
```

**Commit com corpo e referência:**
```
fix(adapter): corrige vazamento de Bukkit em VeinMinerEventTriggerService

Remove import morto de org.bukkit.inventory.ItemStack que estava
vazando a dependência para a camada application.

Closes #42
```

**Commit com breaking change:**
```
refactor(api): altera assinatura de Mechanic.execute()

Agora recebe MechanicContext em vez de parâmetros avulsos.

BREAKING CHANGE: Qualquer mecânica personalizada deve ser
atualizada para usar a nova assinatura.
```

---

### Pull Requests

#### Título

O título do PR deve seguir o mesmo formato de commit:

```
<tipo>(<escopo>): <descrição curta>
```

#### Descrição obrigatória

Todo PR deve incluir a seguinte estrutura:

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
- [ ] Testes de integração Paper adicionados/atualizados (conforme ADR-0013)
- [ ] `AI_CHANGELOG.md` atualizado com a run correspondente (se aplicável)
```

#### Tamanho do PR

- Prefira PRs com **menos de 400 linhas alteradas**.
- Mudanças maiores devem ser divididas em múltiplos PRs.
- Isso facilita a revisão e reduz o risco de conflitos.

#### Revisão

- Pelo menos **1 aprovação de mantenedor** é necessária antes do merge.
- PRs com testes de integração falhando **não podem ser mesclados**.

---

#### Boas práticas adicionais

1. **Commits atômicos:** Cada commit deve representar uma mudança lógica e completa. Evite commits que misturam correções com novas funcionalidades.
2. **Mensagens descritivas:** Se o corpo for necessário, use-o. Não dependa apenas do título.
3. **Revise antes de commitar:** Leia sua mensagem em voz alta — ela faz sentido para quem não estava no contexto?
4. **Use `--amend` com cuidado:** Apenas para ajustes locais antes do push. Não force push em branches compartilhados sem acordo.
5. **Referencie ADRs e issues:** Isso conecta o código às decisões arquiteturais e ao planejamento.

---

## 🚫 7. ESCOPO E LIMITES (Baseado em PROJECT_SCOPE.md e ADRs)

### ✅ Dentro do Escopo (Permitido)

- Desenvolvimento de funcionalidades relacionadas a **blocos customizados, ferramentas customizadas e itens customizados**.
- Implementação de **mecânicas oficiais** (ex: `area_break`, `block_transform`) como módulos, não como core estável.
- Uso de **capacidades oficiais** (`BLOCK_PLACEMENT`, `MECHANIC_CONFIG`) em mecânicas que precisam de argumentos ou colocação de blocos.
- Refatorações que preservem a **arquitetura hexagonal** e a **pureza do domínio**.
- Escrita de testes unitários e de integração para novas funcionalidades (os de integração são **obrigatórios** para mecânicas oficiais, conforme ADR-0013).
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
| **Testabilidade** | 5 | Obrigatório: testes unitários para domínio e aplicação; **testes de integração Paper para mecânicas oficiais (ADR-0013)** |
| **Segurança** | 4 | Tolerância zero para vazamento de Bukkit/Paper em domínio |
| **Performance (CPU/IO)** | 3 | Tolerar até 15% de overhead se trouxer ganho de legibilidade |
| **Tamanho do código** | 2 | Pode ser verboso se clarear a lógica |
| **Velocidade de entrega** | 3 | Prefiro soluções corretas a soluções rápidas e frágeis |

---

## 🔄 11. CONSERVAÇÃO DA COMPLEXIDADE

Ao final de cada tarefa, o agente **deve** gerar um relatório estruturado que documente:
- O que mudou no código.
- Onde a complexidade foi realocada.
- Quais arquivos foram criados, modificados ou removidos.
- Um resumo prático das alterações em cada arquivo.

Isso garante rastreabilidade, facilita code reviews e mantém o histórico de decisões técnicas.

---

### 📋 Relatório de Finalização de Tarefa

O relatório deve seguir este formato:

## 🔍 Relatório de Complexidade

### 📁 Arquivos Modificados

| Arquivo | Tipo de Alteração | Resumo das Mudanças |
| :--- | :--- | :--- |
| `src/main/java/.../X.java` | Modificado | Adicionado método `foo()`; refatorada lógica de validação para usar `BarService`. |
| `src/test/java/.../XTest.java` | Modificado | Adicionados testes para o novo método `foo()` cobrindo casos de sucesso e erro. |
| `src/main/java/.../Y.java` | Criado | Nova classe para encapsular a lógica de transformação; implementa `TransformPort`. |
| `src/main/resources/definitions.yml` | Modificado | Adicionado bloco `example_block` com `numeric_id: 42` para teste. |

### 📊 Resumo da Complexidade

- **Simplificado:** [O que ficou mais simples? Ex: separação de responsabilidades, eliminação de duplicação]
- **Complexidade realocada para:** [Onde a dificuldade foi parar? Ex: no adaptador, na aplicação, no novo serviço]
- **Novo gargalo potencial:** [O que pode se tornar o próximo limite? Ex: concorrência, I/O, crescimento do índice PDC]

### 📝 Registro no AI Changelog

**Obrigatoriamente**, antes de finalizar a tarefa, o agente deve atualizar o arquivo [`AI_CHANGELOG.md`](../../AI_CHANGELOG.md) na raiz do repositório, criando uma nova entrada com o **próximo número sequencial** (ex: `[0003] - YYYY-MM-DD`) e descrevendo as mudanças realizadas nesta run.

A entrada deve seguir o formato:

## [0003] - 2026-07-15

### 🔧 Run 3 – [Título resumido da tarefa]

#### Added
- **Descrição do que foi adicionado** – detalhes, arquivos afetados, motivo.

#### Changed
- **Descrição do que foi alterado** – detalhes, arquivos afetados, impacto.

#### Fixed
- **Descrição do que foi corrigido** – bug, arquivo, solução.

#### Removed
- **Descrição do que foi removido** – arquivo, funcionalidade, motivo.

#### Deprecated
- **Descrição do que foi depreciado** – funcionalidade, alternativa sugerida.

> **Nota:** Se a tarefa não envolveu alterações de código (ex: apenas análise ou planejamento), ainda assim deve ser registrada, com os campos preenchidos como `(Nenhuma alteração de código)` ou similar.

### ✅ Checklist de Validação Pós‑Tarefa

- [ ] `./gradlew test --no-daemon` passou localmente (se houver alterações de código).
- [ ] `./gradlew integrationTest --no-daemon` passou (se aplicável).
- [ ] `tessera index .` foi executado e a validação de símbolos está OK (se houver novos arquivos/símbolos).
- [ ] Arquitetura fitness (ArchUnit) não foi violada.
- [ ] Documentação atualizada (se necessário).
- [ ] Commits seguem o padrão `tipo(escopo): mensagem`.
- [ ] **`AI_CHANGELOG.md` foi atualizado** com a nova entrada da run.

**Instruções para o agente:**
- Preencha a tabela com **todos** os arquivos que sofreram alterações (criação, modificação, exclusão).
- No resumo, seja **conciso**, mas específico o bastante para que um revisor entenda o impacto da mudança sem precisar abrir cada arquivo.
- **Nunca finalize uma tarefa sem atualizar o `AI_CHANGELOG.md`** – isso é tão importante quanto os testes.
- Se a tarefa envolveu múltiplos commits, o relatório pode ser consolidado por arquivo, não por commit.
- Se nenhum arquivo foi alterado (ex: tarefa de análise), justifique no relatório e registre no changelog como `(Nenhuma alteração de código)`.

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
 - Use `validate` e `find-definition` para mapear os símbolos envolvidos.
 - Use `impact` para listar chamadores (se for refatoração).
 - Use `context-pack` no símbolo principal para obter corpo, dependências e chamadores em uma chamada.
 - **Só depois** de esgotar as ferramentas do Tessera, liste os arquivos que serão modificados ou criados (caminhos completos).
 - Mencione quais ADRs/guardrails são afetados.

3. **Execução (com validação contínua):**  
 - Gere o código, análise ou documentação, respeitando a arquitetura.
 - Ao gerar código que referencia símbolos existentes, use `validate` antes de cada referência.
 - **Nunca** use `grep` ou `read` para encontrar definições — use `find-definition`.
 - **Nunca** leia um arquivo inteiro para obter contexto — use `context-pack`.
 - Se precisar de uma visão geral de um pacote, use `search` com padrão (ex: `search '*Service'`) em vez de `get-outline` no diretório.

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
- `2026-07-11 (v2.1.3)`: **Incorporação do ADR-0013 (Test Integration Strategy).** Adicionada a obrigatoriedade de testes de integração Paper para novas mecânicas oficiais, criação da `BasePaperIntegrationTest` e do `TestCustomContentPlugin` para injeção de dependências em testes. Atualização das seções 4 (Testes) e 6 (PR Checklist).
- `2026-07-15 (v2.1.4)`: **Aprimoramento das seções 3.5 e 11.** Adicionadas diretrizes detalhadas para tratamento de erros do Tessera, fallback e compatibilidade com Windows/PowerShell; expansão da seção 11 com relatório prático de arquivos alterados, checklist de validação e obrigatoriedade de registro no `AI_CHANGELOG.md`.

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
- **`docs/adr/0013-test-integration-strategy.md`** — Estratégia formal de testes de integração (obrigatória para novas mecânicas).
- **`docs/TEST_INTEGRATION_PLAN.md`** — Plano de implementação detalhado (fases 0 a 4) para os testes de integração.
- **`AI_CHANGELOG.md`** — Registro histórico de alterações realizadas por agentes (raiz do repositório).

---

**Fim do AGENTS.md — v2.1.4 (CustomContent Engine com diretrizes aprimoradas para Tessera, relatório de complexidade detalhado e registro obrigatório no AI Changelog)**
# AI Context Pack - CustomContent Engine

Status: Derived guidance  
Generated: 2026-06-08  
Purpose: compact operational context for AI review, governance, planning, and future implementation assistance.

> This file is a condensed context pack for AI tools. It does not replace the source documents. If this file conflicts with `docs/PROJECT_SCOPE.md`, `docs/ARCHITECTURE_GUARDRAILS.md`, accepted ADRs, or milestone documents, the original documents win.

---

## 1. Source Of Truth

Primary sources:

- `docs/PROJECT_SCOPE.md`
- `docs/ARCHITECTURE_GUARDRAILS.md`
- `docs/adr/*.md`
- `docs/milestones/*.md`
- `.github/workflows/build-test.yml`
- `docs/CI_AI_REVIEW_BOT.md`
- `docs/AI_CONTINUOUS_EVOLUTION_ARCHITECTURE.md`

Operational rule:

- GitHub Actions is the validation source of truth.
- AI reports are advisory.
- Kilo/Worker output must be checked against diff, CI logs, and documentation.
- Humans remain the final gate for scope, ADR approval, and merge decisions.

---

## 2. Project Identity

Project: **CustomContent Engine**

One-sentence definition:

> A modular, high-performance Paper 1.21+ engine for creating custom blocks, custom tools, and custom items through a pure domain core, binary PDC persistence, and conservative-but-evolvable extensibility based on explicit capabilities and controlled extension points.

Primary platform:

- Paper 1.21+

Folia:

- Architectural objective.
- Not a final support promise until ownership, scheduling, and cross-region behavior are validated.
- `plugin.yml` must not declare `folia-supported: true` without explicit validation and governance.

Pure Spigot:

- Not officially supported.

---

## 3. Current Status

Completed milestones:

- MVP-0: foundation without mechanics.
- MVP-1: first controlled builtin mechanic, `area_break`.
- Post-MVP-1 YAML mechanic bindings.
- Post-MVP-1 conservative runtime ownership guard.
- MVP-2 Custom Mining.
- MVP-3 Custom Durability.
- Tool Tiers: official (promoted from experimental after incubation).
- BlockTransformMechanic: official (first mechanic after area_break, uses MECHANIC_CONFIG and BLOCK_PLACEMENT).

Current major implemented systems:

- YAML definitions loaded at startup.
- Immutable definition registry.
- Custom item identity.
- Custom block identity persisted through compact binary PDC.
- Custom block placement and break lifecycle.
- Custom drops.
- Mechanic contract with explicit capabilities.
- `area_break` builtin mechanic.
- YAML `mechanics.on_block_break` bindings.
- Region safety abstraction.
- Custom mining model:
  - `MiningHardness`.
  - `MiningSpeed`.
  - `ToolTier` (official).
  - `BlockTierRequirement` (official).
  - optional YAML `blocks.<id>.mining.hardness`.
  - optional YAML `items.<id>.mining.speed`.
  - optional YAML `blocks.<id>.mining.required_tier`.
  - optional YAML `items.<id>.mining.tier`.
  - tier eligibility validation in `MiningSessionService.isTierEligible()`.
  - `MiningSession`.
  - absolute-time progress.
  - visual mining stages.
  - `MiningSessionService`.
  - `MiningVisualPort`.
  - `BukkitMiningVisualAdapter`.
  - `MiningInputAdapter`.
  - `MiningRuntimeProcessor`.
  - `CustomMiningCompletionService`.
  - real completion that removes custom block identity, sets block to `AIR`, emits configured drops once, and triggers `mechanics.on_block_break` once.
- GitHub Actions remote build/test/integration validation.
- CI AI Review Bot with Cloudflare Worker, Kilo provider, fallback, documentation context, and governance/interceptor review.

---

## 4. Core Product Scope

The base product focus is strictly:

1. Custom blocks.
2. Custom tools.
3. Custom items.

Every stable-core feature, official mechanic, trigger, or extension point must be directly connected to at least one of these three elements.

Allowed core concerns:

- Define custom blocks, tools, and items.
- Identify custom content.
- Validate definitions.
- Persist custom block identity.
- Register immutable definitions.
- Execute mechanics through explicit contracts.
- Expose narrow capabilities.
- Return abstract execution results.
- Preserve platform isolation.

The engine must not become a generic:

- economy plugin;
- quest framework;
- combat framework;
- teleportation framework;
- GUI/menu manager;
- scripting platform;
- generic ability framework;
- land protection system.

---

## 5. Architecture Rules

Dependency direction:

```text
adapter      -> port
adapter      -> application
application  -> port
application  -> domain
application  -> internalapi
builtin      -> internalapi
builtin      -> domain
bootstrap    -> all modules for manual composition
domain       -> no external dependencies
```

Forbidden dependencies:

```text
domain       -> org.bukkit
domain       -> io.papermc
domain       -> net.minecraft
domain       -> adapter
domain       -> YAML/PDC
application  -> adapter
application  -> Bukkit/Paper/Folia
builtin      -> adapter
builtin      -> bootstrap
mechanic     -> Bukkit/Paper/Folia/NMS/Plugin/Server/World/Player/Block/ItemStack
mechanic     -> DefinitionRegistry
mechanic     -> BlockService/ItemService
mechanic     -> scheduler access
listener     -> complex business logic
core         -> experimental
core         -> devtools
```

Layer expectations:

- Domain must be pure Java records/classes and testable without a server.
- Application orchestrates use cases and must not import Bukkit/Paper/adapters.
- Ports define dependency inversion interfaces.
- Adapters own Bukkit/Paper/PDC/YAML/platform behavior.
- Bootstrap composes modules but must not contain complex business logic.
- Builtin mechanics remain pure, stateless, and platform-independent.

---

## 6. Scheduler, Folia, And Runtime Safety

Current scheduler contract:

```java
void runOnRegion(WorldPosition position, Runnable task);
```

Rules:

- `SchedulerPort` remains limited to `runOnRegion` unless an ADR changes it.
- `runAsync` is out of scope.
- `runOnEntity` is out of scope.
- `SchedulerAccess` is forbidden.
- Mechanics must not schedule work directly.
- World mutation must go through ports/adapters.
- `RegionSafetyPort` must be respected before unsafe query or mutation.
- Folia support must not be declared final until validation is complete.

Same-region-safe principle:

- Runtime may process positions that are safe/owned.
- Unsafe or unprocessed positions must be represented as pure failure/absence/remaining work.
- Mechanics stay unaware of Folia ownership.

---

## 7. Mechanic Model

Mechanics are internal engine extension units, not public API.

Mechanic contract:

```java
MechanicDescriptor descriptor();
MechanicResult execute(MechanicContext context);
```

Mechanic descriptor contains:

- `MechanicId id`
- `Set<Capability> requiredCapabilities`
- `boolean readOnly`

Current core capabilities:

- `BLOCK_QUERY`
- `BLOCK_MUTATION`
- `BLOCK_PLACEMENT`
- `BUDGET_VIEW`
- `COOLDOWN_VIEW`
- `DROP_SINK`
- `EXECUTION_ORIGIN`
- `MECHANIC_CONFIG`

Mechanic result:

- `Done(int affectedBlocks)`
- `Partial(int affectedBlocks, List<WorldPosition> remaining)`
- `Rejected(String reason)`

Mechanic rules:

- Mechanics must be stateless and reusable.
- Mechanics must declare capabilities explicitly.
- Mechanics receive access only through `MechanicContext.require(...)`.
- Mechanics must not receive Bukkit/Paper objects.
- Mechanics must not access services, registries, schedulers, PDC, YAML, adapters, or platform internals.
- `MechanicExecutor` owns cooldown, budget, context creation, execution, `Partial` interpretation, and rescheduling.

Current official builtin mechanics:

- `area_break`
- `block_transform`

`area_break` rules:

- Pure and stateless.
- Uses `ExecutionOrigin` capability.
- Processes additional blocks around origin.
- Does not own scheduler logic.
- Does not know mining exists.
- Continues to run through `mechanics.on_block_break` after custom mining completion when configured.

---

## 8. YAML Mechanics

Accepted format:

```yaml
items:
  ruby_pickaxe:
    material_base: DIAMOND_PICKAXE
    custom_model_data: 1001
    attributes:
      damage: 5.0
      speed: 1.2
      durability: 500
    mechanics:
      on_block_break:
        - area_break
```

Rules:

- `mechanics` is optional.
- Only `on_block_break` is currently supported.
- `on_block_break` is a list of `MechanicId` values or mechanic entries with optional `arguments`.
- Referenced mechanics must exist in `MechanicRegistry`.
- Current official/builtin mechanics accepted in this phase: `area_break`, `block_transform`.
- `block_transform` accepts arguments via `MECHANIC_CONFIG`: `to_block`, `drop_original`, `consume_budget`.
- No conditions.
- No expressions.
- No scripting.
- No permission expressions.
- No cooldown/budget YAML configuration.
- Schema remains `1` because the change is additive/backward compatible.

Example with arguments:

```yaml
items:
  ruby_pickaxe:
    mechanics:
      on_block_break:
        - block_transform
          arguments:
            to_block: 42
            drop_original: true
```

---

## 9. Custom Mining Model

Custom mining is the MVP-2 direction and is complete.

Model:

- custom block hardness;
- custom tool mining speed;
- mining session;
- absolute-time mining progress;
- visual progress stage;
- explicit cancellation;
- controlled completion;
- custom drops;
- `mechanics.on_block_break` integration after successful completion.

Entry/cancellation events:

- `BlockDamageEvent` starts or refreshes a mining session.
- `BlockDamageAbortEvent` cancels a matching session.
- `PlayerQuitEvent` clears session/visual state.
- `PlayerItemHeldEvent` cancels if the tool changes.

Performance rules:

- No world scan.
- No chunk scan.
- No block scan.
- No scan of all players.
- No scan of all custom blocks.
- Process only active sessions.
- One active session per `actorKey`.
- Use absolute time, not per-tick accumulation as source of truth.
- Update visual progress only when stage changes.
- Use bounded processing.
- Completion must be idempotent.

Completion path:

1. Confirm session validity.
2. Confirm target block is still a custom block.
3. Confirm tool/session is still valid.
4. Check `RegionSafetyPort` before mutation.
5. Remove custom block identity once.
6. Set platform block to `AIR` once.
7. Emit configured drops once.
8. Trigger `mechanics.on_block_break` once if configured.
9. Clear visual progress once.
10. Remove session.

Forbidden in custom mining:

- fake `BlockBreakEvent`;
- `Bukkit#callEvent` to simulate break;
- duplicate drops;
- duplicate mechanic execution;
- duplicate identity removal;
- global polling;
- NMS/reflection.

---

## 10. Stability And Core Evolution

The stable core is conservative but evolvable.

Default rule:

- Reject new features from stable core by default.
- Incubate new ideas outside stable core first.

Incubation pipeline:

```text
Idea
-> Technical Spike
-> Experimental Module or Experimental Contract
-> Official Module
-> Candidate Stable Contract
-> Stable Core
```

A candidate may enter stable core only if it is:

- structural;
- recurring;
- platform-independent;
- broadly reused;
- simpler than keeping it outside core;
- directly connected to custom blocks, tools, or items;
- protected by tests/fitness functions;
- supported by ADR/spike/proof.

Stability levels:

1. Internal.
2. Experimental.
3. Official.
4. Stable.
5. Deprecated.

Rules:

- Nothing becomes stable by accident.
- `internalapi` is internal during MVP despite the name.
- Public API requires explicit declaration, ADR, and versioning policy.
- Official modules are maintained but not automatically stable core.

---

## 11. Capability Governance

`MechanicContext` must not become a service locator.

Forbidden patterns:

```java
ctx.getPlugin();
ctx.getServer();
ctx.getWorld();
ctx.getBlockService();
ctx.getDefinitionRegistry();
ctx.getScheduler();
ctx.getAdapter();
```

Allowed pattern:

```java
BlockMutation mutation = ctx.require(BlockMutation.class);
```

Capability categories:

- Core capabilities.
- Module capabilities.
- Forbidden stable-core capabilities.

Forbidden stable-core capability families unless product scope is formally redefined:

- Economy.
- Quest.
- Combat.
- Teleport.
- GUI/menu.
- Generic scripting.
- Generic permission management beyond interaction validation.

---

## 12. Architecture Fitness Functions

The project must preserve automated architecture checks.

Required checks include:

- Domain must not depend on Bukkit/Paper/Spigot/Folia/NMS/YAML/PDC/adapters.
- Application must not depend on adapters/Bukkit/Paper/Folia/NMS/YAML implementations.
- Builtin mechanics must not depend on adapters, services, schedulers, registries, or platform APIs.
- Adapters must not be imported by domain/internalapi/builtin/application.
- No reflection or NMS in production code.
- No disk I/O/YAML parsing/blocking database/network access in hot paths.
- No unbounded loops in mechanics.
- Area operations must respect work budget.
- New mechanics must declare capabilities.
- New stable-core contracts require ADR.
- New extension points start experimental.

Validation source:

- GitHub Actions remote workflow.
- Local Gradle validation is not required for AI automation flows.

---

## 13. CI And AI Governance

GitHub Actions:

- Source of truth for `test`, `build`, and `integrationTest`.
- Runs on GitHub-hosted Linux runners with Java 21.
- Uses Gradle Wrapper in the repository.
- Uploads reports/artifacts when available.

CI AI Review Bot:

- Collects git diff.
- Collects GitHub Actions result/logs.
- Collects project documentation context.
- Sanitizes payload.
- Sends to Cloudflare Worker.
- Uses Kilo Gateway for initial report.
- Runs governance/interceptor review.
- Produces final Markdown response.
- Creates/updates GitHub PR comments or issues.
- Uses fallback when provider fails.

Governance review must check:

- relevance;
- factual support;
- unsupported claims;
- documentation conflicts;
- missed scope/architecture violations;
- whether the report should be published, amended, suppressed, or treated as fallback.

AI governance rules:

- Kilo is advisory, not authoritative.
- Reports must not invent findings.
- Claims must be supported by diff, CI logs, or documentation.
- Unsupported claims must be flagged.
- Documentation conflicts must be surfaced.
- AI-generated plans must not exceed scope without ADR.
- AI must not run Gradle locally.
- AI must not require maintainers to run local Gradle validation.

---

## 14. Continuous AI Evolution Rules

Continuous AI Evolution is documentation-driven, issue-driven, PR-based, and human-gated.

Allowed current/near-future flow:

```text
Push/PR
-> collect diff
-> collect CI logs
-> collect project documentation context
-> Kilo initial report
-> governance/interceptor review
-> issue/comment
-> human decision
```

Future issue-driven flow:

```text
Issue with ai:plan
-> AI generates implementation plan
-> governance checks scope
-> human applies ai:approved
-> bot may open PR
-> GitHub Actions validates
-> human reviews and merges
```

Not allowed:

- AI commits directly to `main`.
- AI merges PRs automatically.
- AI changes `PROJECT_SCOPE.md` without human review.
- AI accepts ADRs automatically.
- AI declares `folia-supported: true` automatically.
- AI treats generated reports as architectural decisions.
- AI promotes experimental concepts to stable core automatically.
- AI introduces public API without ADR-backed approval.

Future `AI_CONTEXT_PACK.md` maintenance:

- This file should eventually be generated/refreshed by a controlled process.
- Updates should go through PR/human review.
- This file must remain derived guidance.

---

## 15. Explicit Out Of Scope

Unless a future ADR/milestone explicitly changes scope, do not implement:

- public API;
- generic scripting;
- GUI/menu systems;
- economy systems;
- quest systems;
- combat framework;
- teleport framework;
- land protection system;
- WorldGuard integration;
- GriefPrevention integration;
- ServiceLoader integration;
- NMS;
- reflection;
- `runAsync`;
- `runOnEntity`;
- `SchedulerAccess`;
- advanced Folia cross-region behavior;
- `folia-supported: true` declaration;
- resource pack generation/hosting;
- hot reload of definitions;
- database persistence;
- chunk LRU cache;
- Fortune;
- Silk Touch;
- Efficiency;
- shared multi-player mining;
- advanced tool tiers unless planned;
- vanilla enchantment semantics;
- direct AI auto-merge;
- direct AI commits to `main`.

---

## 16. AI Review Checklist

When reviewing a diff, an AI reviewer must check:

1. Does the change stay tied to custom blocks, tools, or items?
2. Does it violate `PROJECT_SCOPE.md`?
3. Does it violate `ARCHITECTURE_GUARDRAILS.md`?
4. Does it contradict an accepted ADR?
5. Does it implement a feature listed as out of scope?
6. Does it add forbidden dependencies?
7. Does domain remain pure?
8. Does application remain free of Bukkit/Paper/adapters?
9. Do mechanics remain pure and capability-driven?
10. Does scheduler usage remain limited to `SchedulerPort.runOnRegion`?
11. Does world mutation go through ports/adapters and region safety where needed?
12. Does it avoid fake `BlockBreakEvent` and `Bukkit#callEvent` for simulated breaks?
13. Does it avoid duplicate drops/mechanics/identity removal?
14. Does it keep GitHub Actions as validation source of truth?
15. Does it avoid local Gradle validation assumptions?
16. Does it require an ADR before becoming stable/core?

---

## 17. Implementation Planning Checklist

Before implementing a new feature, ask:

1. Is it directly connected to custom blocks, tools, or items?
2. Is it already allowed by current scope/ADR/milestone?
3. Is it stable core, official module, experimental module, or devtool?
4. Does it need a technical spike?
5. Does it need an ADR?
6. Does it require a new capability?
7. Can it be implemented without platform leakage?
8. Can it be tested without a Minecraft server where applicable?
9. Does it add hot-path cost?
10. Does it preserve Folia safety limitations?
11. Does it avoid global scans and unbounded loops?
12. Does it preserve current completed flows?
13. Does it require documentation updates?
14. Does GitHub Actions cover it?

---

## 18. Current Recommended Next Options

After MVP-2 Custom Mining completion, possible next directions are:

- custom durability;
- tool tiers / effective blocks;
- first mechanic after mining;
- YAML arguments for mechanics;
- Folia real validation;
- CI/test flakiness hardening;
- AI context pack automation;
- issue-driven AI planning.

Each direction must be planned before implementation and checked against scope, ADRs, and architecture guardrails.

---

## 19. Maintenance Note

This file is intentionally compact. It is designed for AI context windows and governance prompts.

It must not be used to silently change scope.

When in doubt:

1. Read `docs/PROJECT_SCOPE.md`.
2. Read `docs/ARCHITECTURE_GUARDRAILS.md`.
3. Read accepted ADRs.
4. Read current milestone documents.
5. Prefer conservative interpretation.
6. Ask for ADR or human review before expanding scope.

# CustomContent Engine

CustomContent Engine is a Paper 1.21+ engine for custom blocks, tools, and items, built with a pure domain core, hexagonal architecture, binary PDC persistence, YAML mechanic bindings, controlled mechanic execution, an engine-controlled custom mining model, and custom tool durability.

The project follows a conservative-but-evolvable core model: the core stays small and protected from feature inflation, while new ideas can evolve through ADRs, spikes, experimental modules, official modules, and fitness functions.

## Project Status

- MVP-0: Complete.
- MVP-1: Complete for the controlled `area_break` scope.
- MVP-2: Complete for the custom mining scope.
- MVP-3: Complete for custom tool durability and wear.
- Tool Tiers: Official (implemented as official mining feature, not experimental).
- VeinMiner: Official (ADR-0011), third official builtin mechanic after `area_break` and `block_transform`.
- Folia: architectural goal; advanced cross-region behavior is not promised as final support yet.
- Public API: not stable and not available yet.

## What Works Today

### MVP-0 Foundation

- Loading and validation of `definitions.yml`.
- Immutable `DefinitionRegistry`.
- Custom item creation with PDC identity.
- `/givecustomitem` debug command.
- Custom block placement.
- Binary chunk PDC persistence.
- Custom block breaking.
- PDC identity removal on break.
- Simple YAML-defined drops.
- Orphan custom block cleanup.
- Basic Paper integration test.

### MVP-1 Mechanics

- Internal mechanic contract.
- `MechanicDescriptor`, `MechanicContext`, `MechanicResult`, and explicit capabilities.
- `ExecutionOrigin` capability.
- `MechanicRegistry`, `MechanicContextFactory`, and `MechanicExecutor`.
- Builtin `AreaBreakMechanic`.
- Controlled `/debugareabreak` command.
- Real `BlockBreakEvent` integration for `area_break`.
- `Partial` rescheduling through `SchedulerPort.runOnRegion`.
- Anti-loop protection for continuation chains.
- Cooldown and work-budget gates.
- Same-region-safe behavior for the controlled MVP-1 scope.

### Post-MVP-1 / MVP-2 / MVP-3 Additions

- YAML mechanic bindings through `items.<id>.mechanics.on_block_break`.
- Removal of the hidden `ruby_pickaxe -> area_break` policy.
- Engine-controlled custom mining for custom blocks and custom tools.
- Optional YAML mining fields:
  - `blocks.<id>.mining.hardness`
  - `items.<id>.mining.speed`
- `MiningSession` with absolute-time progress.
- Visual mining stages.
- `MiningSessionService`.
- Runtime processing of active mining sessions only.
- `MiningVisualPort` and Bukkit visual adapter.
- Custom mining completion flow.
- Completion removes custom block identity once, sets the block to `AIR` once, emits configured drops once, and triggers `mechanics.on_block_break` once.
- No fake `BlockBreakEvent` simulation.
- No `Bukkit#callEvent` for fake block breaking.
- Custom tool durability and wear through `items.<id>.durability`.
- Durability stored in PDC and initialized when custom tools are created.
- Wear applied once per successful custom mining completion.
- `break_when_zero` policy controls whether zero-durability tools are removed or preserved.
- `area_break` does not multiply durability damage; one completion applies one wear event.

## Architecture

The project follows a hexagonal architecture with strict dependency boundaries:

```text
customcontent/
|-- internalapi/   Internal contracts, not stable public API
|-- domain/        Pure business definitions, IDs, policies, and registries
|-- application/   Use cases, orchestration, services, mechanic runtime, mining runtime
|-- port/          Dependency inversion interfaces
|-- adapter/       Bukkit, Paper, PDC, YAML, platform, persistence, and visual implementations
|-- builtin/       Official builtin mechanics, not stable core by default
|-- experimental/  Incubating modules and contracts
|-- devtools/      Debug, profiling, and test tools
`-- bootstrap/     Composition root
```

Layer summary:

- `internalapi`: internal mechanic and identity contracts used by the engine.
- `domain`: pure Java model for definitions, policies, identifiers, immutable registries, and pure mining values.
- `application`: orchestration layer for items, blocks, mechanics, budgets, cooldowns, mining sessions, and runtime composition.
- `port`: interfaces for infrastructure such as block storage, drops, item metadata, scheduling, region safety, visual mining progress, world mutation, protection, and tool wear.
- `adapter`: platform and infrastructure implementations for Bukkit/Paper, PDC, YAML, commands, listeners, mining input, and mining visuals.
- `builtin`: official mechanics such as `area_break`; official does not mean stable public API.
- `experimental`: reserved for future incubating modules or candidate contracts.
- `devtools`: internal development tools and debug commands.
- `bootstrap`: manual composition root for plugin startup.

## Main Architecture Rules

- `domain` has no Bukkit, Paper, Folia, YAML, PDC, NMS, reflection, or adapter dependency.
- `application` does not import `adapter`.
- Builtin mechanics do not import Bukkit, Paper, adapters, or application services.
- Adapters translate platform events and delegate inward.
- Bootstrap is the composition root.
- Mechanics receive only declared capabilities through `MechanicContext`.
- `MechanicContext` does not expose `SchedulerAccess`.
- `SchedulerPort` exposes only `runOnRegion(WorldPosition, Runnable)` in the current controlled scope.
- No `runAsync` or `runOnEntity` in the MVP scopes.
- No NMS or reflection.
- Official modules are not stable core by default.
- Internal contracts are not public API.

## Conservative Evolvable Core

CustomContent Engine protects two goals at the same time:

1. Avoid feature inflation in the stable core.
2. Avoid freezing the engine into a narrow design that prevents future evolution.

New ideas must start outside the stable core as one of:

- technical spike;
- experimental module;
- official module;
- experimental contract;
- devtool.

A feature, capability, or extension point may enter the stable core only after ADR review, technical validation, clear use cases, tests or fitness functions, and proof that it reduces total system complexity without making simple mechanics harder to write.

## MVP-0

MVP-0 is complete and provides the foundation without mechanics:

- `definitions.yml` loading and validation.
- Immutable `DefinitionRegistry`.
- `/givecustomitem`.
- Custom item identity stored in item PDC.
- Custom block place and break.
- Binary custom block identity persistence in chunk PDC.
- PDC identity removal on break.
- Simple YAML-defined drops.
- Orphan custom block cleanup.
- Gradle Wrapper.
- Basic Paper integration test.

## MVP-1

MVP-1 is complete for the current controlled scope: one builtin mechanic, `area_break`.

Implemented MVP-1 pieces:

- `Mechanic` contract according to ADR 0001.
- `MechanicDescriptor` with `MechanicId`, required capabilities, and `readOnly`.
- Capability model with explicit runtime access.
- `ExecutionOrigin` according to ADR 0002.
- `MechanicContext.require(...)`.
- `MechanicResult.Done`, `MechanicResult.Partial`, and `MechanicResult.Rejected`.
- `MechanicRegistry`.
- `MechanicContextFactory`.
- `MechanicExecutor`.
- SchedulerPort-based rescheduling for `Partial`.
- Anti-loop protection for continuation chains.
- Cooldown continuation policy.
- Area work budget.
- Pure and stateless `AreaBreakMechanic`.
- Controlled `/debugareabreak` runtime path.
- Real `BlockBreakEvent` integration.
- Same-region-safe behavior according to the controlled MVP-1 scope.

For real event integration, the original broken block remains owned by the MVP-0 break flow. `area_break` processes only additional blocks around the origin, preventing duplicate origin drops and fake-event recursion.

## YAML Mechanic Bindings

Mechanic activation is declarative through `definitions.yml`.

Example:

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
- The initial supported trigger is `on_block_break`.
- The value of `on_block_break` is a list of `MechanicId` values.
- Referenced mechanics must be registered in `MechanicRegistry`.
- There are no per-mechanic arguments in the current scope.
- There are no conditions, expressions, scripts, or generic ability hooks.
- YAML mechanic bindings do not create a public API.

## MVP-2

MVP-2 is complete for the custom mining model.

MVP-2 adds engine-controlled mining for custom blocks and custom tools:

- `MiningHardness` and `MiningSpeed`.
- Optional YAML fields for mining hardness and speed.
- `MiningSession` with absolute-time progress.
- Pure visual stage calculation.
- `MiningSessionService`.
- One active mining session per actor in the current scope.
- Runtime processing only of active sessions.
- Visual updates only when the mining stage changes.
- Custom mining completion integrated with drops and `mechanics.on_block_break`.
- No world scans, chunk scans, block scans, or all-player scans.

Example YAML direction:

```yaml
blocks:
  ruby_ore:
    material_base: STONE
    numeric_id: 1
    custom_model_data: 1000
    mining:
      hardness: 6.0

items:
  ruby_pickaxe:
    material_base: DIAMOND_PICKAXE
    custom_model_data: 1001
    mining:
      speed: 8.0
```

Completion guarantees:

- custom block identity is removed once;
- block is set to `AIR` once;
- configured drops are emitted once;
- bound `mechanics.on_block_break` mechanics are triggered once;
- fake `BlockBreakEvent` is not used;
- `Bukkit#callEvent` is not used to simulate block breaking.

## MVP-3

MVP-3 is complete for custom tool durability and wear.

MVP-3 adds a minimal durability system for custom tools used in custom mining:

- Optional `durability` section in `items.<id>` YAML.
- `durability.max` — Maximum durability (positive integer).
- `durability.damage_on_custom_block_break` — Damage from mining (zero or positive, defaults to 0).
- `durability.break_when_zero` — Remove tool at zero (defaults to `true`).

Example YAML:

```yaml
items:
  ruby_pickaxe:
    material_base: DIAMOND_PICKAXE
    custom_model_data: 2001
    durability:
      max: 500
      damage_on_custom_block_break: 1
      break_when_zero: true
```

Durability behavior:

- Custom items initialize durability to `max` on creation.
- Durability is stored in PDC and persists across sessions.
- Wear applies once per successful custom mining completion.
- Vanilla block breaking does not affect custom tool durability.
- AreaBreak does not multiply durability damage (one wear per completion).
- Tools at zero durability are removed if `break_when_zero: true` or preserved if `false`.

MVP-3 Limitation: Tool durability wear is applied once per custom mining completion, regardless of how many blocks are broken by AreaBreak. Additional blocks broken by `area_break` do not multiply the durability damage.

## Mining Tiers (Official)

Mining tiers add optional progression gating to custom mining without changing hardness, speed, or durability.

### YAML Fields

```yaml
blocks:
  ruby_ore:
    material_base: STONE
    numeric_id: 1
    custom_model_data: 1000
    mining:
      hardness: 6.0
      required_tier: 2     # optional, positive integer

items:
  ruby_pickaxe:
    material_base: DIAMOND_PICKAXE
    custom_model_data: 1001
    mining:
      speed: 8.0
      tier: 3               # optional, positive integer
```

### Rules

- `items.<id>.mining.tier` — optional positive integer. Absent means the tool does not participate in tier checks.
- `blocks.<id>.mining.required_tier` — optional positive integer. Absent means the block accepts any tool.
- A tool without a declared `tier` cannot satisfy any block `required_tier`.
- A block without `required_tier` accepts any tool, including tools without a tier.
- Tier logic is independent of `mining.speed`, durability, and `area_break`.
- Tier validation occurs when a mining session would start. If the active tool's tier is below the target block's required tier, no session is created and the player receives a chat message.
- If the held item changes during an active mining session, the existing held-item change cancellation path invalidates the session.
- `schema: 1` is preserved; no migration is required because the fields are purely additive and optional.

### Architecture

- `ToolTier` and `BlockTierRequirement` are pure domain value objects in `domain.mining`.
- `ItemDef` and `BlockDef` carry optional tier fields.
- Validation occurs in the adapter before `MiningSessionService.startSession` is called.
- Player-facing feedback is owned by the adapter layer.
- No new core capability, scheduler contract, or public API is introduced.

### Example Tier Progression

```yaml
blocks:
  stone_quarry:
    numeric_id: 1
    required_tool: stone_pickaxe
    mining:
      hardness: 2.0
      required_tier: 1

  iron_ore:
    numeric_id: 2
    required_tool: iron_pickaxe
    mining:
      hardness: 4.0
      required_tier: 2

  ruby_ore:
    numeric_id: 3
    required_tool: ruby_pickaxe
    mining:
      hardness: 6.0
      required_tier: 3

  sapphire_ore:
    numeric_id: 4
    required_tool: sapphire_pickaxe
    mining:
      hardness: 8.0
      required_tier: 4
```

### Stability

- This feature is **official** but not part of the stable core.
- It is maintained by the project and may be used with confidence.
- Breaking changes require ADR and version consideration.
- No compatibility guarantee is provided for future versions without ADR revision.

## BlockTransformMechanic (Official)

`block_transform` is the second official builtin mechanic. It transforms a custom block at the execution origin into another block or material.

Example YAML:

```yaml
items:
  ruby_pickaxe:
    mechanics:
      on_block_break:
        - block_transform
          arguments:
            to_block: 42           # numeric_id of a custom block, or a material name like "stone"
            drop_original: true    # optional, default false
            consume_budget: true   # optional, default true
```

The mechanic uses the `MECHANIC_CONFIG` capability to read arguments. It is pure, stateless, and independent of Bukkit/Paper.

## VeinMinerMechanic (Official)

`vein_miner` is the third official builtin mechanic (ADR-0011). It mines connected same-type custom blocks in a vein using BFS with `HashSet` visited tracking.

Example YAML:

```yaml
items:
  ruby_pickaxe:
    mechanics:
      on_block_break:
        - vein_miner
          arguments:
            max_blocks: 64
            max_depth: 20
            shape: FACE_ADJACENT        # FACE_ADJACENT (default) | ALL_ADJACENT
            require_sneak: true
            respect_fortune: true
            respect_silk_touch: true
            durability_per_block: true
            cooldown_seconds: 5
            allowed_blocks:
              - ruby_ore
              - diamond_ore
```

Arguments:

- `max_blocks` — maximum blocks per execution (default 64, absolute max 512).
- `max_depth` — maximum BFS depth (default 20).
- `shape` — `FACE_ADJACENT` (6 directions, default) or `ALL_ADJACENT` (26 directions). `ALL_ADJACENT` is clamped to `max_blocks<=32` and `max_depth<=10` to protect TPS.
- `require_sneak` — only executes while the actor is sneaking (via the `ActorState` module capability).
- `respect_fortune` / `respect_silk_touch` — apply Fortune/Silk Touch drop multipliers via `EnchantmentView`.
- `durability_per_block` — applies one tool-wear event per block broken (default `true`); otherwise a single wear per execution.
- `cooldown_seconds` — per-execution cooldown.
- `allowed_blocks` — optional list restricting which origin blocks trigger the mechanic.

The mechanic is pure, stateless, and Folia-safe: it returns `MechanicResult.Partial` for rescheduling via `SchedulerPort` when the work budget or region safety is exceeded. Protected blocks (via the optional `ProtectionPort`) are skipped without being counted or mutated. Players can disable it per session with `/veinminertoggle`.

## Current Limitations

- `auto_smelt` not implemented.
- No generic Fortune/Silk Touch enchantment system; `vein_miner` applies fortune/silk_touch drop multipliers via the `EnchantmentView` module capability.
- No Efficiency enchantment support.
- No multiple-player shared mining of the same block in the current scope.
- No repair system for custom tool durability (mending, crafting, anvil).
- No complex permission system.
- No WorldGuard or GriefPrevention integration.
- No advanced Folia cross-region automation.
- No `runAsync` or `runOnEntity`.
- No `SchedulerAccess`.
- No ServiceLoader.
- No public API.
- No database or cache layer.
- No resource pack generation or hosting.
- No GUI/menu system.
- No generic scripting or ability framework.

## Persistence Model

Custom block identity is stored in the chunk `PersistentDataContainer` as a compact binary `BYTE_ARRAY`.

Current format:

```text
Byte 0: schema version
Bytes 1-2: entry count, big-endian
Each entry:
  - relative block position as short
  - numeric_id as short
```

Rules:

- `numeric_id` must be declared in YAML.
- `numeric_id` must be unique.
- `numeric_id` must not be reused for another block type while persisted worlds may still contain it.
- Orphan entries are removed when broken, with rate-limited warnings.
- The adapter reads the full byte array, modifies it in memory, and writes it back.
- No partial mutation inside NBT is assumed.
- No chunk LRU cache exists in the MVP scopes.
- No external database stores world block state.

## Performance Model

The project favors predictable runtime cost:

- definitions are loaded at startup;
- registries are immutable;
- block identity lookup uses binary chunk PDC;
- mechanics use explicit budgets and cooldowns;
- area operations use `WorkBudget`;
- mining is session-driven;
- mining progress is based on absolute time;
- visual mining updates happen only when the stage changes;
- active mining processing is bounded;
- no global world/chunk/block/player scans;
- no blocking I/O in event hot paths;
- no NMS or reflection.

## Build And Test Commands

Windows:

```powershell
.\gradlew.bat test --no-daemon
.\gradlew.bat build --no-daemon
.\gradlew.bat integrationTest --no-daemon
.\gradlew.bat binaryPdcSpike --no-daemon
```

Unix/macOS:

```bash
./gradlew test --no-daemon
./gradlew build --no-daemon
./gradlew integrationTest --no-daemon
./gradlew binaryPdcSpike --no-daemon
```

## Cloud Build

This project includes a GitHub Actions workflow for remote build and tests.

See `docs/build-cloud.md`.

## Quick Architecture Audits With rg

```bash
rg 'SchedulerAccess|runAsync|runOnEntity' src/main/java src/test/java || true

rg '^import org\.bukkit|^import io\.papermc|^import org\.spigot' src/main/java/com/customcontentengine/domain src/main/java/com/customcontentengine/internalapi src/main/java/com/customcontentengine/builtin || true

rg '^import com\.customcontentengine\.adapter' src/main/java/com/customcontentengine/application || true

rg 'WorldGuard|GriefPrevention|net\.minecraft|reflection|Reflect|ServiceLoader' src/main/java src/test/java || true

rg 'Bukkit#callEvent|callEvent\(|BlockBreakEvent' src/main/java/com/customcontentengine/application src/main/java/com/customcontentengine/domain src/main/java/com/customcontentengine/builtin || true
```

## Important Documentation

- `docs/AI_CONTEXT_PACK.md`
- `docs/PROJECT_SCOPE.md`
- `docs/ARCHITECTURE_GUARDRAILS.md`
- `docs/adr/`
- `docs/spikes/`
- `docs/milestones/MVP-0-COMPLETE.md`
- `docs/milestones/MVP-1-COMPLETE.md`
- `docs/milestones/MVP-2-COMPLETE.md`
- `docs/milestones/MVP-3-COMPLETE.md`

Important ADRs:

- ADR 0001 — Mechanic Contract for MVP-1.
- ADR 0002 — Execution Origin Capability.
- ADR 0003 — Conservative Evolvable Core.
- ADR 0004 — Extension Stability Levels.
- ADR 0005 — Capability Governance.
- ADR 0006 — Experimental Module Incubation.
- ADR 0007 — Architecture Fitness Functions.
- ADR 0008 — YAML Mechanic Bindings.
- ADR 0009 — Custom Mining Model.
- ADR 0010 — Tool Tiers and Effective Blocks.
- ADR 0011 — Vein Miner Mechanic.

## Next Phase

Recommended post-MVP-3 work:

- Finalize the documentation freeze for the completed MVP scope.
- Harden MVP-3 durability, mining, and `area_break` integration tests.
- Refine Folia ownership validation and document advanced cross-region behavior.
- Define devtools policy, including future treatment of `/debugareabreak`.
- `vein_miner` is now the third official mechanic (ADR-0011); any further mechanic must still follow the incubation process.

Any expansion after MVP-3 must follow the project scope, architecture guardrails, accepted ADRs, and the incubation or ADR process required for new contracts, scheduler changes, YAML schema changes, persistence changes, extension points, mining behavior changes, durability behavior changes, or additional mechanics.

## License

License: not specified yet.

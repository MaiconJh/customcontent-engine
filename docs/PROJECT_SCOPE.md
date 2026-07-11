# Definitive Scope, Focus, and Boundaries Document — Evolutionary Implementation Version

Project: CustomContent Engine  
Version: 3.2.0 — Evolutionary scope refinements (Tool Tiers official, BlockTransformMechanic)  
Platform: Paper 1.21+ with Folia compatibility validated through technical spikes  
Status: Approved as implementation scope with conservative evolutionary governance. Formal MVP freezing remains conditional on execution and validation of required technical spikes.

---

## 1. Conceptual Plugin Name

CustomContent Engine

---

## 2. One-Sentence Definition

A modular, high-performance Paper 1.21+ engine for creating custom blocks, tools, and items through a pure domain core, binary PDC persistence, and conservative-but-evolvable extensibility based on explicit capabilities and controlled extension points.

---

## 3. Expanded Definition

CustomContent Engine is a Minecraft plugin that provides a solid architectural foundation for creating custom content centered on custom blocks, tools, and items.

It interprets declarative definitions loaded from YAML at startup, manages custom block identity in the world through `PersistentDataContainer` using a compact binary format, applies attributes to tools and items, and executes associated mechanics through a validation pipeline that includes cooldowns, regional operation budgets, protection checks, and explicit capability validation.

The architecture is strictly hexagonal: the stable core, composed of domain, internal contracts, and application orchestration, does not depend on Bukkit, Paper, Folia, PDC, YAML, NMS, or reflection. All infrastructure is connected through ports and adapters.

The project uses a conservative-but-evolvable core model. New ideas start outside the stable core as spikes, experimental modules, official modules, or experimental contracts. The stable core is protected from feature inflation, but it is not immutable by dogma. It may evolve when a repeated, structural, platform-independent need appears across multiple modules and cannot be solved cleanly through existing extension points.

---

## 4. Problem the Plugin Solves

- Rigid monoliths: Traditional custom block/item plugins impose fixed mechanics or require complex configurations that become fragile and hard to adapt.
- Performance issues: Homemade solutions using chunk scans, invisible entities, excessive entity usage, reflection, or disk reads on the tick can degrade TPS.
- Lack of isolation: Business logic mixed with direct Bukkit API calls makes testing difficult and complicates migration to Folia.
- Dangerous extensibility: Adding new mechanics often requires modifying the core or granting unrestricted access to server APIs.
- Premature API freezing: Projects can accidentally expose internal classes as public API before the architecture is ready.
- Core inflation versus stagnation: An extensible engine must avoid absorbing every feature into the core while also avoiding a rigid core that prevents future evolution.

CustomContent Engine solves these problems by separating definitions, engine behavior, platform integration, extension contracts, and official/experimental modules.

---

## 5. Target Audience

- Server administrators who want custom visual identity and functional mechanics for blocks, tools, and items.
- Developers who need to create custom behavior without forking or modifying the plugin core.
- Medium and large servers that require stable TPS with dozens or hundreds of concurrent players.
- Technical server teams that prefer predictable APIs, testable mechanics, and explicit architectural boundaries.

---

## 6. Central Focus

The base product focus is:

1. Custom blocks.
2. Custom tools.
3. Custom items.

Every stable-core feature, official mechanic, event, and design decision must be directly connected to at least one of these three elements.

Any proposal that does not directly involve a custom block, tool, or item managed by the plugin is out of scope for the stable core.

Future extensions may expand behavior around these elements, but must not transform the engine into a generic economy, combat, quest, teleportation, GUI, scripting, NPC, or ability framework.

The scope freeze applies to the MVP implementation scope, not to the long-term product evolution model.

---

## 7. Design Principles

1. Pure and immutable domain: Business classes such as definitions, contexts, and results are Java records or immutable classes with no dependency on `org.bukkit`. Positions and identifiers are represented by dedicated types such as `WorldPosition`, `CustomBlockId`, and `CustomItemId`.

2. Hexagonal architecture: The core exposes ports as interfaces. Adapters implement infrastructure such as Bukkit, PDC, YAML, protection, and schedulers. Dependencies always point inward.

3. Binary PDC persistence: The source of truth for custom blocks in the world is the chunk `PersistentDataContainer`, stored as a compact `BYTE_ARRAY`. The adapter reads the array, modifies the in-memory structure, and writes it back.

4. Folia-safe by construction: No shared mutable global state across threads. Every world modification goes through `SchedulerPort`. In Folia, the adapter validates regional ownership before any regional access.

5. Immutable registry: Definitions loaded from YAML at startup are validated, versioned, and stored in immutable structures. No runtime mutation is allowed in the MVP.

6. Visual logic separated from functional logic: The mapping `id -> material_base + custom_model_data` is the functional contract. `asset_path` is optional metadata for documentation or future export and has no functional effect in the MVP.

7. Extensibility through explicit capabilities: Mechanics implement `Mechanic` and declare required capabilities through `MechanicDescriptor`. `MechanicContext` provides only what was requested and validated.

8. Measurement-driven performance: Architectural decisions are backed by spikes, benchmarks, and fitness tests before formal freezing.

9. Conservative evolvable core: New ideas are rejected from the stable core by default, but the core may evolve through ADRs when a repeated structural need is proven.

10. Official does not mean core: A module may be maintained by the project without becoming part of the stable core.

11. Internal does not mean public: `internalapi` is not a stable public API. Public APIs must be explicitly declared, versioned, and governed.

---

## 8. Allowed Functional Scope

### 8.1 Custom Blocks

- Define blocks with internal ID, base material, custom model data, required tool, and drop table.
- Every persistent block must have a stable `numeric_id` declared in YAML, unique and non-reusable while persisted worlds may still contain it.
- Place custom blocks in the world and persist identity through binary PDC in the chunk.
- Break custom blocks and apply defined drops.
- Remove block identity from PDC when broken.
- Orphaned blocks whose `numeric_id` no longer exists in the registry are silently removed from PDC when broken, with rate-limited warnings in the log.
- Basic right-click interaction may exist only as a future mechanic trigger.

### 8.2 Custom Tools and Items

- Define tools/items with internal ID, base material, custom model data, and attributes such as damage, speed, and modified durability.
- Apply attributes when the item is created using `editMeta()`.
- Associate one or more mechanics with a tool through YAML definitions.
- Create custom items through a debug command in the MVP.

### 8.3 Tool Tiers (Official)

Custom tools may declare a mining tier (`mining.tier`) and custom blocks may declare a required tier (`mining.required_tier`). Tiers are used to gate which tools can mine which blocks, providing a progression system.

- `items.<id>.mining.tier` — optional positive integer. If absent, the tool does not participate in tier checks.
- `blocks.<id>.mining.required_tier` — optional positive integer. If absent, the block has no tier restriction.
- Tier validation occurs at mining session start. If the tool's tier is below the required tier, no session is created.
- Held-item changes are re-validated and cancel the session if tier becomes insufficient.
- Tiers are orthogonal to `mining.speed`, durability, and mechanics.
- Tier logic is implemented in the mining application layer (`MiningSessionService.isTierEligible()`), not in mechanics.
- This feature is **official** but not part of the stable core.

### 8.4 Mechanic Execution

- In the MVP, the only functional extension unit is the `Mechanic` interface.
- Mechanics are behaviors triggered by interaction with a custom block, tool, or item.
- Each mechanic implements `Mechanic` and declares its capabilities through `MechanicDescriptor`.
- `MechanicContext` provides capabilities on demand, such as `ctx.require(BlockMutation.class)`, validated at startup.
- The execution pipeline includes cooldown gates, work budget gates, and protection gates.
- Mechanics return `MechanicResult`: `Done`, `Partial`, or `Rejected`.
- Rescheduling is the responsibility of `MechanicExecutor`, not the mechanic.

### 8.5 BlockTransformMechanic (Official)

`block_transform` is the second official builtin mechanic. It transforms a custom block at the execution origin into another block or material.

Configuration in YAML:

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

The mechanic requires the `MECHANIC_CONFIG` capability to read arguments. It is pure, stateless, and does not access Bukkit/Paper or services.

### 8.6 Flow Control

- Cooldown per player and per mechanic using a flat key structure and automatic expiration.
- Work budget by explicit regional key, `RegionBudgetKey`, reset every region tick.
- Strict respect for event cancellation by external plugins through `ignoreCancelled = true` in action listeners.

---

## 9. Allowed Technical Scope

### 9.1 Layered Architecture

```text
customcontent/
├── internalapi/                 (Internal MVP contracts, not stable third-party API)
│   ├── mechanic/                (Mechanic, MechanicDescriptor, MechanicResult, Capability)
│   └── identity/                (CustomBlockId, CustomItemId, WorldPosition)
├── domain/                      (Pure business rules)
│   ├── definition/              (Records: BlockDef, ItemDef, DropTable, ToolAttributes)
│   ├── policy/                  (Pure functions: validation, drop calculation)
│   └── registry/                (Immutable DefinitionRegistry)
├── application/                 (Use cases and orchestration)
│   ├── block/                   (BlockService)
│   ├── item/                    (ItemService, including tools)
│   ├── mechanic/                (MechanicRegistry, MechanicExecutor, MechanicContextFactory)
│   └── budget/                  (WorkBudget, WorkBudgetManager, RegionBudgetKey)
├── port/                        (Dependency inversion interfaces)
│   ├── SchedulerPort
│   ├── PeriodicSchedulerPort
│   ├── BlockStorePort
│   ├── WorldMutationPort
│   ├── ItemMetadataPort
│   ├── DropPort
│   └── ProtectionPort
├── adapter/                     (Infrastructure implementations)
│   ├── platform/                (PaperSchedulerAdapter, PaperPeriodicSchedulerAdapter, future FoliaSchedulerAdapter)
│   ├── persistence/             (PdcBlockStore, PdcBlockCodec)
│   ├── yaml/                    (YamlDefinitionLoader, YamlDefinitionValidator)
│   └── bukkit/                  (Segmented listeners and commands)
├── builtin/                     (Official mechanics, not stable core)
│   └── mechanic/                (AreaBreakMechanic, BlockTransformMechanic)
├── experimental/                (Optional incubating modules/contracts, not stable)
├── devtools/                    (Debug/profiling/test tools, disabled by default)
└── bootstrap/                   (Composition Root — CustomContentPlugin)
```

### 9.2 Allowed Dependencies

- `adapter -> port`
- `adapter -> application`
- `application -> port`
- `application -> domain`
- `application -> internalapi`
- `builtin -> internalapi`
- `builtin -> domain` only when using pure definitions or value objects
- `bootstrap -> all modules for manual composition`
- `experimental -> internalapi/domain/application only when explicitly allowed by ADR`
- `domain -> no external dependencies`

### 9.3 Forbidden Dependencies

- `domain -> org.bukkit`
- `domain -> adapter`
- `domain -> YAML/PDC`
- `application -> adapter`
- `mechanic -> Bukkit.getScheduler()`
- `mechanic -> DefinitionRegistry`
- `mechanic -> BlockService/ItemService`
- `mechanic -> Bukkit/Paper/Folia/NMS/Plugin/Server/World/Player/Block/ItemStack`
- `listener -> complex business logic`
- `builtin -> adapter`
- `core -> experimental`
- `core -> devtools`

### 9.4 Persistence

- Spatial source of truth: binary PDC in the chunk.
- Format: version header, entry count, and entry sequence.
- Entry format: relative position as `short` and `numeric_id` as `short`.
- `numeric_id`: declared stably in YAML, unique, and never reused for a different block type while persisted worlds may still contain that ID.
- Startup fails if duplicate `numeric_id` values are found.
- If a `numeric_id` is removed from definitions, remaining blocks in the world become orphaned and are handled as described in Section 8.1.
- Operation: the adapter reads the entire `BYTE_ARRAY`, modifies it in memory, and writes it back to PDC.
- No partial mutation inside NBT is promised.
- Cache: no chunk LRU cache in the MVP. Direct lookup through `BlockStorePort`. Cache is a future optimization conditioned by benchmarks.
- Async database: outside the MVP. Reserved for future non-spatial data only.
- Migration: YAML contains a `schema` field. The loader validates and applies migrations before building records.

### 9.5 Scheduler and Threading

- `SchedulerPort` exposes only `runOnRegion(WorldPosition, Runnable)` in the MVP.
- `PeriodicSchedulerPort` exposes only `scheduleAtFixedRate(Runnable, long, long)` returning a cancellable `ScheduledTask`. It is a separate port (ADR-0012) used by infrastructure orchestrators (e.g. `MiningProcessingDriver`) for repetitive, tick-aligned work, keeping them free of direct Bukkit dependencies.
- `runOnEntity` and `runAsync` are outside the MVP and may be introduced only when real use cases exist, using custom abstractions to avoid leaking Bukkit types into the interface.
- In Folia, the adapter validates region ownership before accessing the world.
- `WorkBudget` uses explicit `RegionBudgetKey`, not `ThreadLocal`, as an architectural contract.

---

## 10. Out of Scope

### 10.1 Excluded Features

- Resource pack generation or hosting in the MVP.
- Mechanics that do not involve custom blocks, tools, or items.
- GUI or menus of any kind in the MVP.
- A custom land protection system.
- Tool upgrade or progression systems.
- Hot-reload of definitions.
- Admin commands beyond debug commands explicitly allowed for the MVP.
- Fortune, Silk Touch, or other vanilla enchantment support in the MVP.
- Identity preservation in anvil, smithing, grindstone, or repair in the MVP.

### 10.2 Excluded Responsibilities

- Permission management beyond basic interaction validation.
- Direct integration with other plugin APIs, except `ProtectionPort` as an optional post-MVP contract.
- Entity manipulation beyond the player as the actor.
- Use of NMS, reflection, or internal server APIs.
- Database reads/writes on the game tick.
- Stable public API for third-party plugins in the MVP.

### 10.3 Platforms

- Pure Spigot is not officially supported.
- Paper 1.21+ is the primary target.
- Folia compatibility is validated through spikes and is not an absolute promise before testing.

---

## 11. Definitive MVP

### MVP-0 — Foundation Without Mechanics

Functional scope:

1. Load `definitions.yml` at startup.
2. Validate basic schema and `numeric_id`: uniqueness and stability.
3. Register one custom block with stable `numeric_id`.
4. Register one custom item/tool.
5. Provide debug command `/givecustomitem`.
6. Create item with PDC identity using `editMeta()`.
7. Place custom block in the world.
8. Persist identity in binary PDC in the chunk.
9. Break custom block.
10. Remove identity from PDC when broken, including orphaned blocks, with rate-limited warning.
11. Deliver simple drops defined in YAML.
12. Respect `Event#isCancelled()` in original events through `ignoreCancelled = true`.

Technical components:

- Immutable `DefinitionRegistry`.
- `SchedulerPort` with Paper implementation only for `runOnRegion`.
- `BlockStorePort` with `PdcBlockStore` and `PdcBlockCodec`.
- `BlockService` and `ItemService`.
- Segmented listeners: `BlockBreakAdapter`, `BlockPlaceAdapter`, `ItemCommandAdapter`.
- Unit tests for registry, services, and PDC codec.
- Basic Paper integration test.

### MVP-1 — First Controlled Mechanic

Functional scope:

1. `Mechanic` interface with `MechanicDescriptor`.
2. `MechanicContext` with capabilities: `BlockQuery`, `BlockMutation`, `BudgetView`, `CooldownView`, `DropSink`, and `ExecutionOrigin` when needed.
3. `MechanicRegistry` with capability validation at startup.
4. `MechanicExecutor` with pipeline: cooldown gate, budget gate, protection gate, dispatch.
5. `MechanicResult`: `Done`, `Partial`, `Rejected`.
6. Rescheduling controlled by the executor, not the mechanic.
7. `area_break` mechanic: flat 3x3.
8. `WorkBudget` using `RegionBudgetKey`, with limit of 32 blocks per tick per region.
9. 500ms cooldown per player per mechanic.
10. `ProtectionPort` as an optional contract. If no adapter is implemented, only the original cancelled event is respected. Additional `area_break` blocks are processed according to safe policy defined by spike. Future implementation must not depend on fake Bukkit event simulation.

### Post-MVP-3 — BlockTransformMechanic (Official)

- Implemented `BlockTransformMechanic` as a builtin mechanic.
- Added `BLOCK_PLACEMENT` and `MECHANIC_CONFIG` capabilities.
- Supports arguments: `to_block`, `drop_original`, `consume_budget`.
- Promoted to official status after demonstrating structural value.

### Confirmed Outside the MVP

- Fortune, Silk Touch, and enchantments.
- Identity preservation in anvil, smithing, grindstone, or repair.
- TileState.
- Chunk LRU cache.
- Direct WorldGuard or GriefPrevention integration.
- Pure Spigot.
- ServiceLoader.
- Stable public API for third parties.
- Advanced Folia cross-region behavior.
- `auto_smelt`, `vein_miner`, or any third mechanic.
- External database.
- `SchedulerAccess` inside `MechanicContext`.
- `runOnEntity` and `runAsync` in `SchedulerPort`.

---

## 12. Official Architecture Summary

Layers:

```text
internalapi -> domain -> application -> port <- adapter -> builtin -> bootstrap
```

Dependencies are unidirectional inward.

The domain does not know infrastructure.

Mechanics do not know services.

Adapters implement ports and delegate to application services.

`CustomContentPlugin` in `bootstrap` manually instantiates all adapters and services during `onEnable`.

No DI framework is used in the MVP.

---

## 13. Performance Rules

1. Use binary PDC, never a `Map` serialized into NBT.
2. No disk reads on the game tick. Definitions are loaded at startup.
3. Chunk cache is a future optimization, not part of the MVP. PDC remains canonical source of truth.
4. `WorkBudget` is mandatory for area operations. Counters are keyed by `RegionBudgetKey`. Excess work is rescheduled by the executor.
5. Cooldowns use a flat structure. `ConcurrentHashMap<CooldownKey, Long>` or equivalent should be used when concurrency is required.
6. One PDC read per relevant event. Item ID is extracted at listener entry and passed through context.
7. Area operation limit: 32 blocks per tick per region, adjustable after benchmarks.
8. Every world call goes through `SchedulerPort`, never directly through `Bukkit.getScheduler()`.
9. Protection is checked through `ProtectionPort`, not through fake Bukkit event simulation.
10. Action listeners use `ignoreCancelled = true`.
11. No reflection, NMS, YAML parsing, database access, or blocking I/O in hot paths.
12. Performance claims must be backed by spikes or load tests before becoming formal promises.

---

## 14. Extensibility Rules

1. In the MVP, the only functional extension unit is the `Mechanic` interface.
2. `Mechanic` exposes:
   - `MechanicDescriptor descriptor()` with `MechanicId`, `Set<Capability> requiredCapabilities`, and `boolean readOnly`.
   - `MechanicResult execute(MechanicContext context)`.
3. `MechanicContext` provides capabilities on demand through `ctx.require(...)`. Validation occurs at startup.
4. Capabilities are segregated:
   - Read: `BlockQuery`, `CooldownView`, `BudgetView`, `ExecutionOrigin`.
   - Write: `BlockMutation`, `DropSink`.
5. Mechanics never schedule tasks directly. They return `MechanicResult.Partial` with remaining work, and `MechanicExecutor` decides rescheduling through `SchedulerPort`.
6. Mechanics never receive references to `Plugin`, `Server`, `World`, `DefinitionRegistry`, `BlockService`, or `ItemService`.
7. The stable core is not modified to add a mechanic. A mechanic is implemented and registered in `MechanicRegistry`.
8. Future extension units may be introduced only through ADR, technical spike, and at least two validated use cases. New extension units must start as experimental and must not expand the stable core by default.

---

## 15. Persistence Rules

1. Spatial source of truth: binary PDC in the chunk using `PersistentDataType.BYTE_ARRAY`.
2. Array format:
   - Byte 0: schema version, 1 byte.
   - Bytes 1-2: entry count, 2 bytes, big-endian.
   - Entries: relative position as `short`, 2 bytes, and `numeric_id` as `short`, 2 bytes.
3. `numeric_id`:
   - Declared stably in YAML for every persistent block.
   - Unique. Startup fails if two blocks use the same `numeric_id`.
   - Never reused for another block type while persisted worlds may still contain that ID.
   - Orphaned blocks, where `numeric_id` no longer exists in the registry, are removed from PDC when broken, with rate-limited warning.
4. Operations:
   - Read: read the entire `BYTE_ARRAY` and decode entries.
   - Write on place: decode, add entry, encode, write.
   - Write on remove: decode, remove entry, encode, write.
   - The adapter always reads and writes the full array. No partial mutation inside NBT is assumed.
5. No external database stores world block state in the MVP.
6. Loading: PDC is loaded natively with the chunk. No additional query.
7. Migration: YAML has a `schema` field. The loader applies migrations before building records.

---

## 16. Minecraft / Paper / Folia Integration Rules

1. Official platform: Paper 1.21+. Pure Spigot is not supported.
2. Folia: compatibility validated by technical spikes. The code is Folia-safe by construction, but full support depends on real tests.
3. Folia-safe rules:
   - No mutable static state.
   - Every world call goes through `SchedulerPort`.
   - Folia adapter validates region ownership.
   - `plugin.yml` declares `folia-supported: true` only after spikes are complete.
4. Listeners:
   - Segmented by aggregate, such as `BlockBreakAdapter` and `BlockPlaceAdapter`.
   - Priority: `HIGH` unless ADR changes it.
   - `ignoreCancelled = true` for action listeners.
5. `NamespacedKey` instances are reused as `static final`.
6. Item manipulation: `item.editMeta()` is mandatory. Never use the `getItemMeta()` + `setItemMeta()` pair for writes.
7. No NMS, reflection, or internal server APIs.
8. In Folia, `area_break` on a region border ignores or reschedules blocks outside the current region according to the validated spike result. MVP default is to avoid unsafe cross-region mutation.

---

## 17. Criteria for Accepting or Rejecting New Mechanics

### Accepted Only If All Criteria Are Met

1. The mechanic operates on a custom block, tool, or item registered by the plugin.
2. Required capabilities are available in the current contract and validated at startup.
3. Execution can be split and respects `MechanicResult.Partial` where relevant.
4. It performs no disk or network I/O during execution.
5. It does not call third-party APIs directly.
6. It respects `WorkBudget` for any area or repeated operation.
7. It can be unit tested without a server.
8. It does not require access to Bukkit/Paper classes.

### Rejected If Any Criterion Applies

1. It is outside the block/tool/item domain.
2. It requires mutable global state or uncoordinated cross-region communication.
3. It attempts to access internal services such as `BlockService`, `ItemService`, or registries directly.
4. It performs unbounded loops or ignores `WorkBudget`.
5. It modifies visual presentation such as particles or models outside the allowed resource pack contract.
6. It involves combat, economy, teleportation, quests, GUI, or generic abilities not tied directly to custom blocks/items/tools.

---

## 18. Risks Avoided by This Scope

1. Generic monolith: Mechanics are restricted to the domain and must declare capabilities.
2. PDC bottleneck: Compact binary format reduces cost and overhead compared to textual maps serialized into NBT.
3. Bukkit coupling: Pure domain can be tested without a server.
4. Blocking I/O on tick: No disk reads during chunk events.
5. Folia incompatibility: Folia-safe code from the start, with regional ownership validation.
6. Fake protection events: Protection is checked through `ProtectionPort`, not simulated Bukkit events.
7. Memory leaks: Cooldowns expire automatically, and no premature chunk LRU cache exists.
8. Uncontrolled expansion: Scope changes require ADR and acceptance criteria.
9. Protection plugin conflicts: Cancelled events are respected, and protection is isolated behind a contract.
10. Circular dependencies: Unidirectional architecture prevents cycles.
11. Premature public API: internal APIs are not stable by default.
12. Core stagnation: the core may evolve when a structural need is proven.

---

## 19. Final Declaration of What the Plugin Is

CustomContent Engine is a Paper-first engine for creating custom blocks, tools, and items in Minecraft servers.

It loads immutable YAML definitions at startup, creates items identifiable through PDC, persists custom blocks in binary chunk PDC, and executes mechanics controlled by budget, cooldown, protection, and capability validation.

The architecture strictly separates pure domain, application, ports, adapters, official modules, experimental modules, and devtools.

The domain does not know Bukkit, Paper, Folia, PDC, YAML, NMS, or reflection.

Folia compatibility is an architectural goal validated through spikes, not a final promise before real tests.

The stable product focus is custom blocks, custom tools, and custom items.

---

## 20. Final Declaration of What the Plugin Is Not

- It is not a resource pack generator in the MVP.
- It is not a land protection system.
- It is not a generic ability framework.
- It is not an economy, teleportation, magic, quest, GUI, scripting, or combat plugin.
- It is not a dynamic runtime content loader in the MVP.
- It does not use invisible entities to represent blocks.
- It does not perform blocking I/O on the game thread.
- It does not expose a stable public API to third parties in the MVP.
- It does not officially support pure Spigot.
- It does not use NMS, reflection, or internal server APIs.
- It does not treat official modules as stable core by default.

---

## 21. Evolutionary Extensibility Principle

CustomContent Engine uses a conservative but evolvable core.

The stable core contains only structural mechanisms required to define, identify, persist, validate, and execute custom block, tool, and item behavior.

New ideas must start outside the stable core as spikes, experimental modules, official modules, devtools, or experimental contracts.

The core is not immutable by dogma. It may evolve when a repeated, structural, platform-independent need appears across multiple modules and cannot be solved cleanly through adapters, official modules, or experimental extensions.

Extensibility must not turn the engine into a generic ability, economy, quest, GUI, combat, teleportation, or scripting framework.

The project protects two goals at the same time:

1. Avoid feature inflation in the core.
2. Avoid freezing the engine into a narrow design that prevents future evolution.

---

## 22. Extension Stability Levels

All APIs, modules, contracts, and extension points must be classified with one of the following stability levels:

### 22.1 Internal

- May change at any time.
- No compatibility promise.
- Used by the engine implementation.
- Current `internalapi` remains internal during the MVP despite its name.

### 22.2 Experimental

- Available for validation.
- No compatibility promise.
- Must not be required by the stable core.
- Must have clear documentation saying it may change or be removed.

### 22.3 Official

- Maintained by the project.
- May be shipped with the plugin.
- Not automatically stable API.
- Official modules must not become hidden core dependencies.

### 22.4 Stable

- Compatibility is preserved according to the versioning policy.
- Breaking changes require ADR and versioning action.
- Stable APIs must be explicitly declared.

### 22.5 Deprecated

- Still available temporarily.
- Scheduled for removal or replacement.
- Must have a migration path when used by external modules.

---

## 23. Core Evolution Policy

A feature, capability, extension point, or contract can enter the stable core only if all or nearly all criteria are met:

1. It is required by multiple independent modules.
2. It is independent of Bukkit/Paper/Folia implementation details.
3. It is not tied to one specific mechanic.
4. It can be tested without a server.
5. It reduces total system complexity instead of increasing it.
6. It does not make simple mechanics harder to write.
7. It has an ADR and at least one technical spike or proof.
8. It has architecture fitness tests.
9. It remains directly connected to custom blocks, tools, or items.
10. It cannot be solved cleanly as an official or experimental module.

Default decision: reject from stable core and incubate outside it.

---

## 24. Capability Governance

Capabilities are the main safety mechanism for extension.

### 24.1 Core Capabilities

Core capabilities must be generic, stable, and directly related to custom blocks, tools, or items.

Current or candidate core capabilities:

- `BlockQuery`
- `BlockMutation`
- `BlockPlacement`
- `BudgetView`
- `CooldownView`
- `DropSink`
- `ExecutionOrigin`
- `MechanicConfig`

### 24.2 Module Capabilities

Specialized capabilities belong in official or experimental modules until proven broadly necessary.

Examples:

- `ParticleEmitter`
- `SoundEmitter`
- `TransformRule`
- `VeinGraphQuery`
- `ResourceExport`
- `DebugTraceSink`

### 24.3 Forbidden Stable-Core Capabilities

The following must not enter the stable core unless the product scope is formally redefined:

- Economy capability.
- Quest capability.
- Combat capability.
- Teleport capability.
- GUI/menu capability.
- Generic scripting capability.

### 24.4 Context Anti-God-Object Rule

`MechanicContext` must not become a service locator for the entire plugin.

A capability is not allowed merely because it is convenient. It must be justified by scope, safety, testability, and reuse.

---

## 25. Experimental Module Policy

Experimental modules are the official place for new ideas.

Pipeline:

```text
Idea
-> Technical Spike
-> Experimental Module
-> Official Module
-> Candidate Contract
-> Stable Core
```

Promotion criteria:

1. Solves a repeated problem.
2. Does not require Bukkit/Paper types in domain or mechanics.
3. Works with Folia constraints or defines a safe limitation.
4. Has unit tests.
5. Has at least one integration test when platform behavior is involved.
6. Does not increase complexity of the simple path.
7. Has an ADR before becoming official or stable.
8. Has clear removal or migration strategy if it fails.

Demotion/removal criteria:

1. The module forces core changes without proven need.
2. It leaks platform APIs into mechanics.
3. It becomes too generic and leaves the custom block/tool/item domain.
4. It cannot be made Folia-safe without unacceptable complexity.
5. It harms performance or usability beyond acceptable limits.

---

## 26. Future Extension Points Policy

Future extension points may exist, but none are promised in the MVP.

Candidate extension points:

- `DefinitionContributor`
- `CapabilityProvider`
- `ValidationRule`
- `DropResolver`
- `PersistenceStrategy`
- `ResourcePackExporter`
- `InteractionTrigger`
- `DebugTool`
- `MechanicTestKit`

Rules:

1. A candidate extension point starts experimental.
2. It must be validated by at least two concrete use cases.
3. It must not expose Bukkit/Paper classes unless it is explicitly an adapter-level contract.
4. It must not force all users to understand advanced architecture for simple mechanics.
5. It must not become stable without ADR and fitness tests.

---

## 27. Public API Graduation Rules

The MVP has no stable public API for third parties.

Before a stable public API exists:

1. Public packages must be explicitly declared.
2. Internal packages must remain undocumented as extension contracts.
3. Experimental APIs must be clearly labeled.
4. Compatibility promises must follow a versioning policy.
5. Breaking changes must require ADR.
6. Test coverage must include compatibility-sensitive behavior.

Recommended package meaning:

```text
internalapi     = internal contracts, unstable in MVP
experimentalapi = candidate contracts, no compatibility promise
publicapi       = stable third-party API, created only when formally approved
```

No external module should depend on `application`, `adapter`, `bootstrap`, or persistence internals.

---

## 28. Architecture Fitness Functions

The project must maintain automated architecture checks.

Required checks:

1. `domain` must not depend on Bukkit, Paper, Spigot, Folia, NMS, YAML, PDC, or adapters.
2. `application` must not depend on adapters, Bukkit, Paper, Spigot, Folia, NMS, or YAML implementations.
3. `builtin.mechanic` must not depend on Bukkit, Paper, adapters, services, schedulers, or registries.
4. `adapter` must not be imported by `domain`, `internalapi`, `builtin`, or `application`.
5. `devtools` must not be part of production flow unless explicitly enabled.
6. No reflection or NMS in production code.
7. No disk I/O, YAML parse, or blocking network/database access in event hot paths.
8. New mechanics must declare capabilities and pass startup validation.
9. New stable-core contracts require ADR.
10. New extension points require experimental status before stabilization.

Implementation options:

- ArchUnit tests.
- JUnit dependency tests.
- `rg` audit scripts.
- CI checks.
- Integration tests for Paper/Folia behavior.

---

## 29. Developer Experience Without Core Inflation

Simplicity of use is important, but it must be provided without inflating the stable core.

Allowed convenience layers:

- `MechanicDescriptor` builder.
- `MechanicTestKit`.
- Fake capability implementations for unit tests.
- Example modules.
- Templates.
- Dev-only debug commands.
- Documentation recipes.

Rules:

1. Convenience must not become mandatory core complexity.
2. Simple mechanics should not require understanding every internal layer.
3. Advanced extension should remain possible through explicit APIs.
4. Devtools must be removable or disableable without affecting production behavior.

---

## Conditional Scope Freeze

This document is version 3.2.0 of the scope and is approved as implementation scope with evolutionary governance.

Formal MVP freezing is conditional on execution and validation of the required technical spikes.

### Spike 1 — Binary PDC Performance

Objective: Measure the real read/write cost of `BYTE_ARRAY` with 256, 512, and 1024 custom blocks per chunk under simulated load of 50 players.

Metrics:

- Average lookup time.
- Average insert time.
- Average remove time.
- Memory allocation.
- Impact on block break/place events.

### Spike 2 — Folia Cross-Region Behavior

Objective: Validate `SchedulerPort` in Folia with scenarios including same-region break, `area_break` near region borders, access outside the current region, and rescheduling to the correct region.

Expected result: Clear definition of behavior for blocks outside the current region: ignore, reschedule, or reject.

### Spike 3 — Mechanic Contract Sufficiency

Objective: Model three hypothetical mechanics:

- `area_break`
- `vein_miner`
- `block_transform`

Expected result: Confirmation or adjustment of capabilities available in `MechanicContext`.

---

## Freeze Rule

After the three spikes are executed, documented, and approved, this document may be formally frozen as version `3.2.0-final` for MVP implementation.

This freezes the MVP implementation scope, not the long-term product vision.

Future expansion is allowed through ADRs, technical spikes, experimental modules, official modules, and explicit stability levels, as long as the stable core remains conservative and protected from feature inflation.

Any later change to stable core, stable API, extension points, or scope boundaries must follow the ADR process with technical justification and architecture review approval.

---

End of document.

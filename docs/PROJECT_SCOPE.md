# Definitive Scope, Focus, and Boundaries Document — Conditional Implementation Version

Project: CustomContent Engine  
Version: 3.0.1 — Post-review refinements  
Platform: Paper 1.21+ with Folia compatibility validated through technical spikes  
Status: Approved as conditional implementation scope. Formal freezing is pending execution and validation of the required technical spikes.

---

## 1. Conceptual Plugin Name

CustomContent Engine

---

## 2. One-Sentence Definition

A modular, high-performance Paper 1.21+ engine designed to create custom blocks, tools, and items with a pure domain core, binary PDC persistence, and controlled extensibility through explicit capabilities.

---

## 3. Expanded Definition

CustomContent Engine is a Minecraft plugin that provides a solid architectural foundation for creating custom content. It interprets declarative definitions loaded from YAML at startup, manages custom block identity in the world through `PersistentDataContainer` using a compact binary format, applies attributes to tools, and executes associated mechanics through a validation pipeline that includes cooldowns, regional operation budgets, and protection checks.

The architecture is strictly hexagonal: the core, composed of domain and application layers, does not depend on Bukkit, Paper, Folia, PDC, or YAML. All infrastructure is connected through ports and adapters. The engine is prepared for Folia through validation spikes, respecting its concurrent region model and using specific schedulers without shared mutable global state.

---

## 4. Problem the Plugin Solves

- Rigid monoliths: Traditional custom block/item plugins impose fixed mechanics or require complex configurations that become fragile and hard to adapt.
- Performance issues: Homemade solutions using chunk scans, invisible entities, or excessive disk reads cause TPS degradation.
- Lack of isolation: Business logic mixed with direct Bukkit API calls makes testing difficult and complicates migration to Folia.
- Dangerous extensibility: Adding new mechanics often requires modifying the core or granting unrestricted access to the server API, creating coupling and risk.

CustomContent Engine solves these problems by separating definitions, engine behavior, and platform integration, offering safe extension points and high performance.

---

## 5. Target Audience

- Server administrators who want custom visual identity and functional mechanics for their blocks, tools, and items.
- Developers who need to create custom behavior without forking or modifying the plugin core.
- Medium and large servers that require stable TPS with dozens or hundreds of concurrent players.

---

## 6. Central Focus

Custom blocks, custom tools, and custom items.

Every feature, mechanic, event, and design decision must be directly connected to at least one of these three elements.

Any proposal that does not directly involve a custom block, tool, or item managed by the plugin is automatically out of scope.

---

## 7. Design Principles

1. Pure and immutable domain: Business classes such as definitions, contexts, and results are Java records or immutable classes with no dependency on `org.bukkit`. Positions and identifiers are represented by dedicated types such as `WorldPosition`, `CustomBlockId`, and `CustomItemId`.

2. Hexagonal architecture: The core exposes ports as interfaces. Adapters implement infrastructure such as Bukkit, PDC, YAML, and schedulers. Dependencies always point inward.

3. Binary PDC persistence: The source of truth for custom blocks in the world is the chunk `PersistentDataContainer`, stored as a compact `BYTE_ARRAY`. The adapter reads the array, modifies the in-memory structure, and writes it back. The format reduces overhead compared to textual maps serialized into NBT.

4. Folia-safe by construction: No shared mutable global state across threads. Every world modification goes through `SchedulerPort`. In Folia, the adapter validates `isOwnedByCurrentRegion()` before any regional access.

5. Immutable registry: Definitions loaded from YAML at startup are validated, versioned, and stored in immutable structures. No runtime mutation is allowed.

6. Visual logic separated from functional logic: The mapping `id -> material_base + custom_model_data` is the functional contract. `asset_path` is optional metadata for documentation and export, with no functional effect in the MVP.

7. Extensibility through explicit capabilities: Mechanics implement `Mechanic` and declare their required capabilities through `MechanicDescriptor`. `MechanicContext` provides only what was requested and validated at startup.

8. Measurement-driven performance: Architectural decisions are backed by spikes and benchmarks before formal freezing. `WorkBudget` and per-tick operation limits are calibrated through load tests.

---

## 8. Allowed Functional Scope

### 8.1 Custom Blocks

- Define blocks with internal ID, base material, custom model data, required tool, and drop table.
- Every persistent block must have a stable `numeric_id` declared in YAML, unique and non-reusable.
- Place custom blocks in the world and persist their identity through binary PDC in the chunk.
- Break custom blocks and apply defined drops.
- Remove block identity from PDC when the block is broken.
- Orphaned blocks, whose `numeric_id` no longer exists in the registry, are silently removed from PDC when broken, with rate-limited warnings in the log.
- Basic right-click interaction may exist only as a future mechanic trigger.

### 8.2 Custom Tools and Items

- Define tools/items with internal ID, base material, custom model data, and attributes such as damage, speed, and modified durability.
- Apply attributes when the item is created using `editMeta()`.
- Associate one or more mechanics with a tool through YAML definitions.
- Create custom items through a debug command in the MVP.

### 8.3 Mechanic Execution

- Mechanics are behaviors triggered by interaction with a custom block or custom tool.
- Each mechanic implements `Mechanic` and declares its capabilities through `MechanicDescriptor`.
- `MechanicContext` provides capabilities on demand, such as `ctx.require(BlockMutation.class)`, validated at startup.
- The execution pipeline includes cooldown gates, work budget gates, and protection gates.
- Mechanics return `MechanicResult`: `Done`, `Partial`, or `Rejected`.
- Rescheduling is the responsibility of `MechanicExecutor`, not the mechanic.

### 8.4 Flow Control

- Cooldown per player and per mechanic using a flat key structure and automatic expiration.
- Work budget by explicit regional key, `RegionBudgetKey`, reset every region tick.
- Strict respect for event cancellation by external plugins through `ignoreCancelled = true` in action listeners.

---

## 9. Allowed Technical Scope

### 9.1 Layered Architecture

```text
customcontent/
├── internalapi/                 (Internal MVP contracts, not stable for third parties)
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
│   ├── BlockStorePort
│   ├── WorldMutationPort
│   ├── ItemMetadataPort
│   ├── DropPort
│   └── ProtectionPort
├── adapter/                     (Infrastructure implementations)
│   ├── platform/                (PaperSchedulerAdapter, FoliaSchedulerAdapter)
│   ├── persistence/             (PdcBlockStore, PdcBlockCodec)
│   ├── yaml/                    (YamlDefinitionLoader, YamlDefinitionValidator)
│   └── bukkit/                  (Segmented listeners: BlockBreakAdapter, BlockPlaceAdapter, ItemCommandAdapter)
├── builtin/                     (Official mechanics)
│   └── mechanic/                (AreaBreakMechanic)
└── bootstrap/                   (Composition Root — CustomContentPlugin)
```

### 9.2 Allowed Dependencies

- `adapter -> port`
- `adapter -> application`
- `application -> port`
- `application -> domain`
- `builtin -> internalapi`
- `builtin -> domain`
- `bootstrap -> all modules for manual composition`
- `domain -> no external dependencies`

### 9.3 Forbidden Dependencies

- `domain -> org.bukkit`
- `domain -> adapter`
- `domain -> YAML/PDC`
- `application -> adapter`
- `mechanic -> Bukkit.getScheduler()`
- `mechanic -> DefinitionRegistry`
- `mechanic -> BlockService/ItemService`
- `listener -> complex business logic`

### 9.4 Persistence

- Spatial source of truth: binary PDC in the chunk.
- Format: version header, entry count, and entry sequence.
- Entry format: relative position as `short` and `numeric_id` as `short`.
- `numeric_id`: declared stably in YAML, unique, and never reused for a different block type.
- Startup fails if duplicate `numeric_id` values are found.
- If a `numeric_id` is removed from definitions, remaining blocks in the world become orphaned and are handled as described in Section 8.1.
- Operation: the adapter reads the entire `BYTE_ARRAY`, modifies it in memory, and writes it back to PDC.
- No partial mutation inside NBT is promised.
- Cache: no chunk LRU cache in the MVP. Direct lookup through `BlockStorePort`. Cache is a future optimization conditioned by benchmarks.
- Async database: outside the MVP. Reserved for future non-spatial data only.
- Migration: YAML contains a `schema` field. The loader validates and applies migrations before building records.

### 9.5 Scheduler and Threading

- `SchedulerPort` exposes only:
  - `runOnRegion(WorldPosition, Runnable)`
- `runOnEntity` and `runAsync` are outside the MVP and may be introduced only when real use cases exist, using custom abstractions to avoid leaking Bukkit types into the interface.
- In Folia, the adapter validates `Bukkit.isOwnedByCurrentRegion(location)` before accessing the world.
- `WorkBudget` uses an explicit `RegionBudgetKey`, not `ThreadLocal` as an architectural contract.

---

## 10. Out of Scope

### 10.1 Excluded Features

- Resource pack generation or hosting.
- Mechanics that do not involve custom blocks, tools, or items.
- GUI or menus of any kind.
- A custom land protection system.
- Tool upgrade or progression systems.
- Hot-reload of definitions.
- Admin commands beyond the debug `/givecustomitem`.
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
2. `MechanicContext` with capabilities: `BlockQuery`, `BlockMutation`, `BudgetView`, `CooldownView`.
3. `MechanicRegistry` with capability validation at startup.
4. `MechanicExecutor` with pipeline: cooldown gate, budget gate, dispatch.
5. `MechanicResult`: `Done`, `Partial`, `Rejected`.
6. Rescheduling controlled by the executor, not the mechanic.
7. `area_break` mechanic: flat 3x3.
8. `WorkBudget` using `RegionBudgetKey`, with limit of 32 blocks per tick per region.
9. 500ms cooldown per player per mechanic.
10. `ProtectionPort` as an optional contract. If no adapter is implemented, only the original cancelled event is respected. Additional `area_break` blocks are processed according to the safe policy defined by the spike. Any future implementation must not depend on fake Bukkit event simulation.

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
- `auto_smelt`, `vein_miner`, `block_transform`, or any second mechanic.
- External database.
- `SchedulerAccess` inside `MechanicContext`.
- `runOnEntity` and `runAsync` in `SchedulerPort`.

---

## 12. Official Architecture Summary

Layers:

`internalapi -> domain -> application -> port <- adapter -> builtin -> bootstrap`

Dependencies are unidirectional inward.

The domain does not know infrastructure.

Mechanics do not know services.

Adapters implement ports and delegate to application services.

`CustomContentPlugin` in `bootstrap` manually instantiates all adapters and services during `onEnable`.

No DI framework is used.

---

## 13. Performance Rules

1. Use binary PDC, never a `Map` serialized into NBT. `BYTE_ARRAY` reduces cost and overhead compared to textual serialized maps.
2. No disk reads on the game tick. Definitions are loaded at startup. Block state is read from PDC, already in memory.
3. Chunk cache is a future optimization, not part of the MVP. PDC remains the only canonical source of truth.
4. `WorkBudget` is mandatory for area operations. Counters are keyed by `RegionBudgetKey`. Excess work is rescheduled by the executor.
5. Cooldowns use a flat structure. `ConcurrentHashMap<CooldownKey, Long>` avoids nested maps.
6. One PDC read per event. Item ID is extracted at listener entry and passed through context.
7. Area operation limit: 32 blocks per tick per region, adjustable. Excess work is split.
8. Every world call goes through `SchedulerPort`, never directly through `Bukkit.getScheduler()`.
9. Protection is checked through `ProtectionPort`, not through fake Bukkit event simulation.
10. Action listeners use `ignoreCancelled = true` to avoid unnecessary processing.

---

## 14. Extensibility Rules

1. The only functional extension unit is the `Mechanic` interface.
2. `Mechanic` exposes:
   - `MechanicDescriptor descriptor()` with `MechanicId`, `Set<Capability> requiredCapabilities`, and `boolean readOnly`.
   - `MechanicResult execute(MechanicContext context)`.
3. `MechanicContext` provides capabilities on demand through `ctx.require(...)`. Validation occurs at startup. Runtime failure should only happen due to internal bugs.
4. Capabilities are segregated:
   - Read: `BlockQuery`, `CooldownView`, `BudgetView`
   - Write: `BlockMutation`, `DropSink`
5. Mechanics never schedule tasks directly. They return `MechanicResult.Partial` with remaining positions, and `MechanicExecutor` decides rescheduling through `SchedulerPort`.
6. Mechanics never receive references to `Plugin`, `Server`, `World`, `DefinitionRegistry`, `BlockService`, or `ItemService`.
7. The core is not modified to add a mechanic. A mechanic is implemented and registered in `MechanicRegistry`.

---

## 15. Persistence Rules

1. Spatial source of truth: binary PDC in the chunk using `PersistentDataType.BYTE_ARRAY`.
2. Array format:
   - Byte 0: schema version, 1 byte
   - Bytes 1-2: entry count, 2 bytes, big-endian
   - Entries: relative position as `short`, 2 bytes, and `numeric_id` as `short`, 2 bytes
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
5. No external database stores world block state.
6. Loading: PDC is loaded natively with the chunk. No additional query.
7. Migration: YAML has a `schema` field. The loader applies migrations before building records.

---

## 16. Minecraft / Paper / Folia Integration Rules

1. Official platform: Paper 1.21+. Pure Spigot is not supported.
2. Folia: compatibility validated by technical spikes. The code is Folia-safe by construction, but full support depends on real tests.
3. Folia-safe rules:
   - No mutable static state.
   - Every world call goes through `SchedulerPort`.
   - Folia adapter validates `Bukkit.isOwnedByCurrentRegion(location)`.
   - `plugin.yml` declares `folia-supported: true` only after spikes are complete.
4. Listeners:
   - Segmented by aggregate, such as `BlockBreakAdapter` and `BlockPlaceAdapter`.
   - Priority: `HIGH`.
   - `ignoreCancelled = true` for action listeners.
5. `NamespacedKey` instances are reused as `static final`.
6. Item manipulation: `item.editMeta()` is mandatory. Never use the `getItemMeta()` + `setItemMeta()` pair for writes.
7. No NMS, reflection, or internal server APIs.
8. In Folia, `area_break` on a region border ignores blocks outside the current region in the MVP.

---

## 17. Criteria for Accepting or Rejecting New Mechanics

### Accepted Only If All Criteria Are Met

1. The mechanic operates on a custom block, tool, or item registered by the plugin.
2. Required capabilities are available in the current contract and validated at startup.
3. Execution can be split and respects `MechanicResult.Partial`.
4. It performs no disk or network I/O during execution.
5. It does not call third-party APIs directly.

### Rejected If Any Criterion Applies

1. It is outside the block/tool/item domain.
2. It requires mutable global state or uncoordinated cross-region communication.
3. It attempts to access internal services such as `BlockService`, `ItemService`, or registries directly.
4. It performs unbounded loops or ignores `WorkBudget`.
5. It modifies visual presentation such as particles or models outside the resource pack contract.
6. It involves combat, economy, teleportation, or generic abilities not tied to blocks/items.

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

---

## 19. Final Declaration of What the Plugin Is

CustomContent Engine is a Paper-first engine for creating custom blocks, tools, and items in Minecraft servers.

It loads immutable YAML definitions at startup, creates items identifiable through PDC, persists custom blocks in binary chunk PDC, and executes mechanics controlled by budget, cooldown, and capability validation.

The architecture strictly separates pure domain, application, ports, and adapters.

The domain does not know Bukkit, Paper, Folia, PDC, or YAML.

Folia compatibility is an architectural goal validated through spikes, not a final promise before real tests.

The focus is absolute: custom blocks, custom tools, and custom items.

---

## 20. Final Declaration of What the Plugin Is Not

- It is not a resource pack generator.
- It is not a land protection system.
- It is not a generic ability framework.
- It is not an economy, teleportation, magic, or combat plugin.
- It is not a GUI or menu manager.
- It is not a dynamic runtime content loader.
- It does not use invisible entities to represent blocks.
- It does not perform blocking I/O on the game thread.
- It does not expose a stable public API to third parties in the MVP.
- It does not officially support pure Spigot.
- It does not use NMS, reflection, or internal server APIs.

---

## Conditional Scope Freeze

This document is version 3.0.1 of the scope and is approved as conditional implementation scope.

Formal freezing is conditional on execution and validation of the required technical spikes.

### Spike 1 — Binary PDC Performance

Objective: Measure the real read/write cost of `BYTE_ARRAY` with 256, 512, and 1024 custom blocks per chunk under simulated load of 50 players.

Metrics:

- Average lookup time
- Average insert time
- Average remove time
- Memory allocation
- Impact on block break/place events

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

After the three spikes are executed, documented, and approved, this document will be formally frozen as version `3.0.1-final`.

Any later change must follow the ADR process with technical justification and architecture review approval.

---

End of document.
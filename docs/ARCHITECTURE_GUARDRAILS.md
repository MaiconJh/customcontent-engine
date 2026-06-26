# CustomContent Engine — Architecture Guardrails

Version: 4.0.0 — Conservative Evolvable Core Alignment  
Status: Active guardrails for implementation and architecture review  
Aligned with: `docs/PROJECT_SCOPE.md`, ADR-0003, ADR-0004, ADR-0005, ADR-0006, ADR-0007

This file must be checked before every implementation.

Its purpose is to keep development aligned with the official scope document while protecting two goals at the same time:

1. Prevent feature inflation in the stable core.
2. Prevent the engine from becoming so rigid that future extensibility is blocked.

The core is conservative, but evolvable.

---

## 1. Product Focus

CustomContent Engine exists as a Paper-first engine for:

- custom blocks;
- custom tools;
- custom items;
- mechanics directly attached to custom blocks, tools, or items.

Any feature that is not directly connected to at least one of these elements is out of scope for the base product.

Future extensions may expand behavior around these elements, but must not transform the engine into a generic:

- economy plugin;
- quest framework;
- combat framework;
- teleportation framework;
- GUI/menu manager;
- scripting platform;
- generic ability framework;
- land protection system.

### Decision Rule

If a proposed feature is useful but not structurally tied to custom blocks, tools, or items, it must not enter the core.

It may only be considered as:

- external plugin integration;
- optional module;
- experimental module;
- devtool;
- future ADR-backed extension point.

---

## 2. Platform

- Official platform: Paper 1.21+.
- Folia compatibility is an architectural goal and must be validated through technical spikes.
- Pure Spigot is not officially supported.
- Folia support must not be declared as final until ownership, scheduling, and cross-region behavior are validated.

---

## 3. Current MVP Boundary

Implementation must currently favor MVP simplicity unless an ADR explicitly expands the active milestone.

Allowed MVP-0 work:

- load `definitions.yml` at startup;
- validate basic schema and stable `numeric_id`;
- register one custom block;
- register one custom tool/item;
- provide `/givecustomitem` debug command;
- create custom items with PDC identity;
- place custom blocks;
- persist custom block identity in binary chunk PDC;
- break custom blocks;
- remove block identity from PDC;
- deliver simple YAML-defined drops;
- respect cancelled Bukkit events.

Allowed MVP-1 work only after MVP-0 is stable:

- introduce the `Mechanic` contract;
- introduce `MechanicDescriptor`;
- introduce `MechanicContext`;
- introduce explicit capabilities;
- introduce `MechanicExecutor`;
- introduce `MechanicResult`;
- introduce one controlled mechanic, such as `area_break`, as an official module/builtin mechanic, not as core behavior.

---

## 4. Out of MVP Scope

Do not implement in the MVP unless an ADR explicitly changes the milestone:

- `vein_miner`;
- `auto_smelt`;
- second or third gameplay mechanic beyond the approved MVP-1 mechanic;
- Fortune;
- Silk Touch;
- vanilla enchantment logic;
- anvil handling;
- smithing handling;
- grindstone handling;
- repair handling;
- TileState persistence;
- chunk LRU cache;
- database persistence;
- stable public API;
- ServiceLoader integration;
- WorldGuard integration;
- GriefPrevention integration;
- pure Spigot support;
- advanced Folia cross-region behavior;
- `runAsync`;
- `runOnEntity`;
- `SchedulerAccess` inside `MechanicContext`;
- GUI/menu systems;
- resource pack generation or hosting;
- generic scripting systems.

---

## 5. Stability Levels

Every API, module, extension point, and mechanic must be classified.

### 5.1 Internal

Internal code may change at any time.

Examples:

- implementation details;
- bootstrap composition;
- adapters;
- internal validators;
- internal registries not declared public.

Rules:

- No compatibility promise.
- Third-party usage must not be encouraged.
- Internal packages must not become accidental public API.

### 5.2 Experimental

Experimental code exists for validation.

Examples:

- candidate extension points;
- experimental capabilities;
- prototype mechanics;
- spike-driven modules.

Rules:

- May change or be removed.
- Must be clearly documented as experimental.
- Must not be required by the stable core.
- Must not be used to justify core expansion without ADR review.

### 5.3 Official

Official modules are maintained by the project but are not automatically part of the core.

Examples:

- `area_break` mechanic;
- `block_transform` mechanic;
- future official mechanics;
- debug/dev tooling;
- optional integration modules.

Rules:

- Official does not mean core.
- Official modules must use public or internal contracts, not bypass architecture.
- Official modules must not create circular dependencies.

### 5.4 Stable

Stable contracts are compatibility-sensitive.

Rules:

- Breaking changes require ADR.
- Breaking changes require versioning policy once public API exists.
- Stable contracts must be small and difficult to expand.

### 5.5 Deprecated

Deprecated code is still available temporarily but scheduled for removal.

Rules:

- Must document replacement path.
- Must not be used by new code.
- Must be removed only according to versioning policy once stable API exists.

---

## 6. Core Evolution Policy

The stable core must remain conservative.

The stable core may contain only structural mechanisms required to:

- define custom content;
- identify custom content;
- validate custom content;
- persist custom block identity;
- register definitions;
- execute mechanics through contracts;
- expose explicit capabilities;
- return abstract execution results.

The stable core must not contain feature-specific behavior.

### 6.1 Core Is Not Immutable by Dogma

The core may evolve when a repeated structural need appears across multiple independent modules and cannot be solved cleanly through existing extension points.

A new core contract, capability, or extension point requires:

1. ADR approval.
2. At least one technical spike or proof.
3. At least two concrete use cases or one unavoidable structural need.
4. Tests or fitness functions protecting the new boundary.
5. Evidence that the change reduces total system complexity.
6. Evidence that simple mechanics are not made harder to write.

### 6.2 Default Rejection Rule

Every new feature, capability, or extension point is rejected from the stable core by default.

It must first live as one of:

- spike;
- experimental module;
- official module;
- experimental contract;
- devtool;
- external integration.

Only after validation may it be considered for stable core promotion.

---

## 7. Extension Incubation Pipeline

New ideas must move through this pipeline:

```text
Idea
-> Technical Spike
-> Experimental Module or Experimental Contract
-> Official Module
-> Candidate Stable Contract
-> Stable Core
```

Skipping stages requires explicit ADR justification.

### Promotion Criteria

A feature may move closer to the stable core only if it:

- solves a repeated problem;
- remains tied to custom blocks, tools, or items;
- is independent from Bukkit/Paper/Folia implementation details;
- does not depend on a single mechanic;
- can be tested without a Minecraft server when applicable;
- does not add mandatory complexity to simple use cases;
- does not create dependency cycles;
- has clear performance characteristics;
- has documented failure behavior;
- has guardrails or tests.

---

## 8. Dependency Rules

Allowed:

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

Forbidden:

```text
domain       -> org.bukkit
domain       -> io.papermc
domain       -> net.minecraft
domain       -> adapter
domain       -> YAML/PDC
application  -> adapter
application  -> Bukkit/Paper/Folia
mechanic     -> Bukkit.getScheduler()
mechanic     -> DefinitionRegistry
mechanic     -> BlockService/ItemService
mechanic     -> adapter
listener     -> complex business logic
builtin      -> adapter
builtin      -> bootstrap
```

### Architectural Principle

Dependencies must point inward.

Platform knowledge belongs in adapters.

Composition belongs in bootstrap or explicit composition factories.

Feature behavior belongs in mechanics or modules, not in listeners.

---

## 9. Domain Rules

The domain layer must:

- contain pure Java records or immutable classes;
- contain no Bukkit, Paper, Folia, PDC, YAML, database, or scheduler code;
- be testable without a Minecraft server;
- express definitions, IDs, policies, and registries;
- avoid runtime platform state.

The domain layer must not:

- import `org.bukkit`;
- import `io.papermc`;
- import `net.minecraft`;
- read files;
- access PDC;
- know adapters;
- mutate runtime state;
- contain debug tooling;
- contain platform-specific optimization logic.

---

## 10. Application Rules

The application layer may:

- orchestrate use cases;
- depend on domain;
- depend on ports;
- depend on internal contracts;
- coordinate mechanics;
- enforce budget, cooldown, and protection gates through abstractions;
- execute business flows through abstractions.

The application layer must not:

- import adapters;
- call Bukkit directly;
- read YAML directly;
- access PDC directly;
- contain platform-specific code;
- contain command or listener logic;
- become a dumping ground for unrelated features.

### Composition Rule

If an adapter needs a set of capabilities, that composition should be created by:

- bootstrap;
- application service;
- factory;
- composition root helper.

Adapters should not manually assemble complex mechanic execution graphs inside event handlers or command handlers.

---

## 11. Adapter Rules

Adapters may:

- use Bukkit, Paper, PDC, YAML, and platform APIs;
- implement ports;
- translate external events into application calls;
- delegate logic to application services;
- apply abstract results to the platform.

Adapters must not:

- contain complex business logic;
- duplicate domain rules;
- bypass ports when interacting with core logic;
- assemble large capability graphs directly in listeners or commands;
- become permanent homes for debug-only behavior.

---

## 12. Listener Rules

Bukkit listeners must:

- use `ignoreCancelled = true` for action listeners;
- be segmented by aggregate or responsibility;
- convert events into commands, inputs, or context;
- delegate to application services;
- stay thin;
- perform early exits quickly.

Listeners must not:

- contain complex business logic;
- perform custom drop resolution directly;
- execute mechanics directly without the executor;
- access PDC directly except through dedicated adapters;
- perform heavy loops;
- perform disk or network I/O;
- parse YAML or definitions.

---

## 13. Mechanic Rules

Mechanics are extension units for behavior tied to custom blocks, tools, or items.

When introduced:

- mechanics must implement `Mechanic`;
- mechanics must expose `MechanicDescriptor`;
- mechanics must declare required capabilities;
- capabilities must be validated at startup;
- mechanics must return `MechanicResult`;
- mechanics must not schedule their own tasks;
- `MechanicExecutor` controls rescheduling;
- mechanics must be testable without a Minecraft server;
- mechanics must not receive `Plugin`, `Server`, `World`, `DefinitionRegistry`, `BlockService`, or `ItemService`;
- mechanics must not call third-party plugin APIs directly;
- mechanics must not perform disk or network I/O during execution;
- mechanics must not ignore `WorkBudget` when processing multiple blocks.
- Mechanics may request the `MECHANIC_CONFIG` capability to receive YAML arguments. Arguments are pure data; mechanics must not use them to access services or platform APIs.
- The `block_transform` mechanic is an official implementation that uses `MECHANIC_CONFIG`, `BLOCK_PLACEMENT`, and `BLOCK_MUTATION`. It remains pure and stateless.

### Official Mechanics

Official mechanics may live in `builtin/` or official module packages.

Rules:

- Official mechanics are not core.
- Official mechanics must not be required by core startup unless explicitly enabled by the milestone.
- New official mechanics require ADR or milestone approval.

---

## 14. Capability Governance

Capabilities are explicit permissions granted to mechanics.

A mechanic receives only what it declares and what startup validation approves.

### 14.1 Core Capabilities

Core capabilities must be:

- generic;
- stable;
- platform-independent;
- directly related to custom blocks, tools, or items;
- useful across multiple mechanics.

Examples:

- `BlockQuery`;
- `BlockMutation`;
- `BlockPlacement`;
- `BudgetView`;
- `CooldownView`;
- `DropSink`;
- `ExecutionOrigin`;
- `MechanicConfig`.

### 14.2 Module Capabilities

Specialized capabilities must start outside the stable core.

Examples:

- particle emitter;
- sound emitter;
- transform rule;
- vein graph query;
- resource export;
- advanced visual effect capability.

Rules:

- Module capabilities must not be placed in stable core by default.
- Module capabilities must not force unrelated mechanics to depend on them.
- Module capabilities must have clear ownership.

### 14.3 Forbidden Core Capabilities

The following must not become stable core capabilities:

- economy;
- quest;
- combat;
- teleportation;
- GUI/menu;
- generic scripting;
- generic permission framework;
- direct WorldGuard or GriefPrevention APIs.

### 14.4 Tool Tiers (Official)

Tool tiers are a property of the mining domain, not a mechanic capability. They are implemented in the application layer (`MiningSessionService`) and validated before a mining session is created. Tiers are orthogonal to mechanics and do not affect `MechanicContext`.

### 14.5 Context Anti-God-Object Rule

`MechanicContext` must not become a service locator for the entire plugin.

A capability is not allowed merely because it is convenient. It must be justified by scope, safety, testability, and reuse.

---

## 15. Persistence Rules

Spatial source of truth:

```text
Chunk PersistentDataContainer
PersistentDataType.BYTE_ARRAY
```

Binary format:

```text
Byte 0: schema version
Bytes 1-2: entry count, big-endian
Each entry:
  - relative block position as short
  - numeric_id as short
```

Rules:

- `numeric_id` is mandatory.
- `numeric_id` must be declared in YAML.
- `numeric_id` must be unique.
- `numeric_id` must never be reused for another block type while persisted worlds may still contain that ID.
- The adapter reads the entire byte array, modifies it in memory, and writes it back.
- No partial mutation inside NBT is assumed.
- Orphaned entries are removed when broken, with rate-limited warning.
- No external database stores world block state.
- Cache is not part of the MVP.
- Any cache requires benchmark evidence and ADR approval.

---

## 16. Scheduler Rules

Current MVP:

```text
SchedulerPort:
  runOnRegion(WorldPosition, Runnable)
```

Out of MVP:

```text
runAsync
runOnEntity
SchedulerAccess inside MechanicContext
```

Rules:

- Every world modification must go through `SchedulerPort` or `WorldMutationPort`.
- Never call `Bukkit.getScheduler()` from mechanics.
- Folia ownership validation belongs in the Folia adapter.
- Do not use `ThreadLocal` as the architectural model for regional budgeting.
- `WorkBudget` must use explicit `RegionBudgetKey`.
- Cross-region behavior must be validated by spike before becoming a promise.
- Region-border mechanics must define one of: ignore, reject, split, or reschedule.

---

## 17. Resource Pack Boundary

The plugin may define:

- `material_base`;
- `custom_model_data`;
- optional `asset_path` as documentation/export metadata.

The plugin must not in the MVP:

- generate a resource pack;
- host a resource pack;
- modify models dynamically;
- mix visual asset generation with functional logic.

Future resource-related functionality must start as an experimental or official module, not as stable core behavior.

---

## 18. Performance Rules

- No blocking I/O on the game tick.
- YAML is loaded only at startup.
- No chunk-wide scans.
- No invisible entities for block representation.
- No chunk LRU cache in MVP.
- Area mechanics must use `WorkBudget`.
- Cooldowns must use flat keys.
- Expiration and cleanup must be explicit.
- Avoid reflection in hot path.
- Avoid NMS and internal server APIs.
- Avoid stream-heavy or allocation-heavy code in high-frequency paths when a simple loop is sufficient.
- IDs should be resolved before hot path execution whenever practical.
- Definitions should be immutable and prevalidated.

### Hot Path Review Required

Any change touching these paths requires performance review:

- block place;
- block break;
- item identity lookup;
- PDC encode/decode;
- mechanic execution;
- area operations;
- scheduler dispatch;
- protection checks.

---

## 19. Protection Rules

MVP-0:

- respect `Event#isCancelled()` on original Bukkit events.

MVP-1 and later:

- `ProtectionPort` may exist as a contract.
- Direct integrations with WorldGuard or GriefPrevention are not part of the MVP.
- Do not simulate fake Bukkit protection events.
- External protection plugins must be accessed only through adapters or optional modules.

---

## 20. DevTools and Debug Rules

Debug functionality may exist, but it must not become production architecture.

Allowed:

- `/givecustomitem` in MVP;
- internal debug commands;
- diagnostic tools;
- registry dump tools;
- mechanic test commands.

Rules:

- Debug commands must be clearly isolated.
- Debug commands must not define core architecture.
- Debug commands must not assemble large runtime graphs directly.
- Debug commands must not become the only way to exercise production flows.
- Debug tools should be disabled or guarded in production unless explicitly enabled.

---

## 21. Public API Rules

There is no stable public API in the MVP.

Before introducing public API, the project must define:

- package boundary;
- versioning policy;
- compatibility policy;
- deprecation policy;
- test coverage;
- examples;
- migration rules.

Recommended future separation:

```text
internalapi      unstable internal contracts
experimentalapi  incubating contracts with no compatibility promise
publicapi        stable contracts with compatibility rules
```

Rules:

- `internalapi` must not be marketed as stable public API.
- External usage of internal classes must not force compatibility promises.
- Public API introduction requires ADR.

---

## 22. Architecture Fitness Functions

Architecture must be protected by automated checks whenever practical.

Recommended checks:

- `domain` must not depend on Bukkit/Paper/Folia/NMS.
- `application` must not depend on `adapter`.
- `builtin` mechanics must not depend on Bukkit/Paper/Folia adapters.
- `adapter` must not be used by `domain` or `application`.
- No NMS usage.
- No reflection usage unless explicitly approved by ADR.
- No YAML parsing in event listeners.
- No disk I/O in hot path.
- No debug command registered unintentionally.
- No new stable capability without ADR.
- No new official mechanic without milestone or ADR approval.

Suggested tools:

- ArchUnit;
- JUnit;
- `rg` audit scripts;
- Gradle verification tasks;
- property-based tests for pure codecs and registries.

---

## 23. Changes That Require ADR

A change requires an ADR if it:

- adds functionality outside the MVP;
- adds external plugin integration;
- changes persistence format;
- changes the mechanic contract;
- changes the capability model;
- adds a new core capability;
- adds a new extension point;
- promotes an experimental contract to stable;
- changes Folia behavior;
- adds cache;
- adds database persistence;
- introduces public API;
- changes dependency rules;
- adds scheduler methods;
- adds support for Spigot;
- adds a new official mechanic;
- adds any gameplay system outside custom blocks/tools/items;
- changes stability level policy;
- changes module incubation policy;
- changes architecture fitness functions.

---

## 24. Required Technical Spikes

Formal scope freezing requires these spikes:

1. Binary PDC performance.
2. Folia cross-region behavior.
3. Mechanic contract sufficiency.

Additional recommended spikes before future expansion:

4. Capability governance sufficiency.
5. Public API boundary validation.
6. Official module incubation workflow.
7. Architecture fitness function enforcement.

Until required spikes are complete, implementation must favor MVP simplicity over speculative flexibility.

---

## 25. Implementation Checklist

Before every implementation, verify:

- Is this change inside the current milestone?
- Does it directly relate to custom blocks, tools, or items?
- Is it core, application, adapter, module, experimental, devtool, or external integration?
- Has its stability level been identified?
- Does any domain class import Bukkit/Paper/Folia/NMS?
- Does any application class import adapters?
- Did any listener receive business logic?
- Did any mechanic access services or scheduler directly?
- Was any out-of-scope feature added?
- Does this change add a capability?
- Does this change add an extension point?
- Does this change require an ADR?
- Does this change affect hot path performance?
- Does this change preserve simple mechanic creation?
- Is the code aligned with `docs/PROJECT_SCOPE.md`?
- Is the code aligned with this guardrails document?

---

## 26. Final Decision Rules

### 26.1 MVP Rule

If there is a conflict between extensibility, future compatibility, and MVP simplicity, MVP simplicity wins until the required technical spikes are complete.

### 26.2 Core Rule

If there is a conflict between adding a useful feature and protecting the stable core, protect the stable core.

### 26.3 Evolution Rule

If a repeated structural need cannot be solved cleanly outside the core, the core may evolve through ADR, spike validation, and architecture review.

### 26.4 Extensibility Rule

Extensibility must not mean unlimited scope.

The engine may evolve around custom blocks, tools, items, definitions, persistence, mechanics, capabilities, and controlled module contracts.

It must not become a generic server framework.

---

End of document.

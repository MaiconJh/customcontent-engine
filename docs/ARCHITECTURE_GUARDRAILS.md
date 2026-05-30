# CustomContent Engine — Architecture Guardrails

This file must be checked before every implementation.

Its purpose is to keep development aligned with the official scope document:

`docs/PROJECT_SCOPE.md`

---

## 1. Absolute Focus

CustomContent Engine exists only for:

- custom blocks;
- custom tools;
- custom items.

Any feature that is not directly connected to at least one of these three elements is out of scope.

---

## 2. Platform

- Official platform: Paper 1.21+.
- Folia compatibility is planned but must be validated through technical spikes.
- Pure Spigot is not officially supported.

---

## 3. Current MVP

Development must currently focus only on MVP-0.

Allowed MVP-0 work:

- load `definitions.yml`;
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

---

## 4. Out of MVP Scope

Do not implement:

- `area_break`;
- `vein_miner`;
- `block_transform`;
- `auto_smelt`;
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
- `SchedulerAccess` inside `MechanicContext`.

---

## 5. Dependency Rules

Allowed:

```text
adapter      -> port
adapter      -> application
application  -> port
application  -> domain
builtin      -> internalapi
builtin      -> domain
bootstrap    -> all modules for composition
domain       -> no external dependencies
```

Forbidden:

```text
domain       -> org.bukkit
domain       -> adapter
domain       -> YAML/PDC
application  -> adapter
mechanic     -> Bukkit.getScheduler()
mechanic     -> DefinitionRegistry
mechanic     -> BlockService/ItemService
listener     -> complex business logic
```

---

## 6. Domain Rules

The domain layer must:

- contain pure Java records or immutable classes;
- contain no Bukkit, Paper, Folia, PDC, YAML, database, or scheduler code;
- be testable without a Minecraft server;
- express definitions, IDs, policies, and registries.

The domain layer must not:

- import `org.bukkit`;
- read files;
- access PDC;
- know adapters;
- mutate runtime state.

---

## 7. Application Rules

The application layer may:

- orchestrate use cases;
- depend on domain;
- depend on ports;
- execute business flows through abstractions.

The application layer must not:

- import adapters;
- call Bukkit directly;
- read YAML directly;
- access PDC directly;
- contain platform-specific code.

---

## 8. Adapter Rules

Adapters may:

- use Bukkit, Paper, PDC, YAML, and platform APIs;
- implement ports;
- translate external events into application calls;
- delegate logic to application services.

Adapters must not:

- contain complex business logic;
- duplicate domain rules;
- bypass ports when interacting with core logic.

---

## 9. Listener Rules

Bukkit listeners must:

- use `ignoreCancelled = true` for action listeners;
- be segmented by aggregate or responsibility;
- convert events into commands or context;
- delegate to application services;
- stay thin.

Listeners must not:

- contain complex business logic;
- perform custom drop resolution directly;
- execute mechanics directly;
- access PDC directly except through dedicated adapters;
- perform heavy loops.

---

## 10. Persistence Rules

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
- `numeric_id` must never be reused for another block type.
- The adapter reads the entire byte array, modifies it in memory, and writes it back.
- No partial mutation inside NBT is assumed.
- Orphaned entries are removed when broken, with rate-limited warning.
- No external database stores world block state.

---

## 11. Scheduler Rules

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

---

## 12. Mechanic Rules

Mechanics are not part of MVP-0.

When introduced in MVP-1:

- mechanics must implement `Mechanic`;
- mechanics must expose `MechanicDescriptor`;
- mechanics must declare required capabilities;
- capabilities must be validated at startup;
- mechanics must return `MechanicResult`;
- mechanics must not schedule their own tasks;
- `MechanicExecutor` controls rescheduling;
- mechanics must not receive `Plugin`, `Server`, `World`, `DefinitionRegistry`, `BlockService`, or `ItemService`.

---

## 13. Resource Pack Boundary

The plugin may define:

- `material_base`;
- `custom_model_data`;
- optional `asset_path` as documentation/export metadata.

The plugin must not:

- generate a resource pack;
- host a resource pack;
- modify models dynamically;
- mix visual asset generation with functional logic.

---

## 14. Performance Rules

- No blocking I/O on the game tick.
- YAML is loaded only at startup.
- No chunk-wide scans.
- No invisible entities for block representation.
- No chunk LRU cache in MVP.
- Area mechanics must use `WorkBudget`.
- Cooldowns must use flat keys.
- Expiration and cleanup must be explicit.

---

## 15. Protection Rules

MVP-0:

- respect `Event#isCancelled()` on original Bukkit events.

MVP-1 and later:

- `ProtectionPort` may exist as a contract.
- Direct integrations with WorldGuard or GriefPrevention are not part of the MVP.
- Do not simulate fake Bukkit protection events.

---

## 16. Changes That Require ADR

A change requires an ADR if it:

- adds functionality outside the MVP;
- adds external plugin integration;
- changes persistence format;
- changes the mechanic contract;
- changes the capability model;
- changes Folia behavior;
- adds cache;
- adds database persistence;
- introduces public API;
- changes dependency rules;
- adds scheduler methods;
- adds support for Spigot;
- adds any gameplay system outside custom blocks/tools/items.

---

## 17. Required Technical Spikes

Formal scope freezing requires these spikes:

1. Binary PDC performance.
2. Folia cross-region behavior.
3. Mechanic contract sufficiency.

Until those spikes are complete, implementation must favor MVP simplicity over future flexibility.

---

## 18. Implementation Checklist

Before every implementation, verify:

- Is this change inside MVP-0?
- Does it directly relate to custom blocks, tools, or items?
- Does any domain class import Bukkit/Paper/Folia?
- Does any application class import adapters?
- Did any listener receive business logic?
- Did any mechanic access services or scheduler directly?
- Was any out-of-scope feature added?
- Does this change require an ADR?
- Is the code aligned with `docs/PROJECT_SCOPE.md`?
- Is the code aligned with this guardrails document?

---

## 19. Decision Rule

If there is a conflict between extensibility, future compatibility, and MVP simplicity, MVP simplicity wins until all required technical spikes are complete.
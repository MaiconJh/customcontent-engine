# MVP-1 Area Break Event Integration Plan

Status: Planned  
Date: 2026-06-07

## 1. Objective

Integrate `area_break` with a real `BlockBreakEvent` path for MVP-1 while keeping `AreaBreakMechanic` pure, stateless, and independent from Bukkit/Paper.

The real event may provide the initial platform data, but the mechanic must continue to receive only pure capabilities through `MechanicContext`.

## 2. Current State

The project currently has:

- MVP-0 custom block break handled by `BlockBreakAdapter`.
- MVP-0 custom block identity lookup/removal and simple drops handled through `BlockService`, `BlockStorePort`, and `DropPort`.
- `AreaBreakMechanic` implemented as a pure builtin mechanic.
- Controlled debug runtime through `/debugareabreak`.
- `AreaBreakRuntimeService` composing runtime capabilities in `application`.
- `MechanicExecutor` interpreting `MechanicResult.Partial` and rescheduling continuations through `SchedulerPort.runOnRegion`.
- Cooldown policy for continuations documented in `docs/milestones/MVP-1-COOLDOWN-CONTINUATION-POLICY.md`.
- Spike 2 completed with the same-region-safe recommendation.

No real `BlockBreakEvent` path currently triggers `area_break`.

## 3. Recommended Trigger

The real event integration should trigger `area_break` only when the player breaks a block using a custom item/tool that is explicitly associated with `area_break`.

The current YAML format does not yet define a formal item-to-mechanic association. For the first controlled MVP-1 integration, use an explicit internal activation policy without expanding YAML.

Recommended first implementation:

- add an application-level trigger policy, for example `AreaBreakTriggerPolicy`;
- initialize it in the composition root with a small explicit allowlist of custom tool item IDs that activate `area_break`;
- keep this policy internal to MVP-1 and not a public API;
- keep YAML unchanged for this step.

This avoids a format change while still making the runtime trigger explicit and testable.

A future YAML extension such as item-defined mechanics may be desirable, but it changes the formal definition format. That should be planned separately and may require ADR before implementation.

## 4. Listener Boundary

`BlockBreakAdapter` must remain thin.

The listener may:

- run with `ignoreCancelled = true`;
- read the Bukkit event's origin block location;
- convert the origin to `WorldPosition`;
- read `CustomItemId` from the item used by the player, if present;
- derive an actor key from the player;
- delegate to an application service;
- translate the returned result into Bukkit event effects such as drop suppression when already required by MVP-0.

The listener must not:

- assemble mechanic capabilities;
- execute `AreaBreakMechanic` directly;
- access chunk PDC directly;
- resolve drops directly;
- contain trigger policy beyond simple delegation;
- call scheduler APIs directly.

## 5. Application Boundary

The application layer should decide whether `area_break` applies.

Recommended structure:

- keep the existing MVP-0 custom block break path intact;
- introduce or adjust an application service that receives pure event input:
  - `WorldPosition origin`;
  - optional `CustomItemId usedItemId`;
  - actor key;
  - any pure flags needed to preserve the existing MVP-0 behavior;
- consult the internal `AreaBreakTriggerPolicy`;
- if the policy does not match, return without invoking mechanics;
- if the policy matches, call `AreaBreakRuntimeService` or a small generic mechanic trigger service.

`AreaBreakRuntimeService` should remain responsible for capability composition. It may receive an additional pure execution option to exclude the origin, but Bukkit/Paper must remain outside it.

## 6. Drops And Recursion

Recommended MVP-1 approach: option A.

The real `BlockBreakEvent` keeps the existing MVP-0 handling for the origin block. `area_break` processes only additional positions around the origin.

This is the safest first integration because:

- the origin block already has tested MVP-0 behavior;
- custom PDC identity removal for the origin remains in the existing path;
- origin drops remain produced once by the existing drop flow;
- area drops are limited to additional blocks;
- the mechanic integration does not need to replace the whole break pipeline.

The current `AreaBreakMechanic` computes a flat 3x3 including the origin. Before event integration, the runtime boundary must prevent the origin from being mutated or dropped twice.

Recommended controlled adjustment without changing the mechanic contract:

- provide an origin-excluding capability wrapper in the application runtime path;
- `BlockQuery` returns empty for the origin when executing from the real event path;
- `BlockMutation` treats the origin as a no-op safeguard;
- `DropSink` only receives drops for positions successfully mutated outside the origin.

This keeps `AreaBreakMechanic` pure and avoids changing `Mechanic.execute(MechanicContext)`.

No ADR is needed for this wrapper approach because it does not change formal contracts. An ADR would be required if the project changes `AreaBreakMechanic` contracts, `MechanicResult`, capability result types, or YAML format.

## 7. Same-Region-Safe Policy

The event integration must preserve the Spike 2 recommendation.

Rules:

- process only positions that the runtime can treat as safe for the current region;
- do not mutate positions outside the safe region;
- represent unprocessed positions as `MechanicResult.Partial.remaining`;
- keep advanced automatic cross-region behavior out of MVP-1;
- keep `SchedulerPort` unchanged with only `runOnRegion(WorldPosition, Runnable)`;
- keep scheduler access in `MechanicExecutor`, never in mechanics.

The first real event implementation may remain Paper-oriented and conservative. It must not claim full Folia cross-region behavior.

## 8. Cooldown And Budget

Cooldown rules:

- the initial player-triggered execution checks cooldown normally;
- repeated independent player executions are blocked by the 500ms cooldown;
- internal `Partial` continuations use the continuation cooldown policy and are not blocked by the initial cooldown;
- mechanics continue to request `CooldownView` through `MechanicContext`.

Budget rules:

- `BudgetView` remains mandatory for area operations;
- each additional mutated block consumes budget;
- exhausted budget produces `Partial`;
- `MechanicExecutor` anti-loop protection remains mandatory;
- continuations continue through `SchedulerPort.runOnRegion`.

## 9. ProtectionPort

MVP-0 already respects cancelled Bukkit events through `ignoreCancelled = true`.

For additional blocks processed by `area_break`:

- if a `ProtectionPort` implementation exists, the application/capability boundary may consult it before mutation;
- if no implementation exists, the MVP-1 policy must stay conservative and avoid external integration;
- do not simulate fake Bukkit block break events;
- do not add WorldGuard or GriefPrevention integration in MVP-1;
- do not add complex permission systems.

The first controlled implementation may limit additional processing to plugin-managed custom blocks and the safe policy available through existing ports.

## 10. Out Of Scope

This event integration plan does not include:

- Fortune;
- Silk Touch;
- real durability handling;
- complete required-tool validation;
- complex permissions;
- advanced YAML mechanics configuration;
- a second mechanic;
- `vein_miner`;
- `block_transform`;
- `auto_smelt`;
- WorldGuard integration;
- GriefPrevention integration;
- advanced Folia cross-region behavior;
- `runAsync`;
- `runOnEntity`;
- `SchedulerAccess`;
- ServiceLoader;
- stable public API;
- cache;
- database persistence.

## 11. Probable Future Files

Likely files to touch during the future implementation:

- `src/main/java/com/customcontentengine/adapter/bukkit/BlockBreakAdapter.java`
- `src/main/java/com/customcontentengine/application/block/BlockService.java`, only if the pure MVP-0 break result needs to expose enough information for the trigger boundary
- `src/main/java/com/customcontentengine/application/mechanic/AreaBreakRuntimeService.java`
- a new application service such as `AreaBreakEventTriggerService`
- a new application policy such as `AreaBreakTriggerPolicy`
- `src/main/java/com/customcontentengine/port/ItemMetadataPort.java`, only if the current port cannot read the tool item identity at event entry
- `src/main/java/com/customcontentengine/adapter/bukkit/BukkitItemMetadataAdapter.java`, only if needed to support the port above
- tests for `BlockBreakAdapter`
- tests for the new application trigger service or policy
- tests for `AreaBreakRuntimeService` origin exclusion behavior
- Paper integration test coverage if practical

`definitions.yml` should not be changed for the first controlled integration unless the project chooses the YAML association route.

## 12. Risks

Main risks:

- duplicated drops if the origin is processed by both MVP-0 and `area_break`;
- indirect recursion if additional blocks are broken through Bukkit event simulation;
- origin processed twice because `AreaBreakMechanic` includes it in the 3x3;
- cooldown accidentally blocking internal continuation;
- budget not applied to all additional mutations;
- unsafe Folia region access near boundaries;
- listener accumulating policy and capability composition;
- YAML growing into a mechanic configuration format before the schema decision is made;
- hardcoded MVP-1 trigger policy being mistaken for stable public API.

Mitigations:

- use option A: MVP-0 owns the origin, `area_break` owns additional blocks;
- exclude the origin through runtime capability wrappers;
- mutate additional blocks through `BlockMutation`/ports, not fake events;
- keep trigger policy in application and mark it internal to MVP-1;
- keep cooldown continuation policy unchanged;
- keep `MechanicExecutor` anti-loop protection active;
- add tests around origin exclusion and no duplicate drops.

## 13. Recommended Decision

The real event integration is ready to implement as a controlled MVP-1 step if it follows the conservative trigger and origin policy in this document.

Recommended decisions:

- Trigger: custom item/tool ID must match an internal MVP-1 `area_break` trigger policy.
- YAML: do not change YAML for the first integration.
- Origin/drops: use option A; MVP-0 handles the origin, `area_break` handles only additional positions.
- Origin exclusion: implement through runtime capability wrappers or an application-level execution option, not by adding Bukkit/Paper to the mechanic.
- Recursion: do not simulate Bukkit break events for additional blocks.
- Folia: remain same-region-safe and use `Partial` for unprocessed work.
- Cooldown: initial execution checks cooldown; continuations use the accepted continuation policy.
- ADR: no ADR is needed if the implementation keeps YAML and formal contracts unchanged.

ADR is required before implementation if the project chooses to:

- add formal YAML item-to-mechanic association;
- change `Mechanic.execute`;
- change capability contracts;
- change `MechanicResult.Partial`;
- add scheduler methods;
- introduce advanced cross-region semantics;
- add a new stable extension point.

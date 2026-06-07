# MVP-2 Custom Mining Plan

Status: Planned  
Date: 2026-06-07  
Scope: complete custom mining model for custom blocks and tools

## 1. Objective

MVP-2 introduces Strategy B: complete custom mining.

The goal is to make custom blocks and custom tools use engine-controlled mining behavior instead of relying on vanilla break timing.

This plan does not implement code. It defines the future implementation boundary, architecture, runtime flow, performance strategy, Folia/Paper constraints, YAML direction, risks, and acceptance criteria.

## 2. Current State

The project is post-MVP-1.

Already available:

- custom block identity persisted through binary PDC;
- custom item/tool identity through item metadata;
- custom drops for custom blocks;
- `area_break` as the first builtin mechanic;
- declarative `mechanics.on_block_break` YAML bindings;
- `MechanicExecutor` controlling execution and rescheduling;
- `SchedulerPort.runOnRegion(WorldPosition, Runnable)`;
- `RegionSafetyPort` for conservative runtime ownership checks;
- GitHub Actions for test, build, and integration validation.
- application-level in-memory mining session management for Phase 3, without Bukkit/Paper runtime integration.

Paper is the primary platform.

Folia remains an architectural objective, but `plugin.yml` must continue without `folia-supported: true`.

## 3. Conceptual Model

The future implementation should model the following concepts.

Custom block hardness:

- numeric value declared by block definition;
- represents how long the block should take to mine relative to tool speed;
- must be validated at startup;
- must be pure domain data.

Custom tool mining speed:

- numeric value declared by item/tool definition;
- represents how quickly the tool mines custom blocks;
- must be validated at startup;
- must be pure domain data.

Mining session:

- one active session per player for MVP-2;
- contains player key, target `WorldPosition`, custom block id or numeric id, tool identity, start time, expected duration, and last visual stage;
- is owned by application runtime state, not domain global state;
- is removed on completion or cancellation.

Mining progress:

- computed from absolute time;
- no incremental per-tick accumulation as the source of truth;
- supports bounded processing and deterministic recalculation.

Mining completion:

- validates that the block and tool still match the session;
- checks region safety;
- invokes the custom block break completion path once;
- clears visual progress;
- removes the session.

Visual progress:

- adapter-level output through a port;
- update only when the visible stage changes;
- clear on cancel, completion, quit, target change, or invalid session.

Cancellation:

- explicit event-driven cancellation for abort and quit;
- cancellation when held tool changes;
- cancellation when target block changes, becomes non-custom, becomes unsafe, or session is superseded.

Drops:

- emitted once through existing drop flow;
- vanilla drops must not duplicate custom drops.

Mechanics:

- `mechanics.on_block_break` fires only after successful custom mining completion;
- `AreaBreakMechanic` remains pure and unaware of mining;
- mechanic execution stays under `MechanicExecutor`.

## 4. Event Strategy

Use platform events only as adapters into application commands.

Initial entry points:

- `BlockDamageEvent`: start or refresh a mining session for a custom block.
- `BlockDamageAbortEvent`: cancel the player's active session if it targets the same position.
- `PlayerQuitEvent`: clear the player's active session and visual state.
- `PlayerItemHeldEvent`: cancel active session if the tool changes.

Equivalent validation during session processing may also cancel if the item identity no longer matches.

`BlockBreakAdapter` must remain thin. If custom mining needs a separate listener, prefer a segmented adapter such as `MiningAdapter`.

## 5. Performance Strategy

The implementation must not use global scans.

Forbidden:

- no world scan;
- no chunk scan;
- no block scan;
- no every-player scan;
- no every-custom-block tick check;
- no unbounded loop over sessions;
- no one-task-per-session design if it risks task explosion.

Required:

- process only active mining sessions;
- limit to one active session per player for MVP-2;
- use absolute time for progress;
- update visuals only when stage changes;
- use a bounded per-tick or per-execution budget;
- prefer one controlled driver or controlled regional scheduling;
- remove sessions aggressively on cancellation or invalidation.

The cost of the system must be proportional to active mining sessions, not world size.

## 6. Paper And Folia Strategy

Paper remains the main implementation target.

Folia remains an objective, not a final support declaration.

Rules:

- keep `SchedulerPort` as only `runOnRegion(WorldPosition, Runnable)`;
- use `SchedulerPort.runOnRegion` when block-region execution is required;
- use `RegionSafetyPort` before unsafe query or mutation;
- do not use `runAsync`;
- do not use `runOnEntity`;
- do not create `SchedulerAccess`;
- do not use reflection;
- do not use NMS;
- do not declare `folia-supported: true`.

MVP-2 should remain same-region-safe. Automatic cross-region mining, shared progress, or cross-region continuation needs later ADR approval.

## 7. Architecture Plan

Domain:

- pure mining value objects and policies;
- no Bukkit, Paper, Folia, YAML, PDC, scheduler, or adapter references;
- examples: `MiningHardness`, `MiningSpeed`, `MiningDurationPolicy`, `MiningProgress`, `MiningStage`, `MiningSession`.

Application:

- `MiningSessionService` or equivalent orchestration;
- starts, cancels, processes, and completes sessions;
- owns active session map;
- delegates completion to existing block/mechanic application services;
- enforces one active session per player.

Ports:

- clock/time port for absolute-time progress;
- visual progress port;
- scheduler port already exists;
- region safety port already exists;
- item metadata, block store, world mutation, and drop ports remain isolated.

Adapters:

- Bukkit/Paper listeners for damage, abort, quit, and item-held events;
- visual progress adapter using safe platform APIs;
- scheduler/driver adapter for controlled processing.

Mechanics:

- no dependency on mining;
- no scheduler access;
- no services or registries;
- `AreaBreakMechanic` remains unchanged in principle.

## 8. YAML Direction

Do not implement YAML in this planning step.

Candidate future format:

```yaml
blocks:
  ruby_ore:
    material_base: STONE
    mining:
      hardness: 6.0

items:
  ruby_pickaxe:
    material_base: DIAMOND_PICKAXE
    mining:
      speed: 8.0
```

Recommended schema decision:

- keep `schema: 1` if `mining` is optional and backward compatible;
- require schema bump only if existing fields change meaning, mining becomes mandatory, or migration semantics become incompatible.

Validation should eventually reject:

- negative hardness;
- zero hardness if not explicitly defined as instant;
- negative or zero speed;
- invalid mining section shape;
- tool/block values outside documented bounds.

## 9. Integration With Current Break Flow

Custom mining completion should reuse or refactor toward one custom break completion path.

The completion path must:

1. Check active session validity.
2. Check the custom block still exists.
3. Check the held tool still matches.
4. Check region safety.
5. Remove custom block identity once.
6. Mutate the world block once.
7. Emit custom drops once.
8. Trigger `mechanics.on_block_break` once.
9. Clear visual progress once.
10. Remove the mining session.

Avoid:

- duplicate drops;
- duplicate custom identity removal;
- recursive fake break events;
- making `AreaBreakMechanic` know mining;
- placing business logic in Bukkit listeners.

`area_break` should continue to work because it remains bound to `on_block_break` and is executed after a successful custom mining completion.

## 10. Out Of Scope For First MVP-2

- multiple players mining the same block;
- shared mining progress;
- enchantments;
- Fortune;
- Silk Touch;
- Efficiency;
- advanced tool tiers;
- permissions;
- WorldGuard;
- GriefPrevention;
- public API;
- scripting;
- NMS;
- reflection;
- complex cache;
- database persistence;
- declared full Folia support.

## 11. Risks

Lag from tick loop:

- mitigated by processing only active sessions and using budgets.

Session leaks:

- mitigated by abort, quit, held-item, target-change, completion, and invalidation cleanup.

Duplicate drops:

- mitigated by a single completion path.

Conflict with vanilla:

- mitigated by event boundary design and explicit drop suppression where needed.

Conflict with other plugins:

- mitigated by respecting cancellation and avoiding fake protection events.

Folia thread or region unsafety:

- mitigated by `SchedulerPort.runOnRegion` and `RegionSafetyPort`.

Visual inconsistency:

- mitigated by stage-change-only updates and explicit clear operations.

Break event recursion:

- mitigated by avoiding fake recursive `BlockBreakEvent` flow.

YAML complexity:

- mitigated by only `hardness` and `speed` in MVP-2.

## 12. Future Implementation Steps

1. Add pure mining domain model and unit tests.
2. Extend definitions with optional mining values.
3. Add loader and validator support for optional mining YAML.
4. Add application session service.
5. Add visual progress port and Paper adapter.
6. Add event adapters for damage, abort, quit, and held-item change.
7. Add controlled driver or regional scheduling path.
8. Integrate completion with existing custom break and mechanic trigger flow.
9. Add architecture fitness or rg coverage for new boundaries.
10. Validate through GitHub Actions.

## 13. Acceptance Criteria For Future Implementation

- No global world, player, block, or chunk loop.
- Session starts from `BlockDamageEvent`.
- Session cancels from `BlockDamageAbortEvent`.
- Session clears on `PlayerQuitEvent`.
- Session cancels when tool changes.
- One active session per player.
- Progress is absolute-time based.
- Visual update occurs only when stage changes.
- Completion drops once.
- Completion triggers `on_block_break` mechanics once.
- `area_break` continues working.
- Mutation passes through `RegionSafetyPort`.
- `SchedulerPort` still exposes only `runOnRegion`.
- `plugin.yml` still has no `folia-supported: true`.
- Mechanics remain pure.
- `application` remains free of Bukkit/Paper.
- GitHub Actions validates test, build, and integration tests.

## 14. Validation Plan

Do not rely on local heavy validation as the source of truth.

Future implementation should use:

- focused unit tests for domain progress calculations;
- unit tests for session start/cancel/complete;
- adapter tests for event translation where feasible;
- integration tests for Paper behavior;
- architecture fitness tests;
- GitHub Actions for `test`, `build`, and `integrationTest`.

Local lightweight checks may include:

```bash
git status --short
rg 'SchedulerAccess|runAsync|runOnEntity' src/main/java src/test/java
rg '^import org\.bukkit|^import io\.papermc|^import org\.spigot' src/main/java/com/customcontentengine/domain src/main/java/com/customcontentengine/internalapi src/main/java/com/customcontentengine/builtin
rg '^import com\.customcontentengine\.adapter' src/main/java/com/customcontentengine/application
rg 'net\.minecraft|reflection|Reflect|ServiceLoader' src/main/java src/test/java
```

## 15. Phase 1 Implementation Note

Phase 1 implements only the pure definition model and optional YAML fields:

- `blocks.<id>.mining.hardness`;
- `items.<id>.mining.speed`.

This phase does not implement runtime mining sessions, `BlockDamageEvent`, visual progress, custom break timing, scheduler drivers, or changes to the current block break flow.

`schema` remains `1` because the new `mining` sections are optional and backward compatible.

## 16. Phase 2 Implementation Note

Phase 2 implements only pure mining calculation and session state:

- expected duration from `MiningHardness` and `MiningSpeed`;
- absolute-time progress clamped between `0.0` and `1.0`;
- pure visual stage calculation;
- immutable session identity and timing state.

This phase does not implement active session storage, event adapters, visual adapters, scheduler drivers, custom break timing, world mutation, drops, or `on_block_break` runtime integration.

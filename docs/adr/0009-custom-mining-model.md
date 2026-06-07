# ADR 0009 - Custom Mining Model

Status: Accepted  
Date: 2026-06-07

## Context

CustomContent Engine is post-MVP-1.

MVP-1 delivered the first controlled builtin mechanic, `area_break`, integrated with `BlockBreakEvent`, with mechanics kept pure and runtime execution controlled by `MechanicExecutor`.

ADR 0008 formalized YAML mechanic bindings through explicit triggers such as `mechanics.on_block_break`.

The current block break flow still relies on Minecraft/Paper vanilla break timing. That is not enough for a complete custom content engine because custom blocks and custom tools need engine-defined mining behavior:

- custom block hardness;
- custom tool mining speed;
- controlled mining progress;
- controlled completion;
- visual feedback;
- safe cancellation;
- integration with custom drops;
- integration with mechanics bound to `on_block_break`.

The project has chosen Strategy B: implement a complete custom mining model instead of relying on vanilla mining behavior plus small patches.

This decision must remain conservative. Custom mining is allowed because it is directly tied to custom blocks and custom tools, but it must not become a generic action framework, permission framework, scripting system, or public API.

## Decision

CustomContent Engine will introduce a custom mining model as the MVP-2 direction.

The model is based on explicit mining sessions rather than global world scanning or per-block tick polling.

The conceptual model includes:

- `custom block hardness`;
- `custom tool mining speed`;
- `mining session`;
- `mining progress`;
- `mining completion`;
- `visual progress`;
- cancellation;
- drop integration;
- `on_block_break` mechanic integration after successful custom mining completion.

The initial MVP-2 implementation must use Paper/Bukkit events as entry and cancellation points:

- `BlockDamageEvent` starts or refreshes a mining session.
- `BlockDamageAbortEvent` cancels a session.
- `PlayerQuitEvent` clears a session.
- `PlayerItemHeldEvent`, or equivalent validation during session processing, cancels if the mining tool changes.

The system must process only active sessions.

The MVP-2 limit is one active mining session per player.

Progress must be computed from absolute time, not by adding small increments every tick. A session stores its start time, expected duration, target block position, player key, tool identity, and last visual stage. Each processing pass recomputes progress from the clock.

Visual progress must update only when the visible stage changes.

There must be no global loop over worlds, chunks, blocks, or all online players.

Completion must call the existing custom block break flow or a successor application service that preserves the current behavior:

- remove custom block identity;
- produce custom drops once;
- prevent duplicate vanilla drops;
- trigger `mechanics.on_block_break` once when configured;
- keep `AreaBreakMechanic` independent from mining.

## Architecture

Domain remains pure.

The domain layer may contain mining calculation concepts such as:

- mining hardness value;
- mining speed value;
- mining duration policy;
- mining progress calculation;
- mining visual stage calculation;
- immutable mining session state;
- pure cancellation/completion decisions.

The application layer owns session orchestration:

- start session;
- refresh or replace session;
- cancel session;
- process active sessions;
- complete session;
- dispatch existing block break and mechanic flows.

Adapters translate platform events into application commands:

- block damage start;
- block damage abort;
- player quit;
- held item change;
- optional periodic driver tick or controlled scheduled execution.

Ports isolate platform behavior:

- scheduler or driver execution;
- clock;
- visual mining progress;
- item identity lookup;
- block query/store/mutation;
- drops;
- region safety.

Mechanics remain independent:

- `AreaBreakMechanic` does not know mining exists.
- Mechanics do not access sessions.
- Mechanics do not receive scheduler access.
- Mechanics continue to execute through the existing capability model.

`BlockBreakAdapter` must remain thin. If custom mining needs a new adapter, it should be segmented, such as `BlockDamageAdapter` or `MiningAdapter`, and delegate to application services.

## Performance Decision

The custom mining model must be session-driven.

The implementation must not:

- scan worlds;
- scan chunks;
- scan blocks;
- scan every online player;
- verify every custom block every tick;
- create one scheduler task per mining session if that can cause task explosion.

The implementation should prefer:

- event-driven session creation and cancellation;
- one active session per player for MVP-2;
- processing only active sessions;
- a bounded per-tick or per-execution budget;
- absolute-time progress calculation;
- visual updates only when the visible stage changes;
- one controlled driver or region-controlled scheduling path.

This allows predictable cost proportional to active mining sessions, not world size or player count.

## Paper And Folia Decision

Paper remains the primary platform.

Folia remains an architectural objective, not a final support promise.

`plugin.yml` must not declare `folia-supported: true` as part of this ADR.

When execution needs to touch a block region, it must use:

- `SchedulerPort.runOnRegion(WorldPosition, Runnable)` when scheduling is required;
- `RegionSafetyPort` before query or mutation where unsafe ownership is possible;
- existing world mutation and block store ports.

The model must not introduce:

- `runAsync`;
- `runOnEntity`;
- `SchedulerAccess`;
- reflection;
- NMS;
- ThreadLocal as the architectural model.

Advanced Folia cross-region mining behavior is outside MVP-2 unless a later ADR defines it.

## YAML Direction

Custom mining will require YAML fields, but this ADR does not implement them.

The candidate shape is:

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

The preferred direction is to keep `schema: 1` if the future fields are optional and backward compatible:

- missing `blocks.*.mining` means current/default behavior selected by the implementation plan;
- missing `items.*.mining` means current/default mining speed selected by the implementation plan;
- no existing key changes meaning.

A schema bump is required if the future implementation changes the meaning of existing fields, makes mining mandatory, changes persistence semantics incompatibly, or requires migration behavior that old files cannot satisfy unchanged.

## Runtime Integration

Custom mining completion must integrate with the current custom block break flow without duplicating drops or mechanics.

The implementation must define one completion path that:

1. Confirms the session is still valid.
2. Confirms the target block is still a custom block.
3. Confirms the tool/session is still valid.
4. Checks region safety before mutation.
5. Removes custom block identity once.
6. Mutates the platform block once.
7. Emits custom drops once.
8. Triggers `mechanics.on_block_break` once for the tool item if configured.
9. Clears visual progress.
10. Removes the session.

The implementation must avoid fake recursive `BlockBreakEvent` behavior and must not rely on simulating protection plugins.

If a real Bukkit `BlockBreakEvent` is involved for compatibility with vanilla or other plugins, the event boundary must be explicitly designed so it does not duplicate drops, duplicate custom block identity removal, or recursively invoke custom mining.

## Consequences

Benefits:

- Custom blocks can have real custom hardness.
- Custom tools can have real custom mining speed.
- Mining becomes deterministic and engine-controlled.
- Progress can be visualized without global scans.
- Mechanics remain triggered by explicit completion instead of raw platform timing.
- Paper behavior remains primary while Folia safety remains visible.

Costs:

- More runtime state through active sessions.
- More cancellation paths.
- More integration tests.
- Need careful visual cleanup.
- Need careful duplicate drop and duplicate mechanic prevention.
- Need explicit scheduling/driver design.

## Alternatives Considered

### Strategy A: Keep Vanilla Mining

Rejected for MVP-2.

Vanilla mining timing cannot express complete custom hardness and custom tool speed for engine-managed blocks without fragile patches.

### Strategy B: Complete Custom Mining

Accepted.

This provides a coherent model for hardness, speed, progress, cancellation, completion, visual feedback, drops, and mechanic integration.

### Global Tick Scan

Rejected.

Scanning worlds, players, blocks, or chunks every tick would violate performance guardrails.

### One Scheduler Per Session

Not selected as the default.

It may be acceptable in a small proof, but MVP-2 should prefer a controlled driver or regional scheduling design to avoid task explosion.

### NMS Or Reflection For Mining Progress

Rejected.

The project forbids NMS and reflection.

## Out Of Scope For MVP-2

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

## Required Future Tests

Future implementation must include tests for:

- session starts from block damage;
- session cancels from abort;
- session clears on quit;
- session cancels or invalidates when tool changes;
- one active session per player;
- progress is computed from absolute time;
- visual updates only when stage changes;
- completion removes identity once;
- completion drops once;
- completion triggers `on_block_break` mechanics once;
- missing mining YAML remains valid if fields are optional;
- unsafe region does not mutate;
- `SchedulerPort` still exposes only `runOnRegion`;
- mechanics remain pure;
- architecture fitness functions pass;
- GitHub Actions runs test, build, and integration tests.

## ADR Need For Future Changes

No additional ADR is needed to implement this accepted model if the implementation stays within this decision.

A new ADR is required if implementation needs to:

- change `SchedulerPort`;
- add `runAsync` or `runOnEntity`;
- introduce `SchedulerAccess`;
- change the mechanic contract;
- change `MechanicResult`;
- introduce public API;
- declare `folia-supported: true`;
- add external protection plugin integration;
- add enchantment semantics;
- introduce shared multi-player mining sessions.

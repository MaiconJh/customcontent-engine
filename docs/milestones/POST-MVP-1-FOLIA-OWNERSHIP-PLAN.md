# Post-MVP-1 Folia Ownership Validation Plan

Status: Planned  
Date: 2026-06-07  
Scope: conservative runtime ownership validation before world mutation

## 1. Current State

Paper is the primary platform for CustomContent Engine.

Folia remains an architectural objective, but final support must be validated through focused ownership, scheduling, and runtime behavior checks before it is declared.

The current `SchedulerPort` exposes only:

```java
void runOnRegion(WorldPosition position, Runnable task);
```

This remains sufficient for the current same-region-safe model described by Spike 2.

`area_break` already uses `MechanicResult.Partial` and controlled rescheduling through `MechanicExecutor`. The mechanic itself stays pure and does not schedule work directly.

Advanced automatic cross-region behavior is still outside the current implementation scope. The current accepted direction is same-region-safe first, with `Partial.remaining` representing work that cannot be completed in the current safe execution context.

## 2. Objective

This phase validates and hardens runtime ownership before world mutations.

The goal is to ensure that `WorldMutationPort` implementations and scheduler adapters do not allow unsafe world access when Folia ownership is relevant.

The phase must preserve:

- pure mechanics;
- `application` without Bukkit, Paper, Folia, or adapter dependencies;
- Bukkit/Paper/Folia knowledge inside adapters;
- `MechanicExecutor` ownership of rescheduling decisions;
- `SchedulerPort` limited to `runOnRegion`.

This phase is not a gameplay expansion.

## 3. Points To Evaluate

The next implementation step should inspect and test these runtime points:

- `PaperSchedulerAdapter`, to confirm its current behavior is acceptable for Paper and does not pretend to validate Folia ownership.
- A possible future `FoliaSchedulerAdapter`, to validate execution on the region owning the scheduled `WorldPosition`.
- `BukkitWorldMutationAdapter`, to ensure world mutation is guarded or performed only when the runtime considers the target position safe.
- `StoredBlockMutation`, to ensure mutation behavior does not hide unsafe platform access.
- `AreaBreakRuntimeService`, to ensure capability implementations can refuse positions outside the safe execution context.
- `MechanicExecutor`, to confirm `Partial` remains the flow-control result for incomplete safe-region work.
- `BlockQuery` and `BlockMutation` behavior for positions outside the safe region.

## 4. Desired Behavior

World mutation must happen only for positions that are safe and owned by the current runtime context.

If a position is not safe, the runtime should return a pure failure, absence, or remaining-work signal without touching the platform world state.

For the current contract, the conservative behavior is:

- avoid querying or mutating unsafe positions;
- treat unprocessed unsafe positions as remaining work where the mechanic can represent them through `MechanicResult.Partial`;
- keep `MechanicResult.Partial.remaining` as pure `WorldPosition` values;
- let `MechanicExecutor` decide whether and how remaining work is rescheduled;
- keep mechanics unaware of Folia ownership;
- prevent mechanics from accessing `SchedulerPort` directly.

The following remain prohibited:

- `SchedulerAccess`;
- `runAsync`;
- `runOnEntity`;
- scheduler access from mechanics;
- Bukkit/Paper/Folia types in `domain`, `internalapi`, or `builtin`.

## 5. Out Of Scope

This phase must not implement:

- automatic advanced cross-region execution;
- parallel execution;
- `runAsync`;
- `runOnEntity`;
- `SchedulerAccess`;
- `ThreadLocal` as the architectural model for ownership or budgeting;
- stable public API;
- `ServiceLoader`;
- WorldGuard integration;
- GriefPrevention integration;
- a second mechanic;
- `vein_miner`;
- `block_transform`;
- `auto_smelt`;
- gameplay changes.

## 6. Possible Future Implementation

The implementation phase may consider these options, but this plan does not implement them:

- `FoliaSchedulerAdapter`, if a real Folia runtime test path is available.
- An adapter-level ownership check before world mutation.
- A pure `OwnershipGuardPort`, if ownership validation needs to be visible as an application-level abstraction without leaking platform APIs.
- A method on `WorldMutationPort`, if mutation safety cannot be expressed cleanly through the current adapter implementation.
- Fake ownership tests for application/runtime behavior.
- Platform integration tests that simulate or validate unsafe positions when feasible.
- Documentation of the exact criteria for when `folia-supported: true` can be declared.

Any option must preserve the dependency direction and keep platform-specific ownership knowledge out of mechanics and domain.

## 7. ADR Need

No ADR is required if the future implementation only adds a Folia adapter or adapter-level validation while preserving the existing contracts:

- `SchedulerPort.runOnRegion(WorldPosition, Runnable)`;
- current mechanic capability model;
- current `MechanicResult.Partial` semantics;
- current YAML mechanic bindings;
- current persistence model.

An ADR is required if the implementation changes any of these:

- `SchedulerPort`;
- `BlockQuery`;
- `BlockMutation`;
- `WorldMutationPort` contract;
- `MechanicResult.Partial`;
- automatic cross-region execution semantics;
- ownership as a new stable core concept or capability;
- `plugin.yml` platform support declaration.

## 8. Recommended Next Step

The recommended next step is conservative implementation planning:

1. Identify the current world access points in scheduler, query, and mutation adapters.
2. Add tests that prove unsafe positions are not mutated.
3. Keep `AreaBreakMechanic` unchanged.
4. Keep `BlockBreakAdapter` thin.
5. Keep `SchedulerPort` unchanged unless a real contract gap is proven.
6. Create an ADR only if implementation requires a new contract or changed semantics.

`plugin.yml` must continue without `folia-supported: true` until ownership validation is implemented and verified against real Folia behavior.

Automatic cross-region execution remains deferred until a separate ADR and implementation plan define routing, idempotency, cooldown, budget, retry, and failure semantics.

## 9. Implementation Note

The first conservative implementation may use a pure `RegionSafetyPort` with a Paper adapter that returns true for every position. This keeps Paper behavior unchanged while giving runtime code a platform-independent ownership boundary.

Runtime capability composition may use that guard to avoid unsafe query, mutation, and drop work. Positions rejected by the guard should be surfaced as `MechanicResult.Partial.remaining` when the current mechanic contract can represent them.

This does not implement real Folia ownership checks, automatic cross-region routing, new scheduler methods, or a `plugin.yml` support declaration. A real Folia adapter still requires validation against the available platform API and real server behavior.

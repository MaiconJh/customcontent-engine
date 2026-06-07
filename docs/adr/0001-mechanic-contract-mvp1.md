# ADR 0001 - Mechanic Contract for MVP-1

Status: Accepted  
Date: 2026-06-06

## Context

MVP-0 is complete, as recorded in `docs/milestones/MVP-0-COMPLETE.md`.

Spike 1, recorded in `docs/spikes/001-binary-pdc-performance.md`, measured the current binary PDC format used by `PdcBlockCodec` and recommended keeping that format for MVP-1 planning.

Spike 3, recorded in `docs/spikes/003-mechanic-contract-sufficiency.md`, modeled the planned mechanics `area_break`, `vein_miner`, and `block_transform`. The spike concluded that the planned mechanic contract is conceptually sufficient to express those mechanics without exposing Bukkit/Paper, persistence, schedulers, registries, or internal services to mechanic implementations.

The current production contract remains an MVP-0 placeholder and is insufficient for MVP-1:

- `Capability` is empty.
- `MechanicResult` is still boolean-shaped.
- `Mechanic.execute()` is still no-arg.
- A real `MechanicContext` does not exist yet.

Before implementing `area_break` or any other MVP-1 mechanic, the internal mechanic contract must be formalized.

## Decision

MVP-1 will use the following internal mechanic contract. These contracts remain internal to the engine and do not create a stable public API.

### Mechanic

`Mechanic` exposes:

```java
MechanicDescriptor descriptor();

MechanicResult execute(MechanicContext context);
```

Mechanics receive all runtime access through `MechanicContext`. They must not receive Bukkit/Paper objects, scheduler access, persistence access, registries, or application services.

### MechanicDescriptor

`MechanicDescriptor` contains:

- `MechanicId id`.
- `Set<Capability> requiredCapabilities`.
- `boolean readOnly`.

The descriptor is used during boot validation to ensure the mechanic can only request capabilities that were explicitly declared.

### Capability

`Capability` contains the MVP-1 capability identifiers:

- `BLOCK_QUERY`
- `BLOCK_MUTATION`
- `BUDGET_VIEW`
- `COOLDOWN_VIEW`
- `DROP_SINK`
- `EXECUTION_ORIGIN`

### MechanicContext

`MechanicContext` exposes:

```java
<T> T require(Class<T> capabilityType);
```

`MechanicContext` must expose only capabilities validated during startup.

`MechanicContext` must not expose:

- `Plugin`
- `Server`
- `World`
- Bukkit
- Scheduler
- `SchedulerAccess`
- `DefinitionRegistry`
- `BlockService`
- `ItemService`

### MechanicResult

`MechanicResult` is structured as:

- `Done(int affectedBlocks)`
- `Partial(int affectedBlocks, List<WorldPosition> remaining)`
- `Rejected(String reason)`

`Partial` represents bounded work that was not completed in the current execution window. Remaining work is represented with pure positions only.

### MechanicExecutor

`MechanicExecutor` is responsible for the execution pipeline:

- validates cooldown;
- validates budget;
- creates `MechanicContext`;
- executes `Mechanic`;
- interprets `Partial`;
- controls rescheduling through `SchedulerPort`.

Mechanics must not schedule tasks directly.

## Consequences

Benefits:

- Mechanics stay isolated from the core services and infrastructure.
- `area_break` can be implemented without changing `BlockService` or `ItemService`.
- `SchedulerAccess` is kept out of mechanics.
- Folia compatibility remains safer because rescheduling is centralized in `MechanicExecutor`.
- Budget and cooldown rules stay centralized and harder to bypass.

Costs:

- Existing production mechanic contracts must be changed before MVP-1 mechanics are implemented.
- Existing tests around mechanic placeholders must be adapted.
- Concrete capability interfaces must be created.
- The contract may need refinement after Spike 2 - Folia cross-region behavior.

## Scope Impact

This decision affects MVP-1 mechanic contracts only.

This decision does not:

- alter MVP-0;
- alter persistence;
- alter the binary PDC format;
- alter YAML format;
- add gameplay;
- implement `area_break`;
- implement any mechanic;
- create a stable public API.

Mechanic contracts remain in `internalapi`.

## Alternatives Considered

### Keep boolean/no-arg Mechanic

Rejected. The current no-arg execution and boolean result are insufficient for bounded work, partial execution, capability validation, drops, cooldowns, and block mutation.

### Give SchedulerAccess To Mechanics

Rejected. Direct scheduler access in mechanics violates the guardrails and would weaken Folia safety. Rescheduling belongs to `MechanicExecutor` through `SchedulerPort`.

### Give Mechanics Access To Internal Services

Rejected. Direct access to `BlockService`, `ItemService`, or `DefinitionRegistry` would couple mechanics to the core and bypass capability validation.

### Use Bukkit Directly In Mechanics

Rejected. Bukkit/Paper access in mechanics would break the pure internal contract and make Folia-safe execution harder to reason about.

### Create An Internal EventBus

Rejected for MVP-1. It adds complexity before the first approved mechanic needs it.
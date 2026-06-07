# MVP-1 Mechanic Rescheduling Plan

Status: Planned  
Date: 2026-06-07  
Scope reference: MVP-1 controlled mechanic execution

## 1. Objective

Define the technical plan for controlled mechanic rescheduling in MVP-1.

Rescheduling belongs to `MechanicExecutor`, not to mechanics. Mechanics report incomplete work through `MechanicResult.Partial`; they never call `SchedulerPort`, never receive `SchedulerAccess`, and never decide platform scheduling.

This plan preserves ADR 0001, ADR 0002, and the Spike 2 recommendation that MVP-1 should remain same-region-safe.

## 2. Current State

The project currently has:

- `MechanicExecutor` executing mechanics through `MechanicRegistry` and `MechanicContextFactory`.
- `AreaBreakMechanic` returning `MechanicResult.Done`, `MechanicResult.Partial`, or `MechanicResult.Rejected`.
- `MechanicResult.Partial` carrying `List<WorldPosition> remaining`.
- `SchedulerPort` exposing only `runOnRegion(WorldPosition, Runnable)`.
- Spike 2 recommending same-region-safe behavior for MVP-1.
- `AreaBreakRuntimeService` creating runtime capabilities for the controlled `/debugareabreak` path.

There is no automatic rescheduling today. A `Partial` result is currently just a result.

## 3. Desired MVP-1 Behavior

When a mechanic returns `Done`, execution is complete. The executor returns or propagates the result and schedules nothing.

When a mechanic returns `Rejected`, execution is complete. The executor returns or propagates the rejection reason and schedules nothing.

When a mechanic returns `Partial`, `MechanicExecutor` decides whether to schedule a continuation.

Continuation rules:

- Use `SchedulerPort.runOnRegion(WorldPosition, Runnable)`.
- Use a pure `WorldPosition` from `Partial.remaining` as the region anchor.
- Mechanics never call `SchedulerPort`.
- Mechanics never receive `SchedulerAccess`.
- Mechanics never call Bukkit/Paper scheduling APIs.
- Advanced automatic cross-region behavior remains outside MVP-1.

The MVP-1 implementation should start with a conservative, bounded continuation model. It should be easy to test with a fake `SchedulerPort`.

## 4. Same-Region-Safe Policy

MVP-1 remains same-region-safe.

Recommended policy:

- The executor may reschedule using the first position in `Partial.remaining`.
- Each continuation runs on the region of the chosen anchor position.
- Capability factories create a new execution context for each continuation.
- `ExecutionOrigin` for the continuation uses the selected remaining position.
- `BlockQuery` and `BlockMutation` remain responsible for not processing positions that are unsafe or not processable in the current runtime.
- If a position remains unsafe or not processable, the executor must avoid retrying it forever.
- Cross-region automation that groups, routes, or guarantees completion across multiple Folia regions is deferred.

This policy gives MVP-1 a controlled path for continuing bounded work without claiming full Folia cross-region support.

## 5. Infinite Loop Protection

The future implementation must include explicit loop protection.

Recommended MVP-1 limits:

- Maximum reschedules per mechanic execution chain.
- Maximum continuation steps per chain.
- Progress detection between consecutive `Partial` results.

Progress means at least one of:

- `affectedBlocks` is greater than zero in a continuation;
- `remaining` becomes smaller than the previous `remaining`;
- the selected anchor position changes and the previous position is not retried immediately.

If there is no progress, the executor should stop rescheduling and return or surface the last `Partial` as incomplete. If the origin or context is invalid, it should return `Rejected`.

Recommended initial constants for future implementation:

- `maxReschedules`: small bounded value, such as 8.
- `maxNoProgressSteps`: 1 or 2.

These values can be tuned after tests. Infinite retry loops are explicitly out of scope.

## 6. Preserving Execution Context

`MechanicResult.Partial` currently contains only remaining `WorldPosition` values.

For MVP-1, continuation should use the next remaining position as `ExecutionOrigin` for area-based mechanics such as `area_break`.

Reasoning:

- It uses the existing ADR 0002 capability model.
- It avoids changing `Mechanic.execute(MechanicContext)`.
- It avoids adding scheduler or platform state to mechanics.
- It keeps continuation data pure.

Tradeoff:

- For some future mechanics, the original origin plus richer traversal state may be needed.
- If this becomes necessary, the project should write an ADR before changing `Partial` into a richer continuation model.

For `area_break`, using the next remaining position as the continuation origin is acceptable for a conservative first implementation, but it may expand a new 3x3 around that position. The executor and capability policy must therefore rely on progress limits and idempotent block mutation behavior to prevent duplicate work. A future refinement may introduce a more precise continuation envelope through ADR.

## 7. Impact On AreaBreakRuntimeService

`AreaBreakRuntimeService` can remain the application entry point for controlled initial execution.

Future implementation should let it:

- provide the initial execution request;
- provide or delegate a capability factory for each continuation;
- create `StaticExecutionOrigin` from either the initial origin or the continuation anchor;
- keep `StoredBlockQuery`, `StoredBlockMutation`, `WorkBudgetView`, `CooldownView`, and `DefinitionDropSink` construction outside adapters;
- avoid making the debug command aware of scheduling or capability composition.

`AreaBreakRuntimeService` should not become a general scheduler. It may coordinate with `MechanicExecutor`, but scheduling policy belongs in the executor or an executor-owned helper.

## 8. Impact On SchedulerPort

No SchedulerPort expansion is planned for MVP-1.

Keep:

```java
void runOnRegion(WorldPosition position, Runnable task);
```

Do not add:

- `runAsync`.
- `runOnEntity`.
- `SchedulerAccess`.
- Bukkit/Paper scheduler types to internal contracts.

Spike 2 concluded that `runOnRegion(WorldPosition, Runnable)` is sufficient for MVP-1 same-region-safe behavior. If future implementation requires cancellation handles, delayed execution, grouped region execution, or result propagation, that requires ADR before changing the contract.

## 9. Out Of Scope

The rescheduling implementation plan does not include:

- Advanced Folia cross-region automation.
- Async execution.
- `runAsync`.
- `runOnEntity`.
- `SchedulerAccess`.
- Parallel execution.
- Persistent job queue.
- Database-backed tasks.
- Infinite retries.
- YAML-defined mechanics.
- A second mechanic.
- Public API.
- ServiceLoader.
- Cache.
- WorldGuard or GriefPrevention integration.

## 10. Future Implementation Criteria

Before implementing controlled rescheduling, the future code change should satisfy:

- `MechanicExecutor` tested with fake `SchedulerPort`.
- Unknown mechanic still returns `Rejected`.
- Missing capability still returns `Rejected`.
- `Done` schedules nothing.
- `Rejected` schedules nothing.
- `Partial` can schedule continuation through `SchedulerPort.runOnRegion`.
- Continuation context supplies `ExecutionOrigin`.
- Mechanics remain without `SchedulerAccess`.
- No Bukkit/Paper imports enter `builtin`, `internalapi`, or `domain`.
- `application` does not import `adapter`.
- Infinite-loop protection is covered by tests.
- Same-region-safe behavior remains documented and enforced.
- Cross-region automatic completion is not promised.

## 11. ADR Need

No ADR is required for this plan because it:

- keeps ADR 0001 intact;
- keeps ADR 0002 intact;
- keeps `SchedulerPort.runOnRegion(WorldPosition, Runnable)` unchanged;
- does not change `MechanicResult.Partial`;
- does not change capability contracts;
- does not implement runtime behavior.

ADR is required before any future change that:

- changes `SchedulerPort`;
- adds `SchedulerAccess`;
- adds `runAsync` or `runOnEntity`;
- changes `MechanicResult.Partial`;
- changes `BlockQuery` or `BlockMutation` result contracts;
- introduces advanced automatic cross-region rescheduling semantics.

# Spike 2 - Folia Cross-Region Behavior

Status: Completed  
Date: 2026-06-07  
Scope reference: `docs/PROJECT_SCOPE.md` Spike 2 - Folia Cross-Region Behavior

## 1. Objective

Investigate and document the safe MVP-1 behavior for `area_break` when its flat 3x3 area crosses a chunk or Folia region boundary.

This spike answers:

- whether the current `SchedulerPort.runOnRegion(WorldPosition, Runnable)` contract is sufficient for MVP-1;
- how `area_break` should treat positions outside the currently safe region;
- whether `BlockQuery` and `BlockMutation` should ignore, reject, defer, or report positions outside the safe region;
- whether `MechanicExecutor` should own future rescheduling of `MechanicResult.Partial`;
- whether MVP-1 should first be same-region-safe before attempting automatic cross-region behavior.

## 2. Scope

In scope:

- Model Folia-sensitive execution for the existing `AreaBreakMechanic`.
- Define MVP-1 behavior for same-region and cross-region cases.
- Preserve the current mechanic contract from ADR 0001 and ADR 0002.
- Preserve the current persistence model and PDC binary format.
- Document future rescheduling responsibilities.

Out of scope:

- Real `BlockBreakEvent` integration.
- Advanced `FoliaSchedulerAdapter`.
- Automatic cross-region rescheduling.
- `runAsync`.
- `runOnEntity`.
- `SchedulerAccess`.
- `SchedulerPort` contract changes.
- Persistence or YAML changes.
- Additional mechanics.
- Cache, database, WorldGuard, GriefPrevention, ServiceLoader, or public API.

## 3. Current Runtime State

The current runtime has:

- MVP-0 block lifecycle implemented and tested.
- `AreaBreakMechanic` implemented as a pure, stateless builtin mechanic.
- `ExecutionOrigin` formalized by ADR 0002.
- Controlled runtime execution through `/debugareabreak`.
- Capability composition in `AreaBreakRuntimeService` under `application`.
- A thin `AreaBreakDebugCommandAdapter`.
- No real `BlockBreakEvent` trigger for `area_break`.
- No validated Folia cross-region behavior.

`AreaBreakMechanic` computes a flat 3x3 area from `ExecutionOrigin.origin()`, queries custom block state through `BlockQuery`, consumes budget through `BudgetView`, mutates through `BlockMutation`, emits drops through `DropSink`, and returns `Done`, `Partial`, or `Rejected`.

## 4. Existing Rules

Rules already established by scope, guardrails, and ADRs:

- `SchedulerPort` currently has only `runOnRegion(WorldPosition, Runnable)`.
- Mechanics do not receive `SchedulerAccess`.
- Mechanics do not call Bukkit, Paper, Folia, PDC, adapters, registries, or application services.
- Rescheduling belongs to `MechanicExecutor`, not to mechanics.
- `AreaBreakMechanic` may return `MechanicResult.Partial`.
- `Partial` carries remaining work as pure `WorldPosition` values.
- Folia compatibility is an architectural goal, but full behavior depends on this spike and future validation.
- Advanced Folia cross-region behavior is outside MVP unless explicitly validated.

## 5. Scenarios Analyzed

### Area 3x3 Fully Inside The Same Safe Region

The origin and all eight neighbors are owned by the currently executing region.

Recommended behavior:

- Process all eligible custom block positions.
- Respect cooldown and budget.
- Use `BlockQuery` before `BlockMutation`.
- Emit drops only for successfully mutated custom blocks.
- Return `Done(affectedBlocks)` if the budget is sufficient.
- Return `Partial` only if budget is exhausted.

This is the primary MVP-1 success path.

### Area 3x3 Crossing A Chunk Border

A chunk border is not automatically a Folia region boundary. On Paper, this is normally safe when execution is on the main server thread. On Folia, the safe rule is not chunk-based by itself; it is region-ownership based.

Recommended behavior:

- Do not special-case chunk borders in the pure mechanic.
- Treat every position according to the runtime capability policy.
- If the runtime can prove the neighboring position is safe/owned, process it.
- If the runtime cannot prove it is safe/owned, leave it unprocessed and report it as remaining work.

### Area 3x3 Crossing A Folia Region Boundary

Some positions are outside the currently owned region.

Recommended behavior:

- Process only positions that the runtime confirms are safe/owned.
- Do not query or mutate unsafe positions with Bukkit/Paper world access.
- Do not call `runAsync`.
- Do not call `runOnEntity`.
- Do not expose `SchedulerAccess`.
- Return `Partial(affectedBlocks, remaining)` for positions not processed because they are outside the safe region.
- Leave future rescheduling to `MechanicExecutor`.

### Safe Origin With Unsafe Neighbors

The origin is safe because the event or command is executing in the origin region, but one or more neighbors are not safe.

Recommended behavior:

- The origin and other safe positions may be processed.
- Unsafe neighbors must not be mutated.
- Unsafe neighbors should remain in `Partial.remaining`.
- If no eligible position can be processed because runtime safety rejects the whole area, return `Rejected` or `Partial(0, remaining)` according to the executor policy chosen during implementation.

For MVP-1, prefer `Partial` when execution made a valid attempt but region safety prevented some positions. Prefer `Rejected` when the origin itself is not safe or the runtime cannot establish a valid execution region.

### Partial With Remaining Outside The Current Region

`Partial.remaining` contains pure positions that may be scheduled later.

Recommended behavior:

- The mechanic does not schedule anything.
- The executor may later group remaining positions by region and call `SchedulerPort.runOnRegion`.
- MVP-1 may initially skip automatic rescheduling and report the limitation.
- No remaining position should be lost silently if it represents unprocessed custom block work.

## 6. Recommended MVP-1 Behavior

MVP-1 should be declared same-region-safe.

Recommended behavior:

- Process only positions that are safe/owned in the current execution region.
- Do not mutate positions outside the current safe region.
- Do not use `runAsync`.
- Do not use `runOnEntity`.
- Do not create `SchedulerAccess`.
- Return `Partial` with remaining positions for work that cannot be safely processed now.
- Keep future rescheduling under `MechanicExecutor`.
- Defer automatic cross-region execution until a focused implementation step proves the routing and ownership checks.

This keeps `area_break` useful for the first MVP-1 runtime path without promising unsafe or unvalidated Folia behavior.

## 7. Impact On AreaBreakMechanic

`AreaBreakMechanic` should remain unchanged in principle:

- It stays pure.
- It does not decide Folia ownership.
- It does not know Bukkit/Paper, schedulers, PDC, or adapters.
- It continues to work through `BlockQuery`, `BlockMutation`, `BudgetView`, `CooldownView`, `DropSink`, and `ExecutionOrigin`.

Region safety belongs outside the mechanic. The runtime capabilities decide which positions are processable.

However, current capability contracts do not explicitly distinguish "not a custom block" from "position is not safe to query now." That distinction matters for correct `Partial` behavior. This spike recommends modeling unsafe positions without leaking Bukkit/Paper into mechanics.

## 8. Impact On BlockQuery And BlockMutation

Current contracts:

```java
Optional<Short> BlockQuery.findCustomBlockNumericId(WorldPosition position);

void BlockMutation.breakBlock(WorldPosition position);
```

These are sufficient for the current Paper/debug path but not expressive enough to distinguish every Folia-sensitive outcome.

Recommended MVP-1 interpretation without changing contracts immediately:

- `BlockQuery.empty()` means "no custom block known or not processable by this capability in the current execution."
- `BlockMutation` must perform no side effect for positions it cannot safely mutate.
- The runtime should avoid calling `BlockMutation` for positions it knows are unsafe.

Recommended future refinement before automatic cross-region rescheduling:

- Introduce a pure query result that can represent at least:
  - custom block present;
  - no custom block;
  - unsafe/outside current region.
- Introduce a pure mutation result that can represent at least:
  - mutated;
  - not found;
  - unsafe/outside current region;
  - rejected.

Because that would change the capability contract, it should be proposed through ADR before implementation.

## 9. Impact On MechanicExecutor

`MechanicExecutor` remains the owner of future flow control:

- It creates `MechanicContext`.
- It executes the mechanic.
- It interprets `MechanicResult.Partial`.
- It decides whether remaining work is ignored, reported, retried, split by region, or rescheduled.
- Any future rescheduling must use `SchedulerPort`, not direct scheduler access in mechanics.

For MVP-1 same-region-safe behavior, automatic rescheduling may remain unimplemented. In that case, `Partial` should be surfaced as a controlled result and documented as incomplete work.

Before automatic cross-region rescheduling is added, the executor needs a clear policy for:

- grouping remaining positions by safe region;
- avoiding duplicate drops and duplicate mutations;
- preserving cooldown and budget semantics across continuation;
- preventing infinite retry loops when a position remains unsafe;
- deciding when a `Partial` becomes a `Rejected` or final incomplete result.

## 10. Impact On SchedulerPort

Current contract:

```java
void runOnRegion(WorldPosition position, Runnable task);
```

For MVP-1 same-region-safe behavior, this is sufficient.

Reasoning:

- The origin region can be used as the execution anchor.
- The current MVP-1 recommendation does not require automatic cross-region scheduling.
- The executor can run or delegate a same-region task using a single `WorldPosition`.
- Mechanics remain scheduler-free.

For future automatic cross-region behavior, this contract may still be enough if the executor schedules separate continuation tasks per target region anchor. If future Folia testing shows that continuations need cancellation handles, delayed execution, grouped region ownership checks, or result propagation, a SchedulerPort change should be proposed through ADR before code changes.

## 11. Risks

- Treating unsafe positions as `Optional.empty()` can silently look like "not a custom block."
- `BlockMutation.breakBlock(...)` currently has no pure failure result.
- Automatic rescheduling without strict idempotency can duplicate drops or mutate already-removed blocks.
- Cooldown and budget behavior across continuation tasks is not yet defined.
- Region ownership checks cannot be validated fully without real Folia execution tests.
- The current Paper scheduler adapter does not prove Folia ownership behavior.
- `Partial` currently carries positions but not a full resumable execution envelope.

## 12. Recommended Decision

Recommended decision: keep MVP-1 same-region-safe.

Details:

- Keep `SchedulerPort` as `runOnRegion(WorldPosition, Runnable)` for MVP-1.
- Do not add `runAsync`, `runOnEntity`, or `SchedulerAccess`.
- Do not implement automatic cross-region rescheduling yet.
- Process only positions proven safe/owned by runtime capability implementations.
- Represent unprocessed outside-region positions as `Partial.remaining`.
- Keep `AreaBreakMechanic` pure and unaware of Folia ownership.
- Revisit `BlockQuery`, `BlockMutation`, and `MechanicExecutor` result semantics before cross-region automation.

No ADR is required for this spike because it does not change production contracts, persistence, YAML, scheduler interfaces, or runtime behavior.

ADR will be required if the project chooses to:

- change `SchedulerPort`;
- add capability result types;
- change `BlockQuery` or `BlockMutation`;
- introduce automatic cross-region rescheduling semantics;
- change `MechanicResult.Partial` into a richer continuation model.

## 13. Next Steps

1. Keep the current debug runtime as controlled Paper-oriented execution.
2. Before `BlockBreakEvent` integration, define how the runtime capability layer detects current-region safety.
3. Add tests/modeling for unsafe positions as soon as a pure representation is chosen.
4. Plan `MechanicExecutor` handling of `Partial` as a separate controlled step.
5. If cross-region automation is desired, write an ADR before changing scheduler or capability contracts.
6. Do not declare full Folia support until real Folia ownership behavior is tested.

# MVP-1 Area Break Plan

Status: Planned  
Date: 2026-06-06

## 1. Objective

`area_break` is the first controlled builtin mechanic planned for MVP-1.

Its objective is to break a flat 3x3 area centered on the initial block while preserving the architecture established by MVP-0 and ADR 0001.

The mechanic must:

- operate on pure `WorldPosition` data;
- obtain its origin through a pure execution-origin capability;
- respect `WorkBudget` through `BudgetView`;
- respect cooldown through `CooldownView`;
- use only declared capabilities from `MechanicContext`;
- avoid direct Bukkit/Paper access;
- avoid direct scheduler access;
- avoid direct access to `BlockService`, `ItemService`, and `DefinitionRegistry`;
- return `MechanicResult.Done`, `MechanicResult.Partial`, or `MechanicResult.Rejected`.

## 2. MVP-1 Scope

Included:

- flat 3x3 area;
- origin based on the initially broken block, provided through a pure capability;
- custom block reads through `BlockQuery`;
- controlled mutations through `BlockMutation`;
- drops through `DropSink`;
- operation limiting through `BudgetView`;
- cooldown gate through `CooldownView`;
- `Done`, `Partial`, and `Rejected` results;
- future `Partial` rescheduling controlled by `MechanicExecutor`.

Excluded:

- vein mining;
- advanced configurable shapes;
- configurable radius;
- Fortune;
- Silk Touch;
- durability;
- required-tool validation;
- permissions;
- direct WorldGuard or GriefPrevention integration;
- advanced Folia cross-region behavior;
- async execution;
- `runAsync`;
- `runOnEntity`;
- `SchedulerAccess`.

## 3. Required Capabilities

`area_break` must declare these capabilities:

- `BLOCK_QUERY`
- `BLOCK_MUTATION`
- `BUDGET_VIEW`
- `COOLDOWN_VIEW`
- `DROP_SINK`
- `EXECUTION_ORIGIN`

The planned descriptor is:

```text
MechanicId: area_break
requiredCapabilities:
  - BLOCK_QUERY
  - BLOCK_MUTATION
  - BUDGET_VIEW
  - COOLDOWN_VIEW
  - DROP_SINK
  - EXECUTION_ORIGIN
readOnly: false
```

## 4. Expected Flow

1. A future trigger path asks `MechanicExecutor` to execute `MechanicId("area_break")`.
2. `MechanicExecutor` finds the registered mechanic in `MechanicRegistry`.
3. `MechanicExecutor` asks `MechanicContextFactory` to create a `MechanicContext` containing only the capabilities required by the descriptor.
4. `AreaBreakMechanic` calls `context.require(BlockQuery.class)`, `context.require(BlockMutation.class)`, `context.require(BudgetView.class)`, `context.require(CooldownView.class)`, `context.require(DropSink.class)`, and `context.require(ExecutionOrigin.class)`.
5. The mechanic reads `ExecutionOrigin.origin()` and computes up to 9 flat positions around that origin.
6. The mechanic checks cooldown through `CooldownView`.
7. For each position, the mechanic queries custom block state through `BlockQuery`.
8. Before each mutation, the mechanic checks/consumes budget through `BudgetView`.
9. The mechanic mutates eligible blocks through `BlockMutation`.
10. The mechanic sends drops through `DropSink`.
11. If all eligible positions are processed, the mechanic returns `Done(affectedBlocks)`.
12. If budget runs out before all remaining eligible positions are processed, the mechanic returns `Partial(affectedBlocks, remaining)`.
13. If cooldown or validation rejects execution, the mechanic returns `Rejected(reason)`.

## 5. Partial Model

`Partial` must carry remaining work as `List<WorldPosition>`.

The mechanic must not schedule anything directly. It only reports unfinished pure positions.

`MechanicExecutor` is responsible for eventual future rescheduling through `SchedulerPort`. MVP-1 may begin without automatic rescheduling if that limitation is explicitly documented in implementation notes and tests, but `Partial` must still be returned correctly when work cannot be completed in the current execution window.

The remaining positions must be immutable or defensively copied by the result type, matching the current `MechanicResult.Partial` contract.

## 5.1 Origin Model

`AreaBreakMechanic` must be stateless and reusable. It must not receive `WorldPosition` in its constructor.

The execution origin is provided by a pure capability:

```java
public interface ExecutionOrigin {
    WorldPosition origin();
}
```

The adapter or future trigger path is responsible for translating any platform event into this pure capability before the mechanic executes. The mechanic only reads the origin through `MechanicContext` and never receives Bukkit/Paper objects.

## 6. Integration With MVP-0

`area_break` must build on the MVP-0 block lifecycle without reaching into its internals.

- `BlockMutation` should be implemented on top of existing application services and ports, but the mechanic must not receive or call `BlockService` directly.
- `DropSink` should route drop delivery through the application/adapter boundary, but Bukkit item spawning must remain outside the mechanic.
- `BlockQuery` should expose only the custom block data needed by the mechanic as pure types.
- `ExecutionOrigin` should expose the original block position as a pure `WorldPosition`.
- Chunk PDC remains isolated in `PdcBlockStore`.
- `PdcBlockCodec` format remains unchanged.
- YAML format remains unchanged unless a future ADR explicitly approves a schema change.
- Vanilla drop suppression must remain owned by the adapter/listener boundary, not by the mechanic.
- Existing MVP-0 block break, PDC removal, orphan handling, and simple drops must keep working.

## 7. Future Files

Likely files for the implementation step:

- `src/main/java/com/customcontentengine/builtin/mechanic/AreaBreakMechanic.java`
- `src/test/java/com/customcontentengine/builtin/mechanic/AreaBreakMechanicTest.java`
- application capability implementations under `src/main/java/com/customcontentengine/application/mechanic`
- adapter-facing `DropSink` or mutation bridges where Bukkit conversion is required
- possible focused tests for `MechanicExecutor` handling of `Partial`
- possible focused adjustments in `WorkBudget` or `WorkBudgetManager`

No builtin mechanic should be registered in `CustomContentPlugin` until the capability wiring and trigger path are explicitly reviewed.

## 8. Risks

- Folia region-boundary behavior still depends on Spike 2.
- Spike 1 measured the in-memory codec but did not deeply measure full Paper event cost with PDC access.
- `Partial` without completed rescheduling may make the first implementation conservative.
- Drops may duplicate if the boundary between adapter-level vanilla drop suppression and `DropSink` is not explicit.
- Future tool validation, cooldown tuning, durability, and protection behavior may require additional focused work.
- Cross-region positions must not be mutated until the Folia policy is decided.

## 9. Decision

Recommendation: implementation of a controlled MVP-1 `AreaBreakMechanic` can begin after this plan, limited to Paper/same-region-safe behavior and fake/unit-tested capabilities first.

Spike 2 - Folia Cross-Region Behavior is not blocking for a first conservative Paper-oriented implementation that ignores or rejects positions outside the current safe region through capability policy. It is blocking for any advanced Folia cross-region behavior or automatic cross-region rescheduling.

No ADR is required for this plan because it follows ADR 0001, keeps the persistence format unchanged, does not alter YAML, and does not implement gameplay.

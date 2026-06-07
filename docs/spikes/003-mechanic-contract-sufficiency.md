# Spike 3 - Mechanic Contract Sufficiency

Status: Completed  
Date: 2026-06-06  
Scope reference: `docs/PROJECT_SCOPE.md` Spike 3 - Mechanic Contract Sufficiency

## 1. Objective

Validate whether the planned internal mechanic contract for MVP-1 is sufficient to model future mechanics without exposing the core, internal services, Bukkit/Paper, direct scheduler access, persistence infrastructure, or external integrations.

This is a modeling spike only. It does not implement gameplay, register mechanics, alter persistence, or change runtime behavior.

## 2. Scope

Hypothetical mechanics modeled:

- `area_break`.
- `vein_miner`.
- `block_transform`.

Contracts evaluated:

- `Mechanic`.
- `MechanicDescriptor`.
- `MechanicResult`.
- `Capability`.
- Planned `MechanicContext`.
- Planned `BlockQuery`.
- Planned `BlockMutation`.
- Planned `BudgetView`.
- Planned `CooldownView`.
- Planned `DropSink`.

Constraints applied:

- No direct access to `BlockService`.
- No direct access to `ItemService`.
- No direct access to `DefinitionRegistry`.
- No Bukkit/Paper references in mechanics.
- No direct scheduler access.
- No direct PDC access.
- No disk, database, or network I/O.
- No unbounded work outside `WorkBudget`.

## 3. Contracts Evaluated

### Current MVP-0 Code State

The current production code intentionally contains MVP-0 placeholders:

- `Mechanic` exposes `descriptor()` and a default no-arg `execute()`.
- `MechanicDescriptor` contains `String key` and `Set<Capability> capabilities`.
- `Capability` is currently empty.
- `MechanicResult` is currently a boolean wrapper.
- `MechanicContextFactory` currently returns an empty `Object`.
- `MechanicExecutor` intentionally does not execute real mechanics in MVP-0.

This current code state is not sufficient for MVP-1 mechanics.

### Planned MVP-1 Contract From Scope

The scope document describes a richer contract:

- `Mechanic execute(MechanicContext context)`.
- `MechanicDescriptor` with mechanic ID, required capabilities, and read-only/write marker.
- `MechanicResult`: `Done`, `Partial`, or `Rejected`.
- `MechanicContext.require(...)`.
- Capabilities:
  - Read: `BlockQuery`, `CooldownView`, `BudgetView`.
  - Write: `BlockMutation`, `DropSink`.
- Rescheduling controlled by `MechanicExecutor`, not mechanics.

This planned contract is directionally sufficient for the modeled mechanics, with the caveat that it must be formalized before implementation.

## 4. Hypothetical Mechanic: area_break

### Capabilities Needed

- `BlockQuery`.
- `BlockMutation`.
- `BudgetView`.
- `CooldownView`.
- `DropSink`.

### Theoretical Flow

1. Original event is handled by an adapter and converted into pure context.
2. Mechanic receives a pure `MechanicContext`.
3. Mechanic asks `CooldownView` whether the actor/mechanic pair may run.
4. Mechanic computes the bounded 3x3 target positions around the original block.
5. Mechanic queries each position with `BlockQuery`.
6. Mechanic consumes one operation per mutation through `BudgetView`.
7. Mechanic breaks allowed custom blocks through `BlockMutation`.
8. Mechanic sends configured drops through `DropSink`.
9. If budget is exhausted, mechanic returns `MechanicResult.Partial` with remaining pure positions.
10. `MechanicExecutor` decides whether and how to reschedule through `SchedulerPort`.

### Expected Result

The planned capability model can express `area_break` without exposing Bukkit/Paper, services, scheduler, registry, or PDC to the mechanic.

### Risks

- Requires clear policy for positions outside the current Folia/Paper-safe region.
- Requires `MechanicResult.Partial` to carry remaining pure positions and enough context to resume safely.
- Requires `BlockMutation` to enforce protection and mutation constraints outside the mechanic.
- Requires budget accounting to be mandatory and hard to bypass.

## 5. Hypothetical Mechanic: vein_miner

### Capabilities Needed

- `BlockQuery`.
- `BlockMutation`.
- `BudgetView`.
- `CooldownView`.
- `DropSink`.

### Theoretical Flow

1. Mechanic receives the starting custom block position in pure context.
2. Mechanic checks cooldown through `CooldownView`.
3. Mechanic performs a bounded graph traversal using `BlockQuery`.
4. Traversal stops when:
   - budget is exhausted;
   - max operation count is reached;
   - no connected matching block remains;
   - region policy rejects a position.
5. Each mutation consumes budget through `BudgetView`.
6. Mutations occur through `BlockMutation`.
7. Drops are emitted through `DropSink`.
8. Remaining frontier positions are returned in `MechanicResult.Partial`.
9. `MechanicExecutor` owns rescheduling and region routing.

### Expected Result

The planned contract can express `vein_miner` only if it has explicit bounded traversal state in `MechanicResult.Partial` and if `BudgetView` is mandatory for every queried or mutated block.

### Risks

- Highest risk of unbounded work among the modeled mechanics.
- Requires a hard maximum visited-position limit in addition to per-tick budget.
- Requires careful duplicate-position tracking in pure data, not global mutable state.
- Requires explicit region-border behavior from Spike 2 before implementation.

## 6. Hypothetical Mechanic: block_transform

### Capabilities Needed

- `BlockQuery`.
- `BlockMutation`.
- `BudgetView`.
- Optional `CooldownView`.
- Optional `DropSink`, only if the transform emits items.

### Theoretical Flow

1. Mechanic receives a pure source position and intended transform key from definition/context.
2. Mechanic checks current block identity through `BlockQuery`.
3. Mechanic consumes budget through `BudgetView`.
4. Mechanic requests a controlled mutation through `BlockMutation`.
5. If the transform changes drops or emits byproducts, mechanic uses `DropSink`.
6. Mechanic returns `Done` or `Rejected`.

### Expected Result

The planned capability model can express a simple custom-block-to-custom-block transform without exposing Bukkit/Paper, PDC, registry, or services to the mechanic.

### Risks

- Requires transform targets to be validated at startup from immutable definitions.
- Mechanic must not receive `DefinitionRegistry`; any needed target data must be prevalidated and represented as pure configuration.
- Requires `BlockMutation` to handle both world material mutation and custom block identity mutation atomically enough for MVP semantics.

## 7. Capability Matrix

| Capability | area_break | vein_miner | block_transform | Notes |
| --- | --- | --- | --- | --- |
| `BlockQuery` | Required | Required | Required | Pure lookup of custom block identity/state. |
| `BlockMutation` | Required | Required | Required | Only controlled path for block identity/world mutation. |
| `BudgetView` | Required | Required | Required | Mandatory for bounded work. |
| `CooldownView` | Required | Required | Optional | Required for player-triggered repeatable mechanics. |
| `DropSink` | Required | Required | Optional | Needed when block changes produce drops/items. |
| Scheduler access | Not allowed | Not allowed | Not allowed | Executor owns rescheduling. |
| Service access | Not allowed | Not allowed | Not allowed | Mechanics must not receive services. |
| Registry access | Not allowed | Not allowed | Not allowed | Definitions must be prevalidated outside mechanics. |
| Bukkit/Paper access | Not allowed | Not allowed | Not allowed | Adapters translate to pure types. |
| PDC access | Not allowed | Not allowed | Not allowed | Persistence remains behind ports/adapters. |

## 8. Gaps Found

The planned contract is sufficient in concept, but the current production contract is not yet sufficient for MVP-1.

Required gaps to close before implementing MVP-1 mechanics:

- Replace no-arg `Mechanic.execute()` with context-based execution.
- Define `MechanicContext` and `ctx.require(...)` behavior.
- Populate `Capability` with the required capability identifiers.
- Replace boolean `MechanicResult` with structured `Done`, `Partial`, and `Rejected` outcomes.
- Define how `Partial` carries remaining pure positions/frontier state.
- Add read-only/write metadata to `MechanicDescriptor`.
- Define and validate startup capability wiring in `MechanicRegistry`.
- Ensure `MechanicExecutor` owns cooldown, budget, dispatch, and rescheduling policy.
- Define pure capability interfaces: `BlockQuery`, `BlockMutation`, `BudgetView`, `CooldownView`, and `DropSink`.

## 9. Recommended Decision

Recommendation: adjust the mechanic contract via ADR before implementing MVP-1 mechanics.

Reasoning:

- The current MVP-0 placeholder contract cannot safely express the three modeled mechanics.
- The planned contract in `docs/PROJECT_SCOPE.md` is directionally sufficient.
- Formalizing the planned contract changes production interfaces and therefore requires explicit architecture decision tracking.

No gameplay should be implemented until the ADR is accepted.

## 10. Impact On MVP-1

MVP-1 should begin with a contract-definition step, not with direct implementation of `area_break`.

The first MVP-1 implementation sequence should be:

1. Write ADR for mechanic contract formalization.
2. Implement pure contracts and result types.
3. Implement capability validation at startup.
4. Implement `MechanicContextFactory`.
5. Implement `MechanicExecutor` pipeline.
6. Add focused tests using hypothetical mechanics/fakes.
7. Implement the first approved mechanic only after the contract is validated.

## 11. ADR Need

ADR required: yes, before production contract changes.

This spike itself does not create the ADR because it does not make the contract decision or alter production code. The next step should create a proposed ADR for the MVP-1 mechanic capability contract.

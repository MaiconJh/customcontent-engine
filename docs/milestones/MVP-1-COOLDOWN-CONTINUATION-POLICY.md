# MVP-1 Cooldown Continuation Policy

Status: Accepted for MVP-1 implementation  
Date: 2026-06-07

## Objective

Define how cooldown behaves when a mechanic returns `MechanicResult.Partial` and the engine schedules internal continuations.

Cooldown must protect the initial actor-triggered execution. It must not block continuation tasks that belong to the same execution chain.

## Decision

For MVP-1, cooldown is applied to the initial execution only.

Internal continuations scheduled from `MechanicResult.Partial` receive a continuation `CooldownView` that allows the chain to proceed.

This follows option B from the implementation note:

- the initial `AreaBreakRuntimeService` context uses the normal per-actor cooldown key;
- continuation contexts created for `Partial.remaining` use a `CooldownView` that returns `true`;
- mechanics still request `CooldownView` through `MechanicContext`;
- no mechanic receives `SchedulerPort`;
- no `SchedulerAccess` is introduced.

## Rationale

`AreaBreakMechanic` currently checks cooldown through `CooldownView`, as required by the MVP-1 contract and area-break plan.

After rescheduling was added, continuations reused the same cooldown key (`actorKey:area_break`). A continuation scheduled immediately after the first `Partial` could therefore be rejected by the cooldown created for the initial execution.

That is not the intended behavior. A continuation is not a new player action; it is bounded internal work from the same execution chain.

## Rules

- Initial execution respects cooldown normally.
- Repeated independent executions by the same actor and mechanic are still blocked by cooldown.
- Internal `Partial` continuations do not consume or check the initial cooldown gate again.
- Budget still applies per execution context.
- Anti-loop protection in `MechanicExecutor` still applies.
- Same-region-safe behavior from Spike 2 still applies.
- Cross-region automatic behavior remains outside MVP-1.

## Scope Impact

This policy:

- affects MVP-1 runtime composition only;
- does not alter MVP-0;
- does not alter `Mechanic`;
- does not alter `MechanicResult`;
- does not alter `Capability`;
- does not alter `SchedulerPort`;
- does not add `SchedulerAccess`;
- does not integrate `area_break` with `BlockBreakEvent`;
- does not add gameplay outside the controlled debug runtime.

## ADR Need

No ADR is required because this policy does not change a formal contract. It changes only how application runtime composition supplies an existing capability during internal continuations.

An ADR would be required before moving cooldown fully out of mechanics, changing `CooldownView`, changing `MechanicExecutor` contracts, or adding a new scheduling/cooldown capability.

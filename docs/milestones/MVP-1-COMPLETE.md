# MVP-1 Complete

Status: Complete  
Date: 2026-06-07  
Scope: first controlled builtin mechanic, `area_break`

## Summary

MVP-1 of CustomContent Engine is complete for the conservative controlled scope defined by the project documents, ADRs, spikes, and milestone plans.

This milestone introduces the first builtin mechanic, `area_break`, with controlled runtime integration while preserving the MVP-0 custom item and custom block lifecycle. The scope remains intentionally narrow: there is no formal mechanics YAML, no public mechanics API, no advanced Folia cross-region automation, and no additional mechanic beyond `area_break`.

## Completed Functionality

- Mechanic contract formalized by ADR 0001.
- `ExecutionOrigin` capability formalized by ADR 0002.
- `MechanicDescriptor` with `MechanicId`, required capabilities, and `readOnly`.
- `MechanicContext.require(...)` for capability access.
- Structured `MechanicResult` with `Done`, `Partial`, and `Rejected`.
- `MechanicRegistry`.
- `MechanicContextFactory`.
- `MechanicExecutor`.
- Controlled `Partial` rescheduling through `SchedulerPort.runOnRegion`.
- Anti-loop protection for continuation chains.
- Initial cooldown policy and continuation cooldown policy.
- Area work budget enforcement.
- Pure and stateless `AreaBreakMechanic`.
- Controlled debug runtime through `/debugareabreak`.
- Real `BlockBreakEvent` integration for `area_break`.
- MVP-0 origin block flow preserved.
- `area_break` processes only additional blocks around the origin.
- Duplicate drop prevention for the origin block.
- Fake-event recursion prevention for additional blocks.
- Same-region-safe behavior aligned with Spike 2.

## Important Decisions

- The origin block belongs to the existing MVP-0 break flow.
- `area_break` processes only additional positions around the origin.
- The origin is excluded from `area_break` through runtime capability wrappers.
- Mechanics do not receive or use `SchedulerAccess`.
- Mechanics do not import or depend on Bukkit, Paper, Folia, YAML, PDC, adapters, or application services.
- `SchedulerPort` remains limited to `runOnRegion(WorldPosition, Runnable)`.
- Advanced cross-region Folia behavior remains deferred.
- The temporary internal MVP-1 trigger policy is `ruby_pickaxe -> area_break`.
- The `ruby_pickaxe -> area_break` policy is not a public API and is not a formal YAML contract.
- Formal mechanics YAML was deliberately not implemented in MVP-1.

## Deliberate Limitations

- `ruby_pickaxe -> area_break` is an internal MVP-1 policy only.
- No formal item-to-mechanic association exists in `definitions.yml`.
- No second mechanic was implemented.
- No `vein_miner`.
- No `block_transform`.
- No `auto_smelt`.
- No Fortune or Silk Touch support.
- No real mechanic durability handling.
- No complete required-tool validation.
- No complex permission system.
- No WorldGuard or GriefPrevention integration.
- No advanced Folia cross-region automation.
- No `runAsync`.
- No `runOnEntity`.
- No `SchedulerAccess`.
- No ServiceLoader integration.
- No stable public API.
- No cache layer.
- No database persistence.

## Tests And Audits Executed

The MVP-1 closure is based on the previously executed and approved test and audit set:

- `./gradlew test --no-daemon` passed.
- `./gradlew build --no-daemon` passed.
- `./gradlew integrationTest --no-daemon` passed.
- Architecture and scope `rg` audits passed.
- Post real `area_break` integration audit was approved.

Audits covered:

- forbidden scheduler APIs such as `SchedulerAccess`, `runAsync`, and `runOnEntity`;
- Bukkit/Paper imports in `domain`, `internalapi`, and `builtin`;
- adapter imports in `application`;
- forbidden external integrations such as WorldGuard, GriefPrevention, NMS, reflection, and ServiceLoader;
- out-of-scope mechanics and features such as `vein_miner`, `block_transform`, `auto_smelt`, Fortune, Silk Touch, durability, and advanced tool systems.

## Architecture Preserved

- `domain` remains free of Bukkit, Paper, Folia, YAML, and PDC.
- `internalapi` remains free of Bukkit, Paper, Folia, YAML, and PDC.
- `builtin` remains free of Bukkit, Paper, Folia, YAML, PDC, adapters, and application services.
- `application` does not import `adapter`.
- adapters remain responsible for Bukkit/Paper translation and delegation.
- bootstrap remains the composition root.
- `MechanicExecutor` owns controlled rescheduling.
- `AreaBreakMechanic` remains pure, stateless, and capability-driven.
- MVP-0 item, placement, break, PDC, drops, and orphan handling remain preserved.

## Post-MVP-1 Possibilities

The following items are possible post-MVP-1 work only. They are not implemented by this milestone:

- Plan formal mechanics YAML.
- Write an ADR for item/tool to mechanic association in `definitions.yml`.
- Implement architecture fitness functions or ArchUnit checks according to ADR 0007.
- Refine `BlockQuery` to distinguish "no custom block" from "not processable in this execution context."
- Add a `ProtectionPort` integration point through the approved architecture process.
- Improve Folia adapter behavior and ownership validation.
- Evaluate a second mechanic only through the incubation pipeline.
- Remove, restrict, or make `/debugareabreak` configurable as a development tool.

## Final Declaration

MVP-1 is complete for the current controlled scope: one builtin mechanic, `area_break`, integrated with the real block break flow while preserving MVP-0 behavior and the established architecture boundaries.

Any expansion after MVP-1 must follow `docs/PROJECT_SCOPE.md`, `docs/ARCHITECTURE_GUARDRAILS.md`, the accepted ADRs, and the incubation or ADR process required for new contracts, YAML schema changes, extension points, scheduler changes, persistence changes, or additional mechanics.

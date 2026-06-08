# MVP-2 Complete

Status: Complete  
Date: 2026-06-08  
Scope: custom mining model for custom blocks and custom tools

## Summary

MVP-2 Custom Mining is complete.

This milestone implements engine-controlled custom mining for custom blocks and custom tools while preserving the conservative architecture defined by ADR 0009.

Completed functionality includes:

- `MiningHardness` and `MiningSpeed`;
- optional YAML fields `blocks.<id>.mining.hardness` and `items.<id>.mining.speed`;
- `MiningSession` with absolute-time progress;
- visual mining stages;
- `MiningSessionService`;
- `MiningVisualPort`;
- `BukkitMiningVisualAdapter`;
- `MiningInputAdapter`;
- `MiningRuntimeProcessor`;
- `CustomMiningCompletionService`;
- real completion that removes custom block identity, sets the block to `AIR`, emits configured drops, and triggers `mechanics.on_block_break`.

The implementation does not introduce a generic ability framework, scripting system, public API, or additional mechanic.

## Completed Phases

Phase 1: Domain and YAML mining definitions.

- Added pure mining definition values.
- Added optional `mining.hardness` for blocks.
- Added optional `mining.speed` for items/tools.
- Kept `schema: 1`.

Phase 2: Session, progress, and stage model.

- Added pure session state.
- Added absolute-time progress calculation.
- Added pure visual stage calculation.

Phase 3: Session service.

- Added application-level session orchestration.
- Preserved one active session per `actorKey`.
- Added in-memory session repository.

Phase 4: Runtime ports.

- Added runtime update modeling.
- Added visual and completion ports.
- Kept platform behavior behind ports.

Phase 5: Input adapters.

- Added Bukkit/Paper input handling for block damage, damage abort, player quit, and held-item change.
- Kept listeners thin.
- Did not add real completion, drops, or mechanic execution in that phase.

Phase 6: Visual processing.

- Added controlled active-session processing.
- Added Bukkit visual progress adapter.
- Added bounded processing of active sessions.
- Kept visual updates limited to stage changes.

Phase 7: Real completion.

- Added real custom mining completion.
- Removed custom block identity once.
- Mutated the block to `AIR` once.
- Emitted custom drops once.
- Triggered `mechanics.on_block_break` once.
- Avoided fake `BlockBreakEvent` and `Bukkit#callEvent`.

Phase 8: Hardening and edge cases.

- Added end-to-end hardening tests.
- Covered duplicate completion, cancellation, removed blocks, unsafe regions, missing mining config, visual cleanup, and previous break/mechanic compatibility.
- Confirmed `area_break` still works after custom mining completion.

## Guarantees

The completed MVP-2 flow guarantees:

- no global world scan;
- no chunk scan;
- no scan of all players;
- no scan of all blocks;
- processing only active mining sessions;
- one active session per `actorKey`;
- progress based on absolute time;
- visual progress updates only when the stage changes;
- completion does not duplicate drops;
- completion does not duplicate mechanic execution;
- completion does not duplicate custom identity removal;
- no fake `BlockBreakEvent`;
- no `Bukkit#callEvent` to simulate block breaking.

## Architecture Preserved

MVP-2 preserves the project architecture:

- `domain` remains pure;
- `application` remains free of Bukkit and Paper;
- Bukkit and Paper stay in `adapter` and `bootstrap`;
- `SchedulerPort` still exposes only `runOnRegion(WorldPosition, Runnable)`;
- `RegionSafetyPort` is respected before mutation;
- `AreaBreakMechanic` remains pure and capability-driven;
- `BlockBreakAdapter` remains thin;
- existing MVP-0 and MVP-1 break behavior remains compatible.

## Known Limitations And Out Of Scope

The following remain intentionally outside MVP-2:

- enchantments;
- Fortune;
- Silk Touch;
- Efficiency;
- advanced tool tiers;
- multiple players mining the same block;
- shared mining progress;
- permissions;
- WorldGuard integration;
- GriefPrevention integration;
- stable public API;
- scripting;
- full Folia support declaration;
- `plugin.yml` still does not declare `folia-supported: true`.

## Validation

GitHub Actions passed for the final hardening phase.

Final Phase 8 workflow run:

https://github.com/MaiconJh/customcontent-engine/actions/runs/27109571149

Final Phase 8 commit:

`4ad7f133feaa985da6806298b42e21d4ab425ea9`

The validated workflow includes:

- unit and architecture tests;
- build;
- integration tests;
- plugin artifact upload.

## Post-MVP-2 Options

Possible future work, not implemented by this milestone:

- custom durability;
- tool tiers and effective blocks;
- first mechanic after the custom mining foundation;
- YAML arguments for mechanics;
- real Folia validation;
- additional test flakiness hardening if needed.

Any future expansion must continue following `docs/PROJECT_SCOPE.md`, `docs/ARCHITECTURE_GUARDRAILS.md`, ADR 0008, ADR 0009, and the ADR process when contracts or scope boundaries change.

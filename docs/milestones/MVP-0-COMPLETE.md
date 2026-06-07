# MVP-0 Complete

Status: Complete  
Date: 2026-06-06  
Reference commit/PR: Not available in this local session

## Summary

MVP-0 of CustomContent Engine is complete.

This milestone closes the foundation phase without mechanics. The repository is cleared to plan MVP-1, provided that the required spikes and scope confirmation described in `docs/PROJECT_SCOPE.md` are reviewed before any MVP-1 implementation begins.

## Completed Functionality

- `definitions.yml` loading and validation.
- Immutable `DefinitionRegistry`.
- Functional `/givecustomitem` debug command.
- Custom item creation with `CustomItemId` stored in item PDC.
- Custom block placement from a custom item.
- Custom block identity persistence in binary chunk PDC.
- Custom block breaking.
- Custom block identity removal from PDC on break.
- Simple YAML-defined drops.
- Orphan custom block identity handling with rate-limited warning.
- Gradle Wrapper.
- Basic Paper integration test.

## Verified Architecture Guarantees

- `domain` does not import Bukkit or Paper.
- `internalapi` does not import Bukkit or Paper.
- `application` does not import `adapter`.
- `SchedulerPort` exposes only `runOnRegion`.
- No `runAsync`.
- No `runOnEntity`.
- No `SchedulerAccess`.
- No mechanics have been implemented.
- No external integrations have been added.
- No cache has been added.
- No database has been added.
- No NMS or reflection has been added.

## Tests And Audits Executed

- `./gradlew test --no-daemon`
- `./gradlew build --no-daemon`
- `./gradlew integrationTest --no-daemon`
- Architecture and scope audits with `rg`, including:
  - Bukkit/Paper imports in `domain` and `internalapi`.
  - Adapter imports in `application`.
  - Forbidden scheduler APIs.
  - Out-of-scope mechanics and integrations.
  - Fortune, Silk Touch, enchantment, anvil, smithing, grindstone, TileState, and ServiceLoader checks.

## Explicitly Still Outside MVP

- `area_break`.
- `vein_miner`.
- `block_transform`.
- `auto_smelt`.
- Fortune and Silk Touch.
- Anvil, smithing, grindstone, and repair handling.
- TileState persistence.
- Chunk LRU cache.
- Database persistence.
- WorldGuard and GriefPrevention integration.
- Pure Spigot support.
- ServiceLoader integration.
- Stable public API.
- Advanced Folia cross-region behavior.

## Next Phase

Before implementing MVP-1, the project should execute or explicitly plan the required spikes from `docs/PROJECT_SCOPE.md`:

- Binary PDC performance.
- Folia cross-region behavior.
- Mechanic contract sufficiency.

MVP-1 planning may begin after confirming that the intended work still matches the approved scope and guardrails.

## Decision Note

MVP-1 must not begin with direct implementation of mechanics. The required spikes in `docs/PROJECT_SCOPE.md` must be reviewed first, and any scope, persistence, Folia, scheduler, or mechanic-contract change must follow the ADR process in `docs/adr/0000-architecture-process.md`.

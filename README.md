# CustomContent Engine

CustomContent Engine is a Paper 1.21+ engine for custom blocks, tools, and items, built with a pure domain core, hexagonal architecture, binary PDC persistence, and controlled mechanic execution.

## Project Status

- MVP-0: Complete.
- MVP-1: Complete for the current controlled scope.
- Current phase: Post-MVP-1 planning.
- Folia: architectural goal validated by spikes; advanced cross-region behavior is not promised yet.
- Public API: not stable and not available yet.

## What Works Today

- Loading and validation of `definitions.yml`.
- Immutable `DefinitionRegistry`.
- Custom item creation with PDC identity.
- `/givecustomitem` command.
- Custom block placement.
- Binary chunk PDC persistence.
- Custom block breaking.
- PDC identity removal on break.
- Simple YAML-defined drops.
- Orphan custom block cleanup.
- Basic Paper integration test.
- Internal mechanic contract.
- Mechanic registry, context factory, and executor.
- Builtin `AreaBreakMechanic`.
- Controlled `/debugareabreak` command.
- Real `BlockBreakEvent` integration for `area_break`.
- `Partial` rescheduling through `SchedulerPort.runOnRegion`.
- Same-region-safe behavior for MVP-1.

## Architecture

The project follows a hexagonal architecture with strict dependency boundaries:

```text
customcontent/
|-- internalapi/   Internal contracts, not stable public API
|-- domain/        Pure business definitions, IDs, policies, and registries
|-- application/   Use cases, orchestration, services, mechanic runtime
|-- port/          Dependency inversion interfaces
|-- adapter/       Bukkit, Paper, PDC, YAML, and platform implementations
|-- builtin/       Official builtin mechanics, not stable core by default
|-- experimental/  Incubating modules and contracts
|-- devtools/      Debug, profiling, and test tools
`-- bootstrap/     Composition root
```

Layer summary:

- `internalapi`: internal mechanic and identity contracts used by the engine.
- `domain`: pure Java model for definitions, policies, identifiers, and immutable registries.
- `application`: orchestration layer for items, blocks, mechanics, budgets, cooldowns, and runtime composition.
- `port`: interfaces for infrastructure such as block storage, drops, item metadata, scheduling, world mutation, and protection.
- `adapter`: platform and infrastructure implementations for Bukkit/Paper, PDC, YAML, commands, and listeners.
- `builtin`: official mechanics such as `area_break`; official does not mean stable public API.
- `experimental`: reserved for future incubating modules or candidate contracts.
- `devtools`: internal development tools and debug commands.
- `bootstrap`: manual composition root for plugin startup.

## Main Architecture Rules

- `domain` has no Bukkit, Paper, Folia, YAML, PDC, NMS, or adapter dependency.
- `application` does not import `adapter`.
- Builtin mechanics do not import Bukkit, Paper, adapters, or application services.
- Adapters translate platform events and delegate inward.
- Bootstrap is the composition root.
- Mechanics receive only declared capabilities through `MechanicContext`.
- `MechanicContext` does not expose `SchedulerAccess`.
- No `runAsync` or `runOnEntity` in the MVP.
- No NMS or reflection.

## MVP-0

MVP-0 is complete and provides the foundation without mechanics:

- `definitions.yml` loading and validation.
- Immutable `DefinitionRegistry`.
- `/givecustomitem`.
- Custom item identity stored in item PDC.
- Custom block place and break.
- Binary custom block identity persistence in chunk PDC.
- PDC identity removal on break.
- Simple YAML-defined drops.
- Orphan custom block cleanup.
- Gradle Wrapper.
- Basic Paper integration test.

## MVP-1

MVP-1 is complete for the current controlled scope: one builtin mechanic, `area_break`.

Implemented MVP-1 pieces:

- `Mechanic` contract according to ADR 0001.
- `MechanicDescriptor` with `MechanicId`, required capabilities, and `readOnly`.
- Capability model with explicit runtime access.
- `ExecutionOrigin` according to ADR 0002.
- `MechanicContext.require(...)`.
- `MechanicResult.Done`, `MechanicResult.Partial`, and `MechanicResult.Rejected`.
- `MechanicRegistry`.
- `MechanicContextFactory`.
- `MechanicExecutor`.
- SchedulerPort-based rescheduling for `Partial`.
- Anti-loop protection for continuation chains.
- Cooldown continuation policy.
- Area work budget.
- Pure and stateless `AreaBreakMechanic`.
- Controlled `/debugareabreak` runtime path.
- Real `BlockBreakEvent` integration.
- Same-region-safe behavior according to Spike 2.

For the real event integration, the original broken block remains owned by the MVP-0 break flow. `area_break` processes only additional blocks around the origin, preventing duplicate origin drops and fake-event recursion.

## Current Limitations

- `ruby_pickaxe -> area_break` is an internal and provisional MVP-1 policy.
- No formal YAML mechanic binding exists yet.
- No second mechanic.
- No `vein_miner`.
- No `block_transform`.
- No `auto_smelt`.
- No Fortune or Silk Touch.
- No durability mechanic logic.
- No full tool correctness validation.
- No complex permissions.
- No WorldGuard or GriefPrevention integration.
- No advanced Folia cross-region automation.
- No `runAsync` or `runOnEntity`.
- No `SchedulerAccess`.
- No ServiceLoader.
- No public API.
- No database or cache layer.

## Persistence Model

Custom block identity is stored in the chunk `PersistentDataContainer` as a compact binary `BYTE_ARRAY`.

Current format:

```text
Byte 0: schema version
Bytes 1-2: entry count, big-endian
Each entry:
  - relative block position as short
  - numeric_id as short
```

Rules:

- `numeric_id` must be declared in YAML.
- `numeric_id` must be unique.
- `numeric_id` must not be reused for another block type while persisted worlds may still contain it.
- Orphan entries are removed when broken, with rate-limited warnings.
- The adapter reads the full byte array, modifies it in memory, and writes it back.
- No partial mutation inside NBT is assumed.
- No chunk LRU cache exists in the MVP.

## Build And Test Commands

Windows:

```powershell
.\gradlew.bat test --no-daemon
.\gradlew.bat build --no-daemon
.\gradlew.bat integrationTest --no-daemon
.\gradlew.bat binaryPdcSpike --no-daemon
```

Unix/macOS:

```bash
./gradlew test --no-daemon
./gradlew build --no-daemon
./gradlew integrationTest --no-daemon
./gradlew binaryPdcSpike --no-daemon
```

## Quick Architecture Audits With rg

```bash
rg 'SchedulerAccess|runAsync|runOnEntity' src/main/java src/test/java || true

rg '^import org\.bukkit|^import io\.papermc|^import org\.spigot' src/main/java/com/customcontentengine/domain src/main/java/com/customcontentengine/internalapi src/main/java/com/customcontentengine/builtin || true

rg '^import com\.customcontentengine\.adapter' src/main/java/com/customcontentengine/application || true

rg 'WorldGuard|GriefPrevention|net\.minecraft|reflection|Reflect|ServiceLoader' src/main/java src/test/java || true
```

## Important Documentation

- `docs/PROJECT_SCOPE.md`
- `docs/ARCHITECTURE_GUARDRAILS.md`
- `docs/adr/`
- `docs/spikes/`
- `docs/milestones/MVP-0-COMPLETE.md`
- `docs/milestones/MVP-1-COMPLETE.md`

## Next Phase

Recommended post-MVP-1 work:

- POST-MVP-1 roadmap.
- Architecture Fitness Functions / ArchUnit.
- Formal YAML mechanics planning.
- Folia ownership validation refinement.
- Devtools policy, including future treatment of `/debugareabreak`.
- Evaluate a second mechanic only through the incubation process.

Any expansion after MVP-1 must follow the project scope, architecture guardrails, accepted ADRs, and the incubation or ADR process required for new contracts, scheduler changes, YAML schema changes, persistence changes, extension points, or additional mechanics.

## License

License: not specified yet.

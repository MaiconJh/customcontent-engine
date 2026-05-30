# CustomContent Engine

Modular Paper 1.21+ engine for custom blocks, tools, and items, built with hexagonal architecture, a pure domain core, and PDC-based persistence.

## Status

CustomContent Engine is currently in conditional implementation scope.

The project scope is approved for initial implementation, but final architectural freezing depends on the execution and validation of the required technical spikes:

- PDC binary persistence performance
- Folia cross-region behavior
- Mechanic contract sufficiency

## Platform

- Primary target: Paper 1.21+
- Folia: planned compatibility, pending technical validation
- Spigot: not officially supported

## Purpose

CustomContent Engine exists to help Minecraft servers create and manage:

- Custom blocks
- Custom tools
- Custom items

The plugin focuses on performance, modularity, clean architecture, and safe extensibility.

It is not intended to become a generic ability framework, economy plugin, GUI system, protection plugin, or resource pack generator.

## Core Principles

- Pure domain model with no Bukkit dependencies
- Hexagonal architecture using ports and adapters
- Immutable definitions loaded at startup
- PDC-based binary persistence for custom block identity
- Clear separation between visual data and functional logic
- Controlled extensibility through explicit capabilities
- No blocking I/O on the game tick
- No NMS, reflection, or internal server APIs
- MVP-first development

## Architecture

The project follows a layered architecture:

```text
customcontent/
├── internalapi/
│   ├── mechanic/
│   └── identity/
├── domain/
│   ├── definition/
│   ├── policy/
│   └── registry/
├── application/
│   ├── block/
│   ├── item/
│   ├── mechanic/
│   └── budget/
├── port/
├── adapter/
│   ├── platform/
│   ├── persistence/
│   ├── yaml/
│   └── bukkit/
├── builtin/
│   └── mechanic/
└── bootstrap/

Dependency Rules

Allowed:

adapter      -> port
adapter      -> application
application  -> port
application  -> domain
builtin      -> internalapi
builtin      -> domain
bootstrap    -> all modules for composition
domain       -> no external dependencies

Forbidden:

domain       -> Bukkit/Paper/Folia
domain       -> adapter
domain       -> YAML/PDC
application  -> adapter
mechanic     -> Bukkit.getScheduler()
mechanic     -> DefinitionRegistry
mechanic     -> BlockService/ItemService
listener     -> complex business logic

MVP-0 Scope

The initial implementation is limited to the foundation:

Load definitions.yml at startup

Validate schema and stable numeric_id

Register one custom block

Register one custom item/tool

Provide a debug /givecustomitem command

Create custom items with PDC identity

Place custom blocks

Persist custom block identity in chunk PDC

Break custom blocks

Remove block identity from PDC when broken

Deliver simple YAML-defined drops

Respect cancelled Bukkit events


Out of Scope for MVP

The MVP must not include:

area_break

vein_miner

block_transform

auto_smelt

Fortune or Silk Touch support

Anvil, smithing, grindstone, or repair handling

TileState persistence

Chunk LRU cache

Database persistence

Public third-party API

ServiceLoader

WorldGuard or GriefPrevention integration

Spigot support

Advanced Folia cross-region behavior

runAsync

runOnEntity

Scheduler access inside mechanics


Persistence Model

Custom block identity is stored in the chunk PersistentDataContainer using a compact binary format.

Format:

Byte 0: schema version
Bytes 1-2: entry count, big-endian
Each entry:
  - relative block position as short
  - stable numeric_id as short

Rules:

numeric_id must be declared in YAML

numeric_id must be unique

numeric_id must never be reused for a different block type

Orphaned block entries are removed when broken, with rate-limited warnings

The adapter reads the full byte array, modifies it in memory, and writes it back

No partial mutation inside NBT is assumed


Folia Compatibility

The codebase is designed to be Folia-safe, but full Folia support is conditional.

Folia compatibility must be validated by technical spikes before being declared complete.

Rules:

No shared mutable global state

All world access goes through SchedulerPort

Folia adapters must validate region ownership

Cross-region behavior is not part of MVP


Required Technical Spikes

Spike 1 — Binary PDC Performance

Measure lookup, insert, remove, memory allocation, and event impact with:

256 custom blocks per chunk

512 custom blocks per chunk

1024 custom blocks per chunk


Spike 2 — Folia Cross-Region Behavior

Validate:

Same-region block operations

area_break near region borders

Access outside the current region

Rejection or rescheduling strategy


Spike 3 — Mechanic Contract Sufficiency

Model these hypothetical mechanics:

area_break

vein_miner

block_transform


Goal: verify that the capability-based mechanic contract is sufficient without exposing the core.

Development Rule

Before every implementation, check:

docs/PROJECT_SCOPE.md

docs/ARCHITECTURE_GUARDRAILS.md


If a proposed change is outside the current MVP or violates architectural boundaries, it requires an ADR before implementation.

Decision Rule

When there is conflict between extensibility, future compatibility, and MVP simplicity, MVP simplicity wins until all required spikes are complete.
# ADR-0005 — Capability Governance

Status: Proposed  
Date: 2026-06-07  
Project: CustomContent Engine

---

## Context

CustomContent Engine uses explicit capabilities to keep mechanics pure, testable, and safe. A mechanic receives only what it declares through `MechanicDescriptor` and obtains access through `MechanicContext`.

This model prevents mechanics from depending on Bukkit/Paper/Folia, services, registries, or platform internals.

However, without governance, `MechanicContext` can become a hidden service locator or god object as new capabilities are added for convenience.

---

## Decision

Capabilities are classified as:

1. Core capabilities.
2. Module capabilities.
3. Forbidden stable-core capabilities.

Capabilities may not be added to stable core merely because they are convenient.

---

## Core Capabilities

Core capabilities must be generic, stable, platform-independent, and directly related to custom blocks, tools, or items.

Current/candidate core capabilities:

- `BlockQuery`
- `BlockMutation`
- `BudgetView`
- `CooldownView`
- `DropSink`
- `ExecutionOrigin`

A core capability must satisfy:

1. Used by multiple mechanics or modules.
2. Independent from Bukkit/Paper/Folia.
3. Testable without a server.
4. Not tied to a single mechanic.
5. Directly connected to custom block/tool/item behavior.

---

## Module Capabilities

Specialized capabilities belong to official or experimental modules until they prove broad structural value.

Examples:

- `ParticleEmitter`
- `SoundEmitter`
- `TransformRule`
- `VeinGraphQuery`
- `ResourceExport`
- `DebugTraceSink`
- `VisualFeedbackSink`

Module capabilities may be useful, but they must not expand the stable core by default.

---

## Forbidden Stable-Core Capabilities

The following are forbidden from stable core unless the product scope is formally redefined:

- Economy capability.
- Quest capability.
- Combat capability.
- Teleport capability.
- GUI/menu capability.
- Generic scripting capability.
- Generic permission-management capability beyond interaction validation.

---

## Context Anti-God-Object Rule

`MechanicContext` must not become a service locator.

Forbidden patterns:

- `ctx.getPlugin()`
- `ctx.getServer()`
- `ctx.getWorld()`
- `ctx.getBlockService()`
- `ctx.getDefinitionRegistry()`
- `ctx.getScheduler()`
- `ctx.getAdapter()`

Allowed pattern:

```java
BlockMutation mutation = ctx.require(BlockMutation.class);
```

The mechanic receives behavior as a narrow capability, not broad engine access.

---

## Consequences

### Positive

- Mechanics remain pure and unit-testable.
- Platform access stays behind adapters.
- Core does not inflate with unrelated systems.
- Future capabilities can be incubated safely.

### Negative

- Some mechanics may require extra adapter/application work.
- Capability design requires discipline.
- Convenience is intentionally limited in the stable core.

---

## Guardrails

- Every new core capability requires ADR.
- Every module capability must be marked experimental or official.
- Mechanics cannot access services, registries, plugin instances, scheduler, or platform classes directly.
- Capability validation must occur at startup whenever possible.

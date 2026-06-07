# ADR-0003 — Conservative Evolvable Core

Status: Proposed  
Date: 2026-06-07  
Project: CustomContent Engine

---

## Context

CustomContent Engine is intended to be highly extensible while keeping the stable core small, testable, and independent from Bukkit/Paper/Folia details.

A pure anti-inflation policy protects the project from becoming a generic monolith, but an overly rigid core can create the opposite problem: the engine may become unable to evolve beyond its initial assumptions.

The project needs a rule that preserves both goals:

1. Prevent feature inflation in the core.
2. Avoid freezing the engine into a narrow design that blocks future extensibility.

---

## Decision

The stable core of CustomContent Engine is conservative but evolvable.

The stable core may contain only structural mechanisms required to define, identify, persist, validate, and execute custom block, tool, and item behavior.

New ideas must start outside the stable core as one of the following:

- Technical spike.
- Experimental module.
- Official module.
- Experimental contract.
- Devtool.

The core is not immutable by dogma. A feature, capability, extension point, or contract may enter the stable core only when it proves to be structural, recurring, platform-independent, broadly reused, and simpler than keeping it outside the core.

Default decision: reject from stable core and incubate outside it.

---

## Stable Core May Contain

- Identity types.
- Definition contracts.
- Immutable registries.
- Mechanic contracts.
- Capability model.
- Execution context model.
- Result model.
- Minimal lifecycle and validation rules.
- Pure domain policies.

---

## Stable Core Must Not Contain By Default

- Specific mechanics.
- GUI, HUD, menus.
- Economy, quests, combat, teleportation, generic abilities.
- Debug commands.
- Platform integration.
- Bukkit/Paper/Folia/NMS references.
- Resource pack generation.
- Plugin-specific integrations.
- Experimental extension points.

---

## Entry Criteria for Stable Core

A candidate may enter stable core only if all or nearly all are true:

1. It is required by multiple independent modules.
2. It is independent of Bukkit/Paper/Folia implementation details.
3. It is not tied to one specific mechanic.
4. It can be tested without a server.
5. It reduces total system complexity.
6. It does not make simple mechanics harder to write.
7. It has an ADR and at least one technical spike or proof.
8. It has architecture fitness tests.
9. It remains directly connected to custom blocks, tools, or items.
10. It cannot be solved cleanly as an official or experimental module.

---

## Consequences

### Positive

- The core remains small and stable.
- The engine can still evolve when real patterns emerge.
- Experimental ideas have a safe place to exist.
- Official modules do not automatically become core.
- Long-term extensibility remains possible without turning the project into a generic framework.

### Negative

- New features require more discipline and documentation.
- Some useful features may wait longer before becoming stable.
- The project must maintain module and stability classification.

---

## Guardrails

- Every proposed stable-core change requires ADR.
- Every new extension point starts experimental.
- Every module must declare whether it is internal, experimental, official, stable, or deprecated.
- Official does not mean stable core.
- Convenience APIs must not force new concepts into the stable core.

---

## Related Documents

- `docs/PROJECT_SCOPE.md`
- ADR-0004 — Extension Stability Levels
- ADR-0005 — Capability Governance
- ADR-0006 — Experimental Module Incubation
- ADR-0007 — Architecture Fitness Functions

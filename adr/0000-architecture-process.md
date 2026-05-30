# ADR 0000 — Architecture Decision Process

Status: Accepted  
Date: 2026-05-30  
Project: CustomContent Engine

---

## Context

CustomContent Engine has a strict scope focused on custom blocks, custom tools, and custom items for Paper 1.21+.

The project follows a conditional implementation scope defined in:

- `docs/PROJECT_SCOPE.md`
- `docs/ARCHITECTURE_GUARDRAILS.md`

The architecture is intentionally constrained to avoid uncontrolled expansion, excessive coupling, performance regressions, and premature generic framework design.

Because the plugin is expected to evolve, decisions that affect scope, architecture, persistence, Folia support, or extension contracts must be recorded explicitly.

---

## Decision

All significant architectural or scope-changing decisions must be documented as Architecture Decision Records, also known as ADRs.

ADRs must be stored in:

```text
docs/adr/
```

ADR filenames must use the format:

```text
NNNN-short-title.md
```

Example:

```text
0001-binary-pdc-codec.md
0002-folia-region-policy.md
0003-mechanic-capability-contract.md
```

---

## When an ADR Is Required

An ADR is required when a change:

- adds functionality outside the current MVP;
- changes the official project scope;
- changes the persistence format;
- changes the mechanic contract;
- changes the capability model;
- changes Folia behavior;
- adds support for another platform;
- adds external plugin integration;
- adds database persistence;
- adds caching;
- adds public API;
- changes dependency rules;
- introduces a new scheduler method;
- introduces a new architectural layer;
- adds a mechanic outside the approved MVP;
- weakens or bypasses any rule in `ARCHITECTURE_GUARDRAILS.md`.

---

## ADR Template

Each ADR should follow this structure:

```markdown
# ADR NNNN — Title

Status: Proposed | Accepted | Rejected | Superseded  
Date: YYYY-MM-DD

## Context

Describe the problem, constraint, or architectural pressure.

## Decision

Describe the decision being made.

## Consequences

Describe positive consequences, negative consequences, tradeoffs, and risks.

## Scope Impact

Explain whether this changes MVP scope, architecture boundaries, persistence, Folia behavior, or extension contracts.

## Alternatives Considered

List relevant alternatives and why they were not chosen.
```

---

## Decision Statuses

### Proposed

The decision is under discussion and must not be implemented yet unless explicitly allowed as a spike.

### Accepted

The decision is approved and may be implemented.

### Rejected

The decision was considered but rejected.

### Superseded

The decision was replaced by a newer ADR.

---

## Technical Spikes

The scope requires three mandatory spikes before formal freezing:

1. Binary PDC performance.
2. Folia cross-region behavior.
3. Mechanic contract sufficiency.

The results of these spikes must be recorded either as ADRs or as documents linked from ADRs.

---

## Consequences

This process ensures that the project remains aligned with its central focus:

- custom blocks;
- custom tools;
- custom items;
- high performance;
- clean architecture;
- safe extensibility.

It also prevents accidental scope creep, such as turning the plugin into:

- a generic ability framework;
- an economy plugin;
- a GUI system;
- a land protection plugin;
- a resource pack generator;
- a general-purpose platform library.

---

## Final Rule

If a proposed change conflicts with MVP simplicity, future flexibility, or architectural purity, MVP simplicity wins until the required technical spikes are complete.
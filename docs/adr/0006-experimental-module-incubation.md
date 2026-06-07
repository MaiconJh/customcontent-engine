# ADR-0006 — Experimental Module Incubation

Status: Proposed  
Date: 2026-06-07  
Project: CustomContent Engine

---

## Context

The project must support new ideas without turning the stable core into a generic feature container.

Examples of future ideas include `vein_miner`, `block_transform`, resource export, visual feedback, advanced triggers, profiling tools, and test kits. Some may become valuable; others may be discarded.

A safe incubation process is needed.

---

## Decision

New ideas follow an incubation pipeline before they may affect stable core.

Pipeline:

```text
Idea
-> Technical Spike
-> Experimental Module
-> Official Module
-> Candidate Contract
-> Stable Core
```

Promotion is not automatic. A feature can remain experimental or official forever without becoming stable core.

---

## Stage Definitions

### Idea

A concept without implementation promise.

Requirements:

- Written goal.
- Relation to custom blocks, tools, or items.
- Known risks.

### Technical Spike

A temporary investigation to validate feasibility.

Requirements:

- Clear question.
- Bounded scope.
- Measured or observable result.
- No production promise.

### Experimental Module

A working but unstable implementation.

Requirements:

- Marked experimental.
- Isolated from stable core.
- No compatibility promise.
- Tests for core assumptions.

### Official Module

A maintained project module.

Requirements:

- Approved by ADR.
- Documented usage.
- Does not become hidden core dependency.
- Has integration tests if platform behavior is involved.

### Candidate Contract

A possible future API or extension point.

Requirements:

- At least two validated use cases.
- Architecture fitness tests.
- No platform leakage.
- Review of usability impact.

### Stable Core

A final structural contract.

Requirements:

- Meets ADR-0003 criteria.
- Has versioning strategy.
- Has compatibility tests.

---

## Promotion Criteria

A module or contract may advance only if:

1. It solves a repeated problem.
2. It does not require Bukkit/Paper types in domain or mechanics.
3. It works with Folia constraints or defines a safe limitation.
4. It has unit tests.
5. It has integration tests when platform behavior is involved.
6. It does not increase complexity of the simple path.
7. It has an ADR before becoming official or stable.
8. It has a removal or migration strategy if it fails.

---

## Rejection / Demotion Criteria

A module should be rejected, demoted, or removed if:

1. It forces core changes without proven need.
2. It leaks platform APIs into mechanics.
3. It leaves the custom block/tool/item domain.
4. It cannot be made Folia-safe without unacceptable complexity.
5. It harms performance or usability beyond acceptable limits.
6. It duplicates another module without clear advantage.

---

## Consequences

### Positive

- Innovation can continue safely.
- The project avoids core inflation.
- Failed ideas do not contaminate stable architecture.
- Official modules can evolve independently from core.

### Negative

- Adds governance overhead.
- Requires documentation for experiments.
- Some features take longer to stabilize.

---

## Guardrails

- Experimental modules must not be imported by stable core.
- Official modules must not be treated as mandatory core.
- Devtools must remain disableable.
- A second mechanic beyond `area_break` requires explicit acceptance under this process.

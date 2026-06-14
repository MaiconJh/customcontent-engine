# ADR 0010 - Tool Tiers and Effective Blocks

Status: Accepted  
Accepted: 2026-06-14  
Date: 2026-06-14

---

## Context

CustomContent Engine implements a complete custom mining model under MVP-2, as recorded in `docs/milestones/MVP-2-COMPLETE.md` and governed by ADR-0009.

The current model supports block hardness and tool mining speed, but it contains no notion of minimum tool level or block required level. This means any registered custom tool can mine any custom block, regardless of the conceptual progression the server administrator may wish to express.

The project needs a tier system that:

- restricts which custom tools can mine which custom blocks;
- remains a property of mining, not a new mechanic;
- preserves the conservative core, pure domain, and Folia-safe architecture;
- does not introduce new core capabilities, public APIs, or scheduler contracts;
- remains backward compatible with existing YAML definitions.

Spike 4, recorded in `docs/spikes/004-tool-tiers-effective-blocks.md`, investigated the representation, validation placement, contract impact, and risks of introducing tiers. The spike concluded that tiers can be introduced as pure domain value objects within the existing mining lifecycle without violating architectural boundaries.

---

## Decision

CustomContent Engine will introduce mining tiers as an extension of the custom mining domain, not as a stable core promotion.

### Representation

Tiers are represented as pure domain value objects with integer levels:

- `ToolTier` carries the tier of a custom tool.
- `BlockTierRequirement` carries the minimum tier required to mine a custom block.

Integer levels are required. String enumerations are not used at the domain level.

These types live in the mining domain package and contain no Bukkit, Paper, Folia, PDC, or YAML dependencies.

### YAML Shape

New optional fields are added under existing `mining` sections:

- `items.<id>.mining.tier` — optional positive integer.
- `blocks.<id>.mining.required_tier` — optional positive integer.

If `tier` is absent on a tool, the tool does not participate in tier checks.
If `required_tier` is absent on a block, the block has no tier restriction.

The YAML `schema` remains `1`. No migration is required because the fields are purely additive and optional.

**YAML validation:** Non-positive or non-integer tier values will be rejected at startup with a clear error message. This ensures that only valid positive integers are accepted.

### Validation Placement

Tier validation occurs at mining session creation time, before duration calculation and before the session is persisted in the active session repository.

If the active tool's tier is below the target block's required tier, session creation is rejected. Because no session exists, no visual progress is shown, no scheduler work is consumed, and no completion can occur.

This placement preserves mining completion idempotency and avoids creating work that must later be cancelled.

### Mid-Session Invalidation

If the actor changes held item to a tool with a lower tier than the target block requires, the existing held-item change cancellation path invalidates the active session. No new scheduler, capability, or platform contract is introduced.

### Independence Guarantees

Tier logic is orthogonal to:

- `mining.speed` — tier does not modify duration calculation.
- durability — tier does not modify wear rate or maximum durability.
- `area_break` — the existing official mechanic remains unaware of tiers unless a later explicit decision couples them.

No new `MechanicContext` capability is introduced. Tier checks belong in the mining application and domain layers, not in mechanics.

### Default Behavior

- A tool without a declared tier cannot satisfy any block `required_tier`.
- A block without a declared `required_tier` accepts any tool, including tools without a tier.
- If a tool has a tier but the block does not declare `required_tier`, the block can be mined normally (no tier restriction applies). The presence of a tool tier does not create an additional constraint by itself.

### Contract Stability

The following contracts remain unchanged:

- `BlockQuery.findCustomBlockNumericId` — still returns `Optional<Short>`; tier validation is separate and occurs before session creation.
- `BlockMutation.breakBlock` — unchanged.
- `MechanicResult` — unchanged; tier rejection does not produce a `MechanicResult` because it occurs before mechanic execution.
- `MechanicContext` — unchanged; no new capability is added.

### Core Status

This feature is not promoted to the stable core. It remains an extension of the custom mining implementation. Promotion would require satisfying ADR-0003 entry criteria, including proven repeated structural need across multiple independent modules.

**Stability classification:** This feature begins as **experimental** under the incubation pipeline defined in ADR-0006. It may be promoted to **official** only after it proves repeated structural value across multiple definitions or modules. It is never promoted to **stable core** without satisfying ADR-0003 entry criteria.

---

## Consequences

### Positive

- Adds meaningful progression without inflating the stable core.
- Keeps tier types pure, small, and testable without a server.
- Requires no new core capability, scheduler method, or Folia contract change.
- Preserves existing mining, durability, speed, and mechanic behavior by default.
- Maintains `schema: 1` backward compatibility.
- Reuses existing cancellation infrastructure for held-item changes.
- `BlockQuery`, `MechanicResult`, and `MechanicContext` remain unchanged.

### Negative

- YAML validation surface grows slightly; administrators may misconfigure tiers.
- If future designs couple tiers with speed or durability, additional ADRs will be required.
- If multiple mechanics later require tier awareness, the current mining-only placement may need re-evaluation under ADR-0003 criteria.
- The integer-level model offers no named semantics (e.g., "iron", "diamond"); naming must be handled by documentation or external convention.

---

## Scope Impact

This decision affects custom mining definitions and session creation.

### In Scope

- Mining domain value objects (`ToolTier`, `BlockTierRequirement`).
- Mining definition records used by YAML loading, extending `ItemDef` and `BlockDef` with optional tier fields following the same pattern as existing `Optional<MiningHardness>` and `Optional<MiningSpeed>`.
- Mining session creation validation, including rejection of non-positive or non-integer tier values at startup.
- Held-item change cancellation re-validation for tiers.
- Optional YAML fields under existing `mining` sections.
- YAML validation rejecting non-positive or non-integer tier values.

### Explicitly Out of Scope

- New mechanic capabilities or changes to `BlockQuery`, `BlockMutation`, `MechanicResult`, or `MechanicContext` (all remain unchanged).
- Changes to `area_break` behavior or binding, including any future tier-aware mechanic behavior (requires a separate ADR).
- Changes to `mining.speed`, `MiningHardness`, or duration calculation.
- Changes to durability or wear logic.
- Scheduler contract changes (`runAsync`, `runOnEntity`, `SchedulerAccess`).
- Public API, `ServiceLoader`, or stable core promotion.
- NMS, reflection, or platform leakage.
- Enchantment, upgrade systems, GUI, or tool progression beyond tier gating.
- `folia-supported: true` declaration.
- Schema bump or migration logic.
- Tier-aware mechanics (e.g., `area_break` with tier restrictions) – such changes would require a separate ADR.

---

## Alternatives Considered

### String Enumeration For Tiers

Example: `tier: DIAMOND`, `required_tier: IRON`.

Rejected.

Named strings introduce normalization, comparison, and localization concerns. Integer levels avoid these issues, support natural ordering, and map cleanly to configuration and future persistence.

### Validation At Mining Completion

Validate tier only when the block is about to be removed.

Rejected.

Completion-time validation wastes scheduler and visual processing on sessions that should never exist. It also permits visual mining stages to display for tools that cannot legally mine the block, confusing the player and complicating cancellation semantics.

### Couple Tier With Mining Speed

Implicitly or explicitly make higher-tier tools faster.

Rejected for this ADR.

Eligibility (tier) and rate (speed) are distinct concepts. Coupling them reduces configuration clarity and mixes two independent axes. If speed scaling by tier is desired later, it should use an explicit separate field and require its own ADR.

### Promote Tier To A Core Capability

Create a new `MechanicContext` capability such as `ToolTierQuery`.

Rejected.

No current mechanic requires runtime tier awareness. Adding a capability before multiple modules need it violates ADR-0005 governance and ADR-0003 core evolution criteria. Tier checking belongs in the mining layer, not in the mechanic capability model.

### Implement Tiers As A Separate Experimental Module

Isolate tier logic in an experimental module outside the mining package.

Rejected for now.

Tiers are inherently a mining property. Isolating them before they are proven necessary across multiple modules adds indirection without architectural benefit. The mining domain is the natural home; promotion to an experimental module can be revisited if tier usage spreads beyond mining.

---

## Related Documents

- `docs/PROJECT_SCOPE.md` — product focus, scope boundaries, persistence and scheduler rules.
- `docs/ARCHITECTURE_GUARDRAILS.md` — dependency rules, core evolution policy, capability governance.
- ADR-0003 — Conservative Evolvable Core.
- ADR-0004 — Extension Stability Levels.
- ADR-0005 — Capability Governance.
- ADR-0006 — Experimental Module Incubation.
- ADR-0009 — Custom Mining Model.
- `docs/spikes/004-tool-tiers-effective-blocks.md` — feasibility analysis and recommended design bounds.
- `docs/milestones/MVP-2-COMPLETE.md` — current mining completion guarantees and limitations.

# Spike 4 - Tool Tiers And Effective Blocks

Status: Proposed  
Date: 2026-06-14  
Scope reference: `docs/PROJECT_SCOPE.md`, `docs/ARCHITECTURE_GUARDRAILS.md`, ADR-0003, ADR-0005, ADR-0006, ADR-0009, `docs/milestones/MVP-2-COMPLETE.md`

---

## 1. Objective

Investigate whether mining tiers can be introduced into the custom mining model without violating the conservative core, leaking platform dependencies into the domain or mechanics layers, inflating the stable core with feature-specific behavior, or breaking Folia safety and existing mining idempotency.

This spike asks:

- Can `ToolTier` and `BlockTierRequirement` be represented as pure domain value objects?
- Where should tier validation occur without disrupting existing `MiningSession` and `CustomMiningCompletionService` flows?
- Which existing contracts (`BlockQuery`, `MechanicResult`, `MechanicContext`) would need extension, and can those extensions remain backward compatible?
- Does the proposed feature require a new core capability, or can it be implemented within current mining application services and adapters?
- Is the YAML evolution path compatible with `schema: 1` backward compatibility rules?

This spike does not implement gameplay, modify production code, or change YAML schemas.

---

## 2. Scope

### In Scope

- Represent `ToolTier` as a pure domain value object.
- Represent `BlockTierRequirement` as a pure domain value object.
- Model tier validation within existing mining session start and completion paths.
- Evaluate impact on `MiningSession`, `MiningHardness`, `MiningSpeed`, `ItemDef`, `BlockDef`, `BlockQuery`, `BlockMutation`, `MechanicResult`, and `RegionSafetyPort`.
- Model hypothetical YAML extensions while preserving `schema: 1` backward compatibility.
- Assess whether `area_break`, `mining.speed`, and durability remain independent of tier logic.
- Assess Folia safety implications.
- Assess whether the feature fits within the current core evolution criteria (ADR-0003) or must remain external.

### Out of Scope

- Implementation of tier logic in production code.
- YAML schema version bump or loader migration code.
- Enchantment integration, GUI, or tool upgrade systems.
- Public API exposure.
- Expression languages, scripting, or `SchedulerAccess`.
- Real Folia server execution tests.
- `runAsync`, `runOnEntity`, or scheduler contract changes.
- `area_break` tier-aware behavior implementation.
- Durability multiplier logic based on tier.

---

## 3. Methodology

1. Review current mining domain records (`MiningSession`, `MiningHardness`, `MiningSpeed`) and definition records (`ItemDef`, `BlockDef`) to identify extension points.
2. Model hypothetical pure value objects for `ToolTier` and `BlockTierRequirement` and verify they contain no Bukkit/Paper/Folia imports.
3. Trace the current mining session lifecycle: `MiningSessionService.startSession` through `MiningRuntimeProcessor` to `CustomMiningCompletionService`.
4. Model three validation placement options: session start, session processing, and completion.
5. Evaluate `BlockQuery.findCustomBlockNumericId` expressiveness for tier-related outcomes.
6. Evaluate `MechanicResult` sufficiency for tier-rejected mining.
7. Model hypothetical YAML additions under `schema: 1` optional fields.
8. Cross-reference all findings against `docs/ARCHITECTURE_GUARDRAILS.md` dependency rules, ADR-0003 core evolution criteria, ADR-0005 capability governance, and ADR-0006 incubation pipeline.
9. Identify risks, required ADR decisions, and recommended path.

---

## 4. Analysis

### 4.1 Domain Representation

The current mining domain uses the following pure records:

| Record | Current Purpose | Tier-Relevant? |
| --- | --- | --- |
| `MiningHardness` | Block break time divisor | No |
| `MiningSpeed` | Tool speed multiplier | No |
| `MiningSession` | Active mining state | Indirectly (tool identity) |
| `ItemDef` | Tool definition | Yes (needs `toolTier`) |
| `BlockDef` | Block definition | Yes (needs `requiredTier`) |

#### Proposed Value Objects

```java
public record ToolTier(int level) {
    public ToolTier {
        if (level <= 0) throw new IllegalArgumentException("tier level must be positive");
    }
}

public record BlockTierRequirement(int minimumLevel) {
    public BlockTierRequirement {
        if (minimumLevel <= 0) throw new IllegalArgumentException("minimum tier level must be positive");
    }
}
```

Assessment:

- Both records are pure Java records with primitive validation.
- Neither imports Bukkit, Paper, Folia, YAML, or PDC.
- Both satisfy domain-layer rules (`domain -> no external dependencies`).
- Integer levels are preferred over string enums because:
  - They avoid locale or normalization concerns.
  - They compare naturally (`>=`, `<=`).
  - They serialize compactly if ever persisted.
- Default behavior: absent tier configuration means tier check is skipped (backward compatible).

#### Definition Integration

`ItemDef` currently contains:

```java
Optional<MiningSpeed> miningSpeed,
Optional<ToolDurabilityDefinition> durability
```

Proposed addition:

```java
Optional<ToolTier> toolTier
```

`BlockDef` already contains optional mining fields (e.g., `Optional<MiningHardness>`). Adding `Optional<BlockTierRequirement> requiredTier` follows the same optional-field pattern.

Rationale:

- Optional fields preserve `schema: 1` compatibility.
- Absent fields mean current behavior (no tier restriction).
- Validation at YAML load time ensures `minimumLevel` and `level` are positive integers.

### 4.2 Validation Logic

Three candidate placements for tier validation:

| Placement | Behavior | Tier Check Timing | Tradeoff |
| --- | --- | --- | --- |
| Session Start (`MiningSessionService.startSession`) | Reject immediately if tool tier < block requirement | Before session creation | Cleanest idempotency; no session created for invalid tier |
| Session Processing (`MiningRuntimeProcessor`) | Check on each runtime pass | During progress calculation | Allows tool swap mid-session detection but complicates cancellation |
| Completion (`CustomMiningCompletionService`) | Check only at break moment | At block removal | Minimal disruption but wastes processing time and may cause visual stage display for impossible breaks |

#### Recommended: Session Start

Validation at session start is recommended because:

- `MiningSessionService` already validates `MiningHardness` and `MiningSpeed` before computing `expectedDurationMillis` via `MiningDurationPolicy`.
- Adding tier validation at the same point keeps all mining preconditions in one application service.
- It preserves idempotency: no session is created, so no completion can occur for an invalid tier.
- It does not require changes to `MechanicResult` or `BlockQuery` for the basic mining path.
- It keeps the mechanic contract untouched: mechanics remain unaware of tiers.

#### Tool Swapping During Session

Risk: a player could start a session with a valid tool and swap to a lower-tier tool mid-session.

Current system already handles held-item changes via `MiningInputAdapter`, which triggers `MiningSessionService.cancelSession(actorKey, target)`. Tier invalidation due to tool swap can reuse the same mechanism:

- On held-item change or damage event, the adapter (or a new application method) re-validates tier against the current tool.
- If invalid, the session is cancelled.
- No new capability or core contract change is required.

This preserves the existing cancellation pattern without introducing `SchedulerAccess` or mechanic-layer awareness.

### 4.3 System Interactions

#### `area_break` Inheritance

`AreaBreakMechanic` operates through `BlockQuery`, `BlockMutation`, `BudgetView`, `CooldownView`, and `DropSink`. It does not know mining sessions exist (per ADR-0009).

If tier validation occurs at mining session start (in `MiningSessionService`), `area_break` is unaffected because:

- `area_break` is triggered by `BlockBreakEvent` / `mechanics.on_block_break`, not by mining sessions.
- The mining session flow and `area_break` flow are already independent.
- Tier validation does not alter `BlockMutation.breakBlock` or `DropSink.emit` contracts.

If a future decision places tier logic inside `area_break`, it would require:
- A new capability or domain query for tool tier at execution time.
- Modification of `AreaBreakMechanic`, which is an official module, not the core.
- No core contract change is needed for `area_break` tier awareness.

#### `mining.speed` Independence

`MiningSpeed` computes duration through `MiningDurationPolicy.expectedDurationMillis(MiningHardness, MiningSpeed)`.

If tiers are added, they must not silently alter `mining.speed` semantics unless explicitly designed. Two design options:

1. **Orthogonal**: tier and speed are independent. A tier 2 tool with `mining.speed: 8.0` takes the same duration as a tier 5 tool with `mining.speed: 8.0` against the same hardness. Tier only gates eligibility.
2. **Coupled**: tier modifies speed. A tier 3 tool might have effective speed = `baseSpeed * tierMultiplier`.

Recommendation: keep orthogonal for the initial spike analysis. Coupling speed and tier reduces configuration clarity and mixes two distinct concepts (eligibility vs. rate). If coupling is desired later, it should be a separate YAML field (`mining.speedMultiplier`) rather than implicit tier-based calculation.

#### Durability Independence

Durability wear is applied once per successful custom mining completion in `CustomMiningCompletionService`.

Tier does not affect durability in the initial design. If a future design wants tier to reduce wear (e.g., higher-tier tools lose less durability), that should be an explicit YAML field (`durability.wearReduction`) rather than implicit tier logic. This preserves MVP-3 behavior as documented in `docs/milestones/MVP-2-COMPLETE.md`.

### 4.4 Data Integrity

#### YAML Backward Compatibility

Current YAML shape (relevant fields):

```yaml
blocks:
  ruby_ore:
    material_base: STONE
    mining:
      hardness: 6.0
    numeric_id: 1001

items:
  ruby_pickaxe:
    material_base: DIAMOND_PICKAXE
    mining:
      speed: 8.0
    durability:
      max: 1561
      break_when_zero: true
```

Proposed additive extension:

```yaml
blocks:
  ruby_ore:
    material_base: STONE
    mining:
      hardness: 6.0
      required_tier: 2
    numeric_id: 1001

items:
  ruby_pickaxe:
    material_base: DIAMOND_PICKAXE
    mining:
      speed: 8.0
      tier: 3
    durability:
      max: 1561
```

Assessment:

- `required_tier` under `blocks.<id>.mining` and `tier` under `items.<id>.mining` are both optional.
- Missing `required_tier` means no restriction (backward compatible).
- Missing `tier` means tier 1 by default or no tier check triggered (backward compatible if default behavior is "no tier registered").
- No existing key changes meaning.
- No existing key is removed or renamed.
- `schema: 1` can remain unchanged because all new fields are additive and optional.

If future changes require mandatory tier fields, change persistence semantics, or need migration logic, a schema bump would be required. The current design does not justify that.

#### Schema Migration Path

If `schema: 1` is preserved:

- YAML loader ignores unknown fields (current behavior assumed from scope rules).
- Loader validates new optional fields only when present.
- Missing fields map to `Optional.empty()`.
- No migration function needed.

If a schema bump becomes necessary later, it should follow the existing migration policy in `docs/PROJECT_SCOPE.md` Section 9.4 and require ADR approval.

### 4.5 Contract Impact

#### `BlockQuery`

Current contract:

```java
Optional<Short> findCustomBlockNumericId(WorldPosition position);
```

Returns:
- `Optional.of(numericId)` if a custom block exists.
- `Optional.empty()` if no custom block exists OR if the position is unsafe/outside region.

For basic tier validation at session start, `BlockQuery` does not need to change. The mining service queries the target block before starting a session; if no custom block is found, `Optional.empty()` naturally terminates the miningsession creation path.

However, Spike 2 identified that `Optional.empty()` conflates "no custom block" with "unsafe to query." If tier validation is extended to runtime (e.g., tool swap mid-session), the ambiguity matters. For the current spike's recommended session-start placement, `BlockQuery` remains unchanged.

If future requirements need runtime tier re-validation during active sessions, `BlockQuery` may need a richer result type (as Spike 2 recommended). That change would require ADR and is out of scope for this spike.

#### `MechanicResult`

Current contract has `Done`, `Partial`, and `Rejected`. None carry tier-specific information.

For mining session tier rejection, a new `ProcessResult` from `MiningSessionService` could include a rejection reason. The adapter layer translates this into event cancellation or visual cleanup. `MechanicResult` does not need modification because tier rejection occurs before mechanic execution.

If a mechanic (e.g., `area_break`) is later made tier-aware, `MechanicResult.Rejected` already accepts a `String reason`, which could carry tier-insufficient messaging without contract changes.

#### `MechanicContext`

No new capabilities are required for tier validation. Tier checking belongs in the mining application service, not in mechanics. This preserves ADR-0005's anti-god-object rule for `MechanicContext`.

#### `RegionSafetyPort`

`RegionSafetyPort` is consulted before mining completion mutation. If tier validation occurs at session start (during event handling), `RegionSafetyPort` is not involved because no mutation has occurred yet.

If tier validation were moved to completion time, `RegionSafetyPort` would need to be consulted before rejecting, but that would waste scheduler and region resources for a session that should never have been created. Session-start validation avoids this.

### 4.6 Platform Safety

- Tier validation involves only domain value object comparison (`toolTier.level() >= block.requiredTier().minimumLevel()`).
- No Bukkit/Paper types are required.
- No scheduler calls are needed.
- No `RegionSafetyPort` calls are needed at validation time.
- If tool-swap mid-session cancellation is implemented, it reuses the existing `MiningInputAdapter` pattern, which is already Folia-safe.
- No new adapter is required for basic tier validation.

---

## 5. Risks

### Risk 1: Tool Swapping Invalidates Tier Mid-Session

Medium risk. A player could switch to a lower-tier tool after session start.

Mitigation: Re-validate on held-item change using the existing `MiningInputAdapter` -> `MiningSessionService.cancelSession(actorKey, target)` path. No new core contracts needed.

### Risk 2: `BlockQuery` Ambiguity Masks Tier Failures

Low risk for session-start validation. Medium risk if runtime re-validation is added later.

Mitigation: Keep validation at session start. Defer `BlockQuery` result-type enrichment to a separate ADR if runtime tier checks become necessary.

### Risk 3: `area_break` Assumes No Tier Restrictions

Medium risk if `area_break` becomes tier-aware without coordinated design.

Mitigation: `area_break` is an official module. Its tier behavior should be designed explicitly, not inherited accidentally from mining. The mining tier check at session start does not affect `area_break` paths.

### Risk 4: Core Inflation Pressure

Low-to-medium risk. `ToolTier` and `BlockTierRequirement` are mining-specific concepts. They belong in the mining domain package, not in a shared core capability.

Mitigation: Keep tier types in `domain.mining`. Do not create a new core capability for tier queries. Mechanics that need tier awareness should receive pre-validated data or remain mining-unaware.

### Risk 5: YAML Complexity Creep

Medium risk. Optional tier fields could grow into mandatory fields, enchantment interactions, or upgrade paths.

Mitigation: Spike and ADR must explicitly define `required_tier` and `tier` as optional, additive, and backward compatible. Any mandatory requirement or upgrade system requires a new ADR.

### Risk 6: Durability Interaction Ambiguity

Low risk. If tier and durability interact, the design must specify whether higher-tier tools gain durability or reduced wear.

Mitigation: This spike recommends orthogonal design. Durability interaction is explicitly out of scope and requires separate ADR.

---

## 6. Recommended Decision

**Recommendation: Proceed to ADR with stated design bounds.**

The analysis indicates that mining tiers can be implemented as pure domain value objects within the existing mining application service layer without:

- modifying `BlockQuery`, `MechanicResult`, or `MechanicContext` contracts;
- introducing new core capabilities;
- leaking platform types into the domain or mechanics;
- breaking `schema: 1` backward compatibility;
- disrupting `area_break` independence;
- altering `mining.speed` or durability semantics;
- requiring scheduler or Folia contract changes;
- violating the core evolution criteria in ADR-0003 as an experimental-mining-layer extension.

The feature qualifies as a mining-specific extension, not a stable-core change, and can be incubated as an experimental module or official module enhancement to custom mining rather than a core contract promotion.

---

## 7. Recommended Design Path

### 7.1 Representation

- Use integer levels (`ToolTier(int level)`, `BlockTierRequirement(int minimumLevel)`).
- Place records in `domain.mining`.
- No enum string values.

### 7.2 YAML Fields

```yaml
items:
  ruby_pickaxe:
    mining:
      speed: 8.0
      tier: 3        # optional, additive

blocks:
  ruby_ore:
    mining:
      hardness: 6.0
      required_tier: 2  # optional, additive
```

- Both fields are optional.
- Missing `tier` on an item means no tier registration (tool does not participate in tier checks).
- Missing `required_tier` on a block means no restriction (any tool may mine).
- No `schema` bump required.

### 7.3 Validation Placement

- Primary validation: `MiningSessionService.startSession`, before `MiningDurationPolicy.expectedDurationMillis` is called.
- Mid-session re-validation: reuse existing held-item change cancellation. If the current tool's tier falls below the target block's requirement, cancel the session.
- Completion: no additional tier check needed because an invalid tier would have prevented session creation or cancelled it mid-session.

### 7.4 Independence Guarantees

- `mining.speed` and `MiningDurationPolicy` remain orthogonal to tier.
- `ToolDurabilityDefinition` and `ToolWearPort` remain independent of tier.
- `AreaBreakMechanic` remains unaware of tier unless a future explicit decision couples them.
- No new core capability is introduced.

### 7.5 Default Behavior

- When tier fields are absent, behavior is identical to current MVP-2/MVP-3 behavior.
- Existing worlds and YAML files load and function without modification.

---

## 8. Next Steps

If the maintainer accepts "Proceed to ADR," the following must be resolved before implementation:

1. **Exact YAML field names and nesting**: confirm `tier` under `items.<id>.mining` and `required_tier` under `blocks.<id>.mining`, or choose alternatives.
2. **Default behavior definition**: confirm that missing `tier` means "no tier registration" and missing `required_tier` means "no restriction."
3. **Validation rules**: confirm that `ToolTier.level` and `BlockTierRequirement.minimumLevel` must be positive integers with a defined maximum (suggested: no artificial maximum, validated only as positive).
4. **Mid-session re-validation policy**: confirm that held-item change triggers tier re-validation and cancellation, or defer to a later spike.
5. **`area_break` interaction policy**: explicitly decide whether `area_break` remains tier-unaware or gains optional tier restriction, and whether that belongs in the builtin mechanic or in a YAML binding.
6. **Module placement**: decide whether the feature is implemented as part of the existing mining application service (preferred) or as a separate experimental module.
7. **Capability assessment**: confirm that no new `MechanicContext` capability is needed for the initial implementation.
8. **`BlockQuery` decision**: confirm that `BlockQuery` remains unchanged for the initial implementation, or escalate the Spike 2 enrichment recommendation to a concurrent ADR if runtime tier checks are desired.
9. **Performance consideration**: benchmark tier validation overhead (trivial for single session-start comparison, but relevant if extended to `area_break` multi-block queries).
10. **Tests required**: unit tests for `ToolTier` and `BlockTierRequirement` records; integration tests for session rejection, session cancellation on tool swap, and backward-compatible YAML loading.

---

## 9. Constraints Verification

| Constraint | Status | Notes |
| --- | --- | --- |
| Zero dependency leakage | Satisfied | Proposed records contain no Bukkit/Paper/Folia/NMS imports. |
| Minimalist core | Satisfied | No new core capability proposed. Tier logic stays in mining application/domain. |
| No public API | Satisfied | Implementation remains internal to the engine. |
| No scripting | Satisfied | No expression language or scripting engine introduced. |
| Folia compliance | Satisfied | No `runAsync`, `runOnEntity`, or `SchedulerAccess` required. |
| Idempotency | Satisfied | Validation at session start prevents session creation for invalid tiers; no duplicate completion possible. |
| Scope limitation | Satisfied | Tiers are a property of mining, not a new independent mechanic. |

---

## 10. ADR Requirements

An ADR is required before implementation because:

- The feature adds new YAML fields that affect domain definitions (`ItemDef`, `BlockDef`).
- It modifies the mining session lifecycle (`MiningSessionService`).
- It may affect future schema evolution decisions.
- It touches `docs/milestones/MVP-2-COMPLETE.md`-documented behavior (custom mining completion idempotency boundaries).

The ADR must NOT:

- Bypass the ADR-0006 incubation pipeline.
- Promote tier logic to stable core without proving repeated structural need across multiple modules.
- Declare `folia-supported: true` unless real Folia tests are completed.

---

End of document.

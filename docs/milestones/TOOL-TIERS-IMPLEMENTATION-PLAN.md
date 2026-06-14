# Tool Tiers – Implementation Plan

Status: Planned  
Date: 2026-06-14  
Based on: ADR-0010, Spike 4

---

## 1. Objective

Implement mining tiers as defined in ADR-0010, allowing custom tools to declare a `tier` and custom blocks to declare a `required_tier`, with validation occurring at mining session start and reuse of existing held-item change cancellation for mid-session invalidation.

---

## 2. Prerequisites

- [ ] ADR-0010 accepted.
- [ ] Spike 4 accepted.

---

## 3. Implementation Phases

### Phase 1 – Domain Layer

- Add `ToolTier` record in `domain/mining/`.
- Add `BlockTierRequirement` record in `domain/mining/`.
- Update `ItemDef` to include `Optional<ToolTier>`.
- Update `BlockDef` to include `Optional<BlockTierRequirement>`.
- Unit tests for validation (positive integer, null safety, equality).

### Phase 2 – YAML Loader & Validator

- Extend `YamlDefinitionLoader` to parse `tier` from `items.<id>.mining` and `required_tier` from `blocks.<id>.mining`.
- Extend `YamlDefinitionValidator` to reject non-positive or non-integer tier values at startup with a clear error message.
- Update existing YAML tests to cover absent fields, valid positive integers, zero, negative values, and non-integer inputs.

### Phase 3 – Application Service (MiningSessionService)

- In `MiningSessionService.startSession`, before computing `expectedDurationMillis`, add tier eligibility check:
  - Retrieve tool tier from `ItemDef`.
  - Retrieve block required tier from `BlockDef`.
  - Evaluate eligibility with a pure function.
  - If ineligible, prevent session creation and surface the reason to the adapter layer.
- Add mid-session re-validation on held-item change:
  - In the existing held-item change handling path, after identifying the new tool, check its tier against the active session's target block requirement.
  - If insufficient, cancel the session and trigger visual cleanup.

### Phase 4 – Adapter Feedback

- When tier validation fails at session start, send a player-facing message through the adapter layer (e.g., action bar or chat).
- The message content and channel are adapter decisions; the domain and application layers only expose a rejection reason.

### Phase 5 – Tests

- Unit tests for `ToolTier` and `BlockTierRequirement` records.
- Unit tests for tier eligibility logic.
- Integration tests:
  - Tool tier ≥ block required tier → session starts, mining completes normally.
  - Tool tier < block required tier → no session created, no visual progress, no completion.
  - Tool without tier → cannot mine block with required tier.
  - Block without required tier → any tool (including tiered tools) works.
  - Held-item change to lower-tier tool during active session → session cancelled, visual cleared.
  - YAML without tier fields loads successfully (backward compatibility).
  - YAML with invalid tier values fails startup.
- Verify `area_break` behavior remains unchanged.
- Verify custom mining completion idempotency is preserved.

### Phase 6 – Documentation

- Update `definitions.yml` example (if present) with optional tier fields.
- Update `README.md` to document `tier` and `required_tier`.
- Update `AI_CONTEXT_PACK.md` with tier rules when context pack is regenerated.

---

## 4. Acceptance Criteria

- [ ] All new domain classes contain no Bukkit/Paper/Folia/NMS imports.
- [ ] YAML with missing tier fields loads without error.
- [ ] YAML with invalid tier values (zero, negative, non-integer) fails startup with a clear error.
- [ ] Player cannot start mining a block when the active tool tier is below the block required tier.
- [ ] Held-item change to a lower-tier tool cancels the active mining session.
- [ ] `area_break` continues to work without modification.
- [ ] Custom mining completion remains idempotent (no duplicate drops, no duplicate mechanic execution).
- [ ] GitHub Actions build passes all unit and integration tests.
- [ ] No new core capabilities, scheduler contracts, or public APIs are introduced.

---

## 5. References

- ADR-0010 – Tool Tiers and Effective Blocks
- Spike 4 – Tool Tiers and Effective Blocks
- `docs/ARCHITECTURE_GUARDRAILS.md`
- `docs/PROJECT_SCOPE.md`
- `docs/adr/0003-conservative-evolvable-core.md`
- `docs/adr/0005-capability-governance.md`
- `docs/adr/0006-experimental-module-incubation.md`
- `docs/adr/0009-custom-mining-model.md`
- `docs/milestones/MVP-2-COMPLETE.md`

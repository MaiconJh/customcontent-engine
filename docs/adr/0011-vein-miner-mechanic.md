# ADR-0011 — Vein Miner Mechanic

**Status:** Accepted  
**Accepted**: 2026-07-05  
**Supersedes:** Previous ADR-0011 version (Proposed)

---

## Context

- The project already has the `area_break` (flat area) mechanic implemented and functional.
- The `block_transform` mechanic is planned in earlier ADRs but not yet implemented.
- Vein mining is a common server feature, allowing connected blocks of the same type to be mined in sequence.
- The incubation pipeline defined in ADR-0006 requires a formal ADR before an experimental module becomes official.
- **Spike 5** is planned to validate the performance of the BFS algorithm with `HashSet` visited tracking.
- **Modern implementations** (e.g., VeinBreaker, mc-veinminer, EzMine) have established best practices that should be followed:
  - Respect for enchantments (Fortune, Silk Touch, Unbreaking).
  - Durability wear applied *per block broken*, not just once per execution.
  - Granular configuration per block type and tool.
  - Option to activate only when sneaking.
  - Configurable cooldowns to prevent abuse.
  - Player toggle system (enable/disable the mechanic).

---

## Decision

Implement `vein_miner` as an **official module** (following the incubation pipeline), incorporating the following modern features into the design:

### Algorithm and Performance

- Use **BFS in 6 face-adjacent directions** with `HashSet` for O(1) visited lookup.
- Synchronous processing with `WorkBudget` and return `MechanicResult.Partial` for rescheduling (Folia-safe pattern already used in `area_break`).
- Configurable limits with a hard cap for lag protection:
  - `max_blocks`: 64 (default), absolute maximum of 512.
  - `max_depth`: 20 (default).

### Capabilities and Contracts

The mechanic will use existing capabilities, plus a new one for enchantment queries:

- **Existing Capabilities:**
  - `BLOCK_QUERY`
  - `BLOCK_MUTATION`
  - `BUDGET_VIEW`
  - `COOLDOWN_VIEW`
  - `DROP_SINK`
  - `EXECUTION_ORIGIN`
- **New Module Capability:** `ENCHANTMENT_VIEW`
  - Allows the mechanic to query enchantment levels (e.g., `fortune`, `silk_touch`, `unbreaking`) purely, without accessing Bukkit/Paper.
  - Classified as a **module capability** (ARCHITECTURE_GUARDRAILS.md §14.2), not a stable core capability: it is specialised for mechanics such as `vein_miner` and must not be promoted to the stable core without broader, cross-mechanic justification.
  - Interface (implemented):
    ```java
    public interface EnchantmentView {
        OptionalInt getLevel(String enchantmentKey);
    }
    ```

### YAML Configuration (Arguments)

The mechanic will accept the following arguments in YAML, allowing detailed configuration per tool:

```yaml
items:
  ruby_pickaxe:
    mechanics:
      on_block_break:
        - vein_miner
          arguments:
            max_blocks: 64
            max_depth: 20
            shape: FACE_ADJACENT        # FACE_ADJACENT (default) | ALL_ADJACENT
            require_sneak: true         # Requires sneaking to activate
            respect_fortune: true       # Applies Fortune to drops (default: true)
            respect_silk_touch: true    # Applies Silk Touch (default: true)
            durability_per_block: true  # Wears 1 durability per block (default: true)
            cooldown_seconds: 5         # Specific cooldown in seconds (0 = no cooldown)
            allowed_blocks:             # Optional list of specific blocks
              - ruby_ore
              - diamond_ore
```

**Configuration Rules:**
- `shape`: Defines expansion shape. `FACE_ADJACENT` (6 directions) is the default; `ALL_ADJACENT` (26 directions) is an option for more "organic" veins.
- `respect_fortune` and `respect_silk_touch`: The mechanic will query `EnchantmentView` to adjust drop quantities or block state.
- `durability_per_block`: When `true`, the tool wears for *each* block broken in the vein. When `false`, wear is applied only once (similar to current `area_break` behavior).
- `allowed_blocks`: If specified, the mechanic only executes if the origin block is in the list. If omitted, applies to any custom block matching the origin block type.

### Player Toggle Interface

A global toggle command (e.g., `/veinminer toggle`) will be implemented, allowing the player to enable or disable the mechanic for their session. The state will be stored in-memory in the `application` layer (e.g., `PlayerPreferenceService`) and checked at the start of execution, before cooldown validation.

### Protection and Durability Integration

- Protection checks will be performed via the existing `ProtectionPort` for each block before mutation.
- Per-block durability wear will be applied via `ToolWearPort`, called individually for each broken block (if `durability_per_block` is `true`).

---

## Consequences

### Positive

- Addresses a common player demand without core architecture changes.
- Uses a proven algorithm (BFS face-adjacent) with conservative limits to prevent lag.
- Incorporates modern practices (Fortune, Silk Touch, granular wear) expected by server administrators.
- Maintains pure, Folia-safe architecture through `Partial` and `SchedulerPort`.
- Highly flexible YAML configuration, allowing different behaviors per tool.

### Negative

- The BFS algorithm is more complex than `area_break` or `block_transform`, requiring rigorous performance testing.
- Very high settings (e.g., `max_blocks: 512`) combined with `ALL_ADJACENT` may cause lag.
- Introducing `EnchantmentView` adds a new core capability, though it is a natural extension of the model.

### Risks

- **Unsafe region traversal on Folia:** Mitigated by `RegionSafetyPort` and returning `Partial` for unprocessed positions.
- **Infinite loop or stack overflow:** Mitigated by `max_blocks`, `max_depth` limits, and `MechanicExecutor` anti-loop protection.
- **Duplicate wear application:** Mitigated by the idempotent design of `ToolWearPort` and session management.

---

## Alternatives Considered

| Alternative | Decision | Reason |
| --------- | -------- | ------ |
| DFS (Depth-First Search) | Rejected | May miss disconnected vein portions or create excessively deep paths. |
| `runAsync` for asynchronous processing | Rejected | Violates scheduler guardrails (ADR-0003) and Folia-safe architecture. |
| No limits (`max_blocks` infinite) | Rejected | Could cause severe lag on very large veins. |
| Single wear per execution (without `durability_per_block`) | Rejected | Does not match expected behavior from players and modern plugins. |
| No Fortune/Silk Touch support | Rejected | Makes the mechanic less useful in servers with enchantment-based economies. |

---

## Acceptance Criteria for Official Module

- [x] **Unit Tests:**
  - [x] BFS with `HashSet` for different shapes and sizes.
  - [x] Limit validation (`max_blocks`, `max_depth`).
  - [x] Cooldown and budget verification.
- [ ] **Unit Tests (pending):**
  - [ ] Integration with `EnchantmentView` (mocks for Fortune/Silk Touch).
  - [ ] Per-block durability wear (`durability_per_block`).
- [ ] **Integration Tests (Paper):**
  - [ ] Execution on linear and branched veins.
  - [ ] Drop verification with Fortune and Silk Touch.
  - [ ] Durability wear verification.
  - [ ] Player toggle (enable/disable).
- [ ] **Performance Validation (Spike 5):**
  - [ ] Documented performance results for veins of 10 to 200 blocks.
  - [ ] Confirmation that BFS overhead is acceptable (< 1ms for 64-block veins).
- [ ] **Usage Demonstration:**
  - [ ] At least two different YAML definitions demonstrating usage (e.g., ruby pickaxe with limit 64, diamond pickaxe with limit 128).
- [ ] **Documentation:**
  - [ ] Update `README.md` and `AI_CONTEXT_PACK.md` with the new mechanic and its arguments.

---

## Configuration Example (Final)

```yaml
schema: 1
items:
  ruby_pickaxe:
    material_base: DIAMOND_PICKAXE
    custom_model_data: 2001
    attributes:
      damage: 5.0
      speed: 1.2
      durability: 500
    mining:
      speed: 8.0
      tier: 3
    durability:
      max: 500
      damage_on_custom_block_break: 1
      break_when_zero: true
    mechanics:
      on_block_break:
        - vein_miner
          arguments:
            max_blocks: 64
            max_depth: 20
            require_sneak: true
            respect_fortune: true
            respect_silk_touch: true
            durability_per_block: true
            allowed_blocks:
              - ruby_ore
              - diamond_ore
```

---

## Related ADRs

- ADR-0001 — Mechanic Contract for MVP-1
- ADR-0002 — Execution Origin Capability
- ADR-0003 — Conservative Evolvable Core
- ADR-0005 — Capability Governance
- ADR-0006 — Experimental Module Incubation
- ADR-0008 — YAML Mechanic Bindings
- ADR-0009 — Custom Mining Model
- Spike 5 — Vein Miner Feasibility

---

**Implementation Status (2026-07-06):**

✅ Core BFS implementation with `HashSet` visited tracking (`VeinMinerMechanic.java`)  
✅ Unit tests for basic functionality (`VeinMinerMechanicTest.java`)  
✅ Registration in `MechanicRegistry` and `MechanicBindingValidator` (`CustomContentPlugin.java`)  
✅ `MechanicResult.Partial` support for Folia-safe budget rescheduling  
✅ `EnchantmentView` module capability interface (`internalapi/mechanic/capability/EnchantmentView.java`)  
✅ `VeinMinerMechanic` queries `EnchantmentView` optionally and applies Fortune/Silk Touch drop multipliers (via `DropSink.dropFor(position, id, count)`).  
✅ YAML argument parsing: `MechanicBinding` now carries `arguments`; `YamlDefinitionLoader` reads the `id:` + `arguments:` map form; `YamlDefinitionValidator` accepts it.  
✅ `MechanicDescriptor` gained an explicit `optionalCapabilities` set; `MechanicContextFactory` injects available optional capabilities.  
✅ Unit tests: `YamlDefinitionLoaderTest.loadsMechanicArguments`, `VeinMinerMechanicTest.appliesFortuneFromEnchantmentView`.  
✅ Runtime wiring: `VeinMinerRuntimeService` (application) executes the mechanic with the same Folia-safe capabilities as `AreaBreakRuntimeService`, plus optional `EnchantmentView`.  
✅ `VeinMinerEventTriggerService` (application) dispatches `ON_BLOCK_BREAK` when a binding exists; it receives the `EnchantmentView` from the caller, keeping the application layer free of Bukkit.  
✅ `BukkitEnchantmentViewAdapter` (adapter/platform) reads Fortune/Silk Touch from the `ItemStack`; created in `BlockBreakAdapter` and passed to the trigger.  
✅ `BlockBreakAdapter` now also triggers `vein_miner`; `CustomContentPlugin` composes the new services.  
✅ Integration test: `VeinMinerRuntimeServiceTest` verifies trigger execution and binding absence.  
✅ **YAML arguments plumbed into execution context:** new module capability `MechanicArguments` (`internalapi/mechanic/capability/MechanicArguments.java`); `VeinMinerRuntimeService` wraps `binding.arguments()` and injects it; `VeinMinerMechanic` reads `max_blocks`, `max_depth`, `shape` (FACE_ADJACENT/ALL_ADJACENT), `respect_fortune`, `respect_silk_touch` from the context (clamped to safe absolute limits: `max_blocks` ∈ [1,512], `max_depth` ∈ [1,64]).  
✅ Unit test: `VeinMinerMechanicTest.appliesMaxBlocksFromMechanicArguments`.  

**Not Implemented Yet (scope/guardrail limits):**
- `require_sneak` requires player sneak state, which is not yet available in the mechanic context (no `ExecutionOrigin`/actor sneak capability). It is parsed but not enforced.
- `durability_per_block` requires per-block `ToolWearPort` application; pending (see medium-priority plan).
- Player toggle command and `ProtectionPort` integration remain pending.
- **Spike 5** is not yet wired: there is no `veinMinerSpike` Gradle task in the project. A benchmark/benchmark task must be created (or run ad hoc) to validate BFS performance per ADR-0011 acceptance criteria.

**Next Steps:**
1. Add `require_sneak` (needs sneak state in context) and `durability_per_block` (needs `ToolWearPort`) once the supporting capabilities exist.
2. Create the `veinMinerSpike` Gradle task / benchmark and execute Spike 5.
3. Add Paper integration tests and player toggle command.

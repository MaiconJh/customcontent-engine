# MVP-3 Complete

Status: Complete  
Date: 2026-06-08  
Scope: Custom tool durability and wear for custom mining

## Summary

MVP-3 implements a minimal, conservative custom durability system for custom tools used during custom block mining.

## Implemented Functionality

### Domain Model (`domain/durability/`)

- `ToolDurabilityDefinition` — Pure definition of durability configuration:
  - `max` — Maximum durability (positive integer)
  - `damageOnCustomBlockBreak` — Damage applied per custom block break (zero or positive)
  - `breakPolicy` — `BREAK` or `PRESERVE` when reaching zero

- `ToolDurability` — Runtime durability state:
  - `max` — Maximum durability from definition
  - `current` — Current durability value (clamped 0 to max)

- `ToolWearResult` — Result of applying wear:
  - `newDurability` — Updated durability state
  - `shouldBreak` — Whether the tool should be removed

- `ToolBreakPolicy` — Enum:
  - `BREAK` — Remove tool when durability reaches zero
  - `PRESERVE` — Keep tool at zero durability (default)

### YAML Configuration

Added support for `durability` section under `items.<id>`:

```yaml
items:
  ruby_pickaxe:
    material_base: DIAMOND_PICKAXE
    custom_model_data: 2001
    attributes:
      damage: 5.0
      speed: 1.2
      durability: 500
    durability:
      max: 500
      damage_on_custom_block_break: 1
      break_when_zero: true
```

Rules:
- `durability` is optional
- `durability.max` must be a positive integer
- `durability.damage_on_custom_block_break` defaults to 0, must be zero or positive
- `durability.break_when_zero` defaults to `true`

### Runtime Flow

1. Custom item creation initializes current durability to `max` (stored in PDC)
2. On successful custom mining completion:
   - Tool wear is applied exactly once
   - If `damage_on_custom_block_break > 0`, durability decreases
   - If durability reaches zero and `break_when_zero: true`, tool is removed
   - If durability reaches zero and `break_when_zero: false`, tool remains at zero
3. Wear only applies during custom mining completion (not vanilla break)

### AreaBreak Limitation (MVP-3)

For MVP-3, tool wear is applied once per custom mining completion, regardless of how many blocks are broken by AreaBreak. The wear is not multiplied by additional blocks broken via the `area_break` mechanic.

## Files Modified

- `domain/definition/ItemDef.java` — Added optional `durability` field
- `domain/durability/ToolDurability.java` — New: runtime durability state
- `domain/durability/ToolDurabilityDefinition.java` — New: durability configuration
- `domain/durability/ToolWearResult.java` — New: wear result
- `domain/durability/ToolBreakPolicy.java` — New: break policy enum
- `port/ItemMetadataPort.java` — Extended with durability read/write methods
- `port/ToolWearPort.java` — New: port for applying tool wear
- `adapter/bukkit/BukkitItemMetadataAdapter.java` — PDC persistence for durability
- `adapter/bukkit/BukkitToolWearAdapter.java` — New: Bukkit implementation of ToolWearPort
- `adapter/yaml/YamlDefinitionLoader.java` — Parse `durability` section
- `adapter/yaml/YamlDefinitionValidator.java` — Validate durability fields
- `application/mining/CustomMiningCompletionService.java` — Apply wear on completion
- `bootstrap/CustomContentPlugin.java` — Wire ToolWearPort

## Files Added

- `domain/durability/ToolDurability.java`
- `domain/durability/ToolDurabilityDefinition.java`
- `domain/durability/ToolWearResult.java`
- `domain/durability/ToolBreakPolicy.java`
- `port/ToolWearPort.java`
- `adapter/bukkit/BukkitToolWearAdapter.java`
- `test/java/com/customcontentengine/domain/durability/ToolDurabilityTest.java`
- `test/java/com/customcontentengine/domain/durability/ToolDurabilityDefinitionTest.java`

## Tests Added

- ToolDurability validation tests (max positive, current bounds)
- ToolDurabilityDefinition validation tests (max positive, damage non-negative)
- ToolDurabilityDefinition wear application tests (damage, zero damage, break policy)
- YamlDefinitionLoader durability loading tests
- YamlDefinitionValidator durability validation tests (max positive, damage non-negative, default break_when_zero)

## Architecture Notes

- Domain layer remains pure (no Bukkit/Paper imports in durability classes)
- Adapter layer handles PDC persistence
- Application layer coordinates completion flow
- ToolWearPort allows application to trigger wear without platform knowledge
- AreaBreak continues to work independently; wear is not multiplied

## Known Limitations

- Tool durability only applies to custom block mining completion
- Vanilla block breaking does not affect custom tool durability
- No repair, mending, or crafting system
- No visual durability bar (uses vanilla durability bar via PDC)
- No GUI or commands for durability management

## Validation

GitHub Actions is the source of truth for validation. See workflow run for final confirmation.
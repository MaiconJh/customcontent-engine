# Post-MVP-1 YAML Mechanics Plan

Status: Planned  
Date: 2026-06-07  
Scope: formal item/tool to mechanic binding in `definitions.yml`

## 1. Objective

The objective is to replace the temporary internal policy `ruby_pickaxe -> area_break` with a declarative YAML configuration.

This plan does not implement the YAML change. It defines the intended shape, boundaries, validation rules, runtime impact, compatibility expectations, and ADR need before implementation begins.

The goal is narrow: allow a registered custom item/tool to declare that a registered mechanic should run for a specific supported trigger. This must not turn CustomContent Engine into a generic ability framework, scripting system, or broad event automation platform.

## 2. Current State

`definitions.yml` already defines custom blocks and custom items/tools.

`ruby_pickaxe` currently triggers `area_break` through an internal provisional policy, not through YAML.

`AreaBreakMechanic` already exists as the first builtin mechanic.

`MechanicRegistry`, `MechanicExecutor`, and `MechanicContextFactory` already exist.

Mechanics already declare capabilities through `MechanicDescriptor`, and capabilities are validated by the mechanic infrastructure.

YAML currently knows only blocks, items/tools, attributes, and drops. It does not know mechanics, triggers, or item/tool to mechanic bindings.

## 3. Formal YAML Requirements

The formal YAML must provide an explicit item/tool to mechanic association.

The format must remain simple and readable.

The format must not provide scripting, expressions, arbitrary event hooks, or a generic ability framework.

The first step must not introduce multiple advanced mechanics or complex composition.

The change must remain compatible with the current MVP definitions shape.

Startup validation must fail with clear errors when:

- a referenced item/tool does not exist;
- a referenced mechanic id does not exist in `MechanicRegistry`;
- a mechanic requires capabilities that the runtime cannot provide for the trigger;
- a trigger is unknown or not supported;
- the `mechanics` section has an invalid shape;
- a mechanic id is empty or malformed.

## 4. Proposed YAML Format

### Option A: Flat Mechanic List

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
    mechanics:
      - area_break
```

Benefits:

- smallest possible addition;
- easy to read for a single mechanic;
- low parsing complexity.

Costs:

- ambiguous trigger semantics;
- future triggers would require either a breaking reinterpretation or an additional format;
- it hides when the mechanic runs;
- it nudges the model toward "items have abilities" instead of "items bind mechanics to explicit engine triggers."

### Option B: Explicit Trigger Map

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
    mechanics:
      on_block_break:
        - area_break
```

Benefits:

- explicit about when the mechanic runs;
- avoids guessing runtime behavior from a flat list;
- keeps the first supported trigger narrow;
- gives future triggers a controlled place to exist without changing the meaning of existing YAML;
- aligns with listener segmentation and the current `BlockBreakEvent` integration.

Costs:

- slightly more verbose than a flat list;
- requires a small trigger vocabulary and validation step from the beginning.

## 5. Recommended Format

Use the explicit trigger map:

```yaml
mechanics:
  on_block_break:
    - area_break
```

This is the most conservative first step because it avoids ambiguity, does not introduce a generic ability system, and makes the execution point clear.

Only `on_block_break` should be supported initially. Future triggers must be introduced through ADR, milestone approval, and architecture fitness coverage when needed.

## 6. Initial Allowed Scope

Initial implementation should allow only:

- item/tool to `area_break` binding;
- trigger `on_block_break`;
- mechanics already registered in `MechanicRegistry`;
- a simple list of `MechanicId` values;
- startup validation of item existence, trigger support, mechanic registration, and capability availability.

Initial implementation should not allow:

- per-mechanic arguments;
- nested mechanic configuration;
- conditions;
- expressions;
- scripting;
- dynamic trigger names;
- external modules;
- runtime reload.

## 7. Out Of Scope

The following remain out of scope for the first YAML mechanics step:

- a second mechanic;
- `vein_miner`;
- `block_transform`;
- `auto_smelt`;
- complex configurable arguments;
- conditions;
- expressions;
- scripting;
- GUI behavior;
- permissions per mechanic;
- cooldown configuration in YAML;
- budget configuration in YAML;
- dynamic reload;
- hot reload;
- external modules;
- public API;
- `ServiceLoader`;
- generic ability system.

## 8. Domain Impact

There are two reasonable modeling options.

### Option A: Add Bindings Directly To `ItemDef`

`ItemDef` could receive a mechanics field, such as a map from trigger to mechanic ids.

Benefits:

- simple to discover from the item definition;
- fewer domain types;
- smaller first implementation.

Costs:

- `ItemDef` starts mixing item attributes with runtime behavior bindings;
- future trigger growth could make `ItemDef` too broad;
- it may become tempting to add mechanic arguments, conditions, cooldowns, or ability-like concepts directly to items.

### Option B: Create A Separate Binding Structure

Create a small pure structure such as `MechanicBindingRegistry`, backed by records such as:

```text
MechanicTrigger
MechanicBinding(itemId, trigger, mechanicId)
MechanicBindingRegistry
```

Benefits:

- keeps `ItemDef` focused on item identity, material, model data, and attributes;
- makes mechanic bindings explicit runtime configuration, not item attributes;
- provides a clean place for validation and lookup;
- reduces pressure to turn `ItemDef` into a large object;
- better matches the idea that mechanics are official/internal modules, not stable core item behavior.

Costs:

- introduces a few more pure domain/application types;
- loader and registry construction become slightly more involved.

Recommendation: prefer Option B, a separate binding structure, if the implementation remains small. It keeps the model cleaner and helps prevent core inflation. A minimal direct substructure on `ItemDef` is acceptable only if an ADR decides the smaller implementation is worth the coupling and explicitly limits future growth.

## 9. Loader And Validator Impact

`YamlDefinitionLoader` should parse the optional `mechanics` section under each item/tool.

`YamlDefinitionValidator` should validate the YAML shape before records or registries are built.

A pure registry or registry adjunct should store bindings. The most conservative name is `MechanicBindingRegistry`, separate from `DefinitionRegistry`, unless the ADR chooses to extend `DefinitionRegistry` with an explicit binding component.

Startup should fail when:

- a mechanic id is invalid;
- a mechanic id is not registered in `MechanicRegistry`;
- a trigger is invalid or unsupported;
- a referenced item/tool does not exist;
- the `mechanics` value is not a map;
- the `on_block_break` value is not a list;
- a listed mechanic id is empty or not a string;
- the runtime cannot provide required capabilities for the mechanic on that trigger.

Validation should produce messages that name the item id, trigger, and mechanic id whenever possible.

## 10. Runtime Impact

`AreaBreakTriggerPolicy` should stop using hardcoded `ruby_pickaxe -> area_break`.

`AreaBreakEventTriggerService` or its successor should consult declarative bindings for the item/tool involved in the block break.

`BlockBreakAdapter` should remain thin. It should continue translating Bukkit event data into application inputs and delegate mechanic decisions to application services.

`AreaBreakMechanic` should remain pure, stateless, and capability-driven.

`MechanicExecutor` should remain responsible for execution, cooldown gates, budget gates, protection gates, result handling, and rescheduling.

The runtime lookup should be simple:

1. Adapter extracts the custom item id from the tool in the block break event.
2. Application service asks the binding registry for mechanics bound to `on_block_break`.
3. Registered mechanic ids are resolved through `MechanicRegistry`.
4. `MechanicExecutor` runs each resolved mechanic through the existing pipeline.

For the first step, if more than one mechanic is listed accidentally, the ADR should decide whether startup rejects it or allows the list but only documents/test-covers `area_break`. The more conservative choice is to reject more than one binding until a second mechanic is formally approved.

## 11. Compatibility

Existing `definitions.yml` files without `mechanics` must remain valid.

If the `mechanics` section is absent, no item should trigger mechanics.

The current hardcoded `ruby_pickaxe -> area_break` policy must be removed when formal YAML mechanics are implemented. After implementation, `ruby_pickaxe` should trigger `area_break` only if `definitions.yml` declares the binding.

Schema handling has two viable choices:

- Keep `schema: 1` if the change is strictly additive and missing `mechanics` remains valid.
- Move to `schema: 2` if the project wants YAML mechanics to be treated as a formal configuration contract change with explicit migration semantics.

Recommendation: keep compatibility with existing `schema: 1` files by accepting missing `mechanics`. The ADR should decide whether the example/default `definitions.yml` moves to `schema: 2`. If no migration behavior changes and the field is optional, keeping schema compatibility is reasonable.

## 12. ADR Need

An ADR is recommended before implementation.

Reasons:

- this changes the formal YAML configuration contract;
- it replaces a provisional internal runtime policy;
- it introduces trigger vocabulary;
- it affects loader, validation, registry shape, and runtime dispatch;
- it decides whether bindings live in `ItemDef`, `DefinitionRegistry`, or a separate binding registry;
- it decides schema compatibility versus schema version bump.

This planning document should feed that ADR, but it should not substitute for it.

## 13. Future Tests Needed

Required tests for implementation:

- YAML without `mechanics` remains valid.
- YAML with `mechanics.on_block_break: [area_break]` is valid.
- YAML with unknown mechanic id fails at startup validation.
- YAML with invalid trigger fails.
- YAML with malformed `mechanics` fails.
- YAML referencing a missing item/tool fails, if bindings are ever allowed outside item-local declarations.
- Runtime triggers `area_break` through YAML binding.
- Hardcoded `ruby_pickaxe -> area_break` policy is removed.
- Missing `mechanics` means no mechanic is triggered.
- `BlockBreakAdapter` remains thin.
- `AreaBreakMechanic` remains pure and unchanged.
- `MechanicExecutor` remains responsible for execution and rescheduling.
- Architecture Fitness Functions continue passing.

Additional recommended tests:

- duplicate mechanic ids under one trigger fail or are de-duplicated by explicit ADR decision;
- more than one mechanic under `on_block_break` is rejected until a second mechanic is approved;
- capability mismatch produces a clear validation error naming mechanic id and missing capability.

## 14. Recommended Next Step

Create an ADR for formal YAML mechanics before implementation.

The ADR should decide:

- the YAML format, recommended as `mechanics.on_block_break`;
- the initial trigger vocabulary, recommended as only `on_block_break`;
- whether bindings live in a separate `MechanicBindingRegistry` or directly in item definitions;
- whether `schema` remains compatible or advances;
- startup validation rules and failure messages;
- the removal plan for hardcoded `ruby_pickaxe -> area_break`;
- test and architecture fitness expectations.

Only after that ADR is accepted should Java code or `definitions.yml` be changed.

# ADR 0008 - YAML Mechanic Bindings

Status: Accepted  
Date: 2026-06-07

## Context

MVP-1 is complete, as recorded in `docs/milestones/MVP-1-COMPLETE.md`.

MVP-1 introduced the first controlled builtin mechanic, `area_break`, and integrated it with `BlockBreakEvent` while preserving the MVP-0 custom item and custom block lifecycle.

The current activation path uses a temporary internal policy:

```text
ruby_pickaxe -> area_break
```

That policy was intentionally provisional. It must not become a hidden API, an undocumented configuration contract, or a permanent special case inside application/runtime code.

The project needs a declarative way to associate mechanics with custom items/tools in `definitions.yml`.

The solution must remain conservative:

- it must stay directly tied to custom items/tools and supported engine triggers;
- it must use mechanics already registered in `MechanicRegistry`;
- it must preserve the explicit capability model from ADR 0001 and ADR 0002;
- it must not turn the engine into a generic ability framework;
- it must not introduce scripting, expressions, arbitrary event hooks, or broad behavior composition.

## Decision

`definitions.yml` may declare optional mechanic bindings under an item/tool.

The initial supported format is:

```yaml
items:
  ruby_pickaxe:
    material_base: DIAMOND_PICKAXE
    custom_model_data: 1001
    attributes:
      damage: 5.0
      speed: 1.2
      durability: 500
    mechanics:
      on_block_break:
        - area_break
```

The following rules are accepted:

- `mechanics` is optional.
- If `mechanics` is absent, the item/tool does not trigger mechanics.
- The only accepted trigger initially is `on_block_break`.
- The value of `on_block_break` is a simple list of `MechanicId` values.
- Only mechanics registered in `MechanicRegistry` may be referenced.
- `area_break` is the only official/builtin mechanic accepted in this phase.
- There are no per-mechanic arguments in this phase.
- There are no conditions in this phase.
- There are no expressions in this phase.
- There are no scripts in this phase.
- There are no permissions per mechanic in this phase.
- There is no cooldown or budget configuration in YAML in this phase.
- The declarative binding replaces the temporary internal `ruby_pickaxe -> area_break` policy.

The loader/validator must fail startup with a clear error when:

- a trigger is unknown;
- a mechanic id is invalid;
- a mechanic is not registered;
- the YAML shape is invalid;
- a binding points to a missing item, if a future external binding structure is introduced.

Existing `definitions.yml` files without `mechanics` remain valid.

## Schema Decision

Keep `schema: 1`.

This is an additive and backward-compatible YAML change because:

- `mechanics` is optional;
- missing `mechanics` preserves valid existing definitions;
- items without mechanic bindings continue to behave as normal custom items/tools;
- no existing key changes meaning.

A future incompatible YAML change must bump the schema version.

Examples of changes that would require a schema bump include:

- making `mechanics` mandatory;
- changing the meaning of existing item fields;
- changing the shape of existing `mechanics.on_block_break` entries;
- introducing migration behavior that old files cannot satisfy unchanged.

## Domain And Application Impact

Mechanic bindings must not make `ItemDef` a god object.

The implementation should prefer a clear pure structure such as:

- `MechanicBindingRegistry`;
- `ItemMechanicBindings`;
- `MechanicBinding`;
- or an equivalent small model.

The domain must remain pure. No Bukkit, Paper, Folia, PDC, YAML parser API, adapter type, plugin type, service locator, or scheduler access may enter domain, internal mechanic contracts, or builtin mechanics.

Runtime lookup should be by `CustomItemId` and trigger.

The implementation may choose the exact package and type names, but the model must preserve these ideas:

- item/tool identity is separate from runtime mechanic execution;
- mechanic bindings are explicit configuration, not hidden behavior;
- mechanics are resolved through `MechanicRegistry`;
- capability availability is validated before execution;
- `AreaBreakTriggerPolicy` hardcoding `ruby_pickaxe -> area_break` must be removed when this ADR is implemented.

## Runtime Impact

`BlockBreakAdapter` must remain thin. It should continue translating Bukkit event data into application inputs and delegating behavior decisions to application services.

`AreaBreakEventTriggerService` or its successor must consult declarative bindings instead of relying on a hardcoded `ruby_pickaxe -> area_break` rule.

`AreaBreakMechanic` must remain pure, stateless, and capability-driven.

`MechanicExecutor` remains responsible for:

- execution;
- cooldown gates;
- budget gates;
- protection gates;
- interpreting `MechanicResult`;
- rescheduling through `SchedulerPort`.

No Bukkit or Paper type may enter `domain`, `internalapi`, or `builtin`.

`application` must continue not depending on `adapter`.

## Consequences

Benefits:

- removes the hardcoded `ruby_pickaxe -> area_break` policy;
- makes mechanic activation declarative;
- keeps triggers explicit;
- avoids an ambiguous flat "item abilities" model;
- avoids a generic ability system;
- keeps compatibility with current YAML when `mechanics` is absent;
- makes future trigger additions easier to govern through ADRs and fitness tests.

Costs:

- loader and validator logic become more complex;
- bindings must be validated against `MechanicRegistry`;
- implementation requires additional unit and runtime tests;
- bootstrap order needs care because definitions, bindings, builtin mechanic registration, and capability validation must align;
- error messages must be precise enough for server administrators to fix invalid YAML.

## Alternatives Considered

### Keep `ruby_pickaxe -> area_break` Hardcoded

Rejected.

The hardcoded policy was acceptable only as a controlled MVP-1 bridge. Keeping it would create a hidden API, obscure behavior, and make future mechanics harder to reason about.

### Use A Flat List

Example:

```yaml
mechanics:
  - area_break
```

Rejected for the initial formal contract.

It is compact, but it does not say when the mechanic runs. That ambiguity pushes the project toward an "item has abilities" model instead of explicit trigger-bound mechanics.

### Add A Generic Ability / Condition / Script System

Rejected.

Generic abilities, conditions, expressions, and scripts are outside the scope of the MVP and post-MVP-1 conservative path. They would inflate the core and violate the product boundary around custom blocks, tools, items, and controlled mechanics.

### Add Full Per-Mechanic Arguments Now

Rejected.

Arguments may be useful later, but adding them before a second approved mechanic or repeated need would increase complexity prematurely. The first formal binding should remain a simple trigger-to-mechanic-id list.

### Force A Schema Bump To `schema: 2`

Rejected for this change.

Because `mechanics` is optional and old files remain valid unchanged, the change can remain backward compatible under `schema: 1`. Future incompatible changes must bump the schema version.

## Scope Impact

This decision affects post-MVP-1 work.

This decision does not:

- alter MVP-0 behavior;
- alter the mechanic contract from ADR 0001;
- alter the `ExecutionOrigin` capability from ADR 0002;
- alter persistence or the binary PDC format;
- add a second mechanic;
- add `vein_miner`;
- add `block_transform`;
- add `auto_smelt`;
- create a stable public API;
- add `ServiceLoader`;
- add scripting;
- add external module loading;
- add cooldown or budget configuration in YAML.

## Tests Required For Implementation

Implementation must include tests covering:

- YAML without `mechanics` remains valid.
- YAML with `mechanics.on_block_break: [area_break]` is valid.
- YAML with an invalid trigger fails.
- YAML with an unknown mechanic id fails.
- YAML with invalid `mechanics` shape fails.
- Runtime triggers `area_break` through YAML binding.
- The hardcoded `ruby_pickaxe -> area_break` policy is removed.
- Items without `mechanics` do not trigger mechanics.
- `BlockBreakAdapter` remains thin.
- `AreaBreakMechanic` remains pure.
- Architecture Fitness Functions continue passing.

Recommended additional tests:

- capability mismatch produces a clear startup validation error;
- multiple mechanic ids under `on_block_break` are rejected until another mechanic is formally approved, or explicitly accepted by a later ADR;
- duplicate mechanic ids under one trigger are rejected or normalized by an explicit implementation decision.

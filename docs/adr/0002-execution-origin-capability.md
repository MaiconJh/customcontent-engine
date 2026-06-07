# ADR 0002 - Execution Origin Capability

Status: Accepted  
Date: 2026-06-06

## Context

ADR 0001 formalized the MVP-1 mechanic contract around `Mechanic.execute(MechanicContext)`, explicit capabilities, and structured `MechanicResult` values.

The first builtin mechanic, `AreaBreakMechanic`, needs an execution origin to compute its flat 3x3 area. Passing `WorldPosition` in the mechanic constructor would make the mechanic instance stateful, which is not appropriate for mechanics registered in `MechanicRegistry`. Registered mechanics should be stateless and reusable across executions.

The origin also cannot come from Bukkit `Location`, Bukkit events, Paper objects, or internal services. Mechanics must remain pure and must not access Bukkit/Paper, `BlockService`, `ItemService`, `DefinitionRegistry`, PDC, or scheduler infrastructure.

The adopted solution is a pure capability named `ExecutionOrigin`.

## Decision

Add `EXECUTION_ORIGIN` to the `Capability` enum.

Create the pure capability:

```java
public interface ExecutionOrigin {
    WorldPosition origin();
}
```

Mechanics that need an execution origin must declare `EXECUTION_ORIGIN` in `MechanicDescriptor.requiredCapabilities`.

Mechanics must obtain the origin through:

```java
ExecutionOrigin origin = context.require(ExecutionOrigin.class);
WorldPosition position = origin.origin();
```

Mechanics must not receive origin data through constructors.

Mechanics must not access Bukkit/Paper APIs to discover the origin.

## Consequences

Benefits:

- Mechanics remain stateless and reusable.
- The mechanic contract remains pure.
- Runtime code can provide different origins per execution.
- Bukkit/Paper stays outside mechanics.
- Unit tests can provide origins with simple fakes.

Costs:

- The contract defined by ADR 0001 is expanded with one additional capability.
- `MechanicContextFactory` must support `EXECUTION_ORIGIN`.
- Mechanics that need an origin must declare the capability explicitly.

## Scope Impact

This decision affects MVP-1 mechanic capability modeling.

This decision does not:

- alter MVP-0;
- alter persistence;
- alter the binary PDC format;
- alter YAML;
- register `area_break` in the plugin;
- implement runtime integration;
- create a stable public API.

## Alternatives Considered

### Pass WorldPosition In The Mechanic Constructor

Rejected. This makes the mechanic stateful and unsuitable as a reusable registered mechanic.

### Put Bukkit Location In The Context

Rejected. Bukkit/Paper types must not leak into mechanics or internal pure contracts.

### Read Origin From BlockService Or An Internal Event

Rejected. That would couple mechanics to the application core or runtime plumbing and bypass the capability model.

### Change Mechanic.execute To Receive Origin Directly

Rejected. ADR 0001 already defines `Mechanic.execute(MechanicContext)`, and the capability model is the intended extension point for execution-scoped data.
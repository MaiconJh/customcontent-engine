# ADR-0007 — Architecture Fitness Functions

Status: Proposed  
Date: 2026-06-07  
Project: CustomContent Engine

---

## Context

The project relies on architectural boundaries:

- Pure domain.
- Hexagonal architecture.
- Platform isolation through adapters.
- Mechanics through explicit capabilities.
- No Bukkit/Paper/Folia leakage into core.
- No feature inflation in stable core.

Manual review and documentation are not enough to preserve these boundaries over time. The project needs automated checks that fail when architectural rules are violated.

---

## Decision

CustomContent Engine must maintain architecture fitness functions as part of the test/audit suite.

Fitness functions may be implemented using:

- ArchUnit.
- JUnit dependency tests.
- `rg` audit scripts.
- Gradle verification tasks.
- CI checks.

---

## Required Fitness Functions

### Dependency Boundaries

1. `domain` must not depend on Bukkit, Paper, Spigot, Folia, NMS, YAML, PDC, or adapters.
2. `application` must not depend on adapters, Bukkit, Paper, Spigot, Folia, NMS, or YAML implementations.
3. `builtin.mechanic` must not depend on Bukkit, Paper, adapters, services, schedulers, or registries.
4. `adapter` must not be imported by `domain`, `internalapi`, `builtin`, or `application`.
5. `bootstrap` may compose modules but must not contain complex business logic.

### Hot Path Rules

6. No reflection or NMS in production code.
7. No disk I/O, YAML parsing, blocking database access, or network access in event hot paths.
8. No unbounded loops in mechanics.
9. Area operations must respect `WorkBudget`.
10. World mutation must go through `SchedulerPort` and appropriate ports/adapters.

### Extension Rules

11. New mechanics must declare capabilities.
12. New mechanics must be unit-testable without a server.
13. New stable-core contracts require ADR.
14. New extension points start experimental.
15. Devtools must not be enabled in production flow unless explicitly configured.

### Scope Rules

16. No economy, quest, combat, teleportation, GUI, or generic scripting capability may enter stable core without formal scope redefinition.
17. Official modules must not become hidden dependencies of stable core.
18. Experimental modules must not be imported by stable core.

---

## Suggested ArchUnit Rules

Examples:

```java
noClasses()
    .that().resideInAPackage("..domain..")
    .should().dependOnClassesThat().resideInAnyPackage(
        "org.bukkit..", "io.papermc..", "net.minecraft..", "org.spigotmc.."
    );

noClasses()
    .that().resideInAPackage("..application..")
    .should().dependOnClassesThat().resideInAnyPackage("..adapter..", "org.bukkit..", "io.papermc..");

noClasses()
    .that().resideInAPackage("..builtin.mechanic..")
    .should().dependOnClassesThat().resideInAnyPackage("..adapter..", "..application..", "org.bukkit..", "io.papermc..");
```

---

## Suggested `rg` Audit Commands

```bash
rg "org\.bukkit|io\.papermc|net\.minecraft|org\.spigotmc" src/main/java/com/customcontentengine/domain src/main/java/com/customcontentengine/application src/main/java/com/customcontentengine/builtin

rg "Class\.forName|getDeclared|Method|Field|invoke" src/main/java

rg "Files|Path|InputStream|OutputStream|Yaml|ObjectMapper|Gson" src/main/java/com/customcontentengine/adapter/bukkit src/main/java/com/customcontentengine/application

rg "BukkitRunnable|runTask|Scheduler|Thread|Executor" src/main/java/com/customcontentengine/builtin src/main/java/com/customcontentengine/domain src/main/java/com/customcontentengine/application

rg "Economy|Quest|Combat|Teleport|GUI|Menu|Script" src/main/java/com/customcontentengine/internalapi src/main/java/com/customcontentengine/domain
```

---

## Consequences

### Positive

- Architectural rules become enforceable.
- Regression is caught early.
- Reviews become more objective.
- Scope boundaries stay visible in CI.

### Negative

- Additional test maintenance.
- Some legitimate changes may require updating tests and ADRs.
- The build may fail for architecture reasons, not only functional failures.

---

## Guardrails

- Fitness tests must run with the normal test suite or an explicit architecture verification task.
- A failing architecture rule must not be ignored without ADR.
- Every new stable-core contract must add or update at least one fitness function.
- Every new extension point must include a rule preventing platform leakage.


# ADR-0012 — Periodic Scheduler Abstraction

**Status:** Accepted  
**Date:** 2026-07-05  
**Project:** CustomContent Engine  
**Supersedes:** N/A

---

## Context

The project currently has the `SchedulerPort` interface, which exposes only the following method:

```java
void runOnRegion(WorldPosition position, Runnable task);
```

This abstraction is sufficient for one‑off executions tied to a region, such as rescheduling `MechanicResult.Partial` continuations, and is aligned with the architectural goal of Folia support.

However, the `MiningProcessingDriver` (located in `adapter.bukkit`) directly uses the Bukkit scheduler via `plugin.getServer().getScheduler().runTaskTimer(...)` to periodically process active mining sessions.

This creates three problems:

1. **Hexagonal architecture violation**: The periodic scheduling is coupled to the concrete Bukkit implementation rather than going through a port.
2. **Testing difficulty**: It is impossible to test `MiningProcessingDriver` in isolation without a real server or heavy Bukkit mocks.
3. **Folia barrier**: Although the driver executes tasks that internally call `SchedulerPort.runOnRegion` (which is Folia‑safe), the timer itself is not abstracted. In Folia, the scheduling API differs, and migration would require localised changes to the adapter, which are currently not possible due to the direct coupling.

The current MVP scope does not require periodic scheduling in other contexts, but the need already exists for mining and may arise for future mechanics (e.g., particle effects, block regeneration, etc.).

---

## Decision

Introduce a new dedicated port for periodic scheduling, separate from the existing `SchedulerPort`, to maintain separation of concerns.

### Proposed Port

```java
package com.customcontentengine.port;

public interface PeriodicSchedulerPort {
    /**
     * Schedules a task to run repeatedly at a fixed rate.
     *
     * @param task           the task to execute (must not be blocking)
     * @param initialDelay   delay in server ticks before first execution
     * @param period         interval in server ticks between executions
     * @return a handle that can be used to cancel the task
     */
    ScheduledTask scheduleAtFixedRate(Runnable task, long initialDelay, long period);

    /**
     * Represents a scheduled task that can be cancelled.
     */
    interface ScheduledTask {
        void cancel();
        boolean isCancelled();
    }
}
```

### Adapters

- **PaperPeriodicSchedulerAdapter**: Implements the interface using `BukkitScheduler.runTaskTimer(plugin, task, initialDelay, period)` and adapts `BukkitTask` to `ScheduledTask`.  
  It **must** use `runTaskTimer` (synchronous), not `runTaskTimerAsynchronously`.

- **FoliaPeriodicSchedulerAdapter** (future): Can use the Folia scheduling API when available and validated, without changing the driver or application layer.

### Impact on `MiningProcessingDriver`

The driver will be refactored to accept `PeriodicSchedulerPort` in its constructor instead of using `Plugin` directly:

```java
public final class MiningProcessingDriver {
    private final PeriodicSchedulerPort scheduler;
    private final MiningRuntimeProcessor processor;
    private final LongSupplier clockMillis;
    private final int maxSessionsPerRun;
    private final long periodTicks;
    private PeriodicSchedulerPort.ScheduledTask task;

    public void start() {
        if (task != null) return;
        task = scheduler.scheduleAtFixedRate(
            () -> processor.processActiveSessions(clockMillis.getAsLong(), maxSessionsPerRun),
            periodTicks,
            periodTicks
        );
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }
}
```

`CustomContentPlugin` (bootstrap) will inject `PaperPeriodicSchedulerAdapter` into the driver, using the same `Plugin` instance internally, but keeping the driver free from direct Bukkit dependencies.

### Justification for a Separate Port

- **Separation of concerns**: `SchedulerPort` handles immediate, regional execution (`runOnRegion`). `PeriodicSchedulerPort` handles repetitive scheduling. Future evolutions in one do not affect the other.
- **Folia coherence**: In Folia, `runOnRegion` requires a `WorldPosition`, while periodic scheduling typically operates on the global tick (or may be regionalised later). Keeping them separate simplifies adaptation.
- **Simplicity**: The current `SchedulerPort` contract remains unchanged, reducing the scope of the change and the risk of regressions in existing functionality (e.g., `area_break`).

---

## Consequences

### Positive

- **Testability**: `MiningProcessingDriver` can be tested with a `FakePeriodicSchedulerPort` that manually controls ticks.
- **Architectural compliance**: Scheduling now respects dependency inversion, with platform logic confined to adapters.
- **Folia readiness**: Migration to Folia becomes a matter of implementing a new adapter, without altering the driver or application.
- **Reusability**: Future mechanics that require periodic tasks can use the same port.

### Negative

- **New abstraction**: Adds a small overhead of complexity and one more contract to manage.
- **Bootstrap change**: `CustomContentPlugin` must be adjusted to instantiate and inject the new adapter.
- **Documentation**: The new port must be documented in `ARCHITECTURE_GUARDRAILS.md` and `PROJECT_SCOPE.md` as part of the allowed ports.

---

## Alternatives Considered

| Alternative | Decision | Reason |
| ----------- | -------- | ------ |
| **Extend `SchedulerPort` with `scheduleAtFixedRate`** | Rejected | `SchedulerPort` was designed for immediate regional execution. Adding periodic scheduling would mix responsibilities and confuse the port's purpose. |
| **Keep using `BukkitScheduler` directly** | Rejected | Violates architecture guardrails (ADR-0003, `ARCHITECTURE_GUARDRAILS.md`) and hampers testing and Folia migration. |
| **Use `ScheduledExecutorService` with `runOnRegion`** | Rejected | `ScheduledExecutorService` runs on separate threads, which is unsafe for game state access without proper scheduling. Additionally, it does not resolve the Bukkit coupling. |
| **Create a generic `TaskSchedulerPort` with multiple methods** | Rejected | This would be a broader solution than currently needed. We prefer to start with a specific, evolvable port. |

---

## Scope Impact

### In Scope

- Creation of the `PeriodicSchedulerPort` interface and `ScheduledTask` nested interface.
- Implementation of the `PaperPeriodicSchedulerAdapter`.
- Refactoring of `MiningProcessingDriver` to use the new port.
- Adjustment of `CustomContentPlugin` for dependency injection.
- Unit tests for the driver using a `FakePeriodicSchedulerPort`.

### Out of Scope

- Implementation of the Folia adapter (will be handled in a future ADR or spike).
- Changes to the existing `SchedulerPort`.
- Changes to mechanics, domain, or YAML.
- Periodic scheduling for purposes other than mining.

---

## Guardrails and Constraints

- The new port **must not** expose `runAsync` or `runOnEntity` methods. Scheduling must remain synchronous with the server tick.
- The Paper adapter must use `runTaskTimer`, **not** `runTaskTimerAsynchronously`.
- The scheduled task (`Runnable`) **must not** perform long‑running or blocking operations.
- Period and delay values are in **server ticks** (20 ticks = 1 second).
- `MiningProcessingDriver` remains in the `adapter` layer (as it is an infrastructure orchestrator), but now depends on a `port`, which is permitted.
- The session processing logic (`MiningRuntimeProcessor.processActiveSessions`) continues to use `SchedulerPort.runOnRegion` for each session, preserving regional safety.

---

## Acceptance Criteria

- [x] `PeriodicSchedulerPort` defined in the `port` package.
- [x] `PaperPeriodicSchedulerAdapter` implemented and integrated into the bootstrap.
- [x] `MiningProcessingDriver` refactored to use the new port.
- [x] Unit tests for the driver with `FakePeriodicSchedulerPort` covering:
  - Start and stop of scheduling.
  - Execution of the task at the configured period.
  - Task cancellation.
- [ ] No change in current mining behaviour on a live Paper server.
- [x] Architecture Fitness (`ArchitectureFitnessTest`) updated or verified to ensure the driver no longer depends on Bukkit directly.
- [x] Documentation updated (`ARCHITECTURE_GUARDRAILS.md` and `PROJECT_SCOPE.md`) to mention the new port and its purpose.

---

## Related Documents

- ADR-0003 — Conservative Evolvable Core
- ADR-0009 — Custom Mining Model
- `docs/ARCHITECTURE_GUARDRAILS.md`
- `docs/PROJECT_SCOPE.md`
- `src/main/java/com/customcontentengine/port/SchedulerPort.java`
- `src/main/java/com/customcontentengine/adapter/bukkit/MiningProcessingDriver.java`

---

## Implementation Status (2026-07-06)

✅ **Implemented** — all in-scope items from the ADR are complete and verified via `./gradlew test` (unit tests + Architecture Fitness pass).

### Delivered

- **Port:** `src/main/java/com/customcontentengine/port/PeriodicSchedulerPort.java`
  - `scheduleAtFixedRate(Runnable, long, long)` returning a cancellable `ScheduledTask`.
  - No `runAsync` / `runOnEntity` methods, per guardrails.
- **Paper adapter:** `src/main/java/com/customcontentengine/adapter/platform/PaperPeriodicSchedulerAdapter.java`
  - Uses `BukkitScheduler.runTaskTimer` (synchronous); adapts `BukkitTask` to `ScheduledTask`.
- **Driver refactor:** `src/main/java/com/customcontentengine/adapter/bukkit/MiningProcessingDriver.java`
  - Now depends on `PeriodicSchedulerPort` instead of `Plugin`/`BukkitScheduler`.
  - `start()`/`stop()` semantics preserved (idempotent start, cancel on stop).
- **Bootstrap injection:** `src/main/java/com/customcontentengine/bootstrap/CustomContentPlugin.java`
  - Instantiates `PaperPeriodicSchedulerAdapter` and injects it into `MiningProcessingDriver`.
- **Unit tests:** `src/test/java/com/customcontentengine/adapter/bukkit/MiningProcessingDriverTest.java`
  - `FakePeriodicSchedulerPort` + Mockito `MiningRuntimeProcessor`.
  - Covers start/stop, idempotent start, task cancellation, task execution, and validation of arguments.
- **Documentation:**
  - `docs/ARCHITECTURE_GUARDRAILS.md` §16 (Scheduler Rules) — `PeriodicSchedulerPort` added to allowed MVP ports.
  - `docs/PROJECT_SCOPE.md` — listed under `port/` and `adapter/platform/`, and described in §9.5.

### Acceptance Criteria

All criteria are checked except the live Paper-server behavioural validation, which requires a runtime environment (out of local CI scope).

### Next Steps

1. Validate in a Paper environment before considering Folia extension.
2. Implement `FoliaPeriodicSchedulerAdapter` in a future ADR/spike (out of scope here).

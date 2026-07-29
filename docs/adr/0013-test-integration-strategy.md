---

# ADR 0013 — Test Integration Strategy

**Status:** Accepted  
**Date:** 2026-07-16 (updated)  
**Project:** CustomContent Engine  
**Supersedes:** N/A

---

## Context

CustomContent Engine has a robust suite of **unit tests** covering the domain, application, and adapter layers with mocks. The architecture fitness functions (ArchUnit) are also well-established.

However, the **integration test layer** (Paper environment) is currently underdeveloped, consisting only of a single smoke test (`PaperPluginSmokeIntegrationTest`) that verifies plugin loading. The complex runtime behaviors—such as custom mining sessions, the `MechanicExecutor` pipeline, `SchedulerPort` interactions, PDC persistence, and the BFS algorithm in `vein_miner`—are not validated against a real Paper server.

This creates a significant risk:
- Platform-specific bugs (e.g., `PersistentDataContainer` quirks, scheduler tick accuracy, event cancellation semantics) are not caught until manual testing.
- Folia compatibility cannot be properly validated.
- Future official mechanics may introduce regressions that unit tests cannot detect.

A standardized, phased strategy for integration testing is required to ensure the plugin is production-ready and maintainable.

---

## Decision

CustomContent Engine will adopt a **phased integration test strategy** as defined in `docs/TEST_INTEGRATION_PLAN.md`.

The following architectural mandates are established:

### 1. Integration Test Infrastructure (Mandatory)

- All integration tests must extend a common base class: `BasePaperIntegrationTest`.
- This base class will manage the Paper server lifecycle (start/stop), provide utility methods (e.g., `giveItem`, `breakBlock`, `awaitCondition`), and enforce state cleanup between tests.

### 2. Dependency Injection for Testing

- A dedicated test plugin class (`TestCustomContentPlugin`) will be created, extending `CustomContentPlugin`.
- This test plugin will expose setter methods (e.g., `setProtectionPort`, `setToolWearPort`) to allow overriding production dependencies with fake/mock implementations during test execution.
- This ensures that integration tests can simulate edge cases (e.g., a `ProtectionPort` that blocks specific blocks) without modifying production code.

### 3. Phased Implementation Roadmap

The integration test suite will be built in four phases, progressively increasing coverage:

- **Phase 0** (Foundation): Expand the smoke test to validate the loaded `DefinitionRegistry` (YAML parsing) and basic PDC operations.
- **Phase 1** (Mining Core): Validate the complete custom mining lifecycle (session start, absolute-time progress, completion, idempotency, and rollback).
- **Phase 2** (Basic Mechanics): Validate `area_break` and `block_transform` mechanics, ensuring they interact correctly with the `BlockBreakAdapter` and the real world.
- **Phase 3** (Advanced Mechanics & Durability): Validate the `vein_miner` BFS algorithm with real enchantments (Fortune/Silk Touch), per-block durability wear, and the player toggle (`/veinminertoggle`).
- **Phase 4** (Progression & Protection): Validate tier eligibility (`required_tier`) and the `ProtectionPort` integration (using a fake adapter to block specific world positions).

### 4. Mandatory Integration Tests for New Official Mechanics

Any new official mechanic accepted via ADR **must** include at least one Paper integration test that validates its core flow (execution, result, and side effects on the world/inventory) before the ADR is considered fully implemented.

### 5. CI Integration

The integration tests will run in GitHub Actions as part of the `./gradlew integrationTest --no-daemon` task.
- A timeout of **10 minutes** per suite is mandated.
- Integration test failures will block the PR from being merged.
- The full suite will run on every push to `main` and on pull requests targeting `main`.

### 6. Performance Optimization Strategy (Added 2026-07-16)

With all phases implemented, the full integration suite execution time reached approximately 15 minutes. To reduce this without compromising fidelity, the following optimizations are approved:

**6.1. Server Reuse Across Test Classes**
- The Paper server will be started once per integration test suite execution (lazy `@BeforeAll` initialization and a JVM shutdown hook).
- State is cleaned between tests via commands or by using distinct coordinates per test.
- This eliminates the ~60‑90s startup overhead for each test class.

**6.2. JVM Flags for Fast Startup**
- The Paper process will be launched with `-XX:TieredStopAtLevel=1` and `-XX:+TieredCompilation`.
- This prioritizes C1 compilation (fast startup) over C2 (peak performance), which is safe for functional tests.

**6.3. Controlled Parallelization with `maxParallelForks`**
- Gradle's `integrationTest` task will use `maxParallelForks` set to `min(2, availableProcessors / 2)` in CI.
- Each fork uses isolated temporary directories and free ports, avoiding resource conflicts.

**6.4. Test Profiles: Smoke vs. Complete**
- **`integrationTestSmoke`**: Runs only critical tests (Phases 0–1, tagged with `@Tag("smoke")` or `@Tag("mining")`). Executed on every PR.
- **`integrationTest`**: Runs the full suite (Phases 0–4). Executed on merges to `main` or manually.

---

## Consequences

### Positive

- **Catch Platform-Specific Bugs Early**: Tests will catch Paper/PDC/Scheduler issues that unit tests cannot.
- **Folia Readiness**: The test harness provides a foundation for running Folia-specific tests in the future.
- **Confidence in Official Mechanics**: New mechanics will have proven behavior in a real server environment.
- **Regression Safety**: Critical flows are automatically verified, reducing manual QA overhead.
- **Faster CI Feedback**: PRs now run ~3‑5 minutes instead of ~15 minutes, significantly improving developer productivity.

### Negative

- **Longer CI Times** (mitigated): Full suite still adds time, but smoke tests are fast.
- **Increased Maintenance**: The test infrastructure and state cleanup logic must be maintained alongside production code.
- **Resource Consumption**: Parallelization may increase memory/CPU usage on CI runners (monitoring recommended).

---

## Scope Impact

### In Scope

- Creation of `BasePaperIntegrationTest`.
- Creation of `TestCustomContentPlugin`.
- Implementation of Phases 0 through 4 as outlined in `docs/TEST_INTEGRATION_PLAN.md`.
- Updates to `.github/workflows/build-test.yml` to handle integration test artifacts and timeouts.
- Mandatory integration tests for all future official mechanics.
- **Implementation of performance optimizations** (server reuse, JVM flags, parallelization, smoke profiles) as defined above.

### Out of Scope

- JMH performance benchmarks (already handled by spikes).
- Unit test coverage (already robust).
- Folia-specific integration tests (deferred until the Folia adapter is formally implemented).
- MockBukkit or any substitute that does not replicate real Paper behavior.

---

## Alternatives Considered

### Keep Only Unit Tests (Status Quo)

**Rejected.** Real platform behaviors cannot be simulated with mocks (e.g., PDC chunk serialization, event order, scheduler tick accuracy). This approach would lead to bugs surfacing only in production.

### Use an Embedded Server (e.g., MockBukkit)

**Rejected.** MockBukkit does not fully replicate Paper's behavior, especially regarding PDC and Folia threading. Real Paper integration tests are the only reliable way to validate the plugin.

### Ad-hoc Integration Tests Without a Base Class

**Rejected.** Without a standardized base, each test would duplicate server startup/cleanup logic, leading to flaky tests and higher maintenance costs.

### Run All Tests Sequentially Without Optimization

**Rejected.** 15-minute CI times degrade developer experience. The adopted optimizations maintain fidelity while improving speed.

---

## Related Documents

- `docs/TEST_INTEGRATION_PLAN.md` — Detailed phased implementation plan and optimization strategy.
- `docs/AGENTS.md` — Updated to reference this ADR and new execution profiles.
- `src/integrationTest/java/com/customcontentengine/integration/PaperPluginSmokeIntegrationTest.java` — Existing smoke test.

---

**End of ADR-0013**
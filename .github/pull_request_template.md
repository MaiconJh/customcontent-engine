# .github/PULL_REQUEST_TEMPLATE.md

## Description

<!-- Provide a clear and concise description of what this PR does. -->
<!-- Reference the issue(s) it resolves, if applicable. -->
Fixes #(issue)

## Type of Change

<!-- Please delete options that are not relevant. -->

- [ ] Bug fix (non-breaking change which fixes an issue)
- [ ] New feature (non-breaking change which adds functionality)
- [ ] Breaking change (fix or feature that would cause existing functionality to not work as expected)
- [ ] Documentation update
- [ ] Performance improvement
- [ ] Refactor (no functional changes)
- [ ] Test coverage improvement

---

## How Has This Been Tested?

<!-- Describe the tests that you ran to verify your changes. -->
<!-- Provide instructions so we can reproduce. -->
<!-- Include details about your test environment (OS, Java version, Paper version). -->

- [ ] Unit tests (`./gradlew test`)
- [ ] Integration tests (`./gradlew integrationTest` or `integrationTestSmoke`)
- [ ] Manual testing on a Paper server (describe steps)

---

## Checklist

- [ ] My code follows the project's coding style and architecture guardrails.
- [ ] I have performed a self-review of my own code.
- [ ] I have commented my code, particularly in hard-to-understand areas.
- [ ] I have made corresponding changes to the documentation (e.g., `README.md`, `PROJECT_SCOPE.md`, `ARCHITECTURE_GUARDRAILS.md`).
- [ ] I have added tests that prove my fix is effective or that my feature works.
- [ ] New and existing unit and integration tests pass locally and in CI (`./gradlew test build integrationTest`).
- [ ] Any new official mechanics include at least one Paper integration test (per ADR-0013).
- [ ] I have updated `TEST_INTEGRATION_PLAN.md` if this PR implements a new phase or significant test coverage.
- [ ] I have updated the `CHANGELOG.md` (or the changelog section of `TEST_INTEGRATION_PLAN.md` if applicable).
- [ ] I have verified that no forbidden dependencies (Bukkit/Paper/NMS) were introduced in `domain`, `internalapi`, or `builtin` layers.
- [ ] I have verified that `application` does not depend on `adapter` or Bukkit/Paper directly.
- [ ] I have confirmed that any new `Capability` or extension point is justified and follows the ADR process.
- [ ] I have confirmed that no `runAsync`, `runOnEntity`, or `SchedulerAccess` was introduced.

---

## Related ADRs / Milestones

<!-- If this PR relates to an ADR, list it here. -->
- ADR-0013 (if this PR implements integration tests)
- (Other ADRs)

---

## Breaking Changes

<!-- If this PR introduces breaking changes, describe them here. -->
<!-- Include migration steps if needed. -->

---

## Additional Context

<!-- Add any other context about the PR here. -->

---

## CI Validation

- [ ] GitHub Actions build passed (including integration tests).
- [ ] Architecture Fitness functions passed.

---

## Final Checklist (Maintainer Review)

- [ ] PR is approved by at least one maintainer.
- [ ] All discussions are resolved.
- [ ] The branch is up-to-date with `main` (no merge conflicts).
- [ ] All CI checks are green.

---

**Thank you for your contribution! 🚀**
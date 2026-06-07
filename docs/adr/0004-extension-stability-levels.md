# ADR-0004 — Extension Stability Levels

Status: Proposed  
Date: 2026-06-07  
Project: CustomContent Engine

---

## Context

The project has internal contracts such as `internalapi`, official mechanics under `builtin`, and potential future extension points. Without explicit stability levels, users and contributors may mistake internal or official code for stable public API.

This can accidentally freeze implementation details and make future refactoring harder.

---

## Decision

All APIs, modules, contracts, and extension points must be classified into explicit stability levels.

The stability levels are:

1. Internal.
2. Experimental.
3. Official.
4. Stable.
5. Deprecated.

---

## Stability Levels

### Internal

Internal code is used by the engine implementation and may change at any time.

Rules:

- No compatibility promise.
- Not intended for third-party use.
- May be refactored without migration path.
- Current `internalapi` remains internal during the MVP despite its name.

### Experimental

Experimental code exists to validate ideas.

Rules:

- No compatibility promise.
- Must be documented as experimental.
- Must not be required by stable core.
- May be removed, renamed, or redesigned.

### Official

Official code is maintained by the project and may ship with the plugin.

Rules:

- Official does not mean stable API.
- Official modules must not become hidden core dependencies.
- Official modules may depend on stable/internal contracts according to declared boundaries.

### Stable

Stable code is a compatibility promise.

Rules:

- Must be explicitly declared stable.
- Breaking changes require ADR.
- Public API stability must follow versioning policy.
- Must have tests covering compatibility-sensitive behavior.

### Deprecated

Deprecated code is scheduled for removal or replacement.

Rules:

- Must include reason.
- Must include replacement or migration path when externally used.
- Must include removal target when possible.

---

## Consequences

### Positive

- Prevents accidental public API.
- Allows experiments without long-term compatibility burden.
- Clarifies difference between official modules and stable core.
- Supports future public API without sacrificing current implementation freedom.

### Negative

- Requires documentation discipline.
- Contributors must classify new code.
- Some users may need to understand the difference between official and stable.

---

## Guardrails

- No class or package becomes stable by accident.
- Public API must be explicitly declared.
- Experimental APIs must be labeled.
- Official modules must not be imported by stable core.
- Stable APIs require ADR before breaking changes.

---

## Package Meaning Recommendation

```text
internalapi      = internal MVP contracts, unstable unless reclassified
experimentalapi  = candidate extension contracts, no compatibility promise
publicapi        = stable third-party API, introduced only by ADR
builtin          = official modules, not stable core by default
experimental     = incubating modules/contracts
devtools         = debug/profiling/testing tools, disabled by default
```

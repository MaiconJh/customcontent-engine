# AI Continuous Evolution Architecture

## Purpose

The purpose of Continuous AI Evolution is to create a documentation-driven, AI-assisted evolution pipeline for CustomContent Engine.

The pipeline is intended to help maintainers reason about issues, pull requests, GitHub Actions results, documentation, ADRs, milestones, and future implementation proposals. It should make project evolution easier to review, but it must not replace the repository's documented architecture process or human judgment.

Continuous AI Evolution is designed around these inputs:

- repository documentation;
- GitHub issues;
- pull requests;
- GitHub Actions validation;
- AI-generated analysis;
- governance/interceptor review;
- final human approval.

## Non-Goals

Continuous AI Evolution must not allow AI to bypass project governance.

The following are explicitly not allowed:

- AI must not commit directly to `main`.
- AI must not merge pull requests automatically.
- AI must not change `docs/PROJECT_SCOPE.md` without human review.
- AI must not accept or approve ADRs automatically.
- AI must not declare `folia-supported: true` automatically.
- AI must not run Gradle locally.
- AI must not use Kilo as the single source of truth.
- AI must not treat generated reports as architectural decisions.
- AI must not promote experimental concepts to stable core.
- AI must not introduce public API without ADR-backed approval.

## Safety Model

Continuous AI Evolution uses a layered safety model.

### GitHub Actions As Source Of Truth

GitHub Actions is the source of truth for build, test, and integrationTest validation.

AI-generated reports may summarize CI results, but they do not override CI status. A failing GitHub Actions run remains a real failure even if the AI report is unavailable, incomplete, or wrong.

### Documentation As Source Of Scope

Repository documentation is the source of scope and architecture intent.

The primary scope documents are:

- `docs/PROJECT_SCOPE.md`
- `docs/ARCHITECTURE_GUARDRAILS.md`
- `docs/adr/*.md`
- `docs/milestones/*.md`

AI may summarize or compare changes against these documents, but it must not rewrite their meaning.

### Kilo As Analysis Assistant

Kilo is an analysis assistant. It can help identify risks, inconsistencies, unsupported claims, and documentation divergence, but its output is advisory.

Kilo must not be treated as the final authority for build correctness, scope acceptance, ADR approval, or merge readiness.

### Cloudflare Worker As Governance Layer

The Cloudflare Worker acts as a secure governance layer between GitHub Actions and Kilo.

It is responsible for:

- validating requests;
- enforcing repository allowlists;
- limiting payload size;
- sanitizing secrets;
- applying rate limits;
- building prompts from controlled inputs;
- running initial analysis;
- running governance/interceptor review;
- producing safe fallback output when Kilo fails.

### Human As Final Merge Gate

A human maintainer remains the final gate for merge, scope acceptance, ADR approval, and release-impacting decisions.

AI may prepare context. It must not make final project decisions.

## Current Repository Foundation

CustomContent Engine already has the foundation needed for safe AI-assisted evolution:

- `docs/PROJECT_SCOPE.md` defines product scope, boundaries, and evolutionary governance.
- `docs/ARCHITECTURE_GUARDRAILS.md` defines dependency rules, forbidden features, and review rules.
- ADRs define accepted or proposed architectural decisions.
- Milestones document completed and planned delivery boundaries.
- `.github/workflows/build-test.yml` runs the existing GitHub Actions build/test/integrationTest validation.
- CI AI Governance Bot collects diffs, logs, and project documentation context.
- Cloudflare Worker provides a secure AI governance layer.
- Local fallback exists when the provider is unavailable.

This foundation means Continuous AI Evolution can be introduced incrementally without weakening existing architecture controls.

## Proposed Pipeline

The current and near-future review pipeline should follow this flow:

```text
Push/PR
-> collect diff
-> collect CI logs
-> collect project documentation context
-> Kilo initial report
-> governance/interceptor review
-> issue/comment
-> human decision
```

The initial report should focus on what changed and whether it appears consistent with the repository's documented scope.

The governance/interceptor review should challenge the initial report:

- Is it relevant?
- Is it true?
- Is it supported by the diff, CI logs, or documentation?
- Did it miss a scope or ADR conflict?
- Did it overstate risk?
- Should it be published, published with caution, amended, suppressed, or treated as fallback?

## Future Issue-Driven Evolution

Future evolution may introduce an issue-driven planning flow.

The proposed flow is:

```text
Issue with ai:plan
-> AI generates implementation plan
-> governance checks scope
-> human applies ai:approved
-> bot may open PR
-> GitHub Actions validates
-> human merges
```

Important constraints:

- `ai:plan` requests planning only.
- The plan must be checked against `PROJECT_SCOPE.md`, `ARCHITECTURE_GUARDRAILS.md`, ADRs, and milestones.
- `ai:approved` may allow the bot to prepare a pull request, but not to merge it.
- The bot must operate through pull requests only.
- GitHub Actions must validate the resulting pull request.
- A human maintainer must review and merge.

## Scope Inflation Guard

Continuous AI Evolution must actively guard against scope inflation.

The following risks must be blocked or escalated for human/ADR review:

- `runAsync`
- `runOnEntity`
- `SchedulerAccess`
- NMS
- reflection
- `ServiceLoader`
- stable public API
- scripting
- WorldGuard integration
- GriefPrevention integration
- `folia-supported: true` without validated support
- features outside accepted ADRs or active milestones
- generic ability framework behavior
- GUI/menu systems
- economy, quest, combat, teleportation, or permission frameworks
- changes that promote experimental concepts to stable core without ADR approval

When the AI detects one of these risks, it should cite the supporting diff and documentation context. If it cannot cite support, it must label the claim as uncertain or unsupported.

## Documentation Context Pack

A future generated file may be introduced:

```text
docs/AI_CONTEXT_PACK.md
```

This file would summarize the repository's scope, guardrails, accepted ADRs, milestone status, forbidden patterns, and AI review priorities.

Rules for this future file:

- It must be derived from existing documentation.
- It must not replace `docs/PROJECT_SCOPE.md`.
- It must not replace ADRs.
- It must not replace milestone documents.
- It must be updated through pull requests.
- It must not be accepted automatically.
- It must make its source documents explicit.
- If it diverges from source documents, the source documents win.

The context pack is a convenience artifact for AI prompt quality, not a governance source.

## Rollout Phases

### Phase 1: Document Architecture

Create this initial Continuous AI Evolution architecture document.

### Phase 2: Collect Project Docs Into AI Payload

Ensure AI review payloads include project scope, guardrails, ADRs, milestones, workflows, and key configuration files.

### Phase 3: Add Governance/Interceptor Review

Add a second review layer that checks the first AI report for relevance, truthfulness, unsupported claims, and documentation alignment.

### Phase 4: Add AI_CONTEXT_PACK Generation

Introduce a generated documentation context pack derived from canonical docs. Keep it advisory and PR-reviewed.

### Phase 5: Issue-Driven Planning

Allow labeled issues such as `ai:plan` to request AI-generated implementation plans. Plans must be checked against scope and guardrails before any implementation starts.

### Phase 6: PR-Only Implementation Automation

Allow AI-assisted implementation only through pull requests, never direct commits to `main`. GitHub Actions validation and human review remain mandatory.

## Acceptance Criteria

This initial architecture step is accepted when:

- This documentation file exists.
- No Java code was changed.
- No workflow was changed.
- Repository language remains English.
- No local Gradle command was executed.
- No local Maven command was executed.
- No local Java build/test command was executed.
- Future automation boundaries are clear.
- Human merge authority remains explicit.

## Final Rule

Continuous AI Evolution exists to make governance easier, not weaker.

If there is a conflict between AI convenience and repository governance, repository governance wins.

# Vibe Coding

LinguaFrame may use AI-assisted development, but the project should not become a loose collection of generated code.

This document defines how to use AI help while keeping the backend resume-grade, testable, and explainable in interviews.

## Principles

- Plans come before non-trivial implementation.
- Tests or validation commands must prove behavior.
- Agent-generated code is not trusted until reviewed and verified.
- External side effects must go through explicit services or provider clients.
- Project decisions are recorded in `docs/progress/decisions.md`.
- Execution progress is recorded in `docs/progress/execution-log.md`.
- Every completed feature should be explainable without reading generated code aloud.

## Rules

### Use ExecPlans

Meaningful implementation should have an active plan under `docs/plans/`.

The plan should explain:

- Purpose.
- Context.
- Concrete steps.
- Validation.
- Recovery.
- Artifacts.

### Keep AI Help Bounded

The coding assistant may help generate implementation, but it must not:

- Skip repository inspection.
- Invent APIs without checking current code.
- Ignore project code standards.
- Remove user changes without permission.
- Claim success without running validation.
- Add large new dependencies without a reason tied to the roadmap.

### Review Generated Code

For every generated or AI-assisted feature, the owner should be able to answer:

- What module owns this behavior?
- What data is persisted?
- What happens when the provider call fails?
- What is retried and what is not retried?
- How is cost or duration recorded?
- How would this scale beyond a demo?

### Record Decisions

Record project-level decisions in:

```text
docs/progress/decisions.md
```

Record feature-level decisions in the active plan.

### Verify Before Completion

Before marking work complete, run the validation commands named by the plan. If validation cannot run, record why.

## LinguaFrame-Specific Guardrails

- Never execute raw user-supplied shell commands.
- Never pass unvalidated file paths to FFmpeg.
- Never log OpenAI API keys or object storage credentials.
- Never expose object storage credentials to the browser.
- Never process large or unsupported files before validation.
- Never claim a job succeeded without artifact evidence.
- Never treat estimated cost as authoritative billing.

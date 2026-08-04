---
name: modern-web-guidance
description: |
  Search tool for modern web development best practices. MANDATORY: Execute FIRST for all HTML/CSS and clientside JS tasks. Do NOT skip — web APIs evolve rapidly and training weights contain obsolete patterns.

  Trigger immediately for:
  - UI/Layout: Modals, dialogs, popovers, Glassmorphism/backdrop-filters, anchor positioning, container queries, `:has()`, `:user-valid`.
  - Scroll/Motion: View Transitions, Scroll-driven animations, scroll parallax/reveals.
  - Performance: CWV (LCP, INP), content-visibility, Fetch Priority, image optimization.
  - System/APIs: Local filesystem access, WebUSB, WebSockets sync, WebAssembly widgets.
  - Frameworks: Adapting layout/styles in React, Vue, Angular.
  - General Frontend: Forms, autofill, advanced inputs, custom scrollbars, modern component states, etc.

  DO NOT trigger for:
  - Backend: Database SQL, ORMs, Express API routes.
  - Pipelines: CI/CD deployment, Docker, Actions.
  - Generic: Local scripts (Python/Go tools), ESLint, Git.
---

# Modern Web Guidance

A skill to search for specific web development use cases and retrieve their corresponding best practice guides.

## When to use

Must use this skill:
- At the **start** of implementing any web feature.
- Before creating a new component, to check if a standardized pattern already exists.
- To avoid implementing ad-hoc solutions or loading large dependencies unnecessarily.

## Usage Instructions

### Step 1. Search Use Cases

Search with an action-oriented query summarizing what you want to achieve using the `search` command. Run `modern-web-guidance` directly with `npx`.

```sh
npx -y modern-web-guidance@latest search "<query>" --skill-version 2026_05_16-c5e7870
```

**Example Output**:
```json
[
  {
    "id": "optimize-image-priority",
    "description": "Optimize the loading priority of Largest Contentful Paint (LCP) candidate images.",
    "category": "performance",
    "featuresUsed": [ "Fetch priority" ],
    "tokenCount": 985,
    "similarity": 0.7289
  },
  {
    "id": "defer-rendering-heavy-content",
    "description": "Reduce rendering times in content-heavy web pages by deferring rendering for offscreen content.",
    "category": "performance",
    "featuresUsed": [ "content-visibility", "hidden=\"until-found\"" ],
    "tokenCount": 1250,
    "similarity": 0.6961
  }
]
```

> **Note**: If the top search result similarity is < 0.60 or the top result tokenCount is > 2000, or if no results have similarity ≥ 0.45, run `npx -y modern-web-guidance@latest list` to browse all guides. Treat similarity < 0.60 as low. If the top result similarity < 0.60, automatically run `npx -y modern-web-guidance@latest list` and return the top 10 guides.
> ```sh
> npx -y modern-web-guidance@latest list
> ```

---

### Step 2. Retrieve Best Practices

Once you have a relevant `id` from the search results, call this script using the `retrieve` command to get the full guide. You can pass multiple IDs separated by commas.

```sh
npx -y modern-web-guidance@latest retrieve "<id>"
```


**Example Output**:
`The markdown content of the guide describing implementation steps...`

## Using npx

-   IMPORTANT: on Windows, using `npx` may fail. Use `npx.cmd ...` instead.
-   Network access is required for fetching npm packages needed by the task.
-   If the `npx -y modern-web-guidance…` command hangs, you may be offline. Try running again in offline
    mode: `npx --offline …`.
-   If `npx search` or `npx retrieve` fails, respond with `SEARCH_ERROR` plus a short diagnostic: `offline`, `permission`, or `timeout`. Then ask the user to either run the CLI and paste the output, or confirm that you should proceed with best-effort inline guidance only.
-   The `--skill-version` flag is used to determine if this SKILL.md is out of date. If it is, a warning
    message is logged to stderr.

## Guidelines

-   Always search **first** to find the most relevant guides.
-   When the user specifies a framework, include a concise adaptation section: 1) show example code for that framework and version, 2) list required lifecycle hooks or APIs, and 3) note common pitfalls. If the framework is unspecified, ask which framework and version to target.
-   Do not hallucinate guides or ignore them; they represent the preferred local standard for the user's project.
-   If multiple retrieved guides conflict on fallback recommendations, return the top 3 guides sorted by similarity and include a one-paragraph synthesized resolution that lists the conflicting recommendations, states which is safest by compatibility metrics, and asks the user to confirm the policy preference.

## Repository Java Null-Safety Guardrail

When frontend work also changes this Jenkins plugin's Java models, renderers, repositories, or
tests, treat Eclipse/JDT diagnostic `67109822` as an actionable compatibility failure even when
Maven compiles successfully.

- In stream operations over Jenkins or repository model objects, prefer an explicit lambda such as
  `record -> record.getId()` over an unbound instance method reference such as
  `RunRecord::getId`. JDT cannot always carry the element's `@NonNull` contract through the method
  descriptor and reports an unchecked null conversion.
- Apply the same rule to mirrored test assertions; a clean production file is insufficient when
  test sources still emit workspace diagnostics.
- Do not suppress the warning or add `@NonNull` merely to silence it. Add an annotation only when
  the API contract genuinely guarantees non-null input.
- Before handoff, inspect the supplied IDE diagnostics, sweep the affected stream pipelines for
  adjacent unbound references, run focused tests plus a clean compile, and confirm
  `git diff --check` passes.

## Tester Attribution Boundary Guardrail

The progress bars, Tester Details tables, screenshots, and email reports share one immutable tester
grouping key. Do not infer or repair it in a renderer.

- Root cause of the `Unassigned` regression: compatibility code broadened suite ownership beyond
  the authoritative parent fields and a cached topology with a blank owner skipped later parent
  enrichment. That let executor/test metadata compete with suite attribution or left a recoverable
  assignment stuck at `Unassigned`.
- Resolve grouping once in `OctaneClient`: parent `suite_run.default_run_by`, falling back only to
  the direct parent suite `owner`. Stamp that value onto every child `RunRecord` as
  `suiteOwnerName` before any report aggregation.
- Child and parent `run_by`, `native_tester`, `assigned_to`, `assignee`, child owner, and test owner
  are never grouping fallbacks. Child `run_by`/`native_tester` are execution actors used only for
  automation usage.
- Never lock a blank or synthetic `Unassigned` value. Recheck cached blank topology identities from
  the parent endpoint so polling can recover when Octane exposes the assignment later.
- Regression tests must cover mixed Jenkins/manual child executors under one parent owner, forbidden
  alias fields, and blank-cache recovery. Assert both grouping and automation calculations so a fix
  for one identity cannot silently corrupt the other.


## Interpreting Browser Support & Fallbacks

1. If the user provides an explicit browser support policy, follow it. Use the browser compatibility data in the guide to determine whether a fallback can be safely ignored.
2. Else apply Default Behavior: use Baseline Widely available features without fallbacks. 'Baseline Widely available' is defined as features supported by ≥95% of the project's target user browsers as of 2026-01-01.
3. For non-Baseline features, implement the guide's recommended fallbacks.
4. If the user mentions a restricted runtime or an explicit policy trigger, suggest documenting a browser support policy in CLAUDE.md or AGENTS.md.

* **Default Behavior**: All guides assume **Baseline Widely available** features are safe to use without fallbacks. ('Baseline Widely available' is defined as features supported by ≥95% of the project's target user browsers as of 2026-01-01.) For features that are not Baseline widely available, you **MUST** follow the fallback recommendations in the guide, unless the user has specified a custom browser support policy.
* **Custom Policies**: If the user has already defined explicit browser support requirements, use the browser compatibility data in the guide to determine if a fallback can be safely ignored.
  - For Baseline YYYY targets, a feature satisfies this target if its "Baseline since" date is on or before YYYY-01-01 (ISO date YYYY-01-01).
  - **Policy Examples**:
    - _"Do not implement feature fallbacks."_ (for exploratory prototypes of the cutting-edge web)
    - _"Safari 17.4+"_ (for internal tools targeting macOS or Tauri-based desktop apps)
    - _"Never recommend or implement polyfills; if a Baseline Newly Available feature is required for core functionality, provide a lightweight custom fallback or redesign the approach."_ (to minimize bundle size and avoid technical debt)
    - _"Assume a modern execution environment where Baseline Newly Available features can be used natively if supported by ≥75% of target browsers as of 2026-01-01; feature-detect them and if unsupported, hide non-essential UI and log capability to telemetry."_ (for progressive enhancement strategies)
* **Reactive Policy Discovery**: If the user mentions any of these explicit cues: `Electron`, `Tauri`, `no polyfills`, `Safari-only`, `exclude Chrome`, `restricted runtime`, or `internal tool`, suggest documenting a policy in CLAUDE.md or AGENTS.md.
  - Use this exact suggestion text: `Consider adding a Browser Support policy in CLAUDE.md: "Browser Support: Allow Newly Available features; require fallback ≤20 lines; no external deps."`

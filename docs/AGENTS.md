## Anti-drift rules

- Read `AGENTS.md` before any implementation work.
- Start with the smallest high-signal inspection set:
  - controller / API entrypoint
  - application service
  - store / repository
  - migrations
  - focused tests for the ticket slice
- Do not read the whole repo unless the first pass proves it is necessary.
- Do not broaden the ticket casually or for nice-to-have work.
- If the repo is missing prerequisite foundation required to deliver the requested ticket correctly, it is allowed to broaden scope **only by the smallest coherent amount needed** to complete the intended end-state.
- When broadening scope for missing prerequisites:
  - keep the implementation on the same architectural theme
  - do not branch into unrelated features
  - state clearly what prerequisite was missing
  - include that prerequisite work in the same delivery
- Do not stop at “blocked by missing prior ticket state” unless the repository is fundamentally unusable or the missing work would require a clearly separate major architectural slice.
- Do not claim a behavior changed unless you inspected the code path that actually enforces it.

## Dependency recovery rule

- If the current workspace is behind the expected ticket sequence, do not stop by default.
- Inspect the actual repo state and compare it to the requested ticket.
- If the missing gap is prerequisite groundwork for the same requested outcome, backfill it and continue in one coherent slice.
- Only report `blocked` when:
  - the repo is fundamentally inconsistent or unusable, or
  - the missing prerequisite would require a separate major architectural slice that would make the requested ticket misleading.
- When recovery broadening happens, state exactly:
  - what was missing
  - what prerequisite was backfilled
  - what requested outcome was completed

## Build and cache discipline

- Reuse `C:\Users\Ryzen-pc\.m2\repository` and `C:\Users\Ryzen-pc\Desktop\sys-dds\codex-scratch`.
- Do **not** create project-local Maven repositories.
- Do **not** clear `.m2`, `codex-scratch`, or large caches unless there is a specific diagnosed corruption or lock issue.
- Prefer the smallest cleanup that fixes the blocker.
- If public signatures, DTOs, repository contracts, migrations, or test wiring changed, remove stale module build output first and force a fresh targeted rebuild.
- Never report compile/test results from stale build outputs.

## Verification discipline

- Prefer targeted compile/test commands first.
- Do not jump to full-suite runs unless targeted verification passes or the task explicitly requires full-suite validation.
- Report the exact commands run and the exact result.
- Separate **real code failures** from **environment/tooling failures**.
- If verification is blocked by environment issues, state the blocker precisely and say what you ruled out.
- A ticket is **not complete** unless the required targeted verification actually passed.

## Repo hygiene

- Do not modify `README`, `docs/tickets.md`, Postman, or other repo-wide docs unless the ticket explicitly requires it.
- Do not create or commit temporary logs, downloaded tools, extracted archives, local caches, or generated outputs.
- Update `.gitignore` only when a new generated artifact actually appeared and needs to be ignored.
- Keep file churn tight. Avoid unrelated renames, formatting passes, or opportunistic cleanup.

## Behavior preservation

- Preserve public redirect behavior unless the ticket explicitly changes it.
- Preserve existing async/lifecycle/outbox/projection pipelines unless the ticket explicitly changes them.
- When changing mutation boundaries, verify replay, idempotency, optimistic concurrency, and public read behavior still match current expectations.
- Prefer small migrations that fit current schema and runtime wiring instead of speculative abstractions.

## Recovery steps for local verification issues

- If Maven/Java verification fails because of Windows file-lock or cache access issues, try in this order:
  1. fresh module build output cleanup
  2. forked compiler
  3. rerun the same targeted command
- Do not switch repository layout, invent new cache locations, or do broad cleanup unless the blocker specifically requires it.

## Ticket completion contract

A ticket is not done until the return includes all of the following:
- completion status: `complete`, `groundwork but incomplete`, or `blocked`
- changed files
- exact commands run
- actual compile/test results
- blockers clearly separated from code issues
- short note on remaining risks
- update to `AGENTS.md` only if a **new recurring executor mistake** was discovered
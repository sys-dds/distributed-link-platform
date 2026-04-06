@@ -0,0 +1,59 @@
## Planning rule

- Read `AGENTS.md` first.
- Before coding, create a short execution checklist for:
  - implementation
  - verification
  - prerequisite recovery if needed
- Work through it item by item.

## Scope rule

- Inspect the smallest high-signal set first:
  - controller
  - application service
  - store/repository
  - migrations
  - focused tests
- If prerequisite groundwork is missing, backfill only the smallest coherent amount needed and continue.
- Only report `blocked` if the repo is fundamentally unusable or the missing work is clearly a separate architectural slice.

## Build and verification rule

- Reuse `C:\Users\Ryzen-pc\.m2\repository` and `C:\Users\Ryzen-pc\Desktop\sys-dds\codex-scratch`.
- Do not create project-local Maven repositories.
- Do not clear caches unless there is a specific diagnosed issue.
- If signatures, DTOs, repository contracts, migrations, or test wiring changed, remove stale module build output and do a fresh targeted rebuild.
- Never report results from stale outputs.
- Prefer targeted compile/test commands first.
- Separate code failures from environment/tooling failures.

## Repo hygiene rule

- Do not modify `README`, `docs/tickets.md`, Postman, or repo-wide docs unless explicitly required.
- Do not commit temp logs, downloads, caches, extracted tools, or generated outputs.
- Keep file churn tight.

## Preservation rule

- Preserve public redirect behavior unless explicitly changing it.
- Preserve existing async/lifecycle/outbox/projection behavior unless explicitly changing it.
- When changing mutation boundaries, verify replay, idempotency, optimistic concurrency, and public read behavior still hold.
- Prefer small migrations over speculative abstractions.

## Local recovery rule

- If Maven/Java verification fails due to Windows lock/cache issues, try:
  1. fresh module build output cleanup
  2. forked compiler
  3. rerun the same targeted command

## Completion contract

A ticket is not done unless the return includes:
- completion status: `complete`, `groundwork but incomplete`, or `blocked`
- changed files
- exact commands run
- actual compile/test results
- blockers separated from code issues
- remaining risks
  No newline at end of file

TICKET-009 - Strengthen API contract tests for create-link and redirect behavior

Status: Ready

title[]

Strengthen API contract tests for create-link and redirect behavior

technical_detail[]

Strengthen the automated test foundation around the current Link Platform HTTP contract without changing current business behavior. This ticket should make the existing create-link and redirect API behavior more explicitly protected through focused API contract tests that cover both success and failure responses.

The current platform already has several important correctness rules: invalid input rejection, duplicate slug rejection, reserved slug rejection, self-target URL rejection, missing slug handling, successful create-link behavior, and successful redirect behavior. This ticket should make those current behaviors more comprehensively and intentionally protected by tests so later changes can be made with lower regression risk.

The work should stay focused on test quality and contract coverage, not on adding new product behavior. Prefer small, maintainable tests that clearly document the current HTTP contract. Reuse the existing testing style and infrastructure unless a very small improvement is directly useful. Keep runtime code changes to an absolute minimum and only make them if they are directly required to support clear, stable tests.

This ticket must not broaden into new validation rules, URL normalization, reserved-route expansion, self-target policy changes, persistence redesign, caching, analytics, auth, or frontend work. Success and error behavior should remain unchanged. The goal is to strengthen confidence in the current API surface, not redesign it.

feature_delivered_by_end[]

The current create-link and redirect API contract is more comprehensively protected by focused automated tests, making future changes safer without altering runtime behavior.

how_this_unlocks_next_feature[]

This creates a stronger CI-grade safety net before broader validation, normalization, or architectural work, reducing regression risk as the platform evolves.

acceptance_criteria[]
Current create-link success behavior is covered by focused API tests
Current redirect success behavior is covered by focused API tests
Current handled error paths are covered by focused API tests, including:
invalid input
duplicate slug
reserved slug
self-target URL
missing slug
Error-response tests verify the current RFC 7807 Problem Details contract
Success-response tests verify the current success response shapes remain unchanged
Tests remain small, readable, and maintainable
Existing tests still pass or are updated appropriately
No unnecessary production-code, schema, or infrastructure changes are introduced
code_target[]
apps/api
README.md only if a very small clarification about test coverage or manual verification is truly needed
postman only if a tiny addition materially helps validate the current contract
proof[]
passing automated tests
focused API tests cover current success and failure contracts
RFC 7807 error-response contract is explicitly verified
redirect behavior remains explicitly verified
delivery_note[]

Deliberately postponed: new validation rules, broader URL normalization/canonicalization, reserved-route expansion, self-target policy changes, auth, caching, analytics, and any persistence/schema changes beyond what already exists.
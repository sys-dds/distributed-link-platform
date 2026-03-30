TICKET-007 - Prevent self-referential target URLs using configured public base URL

Status: Ready

title[]

Prevent self-referential target URLs using configured public base URL

technical_detail[]

Harden the create-link flow so clients cannot create short links whose originalUrl points back to the Link Platform itself. This ticket must prevent self-referential target URLs before persistence while keeping the current create-link, redirect, reserved-slug, and PostgreSQL-backed persistence behavior working for valid external URLs.

The implementation must introduce one small explicit application configuration value representing the platform’s public base URL. The self-target rule must use that configured public base URL rather than guessing from request headers or relying on ad hoc runtime inference. The comparison should be reasonably robust for this stage and handle normal URL variations such as host casing and default port equivalence where directly relevant.

The rule must reject URLs that target the platform itself so the system does not allow links that loop back into the same service. Rejection must happen before persistence so invalid self-referential URLs never reach storage. Keep the implementation minimal and focused on current correctness.

Do not broaden this ticket into general URL normalization or broader URL policy work. Do not add reserved-route changes beyond what already exists. Do not add expiration rules, analytics, caching, auth, or a new error framework. Keep the current API response shape and current error handling style unless a very small localized change is directly needed to produce a clear client error.

feature_delivered_by_end[]

The platform rejects short-link targets that point back to the platform itself, preventing redirect loops while preserving the normal create and redirect flow for valid external URLs.

how_this_unlocks_next_feature[]

This strengthens core API correctness and makes future validation and error-shaping work safer by preventing a class of broken redirects at the boundary.

acceptance_criteria[]
Creating a link whose originalUrl points to the platform’s own configured public base URL is rejected with a clear client error
Self-target validation happens before persistence
The self-target rule uses explicit application configuration rather than request-header inference
The comparison handles normal URL variations reasonably for this stage, including host casing and default-port equivalence where relevant
Valid external URLs still create successfully
Existing redirect behavior still works
Existing reserved-slug behavior still works
Existing duplicate slug behavior still works
Existing tests still pass or are updated appropriately
New tests cover self-target rejection at both the application/service level and HTTP/API level
No unnecessary schema or infrastructure changes are introduced
code_target[]
apps/api
README.md only if required for config or manual testing clarification
postman only if a negative self-target request adds clear value
proof[]
self-target URL creation returns a clear client error
valid external URL creation still succeeds
valid redirect still succeeds
existing reserved-slug behavior remains unaffected
passing automated tests
delivery_note[]

Deliberately postponed: broader URL normalization, canonicalization policy, expiration rules, RFC 7807 response format, advanced host validation, and any schema changes beyond what already exists.
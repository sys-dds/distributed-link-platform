TICKET-006 - Reserve system routes and reject conflicting slugs

Status: Ready

title[]

Reserve system routes and reject conflicting slugs

technical_detail[]

Harden the create-link flow so clients cannot create short-link slugs that conflict with existing top-level application routes or framework/system endpoints. This ticket must reject reserved slugs before persistence and keep the current create-link and redirect behavior unchanged for valid slugs.

The reserved-slug rule must be case-insensitive and must be based on the routes that already exist in the current application. At minimum, the implementation must reject slugs that conflict with the currently exposed top-level system/application routes such as api, actuator, and error. Additional reserved slugs may be included only if they already exist in the current running application and are true top-level route conflicts. Do not reserve speculative future paths.

The implementation must stay minimal and must not introduce broader validation or policy work. It must not add URL normalization, expiration rules, self-redirect prevention, broader alias policy, caching, analytics, or a new error framework. The rule must be enforced before persistence so invalid reserved slugs never reach the storage layer.

Keep the persistence design and runtime storage implementation from TICKET-005 intact. No database migration is expected for this ticket unless a very small change is directly required, which is unlikely. Keep the current API response shape and current error handling style unless a very small localized change is directly needed to produce a clear client error.

feature_delivered_by_end[]

The platform rejects route-conflicting slugs with a clear client error while preserving the normal short-link create and redirect flow for valid slugs.

how_this_unlocks_next_feature[]

This hardens API correctness before broader validation and error-shaping work, and it protects the redirect surface as more endpoints are added over time.

acceptance_criteria[]
Creating a link with a reserved slug is rejected with a clear client error
Reserved slug checks are case-insensitive
Reserved slug checks happen before persistence
The minimum reserved set covers the current top-level conflicting routes already exposed by the application, including api, actuator, and error
Valid slug creation still works
Valid redirect behavior still works
Duplicate slug behavior still works for non-reserved slugs
Existing system endpoints still behave normally
Existing tests still pass or are updated appropriately
New tests cover reserved slug rejection at both the application/service level and HTTP/API level
No unnecessary schema or infrastructure changes are introduced
code_target[]
apps/api
README.md only if required for manual testing or behavior clarification
postman only if a negative reserved-slug request adds clear value
proof[]
reserved slug creation returns a clear client error
valid slug creation still succeeds
valid redirect still succeeds
existing system endpoints remain unaffected
passing automated tests
delivery_note[]

Deliberately postponed: broader validation hardening, URL normalization, self-redirect prevention, expiration rules, RFC 7807 response format, and any persistence/schema changes beyond what already exists.
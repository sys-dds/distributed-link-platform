Status: Ready

title[]

Standardize API error responses using RFC 7807 Problem Details

technical_detail[]

Harden the Link Platform API by replacing the current ad hoc JSON error response shape with a consistent RFC 7807 Problem Details response for handled client and not-found errors. This ticket must keep the existing business rules and status codes unchanged while standardizing the response contract returned by the API.

At minimum, the handled error paths for invalid input, reserved slug rejection, self-target URL rejection, duplicate slug rejection, and missing slug resolution must return RFC 7807-shaped responses. The implementation should prefer the framework-native smallest clean approach rather than introducing a custom error framework.

The response contract should be consistent and predictable for current API consumers. It should include the standard Problem Details core fields appropriate for this stage, and the API should continue to return the same HTTP status codes it returns today for the same failure conditions.

Do not broaden this ticket into new validation rules, URL normalization, reserved-route expansion, self-target logic changes, persistence changes, authentication, analytics, caching, or a broader API redesign. Success response bodies must remain unchanged. Keep the implementation focused on error response standardization only.

feature_delivered_by_end[]

The API returns a consistent RFC 7807-style error payload for current handled error cases, making failures easier to consume and reason about without changing the existing business behavior.

how_this_unlocks_next_feature[]

This creates a stable error contract before broader validation and API-hardening work, and it makes future correctness rules easier to add without growing a patchwork of inconsistent error bodies.

acceptance_criteria[]
Current handled client and not-found error paths return RFC 7807 Problem Details responses
Existing status codes remain unchanged for the same failure conditions
At minimum, invalid input, reserved slug rejection, self-target URL rejection, duplicate slug rejection, and missing slug resolution are covered
Success response shapes remain unchanged
Existing business rules remain unchanged
Existing tests still pass or are updated appropriately
New API tests verify the standardized error response shape
No unnecessary infrastructure, schema, or persistence changes are introduced
code_target[]
apps/api
README.md only if manual testing guidance or response examples need clarification
postman only if validating the new error shape adds clear value
proof[]
handled API errors return RFC 7807-shaped responses
current success responses remain unchanged
status codes remain unchanged
passing automated tests
delivery_note[]

Deliberately postponed: new validation rules, broader URL normalization/canonicalization, reserved-route expansion, self-target policy changes, auth, caching, analytics, and any persistence/schema changes beyond what already exists.
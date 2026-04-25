# Local Proof Scenarios

## Redirect cache proof

Create a link, redirect once, confirm miss, redirect again, confirm hit.

## Analytics freshness proof

Create a link, drive redirect traffic, let worker process, then check summary/top/trending/activity/freshness.

## Projection rebuild proof

Create projection job, poll progress, confirm completion, compare derived views.

## Query fallback proof

Use broken query datasource runtime and confirm fallback to primary.

## Redirect failover proof

Use regional redirect runtime and no-failover proof runtime to prove both failover and fail-closed posture.

Back to [README](../../README.md).

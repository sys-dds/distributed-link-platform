# Backend Proof

This document gives a reproducible, local proof for the backend hot paths, freshness guarantees, and degraded behavior.

## Start Local Infra

Base stack:

```powershell
docker-compose -f infra\docker-compose\docker-compose.yml up -d
```

Optional proof-only services:

```powershell
docker-compose -f infra\docker-compose\docker-compose.yml --profile proof up -d
```

Services:

- `http://localhost:8080` control-plane API
- `http://localhost:8081` redirect `eu-west-1`
- `http://localhost:8082` redirect `us-east-1`
- `http://localhost:8083` control-plane API with a broken dedicated query datasource to prove primary fallback
- `http://localhost:8084` redirect without failover to prove explicit single-region degraded behavior

Default API keys:

- free owner: `free-owner-api-key`
- pro owner: `pro-owner-api-key`

## Redirect Hot Path

Create a link through the control plane:

```powershell
curl -s -X POST http://localhost:8080/api/v1/links `
  -H "X-API-Key: pro-owner-api-key" `
  -H "Content-Type: application/json" `
  -d '{"slug":"redirect-hot","originalUrl":"https://example.com/redirect-hot"}'
```

Hit the redirect twice:

```powershell
curl -i http://localhost:8081/redirect-hot
curl -i http://localhost:8081/redirect-hot
```

Proof:

- both responses return `307` with `Location: https://example.com/redirect-hot`
- `GET http://localhost:8081/actuator/metrics/link.redirect.cache.miss` shows the first miss
- `GET http://localhost:8081/actuator/metrics/link.redirect.cache.hit` shows the second request was served from cache

## Owner Query Hot Path

Warm the control-plane discovery and detail paths:

```powershell
curl -s "http://localhost:8080/api/v1/links/discovery?search=redirect" -H "X-API-Key: pro-owner-api-key"
curl -s "http://localhost:8080/api/v1/links/discovery?search=redirect" -H "X-API-Key: pro-owner-api-key"
curl -s http://localhost:8080/api/v1/links/redirect-hot/traffic-summary -H "X-API-Key: pro-owner-api-key"
curl -s http://localhost:8080/api/v1/links/redirect-hot/traffic-summary -H "X-API-Key: pro-owner-api-key"
```

Proof:

- `GET http://localhost:8080/actuator/metrics/link.cache.hit` with `tag=area:discovery` increases on the second discovery call
- `GET http://localhost:8080/actuator/metrics/link.cache.hit` with `tag=area:traffic_summary` increases on the second traffic-summary call

## Live Analytics Freshness

Drive public traffic:

```powershell
curl -i http://localhost:8081/redirect-hot
curl -i http://localhost:8081/redirect-hot
```

Give the worker a moment to relay and consume the click events, then query owner analytics:

```powershell
curl -s http://localhost:8080/api/v1/links/redirect-hot/traffic-summary -H "X-API-Key: pro-owner-api-key"
curl -s "http://localhost:8080/api/v1/links/traffic/top?window=24h" -H "X-API-Key: pro-owner-api-key"
curl -s "http://localhost:8080/api/v1/links/traffic/trending?window=24h" -H "X-API-Key: pro-owner-api-key"
curl -s http://localhost:8080/api/v1/links/activity -H "X-API-Key: pro-owner-api-key"
```

Proof:

- traffic summary `totalClicks`, `clicksLast24Hours`, and `clicksLast7Days` all increase
- top links reflects the new count
- trending reflects the new current-window count
- recent activity shows a leading `clicked` event

Rollup rebuild proof:

```powershell
curl -s -X POST http://localhost:8080/api/v1/projection-jobs `
  -H "X-API-Key: pro-owner-api-key" `
  -H "Content-Type: application/json" `
  -d '{"jobType":"CLICK_ROLLUP_REBUILD"}'
```

Then poll:

```powershell
curl -s http://localhost:8080/api/v1/projection-jobs/1 -H "X-API-Key: pro-owner-api-key"
```

When the job reaches `COMPLETED`, the same traffic-summary, top-links, and trending endpoints remain aligned with rebuilt rollups.

## Failover And Degraded Behavior

Configured failover posture:

```powershell
curl -s http://localhost:8081/actuator/health/readiness
```

Proof:

- `redirectRuntime.failoverConfigured=true`
- `redirectRuntime.failoverRegion=us-east-1`
- `redirectRuntime.primaryFailurePolicy=regional-failover`
- `redirectRuntime.cacheDegradationPolicy=fallback-to-primary`

No-failover posture:

```powershell
curl -s http://localhost:8084/actuator/health/readiness
```

Proof:

- `redirectRuntime.failoverConfigured=false`
- `redirectRuntime.primaryFailurePolicy=fail-closed-service-unavailable`

Query-path fallback posture:

```powershell
curl -s http://localhost:8083/api/v1/me -H "X-API-Key: pro-owner-api-key"
curl -s http://localhost:8083/actuator/health/readiness
```

Proof:

- owner reads still succeed on `8083`
- `queryDataSource.route=primary-fallback`
- `queryDataSource.dedicatedConfigured=true`
- `queryDataSource.dedicatedAvailable=false`
- `queryDataSource.fallbackPolicy=primary-on-dedicated-query-failure`

Redis/cache degradation posture:

- stop Redis while the stack is running
- redirect requests still resolve from primary when the database is healthy
- `link.cache.degraded` metrics increase
- readiness still advertises `cacheDegradationPolicy=fallback-to-primary`

## Public Redirect Correctness

Public redirects must continue to behave as public endpoints:

```powershell
curl -i http://localhost:8081/redirect-hot
curl -i "http://localhost:8081/redirect-hot?src=campaign"
```

Proof:

- both responses remain `307`
- the `Location` header preserves the original target URL
- query strings are preserved when the redirect is served or failed over

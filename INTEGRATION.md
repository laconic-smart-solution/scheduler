# Laconic Scheduler — Integration Guide

How calling services schedule work against this platform.

Service base URL (local): `http://localhost:8010`  
API prefix: `/api/v1/schedules`

Run / env / architecture: [`README.md`](./README.md) · [`ARCHITECTURE.md`](./ARCHITECTURE.md)

---

## Routes

| Method | Path | Notes |
|--------|------|--------|
| `POST` | `/api/v1/schedules` | Create action + schedule. **201** if new, **200** if idempotent replay |
| `GET` | `/api/v1/schedules/{id}` | Fetch schedule + action |
| `POST` | `/api/v1/schedules/{id}/cancel` | Cancel (poller ignores) |

No auth headers today — protect at the gateway / network layer.

---

## Create schedule

### `ONCE_AT` + `WEBHOOK`

```http
POST /api/v1/schedules
Content-Type: application/json
```

```json
{
  "idempotencyKey": "order-shipment-notify-42",
  "maxAttempts": 3,
  "scheduleKind": "ONCE_AT",
  "runAt": "2026-07-21T10:00:00Z",
  "action": {
    "type": "WEBHOOK",
    "payloadJson": "{\"url\":\"https://your-app.example/hooks/scheduler\",\"method\":\"POST\",\"headers\":{\"X-Hook-Secret\":\"…\"},\"body\":{\"orderId\":42}}"
  }
}
```

### `CRON` (supported create shape)

```json
{
  "idempotencyKey": "daily-report",
  "maxAttempts": 3,
  "scheduleKind": "CRON",
  "cronExpression": "0 0 9 * * ?",
  "timeZone": "Asia/Yangon",
  "firstExecutionAt": "2026-07-22T02:30:00Z",
  "action": {
    "type": "WEBHOOK",
    "payloadJson": "{\"url\":\"https://your-app.example/hooks/daily-report\",\"method\":\"POST\",\"body\":{}}"
  }
}
```

> **Note:** Cron *next-fire* after success still uses a short placeholder delay until expression evaluation lands. Prefer `ONCE_AT` for production fires today, or set `firstExecutionAt` deliberately and treat cron as experimental.

### Field rules

| Field | Required | Notes |
|-------|----------|--------|
| `scheduleKind` | yes | `ONCE_AT` or `CRON` |
| `runAt` | for `ONCE_AT` | ISO-8601 instant (UTC recommended) |
| `cronExpression` / `timeZone` / `firstExecutionAt` | for `CRON` | all required for cron creates |
| `action.type` | yes | currently only `WEBHOOK` |
| `action.payloadJson` | yes | **string** containing JSON (validated for WEBHOOK) |
| `action.metadataJson` | no | free-form string |
| `idempotencyKey` | recommended | unique; replay returns same schedule (**200**) |
| `maxAttempts` | no | default **3** |

---

## WEBHOOK payload

`payloadJson` must parse to:

```json
{
  "url": "https://your-app.example/hooks/scheduler",
  "method": "POST",
  "headers": { "Authorization": "Bearer …" },
  "body": { "any": "json" }
}
```

| Field | Required | Default |
|-------|----------|---------|
| `url` | yes | — |
| `method` | no | `POST` |
| `headers` | no | `{}` |
| `body` | no | omitted / null |

On fire, the scheduler HTTP client also adds:

| Header | Meaning |
|--------|---------|
| `X-Scheduler-Schedule-Id` | Schedule id |
| `X-Scheduler-Execution-Id` | This attempt id |
| `X-Scheduler-Action-Id` | Action id |
| `X-Scheduler-Retry-Count` | 0-based retry count |

**Success:** HTTP **2xx**. Anything else → failed attempt → retry / dead letter per `maxAttempts` and exponential backoff.

---

## Response shape

```json
{
  "id": "…",
  "actionId": "…",
  "status": "SCHEDULED",
  "scheduleKind": "ONCE_AT",
  "runAt": "2026-07-21T10:00:00Z",
  "cronExpression": null,
  "timeZone": null,
  "nextExecutionAt": "2026-07-21T10:00:00Z",
  "attemptCount": 0,
  "maxAttempts": 3,
  "idempotencyKey": "order-shipment-notify-42",
  "createdAt": "…",
  "updatedAt": "…",
  "action": {
    "id": "…",
    "type": "WEBHOOK",
    "payloadJson": "{…}",
    "metadataJson": null
  }
}
```

### Statuses you will see

| Status | Meaning |
|--------|---------|
| `SCHEDULED` | Waiting for `nextExecutionAt` |
| `RUNNING` | Claimed / executing |
| `SUCCESS` | Terminal success (`ONCE_AT`) |
| `RETRYING` | Waiting for next attempt |
| `DEAD` | Retries exhausted |
| `CANCELLED` | Cancelled via API |
| `FAILED` | Transient (usually moves to `RETRYING` / `DEAD` quickly) |
| `PAUSED` | Paused (engine ignores until resumed — resume API not exposed yet) |

---

## Get / cancel

```http
GET /api/v1/schedules/{id}
```

```http
POST /api/v1/schedules/{id}/cancel
```

Cancel sets `CANCELLED` and clears `nextExecutionAt`. In-flight `RUNNING` may still finish; the recorder will not revive a cancelled schedule into a new fire.

---

## Errors

| HTTP | When |
|------|------|
| `400` | Validation / bad webhook payload / illegal args |
| `404` | Unknown schedule id |
| `409` | Conflict (e.g. cannot cancel terminal state) |
| `500` | Unexpected |

Body:

```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "…",
  "path": "/api/v1/schedules",
  "details": { "field": "message" }
}
```

(`details` present for bean-validation failures.)

---

## Idempotency

1. Client sends a stable `idempotencyKey` (e.g. business event id).
2. First successful create → **201** + new ids.
3. Retry with the same key → **200** + **same** schedule/action (no duplicate fire).

Do **not** reuse a key for a different payload/intent.

---

## curl examples

```bash
# Create once-at webhook (due in ~1 minute)
curl -s -X POST "http://localhost:8010/api/v1/schedules" \
  -H "Content-Type: application/json" \
  -d "{
    \"idempotencyKey\": \"demo-$(date +%s)\",
    \"maxAttempts\": 3,
    \"scheduleKind\": \"ONCE_AT\",
    \"runAt\": \"$(date -u -d '+1 minute' +%Y-%m-%dT%H:%M:%SZ)\",
    \"action\": {
      \"type\": \"WEBHOOK\",
      \"payloadJson\": \"{\\\"url\\\":\\\"https://httpbin.org/post\\\",\\\"method\\\":\\\"POST\\\",\\\"body\\\":{\\\"hello\\\":\\\"scheduler\\\"}}\"
    }
  }"

# Get
curl -s "http://localhost:8010/api/v1/schedules/<id>"

# Cancel
curl -s -X POST "http://localhost:8010/api/v1/schedules/<id>/cancel"
```

Windows PowerShell: prefer `Invoke-RestMethod` or put the JSON in a file and `-d @body.json`.

---

## Receiving the webhook (your app)

1. Expose HTTPS endpoint.
2. Verify optional shared secret via your `headers` in `payloadJson`.
3. Use `X-Scheduler-*` headers for logging / dedupe.
4. Return **2xx** only when the side effect is durable (or you accept retries).

Retries use exponential backoff (`base × multiplier^(attempt-1)`, capped). After `maxAttempts`, status → `DEAD`.

---

## Ops hooks for integrators

| Endpoint | Use |
|----------|-----|
| `GET /actuator/health` | Ready / poll freshness |
| `GET /actuator/prometheus` | Scrape tick, dispatch, reaper, poll lag |

Default poll interval is **5s**; due work is claimed with Oracle `FOR UPDATE SKIP LOCKED` so multiple scheduler instances are safe.

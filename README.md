# Laconic Scheduler

Stateless, horizontally scalable **schedule platform** for Laconic.

| | |
|---|---|
| Stack | Java 21 · Spring Boot 4 · JPA · Quartz · Oracle · WebClient |
| Default port | **8010** |
| Schema | `LCN_SCHEDULER` (Hibernate `ddl-auto=update`) |

**Oracle is the source of truth.** Quartz only runs a single poll timer; it does not store business schedules.

| Concern | Owner |
|---------|--------|
| **When** | `Schedule` + `SchedulerEngine` |
| **What** | `Action` |
| **How** | `TaskExecutor` (first type: `WEBHOOK`) |
| **Wake** | Quartz poll job → `SchedulerEngine.tick()` |

Deep design: [`ARCHITECTURE.md`](./ARCHITECTURE.md)  
Calling apps / HTTP contract: [`INTEGRATION.md`](./INTEGRATION.md)

---

## Quick start

### Prerequisites

- JDK 21
- Oracle reachable as `LCN_SCHEDULER` (see `.env.example`)

### Configure

```bat
copy .env.example .env
```

Edit Oracle URL / credentials. Defaults target `192.168.0.131:1521/LACONIC`.

### Run (Windows)

```bat
run.bat
```

Loads `.env`, frees port `8010` if needed, then `mvnw spring-boot:run`.

### Run (Maven)

```bash
./mvnw spring-boot:run
```

Health: http://localhost:8010/actuator/health

---

## Pipeline

```text
Quartz poll
  → StaleRunningReaper (optional)
  → ScheduleLoader (due rows + FOR UPDATE SKIP LOCKED)
  → ScheduleClaimer (RUNNING)
  → ExecutionDispatcher → TaskExecutor → ExecutionRecorder
```

Lifecycle (simplified):

```text
CREATED → SCHEDULED → RUNNING → SUCCESS
                      └→ FAILED → RETRYING → RUNNING …
                                 └→ DEAD
                      └→ CANCELLED / PAUSED
```

---

## Configuration

| Env | Default | Meaning |
|-----|---------|---------|
| `SCHEDULER_PORT` | `8010` | HTTP port |
| `SPRING_DATASOURCE_*` | see `.env.example` | Oracle JDBC |
| `SCHEDULER_POLL_ENABLED` | `true` | Quartz wake |
| `SCHEDULER_POLL_INTERVAL_MS` | `5000` | Poll interval |
| `SCHEDULER_ENGINE_BATCH_SIZE` | `50` | Due rows per tick |
| `SCHEDULER_RETRY_BASE_DELAY_MS` | `60000` | Retry base delay |
| `SCHEDULER_RETRY_MULTIPLIER` | `2.0` | Exponential backoff |
| `SCHEDULER_RETRY_MAX_DELAY_MS` | `3600000` | Cap |
| `SCHEDULER_REAPER_ENABLED` | `true` | Stale `RUNNING` recovery |
| `SCHEDULER_REAPER_STALE_AFTER_MS` | `300000` | Lease window |
| `SCHEDULER_WEBHOOK_*_TIMEOUT_MS` | `3000` / `10000` | Outbound HTTP |
| `SCHEDULER_HEALTH_POLL_STALE_MULTIPLIER` | `3` | Poll health threshold |

Reference DDL (docs only): `src/main/resources/db/schema-oracle.sql`

---

## Actuator

| Path | Purpose |
|------|---------|
| `/actuator/health` | DB + poll freshness |
| `/actuator/metrics` | Micrometer meters |
| `/actuator/prometheus` | Prometheus scrape |

---

## Tests & CI

```bash
# Unit tests (no Oracle) — same as CI
./mvnw -B test -DexcludedGroups=oracle

# Oracle ITs (needs live LCN_SCHEDULER + .env)
./mvnw -B test -Dgroups=oracle
```

GitHub Actions: `.github/workflows/ci.yml` (JDK 21, excludes `@Tag("oracle")`).

---

## Project layout

```text
com.laconic.scheduler
├── scheduler      # domain, API, engine, poll
├── execution      # dispatcher, recorder, Execution
├── executor       # TaskExecutor + registry
├── webhook        # WEBHOOK payload + HTTP executor
├── retry          # backoff + stale RUNNING reaper
├── health         # poll health indicator
├── metrics        # Micrometer
└── exception      # API errors
```

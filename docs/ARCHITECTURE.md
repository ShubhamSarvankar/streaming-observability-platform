# Architecture and Design Decisions

This document covers the reasoning behind every significant architectural choice
in the streaming log observability platform. The goal is to explain not just what
was built, but why — including the trade-offs that were accepted, the problems
that were encountered mid-build, and how they were resolved.

---

## 1. System overview

```
log-replay-producer ──►  Kafka (3 partitions)  ──►  Spark Structured Streaming
                                 ▲                           │
                         anomaly-injector              (5 Cassandra tables)
                         (on demand)                        │
                                                    Spring Boot API :8080
                                                            │
                                          user ──► LangGraph Agent ──► (5 HTTP tools)
```

Data flows in one direction through the pipeline. The agent layer sits entirely
outside the data pipeline — it reads aggregated results from the API and never
touches Kafka, Spark, or Cassandra directly.

---

## 2. Streaming data pipeline

### 2.1 Ingestion: producer and Kafka

**The producer** replays the NASA HTTP access log (Common Log Format, ~1.9 M
lines) into Kafka at a configurable rate (default 200 req/s). Parsing is
isolated in `log_schema.py` as a pure function `parse_line(str) -> dict | None`.
This separation made it possible to unit-test the regex against real log lines
before wiring any Kafka code — a deliberate build order choice that surfaced
format edge cases early.

Unparseable lines are counted and skipped, not crashed on. This matters for a
real dataset: the NASA logs contain a small number of malformed lines from the
original 1995 era; crashing would halt replay.

**Kafka** is in KRaft mode (single combined broker+controller, no ZooKeeper).
ZooKeeper was deliberately excluded — it adds operational complexity and a
separate failure domain that adds nothing for a single-broker development setup.
KRaft became stable in Kafka 3.3 and is the correct choice for new deployments.

**Message key = `client_ip`.** Keying by IP colocates all events from one client
on the same partition, preserving per-IP ordering for downstream consumers.
For a session-analysis workload this would be essential; for this pipeline it
ensures Spark's per-IP aggregation is partition-local.

**Three partitions** on the `access-logs` topic. One partition per Spark task in
`local[*]` mode gives parallelism without over-partitioning; the same count
works correctly when scaled to a real cluster with multiple executors.

> **Infrastructure note mid-project:** Bitnami removed all versioned images from
> Docker Hub in 2024. The original `bitnami/kafka:3.7.1` and `bitnami/spark:3.5.3`
> images stopped resolving. Migration to `apache/kafka:3.7.1` and
> `apache/spark:3.5.3` (official Apache images) required rewriting the entire
> Kafka env-var config from Bitnami's `KAFKA_CFG_*` convention to Apache's
> direct-mapping `KAFKA_*` convention, and adjusting the Spark Dockerfile CMD to
> use the correct `spark-submit` path at `/opt/spark/bin/spark-submit`.

### 2.2 Stream processing: Spark Structured Streaming

**Event-time windows, not processing-time.** The NASA dataset is from 1995;
processing-time windows would produce nonsense (all events would fall in the
same "now" window). Event-time windows with `to_timestamp("timestamp")` preserve
the original distribution. This also makes the system correct for any replay-speed
dataset, not just real-time feeds.

**UTC enforcement.** The NASA log timestamps carry a `-0400` (EDT) offset. Without
`.config("spark.sql.session.timeZone", "UTC")`, Spark converts event-time windows
to the container's local timezone and stores them that way in Cassandra. Any
subsequent query using UTC-suffixed ISO timestamps (`Z`) then silently returns
zero rows — the values exist but the timestamps don't match. UTC enforcement on
the Spark session eliminates this class of silent failure entirely.

**10-minute tumbling windows with a 5-minute watermark.** The window size is a
query-design choice: 10-minute granularity gives enough resolution to identify
traffic spikes without producing so many rows that time-range queries become
expensive. The watermark allows Spark to drop state for windows that are more
than 5 minutes behind the watermark and commit those results to Cassandra.

**`foreachBatch` for multi-table writes.** The streaming job writes to four
tables (and a fifth for anomalies) from a single stream. Spark's `foreachBatch`
gives a micro-batch DataFrame; the job runs separate aggregations and writes
within each batch. This is more efficient than maintaining five separate streaming
queries, and it makes the anomaly logic read naturally (compute the per-IP agg,
then filter for spikes in the same batch).

**Output mode `update` with `mode("append")` writes.** Spark's `update` output
mode emits only rows that changed in each micro-batch; combined with Cassandra's
upsert semantics (`mode("append")` on a primary key), this means re-running the
job after a failure naturally resumes from the checkpoint without duplicating data.

### 2.3 Anomaly detection

Anomaly detection lives entirely in the Spark job — not in the API, not in the
agent. This is a separation-of-concerns decision: Spark already has the windowed
aggregations in memory. Adding a filter pass costs almost nothing and avoids
duplicating aggregation logic elsewhere.

Two rules:
- **IP volume spike:** `request_count >= ANOMALY_IP_FLOOR` (default 500) in a
  single window. Simple threshold; no statistical model. This is deliberate —
  for an observability demo the threshold is tuneable and the logic is auditable
  at a glance.
- **Status class spike:** `4xx` or `5xx` count `>= ANOMALY_STATUS_FLOOR` (default
  200) in a window. Same reasoning.

Results are written to the `anomalies` table with the metric type, subject (IP
or status class), observed value, and threshold. The agent reads and explains
this table; it never re-derives anomalies.

### 2.4 Storage: Cassandra schema design

Cassandra requires query-first table design — the primary key must match the
access pattern or reads will require full-table scans (which Cassandra handles
very poorly). Every table in this schema is designed for exactly one query:

| Table | Partition key | Clustering | Designed for |
|---|---|---|---|
| `metrics_by_path` | `path` | `window_start DESC` | Time-range query for one URL path |
| `metrics_by_ip` | `client_ip` | `window_start DESC` | Time-range query for one IP |
| `metrics_by_status` | `status_class` | `window_start DESC` | Time-range query for one status class |
| `top_ips_by_window` | `window_start` | `request_count DESC, client_ip` | Top-N IPs for one window |
| `anomalies` | `day` | `window_start DESC, metric, subject` | All anomalies for one day |

The `top_ips_by_window` design is worth highlighting: by putting `request_count`
in the clustering key in descending order, a `SELECT ... LIMIT N` query returns
the top N IPs for a given window with no application-side sorting. The storage
layer does the work the query needs.

`window_start` is stored as UTC throughout. This is enforced by the Spark UTC
session timezone (§2.2) and validated in testing: a time-range query using
naive local timestamps silently returns nothing rather than failing — the sort
of bug that survives unit tests and breaks in integration.

---

## 3. Serving layer: Spring Boot API

### 3.1 Layered architecture

The API follows a strict `controller → service → repository` layering:

- **Controllers** bind and validate request parameters, call the service, and
  return DTOs. They contain no business logic.
- **Services** call repositories, map `@Table` entities to DTOs, and return DTO
  lists. Cassandra entity types never cross the service boundary.
- **Repositories** are Spring Data Cassandra interfaces with `@Query` annotations.
  Each repository has exactly one query method, matching the table's access pattern.

A single `@RestControllerAdvice` in `ApiExceptionHandler` converts all exceptions
to the `ErrorDto` shape. This means every error response — validation failures,
`to` before `from`, Cassandra unavailable — has the same JSON structure:
`{timestamp, status, error, message, path}`.

### 3.2 The `path` query parameter decision

The original API contract had the URL path as a path segment:
`GET /api/metrics/path/{path}` with `{path}` URL-encoded
(e.g., `%2Fshuttle%2Fmissions%2F`).

This broke in testing: Tomcat rejects requests where a decoded path segment
contains a forward slash, treating it as a potential path-traversal attack. This
is a Tomcat security setting (`allowEncodedSlash=false` by default), and enabling
it is discouraged — it weakens a protection that exists for good reasons.

The fix was to change the contract: `GET /api/metrics/path?path=/shuttle/missions/`
passes the path as a query parameter. Query parameters are not subject to the
same path-traversal check. The change is cleaner than relaxing Tomcat's security
settings, and it makes the intent explicit: the `path` value is data, not a URL
routing component. This required updating the API spec, the controller, the agent
tool, and the README curl examples simultaneously.

### 3.3 Timestamps

All API timestamps are ISO-8601 UTC (`Z`-suffixed). `Instant.parse()` in Java
rejects non-UTC offsets, and `Instant` serializes to UTC by default via Jackson.
The agent always passes UTC timestamps to tools (enforced in `tools.py`), which
pass them as `Z`-suffixed strings to the API, which queries Cassandra with UTC
values. There is no point in the chain where a local-time value can silently
produce empty results.

---

## 4. Natural language query layer: LangGraph agent

### 4.1 Why LangGraph

LangGraph models the agent as an explicit state machine rather than a chain or
a sequence of prompts. The benefit is that the routing logic — when to retry,
when to refine, when to give up — is code, not LLM output. The LLM provides
intent extraction and answer composition; the graph handles control flow.

This also makes the agent's behavior inspectable. Every state transition is a
named Python function. A failed run leaves a complete state object showing which
node failed, what the intent was, and what the tool returned.

### 4.2 The trust boundary

The hardest constraint in the agent design: **the LLM never writes a query.**

The LLM sees:
- The user's question
- A description of the intent schema (metric type, subject, time range)
- The names of five tool functions and their typed signatures
- The result of the tool call (pre-aggregated Python dict)

The LLM does not see:
- CQL syntax
- Cassandra table or column names
- The API's URL structure
- Raw HTTP responses

`tools.py` contains the only HTTP call site in the agent (`httpx.get` on one
line). Every data access goes through one of five functions. The LLM can
express intent, select a tool, and compose a human-readable answer — nothing
else. This boundary holds even if the model is adversarial or confused, because
the graph provides no mechanism to break it.

### 4.3 Graph structure

```
parse_intent → select_tool → execute_tool → check_result → compose_answer → END
                    ↑                              │
                    └──────── refine (max 2) ◄────┘ (if empty result / bad args)
```

The `refine` edge is bounded at 2 iterations. Without a cap, an empty result
(e.g., a genuinely empty Cassandra partition) would loop forever. At 2 refines,
the graph widens the query (e.g., expands the time range) twice and then accepts
the result — including an empty result — and composes an honest "no data found"
answer.

### 4.4 Intent extraction and tool mapping

`parse_intent` calls `llm.extract()` with an intent schema. The schema
descriptions are carefully worded to distinguish similar metrics:

- `top_ips` is for "which IP is busiest / most requests *in a specific window*"
  (one point in time)
- `ip_volume` is for "how many requests did *a specific named IP* make *over a
  range*"

Getting this distinction right in the schema description was required to prevent
the model from mapping "which IP made the most requests" to `ip_volume` (a
range query) instead of `top_ips` (a window query). The fix was iterative:
the first version crashed because `ip_volume` got selected with a null `to`
argument; the defensive `except (KeyError, TypeError, ValueError, AttributeError)`
in `select_tool` was added to route those failures to `refine` rather than
crashing the graph.

### 4.5 Result aggregation: `_precompute`

A `count_hits` query over a full month returns hundreds of rows — one per
10-minute window. Passing a 500-row JSON list to a 7B local model (qwen2.5:7b)
caused two separate failures:

1. **Context overflow:** the raw JSON was ~40 KB, potentially truncated or
   degraded by the model's attention window.
2. **Arithmetic failure:** when the model received the full list, it described
   the pattern of the first few rows rather than summing `hitCount` across all
   rows — even after explicit instruction to sum.

The fix was `_precompute(tool, result)` in `graph.py`: before handing the result
to the LLM, compute the aggregate totals in Python and pass a small dict:

```python
{"total_hits": 722, "total_bytes": 8856043, "windows_aggregated": 608}
```

The LLM receives a fact, not a dataset. It cannot get the arithmetic wrong
because there is no arithmetic for it to do. The initial version of this dict
included a `"sample"` key with three example rows; both Claude and qwen
latched onto the word "sample" and concluded the data was incomplete. Removing
that key resolved the issue completely.

This pattern — aggregate in code, let the model phrase — is the correct division
of labour between deterministic code and a probabilistic model.

### 4.6 Swappable LLM providers

`llm.py` defines a two-method abstract interface:

```python
class LLM:
    def complete(self, system: str, user: str) -> str: ...
    def extract(self, system: str, user: str, schema: dict) -> dict: ...
```

`AnthropicLLM` and `OllamaLLM` both implement this interface. `get_llm()` reads
`LLM_PROVIDER` from the environment and returns the appropriate instance. The
rest of the codebase — graph, tools, prompts — imports only the `LLM` type. No
other module knows which provider is in use.

The two implementations differ in one meaningful way beyond the API call:
`OllamaLLM.extract()` passes the JSON schema as a `format` parameter to the
Ollama client, enabling grammar-constrained decoding (the model's output is
guaranteed to be valid JSON matching the schema). `AnthropicLLM.extract()` uses
prompt-based extraction with a fence-stripping fallback, since Anthropic's API
does not expose grammar constraints at the same level.

The system was tested end-to-end on both providers. Both pass the same three DoD
questions and boundary conditions. qwen2.5:7b's answers are occasionally less
fluent (formatting as JSON instead of prose; occasional non-English responses
before language instructions were hardened), but all data is correct and all
tool selections are accurate after prompt refinement.

---

## 5. Operational architecture

### 5.1 Service profiles

Not all services run all the time. `docker-compose.yml` uses Compose profiles:

| Profile | Services started |
|---|---|
| *(default)* | kafka, cassandra, cassandra-init, producer, spark-streaming |
| `api` | springboot-api |
| `agent` | agent (interactive) |
| `ollama` | ollama (GPU-enabled LLM server) |
| `tools` | anomaly-injector (on-demand burst injection) |

This keeps the default data-plane startup lightweight. The API and agent are
started explicitly when needed.

### 5.2 Startup ordering

Cassandra takes ~90 seconds to become healthy. The schema init container
(`cassandra-init`) runs `cqlsh -f schema.cql` only after Cassandra passes its
healthcheck. `producer` and `spark-streaming` both `depend_on: condition:
service_completed_successfully` on `cassandra-init` — they will not start until
the schema exists. This means the startup sequence is deterministic regardless of
machine speed.

### 5.3 GPU Ollama

The `ollama` service uses the Compose `deploy.resources.reservations.devices`
block (the Compose-spec form, not the legacy `runtime: nvidia` key):

```yaml
deploy:
  resources:
    reservations:
      devices:
        - driver: nvidia
          count: all
          capabilities: ["gpu"]
```

The agent container sets `OLLAMA_HOST=http://ollama:11434` and uses
`ollama.Client(host=...)` directly rather than the module-level default — this
ensures the host is picked up at instantiation time from the environment, not
from a global that might be set before the environment is configured.

`extra_hosts: host-gateway` is set on the agent service so `host.docker.internal`
resolves on Linux containers (Docker Desktop for Windows resolves it automatically;
this addition makes the fallback work on Linux hosts too).

---

## 6. Scaling to production

The gap between single-host and multi-node is purely configuration. The
application code does not assume single-node deployment:

| Component | Single-host | Multi-node change |
|---|---|---|
| Kafka | 1 broker, KRaft | Add brokers; set RF=3, `min.insync.replicas=2` |
| Cassandra | SimpleStrategy RF 1 | `NetworkTopologyStrategy` RF 3; comma-separated seed nodes in `CASSANDRA_CONTACT_POINTS` |
| Spark | `local[*]` | `spark-submit --master spark://... --num-executors N` |
| Services | All containers on one host | Move each container to its target host; update hostnames in env vars |

Producer parsing, the Spark streaming logic, the Spring Boot API, and the
LangGraph agent are all unchanged. The separation of pipeline topology from
application logic was a deliberate design goal.

---

## 7. API reference

All responses are JSON. All timestamps are ISO-8601 UTC.

| Method | Path | Query params | Returns |
|---|---|---|---|
| GET | `/api/metrics/path` | `path`, `from`, `to` | `PathMetricDto[]` |
| GET | `/api/metrics/ip/{ip}` | `from`, `to` | `IpMetricDto[]` |
| GET | `/api/metrics/status/{statusClass}` | `from`, `to` | `StatusMetricDto[]` |
| GET | `/api/metrics/top-ips` | `window`, `n` (default 10, max 100) | `TopIpDto[]` |
| GET | `/api/anomalies` | `day` (YYYY-MM-DD) | `AnomalyDto[]` |
| GET | `/api/health` | — | `{"status":"UP"}` |

**DTOs:**
```
PathMetricDto   { path, windowStart, hitCount, byteTotal }
IpMetricDto     { clientIp, windowStart, requestCount }
StatusMetricDto { statusClass, windowStart, count }
TopIpDto        { clientIp, requestCount }
AnomalyDto      { windowStart, metric, subject, value, threshold }
ErrorDto        { timestamp, status, error, message, path }
```

Validation errors return 400 + `ErrorDto`. Empty result sets return 200 + `[]`,
not 404. Cassandra unavailable returns 503 + `ErrorDto`.

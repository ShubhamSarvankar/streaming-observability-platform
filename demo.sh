#!/usr/bin/env bash
# End-to-end demo: smoke checks + three agent questions + anomaly inject + explain.
# Runtime with WINDOW_MINUTES=1: ~3 min.  With WINDOW_MINUTES=10: ~12 min.
#
# Requires: bash (Linux, macOS, or WSL on Windows — not PowerShell/cmd).
# Prerequisites: docker compose up -d already ran and data is flowing.
#   For the Anthropic agent path: LLM_API_KEY must be set in .env.
#   For the Ollama/GPU path:      add --profile ollama to the agent run commands
#                                  below, and ensure ollama is up with the model pulled.
set -euo pipefail

YELLOW='\033[1;33m'; GREEN='\033[0;32m'; RED='\033[0;31m'; NC='\033[0m'
step() { echo -e "\n${YELLOW}==> $*${NC}"; }
ok()   { echo -e "${GREEN}OK${NC}"; }
fail() { echo -e "${RED}FAIL: $*${NC}"; exit 1; }

# ── 1. API health ──────────────────────────────────────────────────────────────
step "1. API health check"
curl -sf localhost:8080/api/health | grep -q '"UP"' && ok || fail "API not healthy"

# ── 2. Cassandra row counts ────────────────────────────────────────────────────
step "2. Cassandra row counts (all four metric tables must be > 0)"
docker compose exec -T cassandra cqlsh -e "
  USE obs;
  SELECT count(*) FROM metrics_by_path;
  SELECT count(*) FROM metrics_by_ip;
  SELECT count(*) FROM metrics_by_status;
  SELECT * FROM top_ips_by_window LIMIT 3;
"

# ── 3. Sample API calls ────────────────────────────────────────────────────────
step "3a. Top IPs for window 1995-07-03T16:20:00Z"
curl -s "localhost:8080/api/metrics/top-ips?window=1995-07-03T16:20:00Z&n=5" \
  | python3 -m json.tool

step "3b. Path metrics for /shuttle/missions/ in July 1995 (showing first 3 windows)"
curl -s "localhost:8080/api/metrics/path?path=/shuttle/missions/&from=1995-07-01T00:00:00Z&to=1995-08-01T00:00:00Z" \
  | python3 -c "
import sys, json
d = json.load(sys.stdin)
total = sum(r['hitCount'] for r in d)
print(f'{len(d)} windows, {total} total hits')
for r in d[:3]:
    print(' ', r)
"

# ── 4. Agent — three required questions (non-interactive) ─────────────────────
step "4. Agent — three DoD questions (non-interactive)"
echo "  Provider: ${LLM_PROVIDER:-anthropic} / model: ${LLM_MODEL:-claude-haiku-4-5}"
TODAY=$(date -u +%Y-%m-%d)
printf '%s\n' \
  "how many hits to /shuttle/missions/ in July 1995" \
  "which IP made the most requests in the window starting 1995-07-03T16:20:00Z" \
  "show 4xx spikes on ${TODAY}" \
  | docker compose --profile api --profile agent run --rm --no-tty agent

# ── 5. Anomaly inject ─────────────────────────────────────────────────────────
step "5. Inject anomaly burst (1500 req from 10.66.66.66, 600 × 404s)"
docker compose --profile tools run --rm anomaly-injector

# ── 6. Wait for anomaly window ────────────────────────────────────────────────
WAIT=${WINDOW_MINUTES:-10}
step "6. Waiting $((WAIT + 1)) min for Spark to close and flush the window..."
echo "   Tip: set WINDOW_MINUTES=1 WATERMARK_MINUTES=1 in .env and restart"
echo "   spark-streaming to reduce this wait to ~1 minute."
sleep $(( (WAIT + 1) * 60 ))

# ── 7. Confirm anomaly rows ────────────────────────────────────────────────────
step "7. Anomaly rows in Cassandra"
docker compose exec -T cassandra cqlsh -e "SELECT * FROM obs.anomalies LIMIT 10;"

step "7b. Anomaly rows via API"
curl -s "localhost:8080/api/anomalies?day=${TODAY}" | python3 -m json.tool

# ── 8. Agent explains the anomaly (non-interactive) ───────────────────────────
step "8. Agent explains the anomaly"
printf '%s\n' "show anomalies on ${TODAY}" \
  | docker compose --profile api --profile agent run --rm --no-tty agent

# ── 9. Optional: interactive agent session ────────────────────────────────────
step "9. Interactive agent (type questions; Ctrl-D to exit)"
docker compose --profile api --profile agent run --rm agent

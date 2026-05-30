"""
Phase 3 DoD test harness.
Run inside the agent container:
  docker compose --profile api --profile ollama run --rm -v ./agent:/app agent python test_dod.py
"""
import json
import sys
import traceback
from datetime import datetime, date

from graph import build_graph

graph = build_graph()

SEP = "-" * 72


def _serialise(obj):
    if isinstance(obj, (datetime, date)):
        return obj.isoformat()
    raise TypeError(type(obj))


def run(label, question):
    print(f"\n{SEP}")
    print(f"QUESTION: {label}")
    print(f'  "{question}"')
    print(SEP)
    try:
        state = graph.invoke({"question": question})
    except Exception as exc:
        print(f"UNHANDLED EXCEPTION — {type(exc).__name__}: {exc}")
        traceback.print_exc()
        return None
    print(f"TOOL SELECTED : {state.get('tool_name')}")
    args = state.get('tool_args')
    print(f"TOOL ARGS     : {json.dumps(args, default=_serialise, indent=2)}")
    raw = state.get("raw_result")
    if isinstance(raw, list):
        print(f"RAW RESULT    : {len(raw)} rows — first 3:")
        for row in raw[:3]:
            print(f"  {row}")
    else:
        print(f"RAW RESULT    : {raw}")
    print(f"REFINE COUNT  : {state.get('refine_count', 0)}")
    print(f"ERROR         : {state.get('error')}")
    print(f"\nANSWER:\n{state.get('answer')}")
    return state


# ── 1. Required DoD questions ─────────────────────────────────────────────────
print("\n" + "=" * 72)
print("SECTION 1 — Required DoD questions")
print("=" * 72)

q1 = run("Q1", "how many hits to /shuttle/missions/ in July 1995")
q2 = run("Q2", "which IP made the most requests in the window starting 1995-07-03T16:20:00Z")
q3 = run("Q3", "show 4xx spikes on 2026-05-30")

# ── 2. Refine loop: ask about data that does not exist ────────────────────────
print("\n" + "=" * 72)
print("SECTION 2 — Refine loop (no data)")
print("=" * 72)

q_nodata = run("REFINE", "how many hits to /no/such/path/xyzzy/ in July 1995")
if q_nodata is not None:
    rc = q_nodata.get('refine_count', 0)
    print(f"\nRefine loop fired {rc} time(s); capped correctly (<=2): {rc <= 2}")

# ── 3. Out-of-scope question ──────────────────────────────────────────────────
print("\n" + "=" * 72)
print("SECTION 3 — Out-of-scope question")
print("=" * 72)

q_oos = run("OOS", "what is the weather like today?")
if q_oos is not None:
    print(f"\nGraceful (no crash, answer present): {bool(q_oos.get('answer'))}")

print(f"\n{SEP}\nDONE\n")

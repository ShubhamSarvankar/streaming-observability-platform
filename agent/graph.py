from typing import TypedDict, Optional, Any
from datetime import datetime, date, timezone
from langgraph.graph import StateGraph, END
from llm import get_llm
from tools import TOOLS, TOOL_SPECS, ToolError

llm = get_llm()


class AgentState(TypedDict, total=False):
    question: str
    intent: Optional[dict]
    tool_name: Optional[str]
    tool_args: Optional[dict]
    raw_result: Optional[Any]
    refine_count: int
    answer: Optional[str]
    error: Optional[str]


_INTENT_SCHEMA = {
    "metric": (
        "one of: hits_for_path, ip_volume, status_distribution, top_ips, anomalies. "
        "Use top_ips when the question asks which IP is busiest / made the most requests "
        "in a specific window (no range, just one window). "
        "Use ip_volume when the question asks how many requests a SPECIFIC NAMED IP made "
        "over a time range."
    ),
    "subject": "the path / ip / status_class, or null (null for top_ips and anomalies)",
    "from": "ISO-8601 datetime or null; for top_ips this is the window start",
    "to": "ISO-8601 datetime or null; null for top_ips and anomalies",
    "day": "YYYY-MM-DD or null; only set for anomalies",
    "n": "integer or null",
}

_TOOL_FOR_METRIC = {
    "hits_for_path": "count_hits",
    "ip_volume": "ip_volume",
    "status_distribution": "status_distribution",
    "top_ips": "top_ips",
    "anomalies": "list_anomalies",
}


def _parse_dt(s):
    return datetime.fromisoformat(s.replace("Z", "+00:00"))


def parse_intent(state: AgentState) -> AgentState:
    system = ("You translate questions about web-server access-log metrics "
              "into a structured intent. The data is NASA web logs from "
              "July 1995; if no time range is given, assume all of July 1995 "
              "(1995-07-01 to 1995-08-01).")
    intent = llm.extract(system, state["question"], _INTENT_SCHEMA)
    return {**state, "intent": intent, "refine_count": state.get("refine_count", 0)}


def select_tool(state: AgentState) -> AgentState:
    intent = state["intent"] or {}
    tool = _TOOL_FOR_METRIC.get(intent.get("metric"))
    if tool is None:
        return {**state, "error": "could not map question to a known metric"}
    args = {}
    try:
        if tool == "count_hits":
            args = {"path": intent["subject"],
                    "frm": _parse_dt(intent["from"]),
                    "to": _parse_dt(intent["to"])}
        elif tool == "ip_volume":
            args = {"client_ip": intent["subject"],
                    "frm": _parse_dt(intent["from"]),
                    "to": _parse_dt(intent["to"])}
        elif tool == "status_distribution":
            args = {"status_class": intent["subject"],
                    "frm": _parse_dt(intent["from"]),
                    "to": _parse_dt(intent["to"])}
        elif tool == "top_ips":
            args = {"window": _parse_dt(intent["from"]),
                    "n": int(intent.get("n") or 10)}
        elif tool == "list_anomalies":
            args = {"day": date.fromisoformat(intent["day"])}
    except (KeyError, TypeError, ValueError, AttributeError) as e:
        return {**state, "tool_name": tool, "tool_args": None,
                "error": None, "intent": {**intent, "_argerr": str(e)}}
    return {**state, "tool_name": tool, "tool_args": args, "error": None}


def execute_tool(state: AgentState) -> AgentState:
    if state.get("tool_args") is None:
        return state
    try:
        result = TOOLS[state["tool_name"]](**state["tool_args"])
        return {**state, "raw_result": result, "error": None}
    except ToolError as e:
        return {**state, "raw_result": None, "error": str(e)}


def _empty(result) -> bool:
    return isinstance(result, list) and len(result) == 0


def _precompute(tool, result):
    """For multi-row aggregate tools, compute totals in Python so the LLM
    receives a small dict with the answer already summed, not raw JSON."""
    if not isinstance(result, list) or not result:
        return None
    try:
        if tool == "count_hits":
            return {
                "total_hits": sum(r.get("hitCount", 0) for r in result),
                "total_bytes": sum(r.get("byteTotal", 0) for r in result),
                "windows_aggregated": len(result),
            }
        if tool == "ip_volume":
            return {
                "total_requests": sum(r.get("requestCount", 0) for r in result),
                "windows_aggregated": len(result),
            }
        if tool == "status_distribution":
            return {
                "total_count": sum(r.get("count", 0) for r in result),
                "windows_aggregated": len(result),
            }
    except (TypeError, KeyError, AttributeError):
        pass
    return None


def compose_answer(state: AgentState) -> AgentState:
    if state.get("error"):
        system = ("Explain plainly to the user that the query could not be "
                  "completed and why. Be brief.")
        ans = llm.complete(system, f"Question: {state['question']}\n"
                                    f"Error: {state['error']}")
        return {**state, "answer": ans}

    tool = state.get("tool_name")
    result = state.get("raw_result")

    if tool == "list_anomalies" and result:
        system = ("Answer in plain English. Summarize these flagged anomalies for an operator. "
                  "For each: the metric, the subject (IP or status class), the observed "
                  "value, the threshold it exceeded, and the window. Be concise. "
                  "Use ONLY the JSON; do not speculate about root cause.")
        user = f"Question: {state['question']}\nAnomalies JSON: {result}"
        return {**state, "answer": llm.complete(system, user)}

    agg = _precompute(tool, result)
    result_for_llm = agg if agg is not None else result
    if agg is not None:
        system = ("Answer the user's question in plain English. "
                  "The data below contains COMPLETE pre-computed totals over the "
                  "entire result set — nothing is missing, nothing is a sample. "
                  "Report the total directly as the answer. Do not invent numbers.")
    else:
        system = ("Answer the user's question in plain English using ONLY the "
                  "provided data. Do not invent numbers. If the result is empty, "
                  "say no data was found for that query.")
    user = (f"Question: {state['question']}\n"
            f"Tool: {tool}\n"
            f"Data: {result_for_llm}")
    return {**state, "answer": llm.complete(system, user)}


def refine(state: AgentState) -> AgentState:
    system = ("The previous query returned nothing or invalid args. Produce a "
              "corrected intent. If a time range was too narrow, widen it "
              "toward all of July 1995. Keep the same metric unless clearly wrong.")
    user = (f"Question: {state['question']}\n"
            f"Previous intent: {state.get('intent')}")
    new_intent = llm.extract(system, user, _INTENT_SCHEMA)
    return {**state, "intent": new_intent,
            "refine_count": state.get("refine_count", 0) + 1}


def route_after_select(state: AgentState) -> str:
    if state.get("error"):
        return "compose_answer"
    if state.get("tool_args") is None:
        return "refine"
    return "execute_tool"


def route_after_execute(state: AgentState) -> str:
    if state.get("error"):
        return "compose_answer"
    if _empty(state.get("raw_result")) and state.get("refine_count", 0) < 2:
        return "refine"
    return "compose_answer"


def build_graph():
    g = StateGraph(AgentState)
    g.add_node("parse_intent", parse_intent)
    g.add_node("select_tool", select_tool)
    g.add_node("execute_tool", execute_tool)
    g.add_node("compose_answer", compose_answer)
    g.add_node("refine", refine)

    g.set_entry_point("parse_intent")
    g.add_edge("parse_intent", "select_tool")
    g.add_conditional_edges("select_tool", route_after_select,
        {"execute_tool": "execute_tool", "refine": "refine",
         "compose_answer": "compose_answer"})
    g.add_conditional_edges("execute_tool", route_after_execute,
        {"refine": "refine", "compose_answer": "compose_answer"})
    g.add_edge("refine", "select_tool")
    g.add_edge("compose_answer", END)
    return g.compile()

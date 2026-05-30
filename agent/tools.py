import os
import httpx
from datetime import datetime, date, timezone

API = os.environ.get("API_BASE_URL", "http://springboot-api:8080")
_TIMEOUT = 15.0


class ToolError(Exception):
    pass


def _iso(dt: datetime) -> str:
    if dt.tzinfo is None:
        dt = dt.replace(tzinfo=timezone.utc)
    return dt.isoformat()


def _get(path: str, params: dict):
    try:
        r = httpx.get(f"{API}{path}", params=params, timeout=_TIMEOUT)
    except httpx.HTTPError as e:
        raise ToolError(f"request failed: {e}")
    if r.status_code == 400:
        raise ToolError(f"bad request: {r.text}")
    if r.status_code >= 500:
        raise ToolError(f"server error {r.status_code}")
    return r.json()


def count_hits(path: str, frm: datetime, to: datetime):
    return _get("/api/metrics/path",
                {"path": path, "from": _iso(frm), "to": _iso(to)})


def ip_volume(client_ip: str, frm: datetime, to: datetime):
    return _get(f"/api/metrics/ip/{client_ip}",
                {"from": _iso(frm), "to": _iso(to)})


def status_distribution(status_class: str, frm: datetime, to: datetime):
    return _get(f"/api/metrics/status/{status_class}",
                {"from": _iso(frm), "to": _iso(to)})


def top_ips(window: datetime, n: int):
    return _get("/api/metrics/top-ips", {"window": _iso(window), "n": n})


def list_anomalies(day: date):
    return _get("/api/anomalies", {"day": day.isoformat()})


TOOLS = {
    "count_hits": count_hits,
    "ip_volume": ip_volume,
    "status_distribution": status_distribution,
    "top_ips": top_ips,
    "list_anomalies": list_anomalies,
}

TOOL_SPECS = {
    "count_hits": {"path": "str", "frm": "datetime", "to": "datetime"},
    "ip_volume": {"client_ip": "str", "frm": "datetime", "to": "datetime"},
    "status_distribution": {"status_class": "str", "frm": "datetime", "to": "datetime"},
    "top_ips": {"window": "datetime", "n": "int"},
    "list_anomalies": {"day": "date"},
}

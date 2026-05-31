import re
from datetime import datetime

_LINE = re.compile(
    r'^(?P<host>\S+)\s+\S+\s+\S+\s+'
    r'\[(?P<ts>[^\]]+)\]\s+'
    r'"(?P<method>\S+)\s+(?P<path>\S+)\s+(?P<proto>[^"]*)"\s+'
    r'(?P<status>\d{3}|-)\s+'
    r'(?P<bytes>\d+|-)\s*$'
)

_TS_FMT = "%d/%b/%Y:%H:%M:%S %z"
_IP = re.compile(r'^\d{1,3}(\.\d{1,3}){3}$')


def parse_line(line):
    m = _LINE.match(line.strip())
    if not m:
        return None
    g = m.groupdict()
    try:
        ts = datetime.strptime(g["ts"], _TS_FMT).isoformat()
    except ValueError:
        return None
    host = g["host"]
    if not _IP.match(host):
        return None
    status = int(g["status"]) if g["status"] != "-" else 0
    nbytes = int(g["bytes"]) if g["bytes"] != "-" else 0
    method = g["method"] if g["method"].isalpha() else "UNKNOWN"
    return {
        "host": host,
        "client_ip": host,
        "timestamp": ts,
        "method": method,
        "path": g["path"],
        "protocol": g["proto"],
        "status": status,
        "bytes": nbytes,
    }

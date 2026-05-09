#!/usr/bin/env python3
import argparse
import json
import secrets
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlparse

state_lock = threading.Lock()
state = {
    "requests": {},
    "order": [],
    "rules": {},
}

stats_lock = threading.Lock()
server_stats = {
    "tokens": 0,
    "tokens_today": 0,
    "tokens_day": 0,
    "entries": [],
    "last_prompt": None,
}


def get_midnight_epoch():
    now = time.localtime()
    midnight = time.struct_time(
        (
            now.tm_year,
            now.tm_mon,
            now.tm_mday,
            0,
            0,
            0,
            now.tm_wday,
            now.tm_yday,
            now.tm_isdst,
        )
    )
    return int(time.mktime(midnight))


def add_tokens(n):
    with stats_lock:
        server_stats["tokens"] += n
        current_day = get_midnight_epoch()
        if current_day != server_stats.get("_day", 0):
            server_stats["tokens_today"] = 0
            server_stats["_day"] = current_day
        server_stats["tokens_today"] += n


def add_entry(text):
    with stats_lock:
        server_stats["entries"].insert(0, text)
        server_stats["entries"] = server_stats["entries"][:8]


def set_last_prompt(prompt):
    with stats_lock:
        server_stats["last_prompt"] = prompt


def now_ms():
    return int(time.time() * 1000)


def make_request(payload):
    request_id = secrets.token_hex(8)
    tool = payload.get("tool", "unknown")
    title = payload.get("title") or tool
    item = {
        "id": request_id,
        "created_at": now_ms(),
        "status": "pending",
        "decision": None,
        "agent": payload.get("agent", "agent"),
        "tool": tool,
        "title": title,
        "detail": payload.get("detail", ""),
        "cwd": payload.get("cwd", ""),
        "metadata": payload.get("metadata", {}),
    }
    with state_lock:
        state["requests"][request_id] = item
        state["order"].append(request_id)
        state["order"] = state["order"][-100:]
    add_tokens(50)
    add_entry(title)
    set_last_prompt({"id": request_id, "tool": tool, "hint": title[:40]})
    return item


def public_state():
    with state_lock:
        items = [
            state["requests"][rid] for rid in state["order"] if rid in state["requests"]
        ]
    pending = [r for r in items if r.get("status") == "pending"]
    running = [r for r in items if r.get("status") == "pending"]
    with stats_lock:
        result = {
            "requests": items,
            "total": len(items),
            "running": len(running),
            "waiting": len(pending),
            "msg": "",
            "entries": list(server_stats["entries"]),
            "tokens": server_stats["tokens"],
            "tokens_today": server_stats["tokens_today"],
            "prompt": server_stats["last_prompt"],
        }
    if pending:
        last = pending[-1]
        result["msg"] = "approve: " + last.get("tool", "?")
        result["prompt"] = {
            "id": last["id"],
            "tool": last.get("tool", ""),
            "hint": (last.get("detail") or last.get("title", ""))[:40],
        }
    return result


class Handler(BaseHTTPRequestHandler):
    server_version = "AgentCodeBuddy/0.1"

    def log_message(self, fmt, *args):
        if self.server.quiet:
            return
        super().log_message(fmt, *args)

    def send_json(self, code, data):
        body = json.dumps(data, ensure_ascii=False).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(body)

    def read_json(self):
        length = int(self.headers.get("Content-Length", "0"))
        if length <= 0:
            return {}
        raw = self.rfile.read(length)
        return json.loads(raw.decode("utf-8"))

    def do_GET(self):
        path = urlparse(self.path).path
        if path == "/api/state":
            return self.send_json(200, public_state())
        if path == "/health":
            return self.send_json(200, {"ok": True})
        return self.send_json(404, {"error": "not_found"})

    def do_POST(self):
        path = urlparse(self.path).path
        try:
            if path == "/api/request":
                payload = self.read_json()
                item = make_request(payload)
                return self.send_json(200, item)
            if path.startswith("/api/decide/"):
                request_id = path.rsplit("/", 1)[-1]
                payload = self.read_json()
                decision = payload.get("decision")
                if decision not in {"allow_once", "always_allow", "reject"}:
                    return self.send_json(400, {"error": "bad_decision"})
                with state_lock:
                    item = state["requests"].get(request_id)
                    if not item:
                        return self.send_json(404, {"error": "not_found"})
                    item["status"] = "decided"
                    item["decision"] = decision
                    item["decided_at"] = now_ms()
                add_tokens(100)
                add_entry(decision + " " + item.get("tool", ""))
                if decision in {"allow_once", "always_allow"}:
                    set_last_prompt(None)
                return self.send_json(200, item)
            if path.startswith("/api/wait/"):
                request_id = path.rsplit("/", 1)[-1]
                payload = self.read_json()
                timeout = float(payload.get("timeout", 300))
                deadline = time.time() + timeout
                while time.time() < deadline:
                    with state_lock:
                        item = state["requests"].get(request_id)
                        if not item:
                            return self.send_json(404, {"error": "not_found"})
                        if item["status"] == "decided":
                            return self.send_json(200, item)
                    time.sleep(0.2)
                return self.send_json(408, {"error": "timeout", "id": request_id})
        except json.JSONDecodeError:
            return self.send_json(400, {"error": "bad_json"})
        except Exception as exc:
            return self.send_json(500, {"error": str(exc)})
        return self.send_json(404, {"error": "not_found"})


def main():
    parser = argparse.ArgumentParser(description="Agent Code Buddy approval bridge")
    parser.add_argument("--host", default="0.0.0.0")
    parser.add_argument("--port", type=int, default=8765)
    parser.add_argument("--quiet", action="store_true")
    args = parser.parse_args()

    httpd = ThreadingHTTPServer((args.host, args.port), Handler)
    httpd.quiet = args.quiet
    print(f"Agent Code Buddy listening on http://{args.host}:{args.port}", flush=True)
    httpd.serve_forever()


if __name__ == "__main__":
    main()

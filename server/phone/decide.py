#!/usr/bin/env python3
import json
import sys
import urllib.request
import subprocess


def main():
    if len(sys.argv) != 4:
        return 2
    server, request_id, decision = sys.argv[1:]
    body = json.dumps({"decision": decision}).encode("utf-8")
    request = urllib.request.Request(
        f"{server}/api/decide/{request_id}",
        data=body,
        headers={"Content-Type": "application/json"},
    )
    try:
        with urllib.request.urlopen(request, timeout=8) as response:
            response.read()
        subprocess.run(["termux-toast", f"Code Buddy: {decision}"], check=False)
        return 0
    except Exception as exc:
        subprocess.run(["termux-toast", f"Code Buddy error: {exc}"], check=False)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())

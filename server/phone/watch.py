#!/usr/bin/env python3
import argparse
import hashlib
import json
import os
import subprocess
import time
import urllib.request


def get_json(url):
    with urllib.request.urlopen(url, timeout=8) as response:
        return json.loads(response.read().decode("utf-8"))


def ensure_scripts(args):
    os.makedirs(args.install_dir, exist_ok=True)

    open_ui_path = os.path.join(args.install_dir, "open-ui.sh")
    if not os.path.exists(open_ui_path):
        with open(open_ui_path, "w") as f:
            f.write("#!/bin/bash\n")
            f.write(
                'am start -a android.intent.action.VIEW -d "$BUDDY_UI_URL" '
                "--activity-clear-top --activity-single-top --activity-reorder-to-front "
                "> /dev/null 2>&1\n"
            )
        os.chmod(open_ui_path, 0o755)

    decide_path = os.path.join(args.install_dir, "decide.sh")
    if not os.path.exists(decide_path):
        with open(decide_path, "w") as f:
            f.write("#!/bin/bash\n")
            f.write(f'cd "{os.path.dirname(os.path.abspath(__file__))}"\n')
            f.write('python3 decide.py "$@"\n')
        os.chmod(decide_path, 0o755)


def notify(args, item):
    title = f"{item.get('agent', 'agent')}: {item.get('tool', 'tool')}"
    detail = item.get("detail") or item.get("title") or "Approval request"
    first_line = (
        detail.strip().splitlines()[0] if detail.strip() else "Approval request"
    )
    if len(first_line) > 120:
        first_line = first_line[:117] + "..."
    request_id = item["id"]
    numeric_id = int(hashlib.sha1(request_id.encode()).hexdigest()[:7], 16)
    decide = os.path.join(args.install_dir, "decide.sh")
    open_ui_script = os.path.join(args.install_dir, "open-ui.sh")
    open_ui = f"BUDDY_UI_URL={args.ui_url} {open_ui_script}"
    cmd = [
        "termux-notification",
        "--id",
        str(numeric_id),
        "--channel",
        "agent-code-buddy",
        "--priority",
        "high",
        "--sound",
        "--vibrate",
        "120,80,120",
        "--title",
        title,
        "--content",
        first_line,
        "--action",
        open_ui,
        "--button1",
        "Allow",
        "--button1-action",
        f"{decide} {args.server} {request_id} allow_once",
        "--button2",
        "Always",
        "--button2-action",
        f"{decide} {args.server} {request_id} always_allow",
        "--button3",
        "Reject",
        "--button3-action",
        f"{decide} {args.server} {request_id} reject",
        "--on-delete",
        open_ui,
    ]
    subprocess.run(cmd, check=False)


def main():
    parser = argparse.ArgumentParser(
        description="Agent Code Buddy phone notification watcher"
    )
    parser.add_argument(
        "--server", default=os.environ.get("BUDDY_URL", "http://127.0.0.1:8765")
    )
    parser.add_argument("--ui-url", default=os.environ.get("BUDDY_UI_URL"))
    parser.add_argument(
        "--install-dir", default=os.path.expanduser("~/agent-code-buddy-phone")
    )
    parser.add_argument("--interval", type=float, default=1.5)
    args = parser.parse_args()
    if not args.ui_url:
        args.ui_url = args.server

    ensure_scripts(args)
    seen = set()
    subprocess.run(
        ["termux-notification-channel", "agent-code-buddy", "Agent Code Buddy"],
        check=False,
    )
    while True:
        try:
            data = get_json(f"{args.server}/api/state")
            pending = [
                item
                for item in data.get("requests", [])
                if item.get("status") == "pending"
            ]
            pending_ids = {item["id"] for item in pending}
            seen &= pending_ids
            for item in pending:
                if item["id"] not in seen:
                    notify(args, item)
                    seen.add(item["id"])
        except Exception as exc:
            subprocess.run(
                [
                    "termux-notification",
                    "--id",
                    "8765001",
                    "--channel",
                    "agent-code-buddy",
                    "--alert-once",
                    "--title",
                    "Code Buddy disconnected",
                    "--content",
                    str(exc)[:160],
                ],
                check=False,
            )
            time.sleep(5)
        time.sleep(args.interval)


if __name__ == "__main__":
    main()

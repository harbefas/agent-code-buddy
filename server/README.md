# Agent Code Buddy Server

Python HTTP API server. Receives tool-use approval requests from agents and exposes endpoints for the Android app.

## Run

```bash
python3 server.py --port 8765
```

## API

- `GET /api/state` — Returns current requests and stats
- `POST /api/request` — Submit a new approval request
- `POST /api/decide/<id>` — Approve/reject a request
- `POST /api/wait/<id>` — Long-poll for a decision
- `GET /health` — Health check

## Request approval

```bash
./bin/buddy-request --agent codex --tool Bash --title "Run command" --detail "git status"
```

Exit codes:

- `0`: allowed
- `1`: rejected
- `2`: bridge error or timeout

## Claude Code PreToolUse hook

Example `~/.claude/settings.json` snippet:

```json
{
  "hooks": {
    "PreToolUse": [
      {
        "matcher": "Bash|Edit|Write",
        "hooks": [
          {
            "type": "command",
            "command": "BUDDY_URL=http://127.0.0.1:8765 /path/to/agent-code-buddy/server/bin/claude-pretooluse-hook"
          }
        ]
      }
    ]
  }
}
```

Reference: https://docs.claude.com/en/docs/claude-code/hooks

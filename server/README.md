# Agent Code Buddy

Phone approval surface for local coding agents. It is intentionally generic: Codex, Claude Code, shell scripts, or any other agent can request an approval through the same bridge.

## Run

```bash
cd /home/nfvelten/code/personal/agent-code-buddy
./server.py --host 0.0.0.0 --port 8765
```

Open this on the phone browser:

```text
http://YOUR_ARCH_IP:8765
```

## Ask for approval

```bash
./bin/buddy-request --agent codex --tool Bash --title "Run command" --detail "git status"
```

Exit codes:

- `0`: allowed
- `1`: rejected
- `2`: bridge error or timeout

## Claude Code hook shape

Use `bin/buddy-request` from a `PreToolUse` hook. The hook adapter can translate Claude's JSON input to `--tool`, `--title`, and `--detail`, then block until the phone decision returns.

## Notes

This first version keeps state in memory. Restarting the server clears pending requests and history.

## Claude Code PreToolUse hook

Claude Code supports `PreToolUse` hooks that return JSON with `hookSpecificOutput.permissionDecision` set to `allow`, `deny`, or `ask`.

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
            "command": "BUDDY_URL=http://127.0.0.1:8765 /home/nfvelten/code/personal/agent-code-buddy/bin/claude-pretooluse-hook"
          }
        ]
      }
    ]
  }
}
```

Reference: https://docs.claude.com/en/docs/claude-code/hooks

## Phone notification watcher

The LineageOS phone runs a Termux watcher that polls the bridge and creates Android notifications through Termux:API.

Phone files:

```text
~/agent-code-buddy-phone/watch.py
~/agent-code-buddy-phone/decide.py
~/agent-code-buddy-phone/open-ui.sh
~/agent-code-buddy-phone/decide.sh
~/bin/start-code-buddy-watch
~/.termux/boot/start-code-buddy
```

Manual start from the phone or SSH:

```bash
nohup ~/bin/start-code-buddy-watch >~/code-buddy-watch.log 2>&1 &
```

Termux:Boot starts it automatically on phone boot. The notification body opens the approval UI in LineageOS Jelly using root `am start`; notification buttons can approve/reject directly.

## On-demand mode

Preferred mode: no persistent bridge on the PC.

```bash
./bin/buddy-ask --agent codex --tool Bash --title "Run command" --detail "git status"
```

What happens:

1. `buddy-ask` starts a temporary HTTP server on a random local port.
2. It sends a Termux notification to the phone over SSH.
3. The notification opens the temporary approval UI or approves/rejects directly.
4. `buddy-ask` exits and the temporary server stops.

The phone polling watcher is not required for this mode and has been disabled from Termux:Boot.

## Persistent history

On-demand decisions are appended to:

```text
/home/nfvelten/code/personal/agent-code-buddy/history.jsonl
```

Override with:

```bash
BUDDY_HISTORY=/path/to/history.jsonl ./bin/buddy-ask --agent codex --tool Bash --detail "git status"
```

Each line is one JSON object containing request metadata, decision, and timestamps.

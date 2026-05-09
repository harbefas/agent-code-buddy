# Agent Code Buddy

Companion approval surface for local coding agents. Server runs on your PC, native Android app receives approval requests and posts notifications.

## Structure

```
.
├── server/      Python HTTP API server
└── android/     Native Android app (APK)
```

## Server

Runs on your PC. Receives tool-use approval requests from agents and exposes an API for the Android app.

```bash
cd server
python3 server.py --port 8765
```

### API Endpoints

- `GET /api/state` — Returns current requests and stats
- `POST /api/request` — Submit a new approval request
- `POST /api/decide/<id>` — Approve/reject a request
- `POST /api/wait/<id>` — Long-poll for a decision
- `GET /health` — Health check

## Android App

Native Android companion that receives approval broadcasts, posts notifications, and lets you approve/deny directly from the notification shade.

### Build

```bash
cd android
./build.sh
```

Outputs: `android/build/agent-code-buddy-signed.apk`

### Install over SSH (Termux)

```bash
scp -P 8022 android/build/agent-code-buddy-signed.apk \
  u0_a186@<phone-ip>:~/agent-code-buddy.apk

ssh -p 8022 u0_a186@<phone-ip> \
  'su -c "pm install -r /data/data/com.termux/files/home/agent-code-buddy.apk"'
```

### Broadcast API

From Termux or PC:

```bash
adb shell am broadcast \
  -n com.nfvelten.agentcodebuddy/.ApprovalReceiver \
  -a com.nfvelten.agentcodebuddy.APPROVAL \
  --es url "http://<pc-ip>:12345" \
  --es request_id "abc123" \
  --es title "Code Buddy · codex" \
  --es content "Bash · git status"
```

## License

MIT

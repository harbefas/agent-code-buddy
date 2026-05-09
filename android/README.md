# Agent Code Buddy Android

Native Android companion for Agent Code Buddy.

The app receives approval requests from Termux/SSH broadcasts, posts a native
Android notification with the buddy icon, and opens the approval UI inside an
embedded WebView.

## Build

```sh
./build.sh
```

The signed debug APK is written to:

```text
build/agent-code-buddy-signed.apk
```

## Install over Termux SSH

```sh
scp -F /dev/null -i ~/.ssh/termux_moto_g7 -P 8022 \
  build/agent-code-buddy-signed.apk \
  u0_a186@192.168.237.112:~/agent-code-buddy.apk

ssh -F /dev/null -i ~/.ssh/termux_moto_g7 -p 8022 \
  u0_a186@192.168.237.112 \
  'su -c "pm install -r /data/data/com.termux/files/home/agent-code-buddy.apk"'

ssh -F /dev/null -i ~/.ssh/termux_moto_g7 -p 8022 \
  u0_a186@192.168.237.112 \
  'su -c "pm grant com.nfvelten.agentcodebuddy android.permission.POST_NOTIFICATIONS"'
```

## Broadcast API

Termux can create a native approval notification with:

```sh
am broadcast \
  -n com.nfvelten.agentcodebuddy/.ApprovalReceiver \
  -a com.nfvelten.agentcodebuddy.APPROVAL \
  --es url "http://192.168.237.147:12345" \
  --es request_id "abc123" \
  --es title "Code Buddy · codex" \
  --es content "Bash · git status"
```

Notification actions post decisions directly to:

```text
<url>/api/decide/<request_id>
```

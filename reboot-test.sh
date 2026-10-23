#!/bin/bash
cd "$(dirname "$0")" || exit 1
ADB=./platform-tools/adb
TV="${TV:?先设置 TV=<电视IP>}"

echo "[reboot] $(date +%T) 重启电视"
"$ADB" reboot
sleep 8

echo "[wait] 等待电视回来..."
for i in $(seq 1 90); do
  if nc -z -G 2 "$TV" 5555 2>/dev/null; then echo "[ok] 5555 端口 $(date +%T)"; break; fi
  sleep 2
done

"$ADB" kill-server >/dev/null 2>&1; "$ADB" start-server >/dev/null 2>&1
STATE=""
for i in $(seq 1 60); do
  "$ADB" connect "$TV:5555" >/dev/null 2>&1
  STATE=$("$ADB" devices | awk -v t="$TV:5555" '$1==t {print $2}')
  [ "$STATE" = "device" ] && break
  sleep 3
done
echo "[state] $STATE"
[ "$STATE" != "device" ] && { echo "[fail] 重启后连不上 adb"; exit 2; }

sleep 6
echo "=== 开机后焦点 ==="
"$ADB" shell dumpsys window 2>/dev/null | grep mCurrentFocus | head -2 | tr -d '\r'
echo "=== 属性 ==="
for p in persist.service.homepage.pkg persist.service.homepage.cls; do echo -n "$p = "; "$ADB" shell getprop $p 2>/dev/null | tr -d '\r'; done
"$ADB" exec-out screencap -p > after-boot.png
echo "[done] 截图 after-boot.png $(date +%T)"

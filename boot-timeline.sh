#!/bin/bash
cd "$(dirname "$0")" || exit 1
ADB=./platform-tools/adb; TV="${TV:?先设置 TV=<电视IP>}"
"$ADB" reboot; sleep 10
for i in $(seq 1 90); do nc -z -G 2 "$TV" 5555 2>/dev/null && break; sleep 2; done
"$ADB" kill-server >/dev/null 2>&1; "$ADB" start-server >/dev/null 2>&1
for i in $(seq 1 60); do "$ADB" connect "$TV:5555" >/dev/null 2>&1; S=$("$ADB" devices | awk -v t="$TV:5555" '$1==t{print $2}'); [ "$S" = "device" ] && break; sleep 3; done
echo "[state] $S $(date +%T)"
for i in $(seq 1 12); do
  F=$("$ADB" shell dumpsys window 2>/dev/null | grep mCurrentFocus | head -1 | tr -d '\r' | sed 's/.*u0 //')
  P=$("$ADB" shell getprop persist.service.homepage.pkg 2>/dev/null | tr -d '\r')
  echo "$(date +%T)  focus=${F:-?}  prop=${P:-?}"
  sleep 2
done

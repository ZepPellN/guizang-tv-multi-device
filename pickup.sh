#!/bin/bash
# 等待电视打开 ADB，然后自动安装 guizang-tv 并采集设备信息
cd "$(dirname "$0")" || exit 1

ADB=./platform-tools/adb
TV="${TV:?先设置 TV=<电视IP>}"
APK=guizang-tv-v0.3.0.apk
INFO=tv-info.txt

echo "[wait] 等待 $TV:5555 打开（最多约 20 分钟）..."
n=0
while true; do
  if nc -z -G 2 "$TV" 5555 2>/dev/null; then echo "[ok] 5555 已开放"; break; fi
  n=$((n+1))
  if [ "$n" -gt 600 ]; then echo "[timeout] 等待超时，ADB 没有被打开"; exit 1; fi
  sleep 2
done

"$ADB" kill-server >/dev/null 2>&1
"$ADB" start-server >/dev/null 2>&1

STATE=""
for i in $(seq 1 80); do
  "$ADB" connect "$TV:5555" >/dev/null 2>&1
  STATE=$("$ADB" devices | awk -v t="$TV:5555" '$1==t {print $2}')
  echo "[state] ${STATE:-none}"
  [ "$STATE" = "device" ] && break
  [ "$STATE" = "unauthorized" ] && echo "[action] 请在电视屏幕上选择『允许』（建议勾选始终允许）"
  sleep 3
done

if [ "$STATE" != "device" ]; then
  echo "[fail] 未能获得 ADB 授权，最后状态：${STATE:-none}"
  exit 2
fi

echo "[info] 采集设备信息 -> $INFO"
{
  echo "== collected $(date '+%F %T') =="
  for k in ro.product.model ro.product.brand ro.product.manufacturer \
           ro.build.version.release ro.build.version.sdk ro.build.display.id \
           ro.build.fingerprint ro.product.cpu.abi ro.serialno; do
    printf '%s: %s\n' "$k" "$("$ADB" shell getprop $k 2>/dev/null | tr -d '\r')"
  done
  echo "wm size: $("$ADB" shell wm size 2>/dev/null | tr -d '\r' | tr '\n' ' ')"
  echo "wm density: $("$ADB" shell wm density 2>/dev/null | tr -d '\r' | tr '\n' ' ')"
  echo "== third-party packages =="
  "$ADB" shell pm list packages -3 2>/dev/null | tr -d '\r' | sort
  echo "== home / launcher candidates =="
  "$ADB" shell cmd package resolve-activity --brief -c android.intent.category.HOME -a android.intent.action.MAIN 2>/dev/null | tr -d '\r'
  "$ADB" shell dumpsys package | grep -A2 -E 'android.intent.category.HOME' 2>/dev/null | head -20
} > "$INFO" 2>&1

echo "[install] 安装 $APK ..."
"$ADB" install -r "$APK" 2>&1 | tail -5

echo "[launch] 启动归藏电视 ..."
"$ADB" shell am start -n io.github.op7418.guizangtv/.FrameHomeActivity 2>&1 | tail -5

echo "[done] 看看电视上是否出现归藏电视界面"

#!/usr/bin/env bash
# 一键安装归藏电视到局域网里的 Android 电视。
#
#   TV=192.168.1.20 ./install.sh
#
# 需要：adb（脚本会优先用仓库里的 platform-tools/adb）；若还没有构建过 APK，
# 还需要 JAVA_HOME + ANDROID_HOME + gradle（或仓库内的 build-env/）。
set -euo pipefail
cd "$(dirname "$0")"

PKG=io.github.op7418.guizangtv
ACTIVITY="$PKG/.FrameHomeActivity"
APK=guizang-tv/app/build/outputs/apk/debug/app-debug.apk

step() { printf '\n\033[1;36m== %s\033[0m\n' "$*"; }
fail() { printf '\033[1;31m错误：%s\033[0m\n' "$*" >&2; exit 1; }

# ---------- 参数 ----------
[ -n "${TV:-}" ] || fail "用法: TV=<电视IP> ./install.sh"

ADB="${ADB:-}"
if [ -z "$ADB" ]; then
  if [ -x platform-tools/adb ]; then ADB=platform-tools/adb
  elif command -v adb >/dev/null 2>&1; then ADB=adb
  else fail "找不到 adb。下载 Android Platform Tools 放到 platform-tools/，或安装 adb 后用 ADB=/path/to/adb 指定。"
  fi
fi

# ---------- 1. 连接 ----------
step "1/5 连接电视 $TV  (adb: $ADB)"
"$ADB" start-server >/dev/null 2>&1 || true
"$ADB" connect "$TV:5555" >/dev/null 2>&1 || true

state=""
for _ in $(seq 1 20); do
  state=$("$ADB" devices | awk -v t="$TV:5555" '$1 == t { print $2 }')
  [ "$state" = "device" ] && break
  [ "$state" = "unauthorized" ] && echo "  请在电视屏幕上选择「允许 USB 调试」（建议勾选「始终允许」）…"
  sleep 2
done
[ "$state" = "device" ] || fail "连不上 $TV:5555（状态：${state:-未连接}）。检查：电视已打开 ADB 调试、电脑与电视同一局域网、没有 AP 隔离。"

# ---------- 2. 识别设备 ----------
step "2/5 识别设备"
prop() { "$ADB" -s "$TV:5555" shell getprop "$1" 2>/dev/null | tr -d '\r'; }
MANUFACTURER=$(prop ro.product.manufacturer)
BRAND=$(prop ro.product.brand)
MODEL=$(prop ro.product.model)
SDK=$(prop ro.build.version.sdk)
printf '  厂商: %s\n  品牌: %s\n  型号: %s\n  Android API: %s\n' \
  "${MANUFACTURER:-?}" "${BRAND:-?}" "${MODEL:-?}" "${SDK:-?}"
if [ -n "${SDK:-}" ] && [ "$SDK" -lt 26 ] 2>/dev/null; then
  fail "需要 Android 8（API 26）以上，这台是 API $SDK。"
fi

# ---------- 3. APK ----------
step "3/5 准备 APK"
if [ -d build-env ]; then
  JAVA_HOME="${JAVA_HOME:-$PWD/build-env/jdk-17.0.21+9/Contents/Home}"
  ANDROID_HOME="${ANDROID_HOME:-$PWD/build-env/sdk}"
  [ -x build-env/gradle-8.9/bin/gradle ] && GRADLE="${GRADLE:-$PWD/build-env/gradle-8.9/bin/gradle}"
fi

needs_build=1
if [ -f "$APK" ] && [ -z "$(find guizang-tv/app/src guizang-tv/app/build.gradle -newer "$APK" -print -quit 2>/dev/null)" ]; then
  needs_build=0
  echo "  使用已有 APK：$APK"
fi

if [ "$needs_build" = 1 ]; then
  GRADLE="${GRADLE:-$(command -v gradle || true)}"
  [ -n "$GRADLE" ] && [ -x "$GRADLE" ] || fail "需要构建：找不到 gradle。装 Gradle 8.9 并设 GRADLE=/path/to/gradle，或把已构建的 APK 放到 $APK。"
  [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ] || fail "需要构建：JAVA_HOME 未指向 JDK 17。"
  [ -n "${ANDROID_HOME:-}" ] || fail "需要构建：ANDROID_HOME 未设置（需要 Android SDK Platform 34 + Build Tools 34.0.0）。"
  echo "  构建中（首次会下载依赖）…"
  JAVA_HOME="$JAVA_HOME" ANDROID_HOME="$ANDROID_HOME" "$GRADLE" -p guizang-tv :app:assembleDebug -q
  [ -f "$APK" ] || fail "构建结束但没找到 $APK"
fi

# ---------- 4. 安装 ----------
step "4/5 安装"
install_log=$(mktemp)
if ! "$ADB" -s "$TV:5555" install -r "$APK" >"$install_log" 2>&1; then
  if grep -qiE "INSTALL_FAILED_UPDATE_INCOMPATIBLE|signatures do not match" "$install_log"; then
    echo "  电视上已有不同签名的同名应用，先卸载再装（会清掉应用内设置）"
    "$ADB" -s "$TV:5555" uninstall "$PKG" >/dev/null 2>&1 || true
    "$ADB" -s "$TV:5555" install "$APK" >"$install_log" 2>&1 || { cat "$install_log"; fail "安装失败"; }
  else
    cat "$install_log"; fail "安装失败"
  fi
fi
grep -q "Success" "$install_log" && echo "  已安装" || { cat "$install_log"; fail "安装失败"; }

# ---------- 5. 机型配置 ----------
step "5/5 按机型做一次性配置"

# 出厂固件一律先设成系统默认桌面（部分品牌会忽略，应用启动后会自己接管）
"$ADB" -s "$TV:5555" shell pm set-home-activity "$ACTIVITY" >/dev/null 2>&1 \
  && echo "  已设为系统默认桌面" \
  || echo "  未能设置默认桌面（可稍后在系统设置里手动选择）"

case "$(printf '%s %s' "$MANUFACTURER" "$BRAND" | tr '[:upper:]' '[:lower:]')" in
  *skyworth*|*coocaa*)
    # 创维/酷开：开机首页由 /system/etc/bootintent.xml 的 modelhome 规则决定
    "$ADB" -s "$TV:5555" shell setprop persist.service.model.pkg "$PKG"
    "$ADB" -s "$TV:5555" shell setprop persist.service.model.cls "$PKG.FrameHomeActivity"
    echo "  已写入开机首页属性（persist.service.model.*）"
    echo "  说明：Home 键用的 persist.service.homepage.* 由应用回到前台时自己认领"
    ;;
  *)
    echo "  没有该品牌的额外配置；如果 Home 键仍回原桌面，见 README 的排查一节"
    ;;
esac

"$ADB" -s "$TV:5555" shell am start -n "$ACTIVITY" >/dev/null 2>&1 || true

step "完成，请在电视上逐项确认"
cat <<'EOF'
  1. 首页出现列表，条目和你在 assets/devices.json 里配置的一致
  2. 每个入口都能打开（包括「打开原来的电视首页」）
  3. 主列表按返回 → 回到原桌面
  4. 从任意应用按 Home → 回到归藏
  5. 完整重启一次 → 直接进归藏

  有入口打不开：多半是包名/组件不对，改 guizang-tv/app/src/main/assets/devices.json，
  见 guizang-tv/docs/DEVICE-PROFILES.md。
  想还原：TV=<电视IP> ./restore-stock.sh（创维）或卸载 APK。
EOF

# 客厅电视定制记录

## 设备

| 项 | 值 |
| --- | --- |
| 地址 | `<电视IP>`（局域网，DHCP） |
| 型号 | 创维 `1AA02_BC20` 商用电视（Skyworth / Coocaa） |
| 系统 | Android 9（API 28），MediaTek T963，`armeabi-v7a` |
| 屏幕 | 1920×1080，density 240 |
| 原桌面 | `com.tianci.movieplatform/com.coocaa.homepage.vast.RealHomePageActivity` |
| ADB | 已开启，`platform-tools/adb connect <电视IP>:5555` |

> 公开仓库不含设备序列号、局域网 IP、个人照片；这些只留在本机。

ADB 打开方式：设置 → 通用 → 关于本机 → 本机信息 → 更多信息 → 遥控器慢按 `上上下下左右左右` → 工厂菜单 → 通用设置 → 单键开关 + ADB 开关 → 长按电源键重启。

## 装的是什么

`guizang-tv`（归藏电视，op7418/guizang-tv）的本地定制版，MIT 许可。
包名 `io.github.op7418.guizangtv`，versionName `0.4.0-dev`，debug 签名（本机 `~/.android/debug.keystore`）。

- `guizang-tv-v0.3.0.apk`：官方发布包，SHA256 已校验，仅用于对照。
- `guizang-tv/`：定制后的源码。
- `guizang-tv/app/build/outputs/apk/debug/app-debug.apk`：当前电视上装的包。

## 仓库

`tv-custom/` 是独立 git 仓库（父目录 `AI/project` 已把 `tv-custom/` 加进 `.gitignore`）。

| 路径 | 内容 |
| --- | --- |
| `guizang-tv/` | 定制源码；设备配置见 `docs/DEVICE-PROFILES.md` |
| `patches/` | 相对上游 `312c011` 的完整 patch 与说明 |
| `wallpapers/` | 壁纸原图，本机私有，不入库 |
| `build-env/` `platform-tools/` | 本机构建环境与 adb，不入库 |

## 相对上游的改动

完整差异：[patches/0001-skyworth-bc20-device-profiles.patch](patches/0001-skyworth-bc20-device-profiles.patch)，说明和基线提交见 [patches/README.md](patches/README.md)。

核心是把上游写死的小米逻辑抽成设备配置：

1. **新增 `DeviceProfile` + `assets/devices.json`**：按 `Build.MANUFACTURER / BRAND / MODEL` 匹配机型，配置首页默认项、原桌面组件、HDMI、信号源、设置 Intent、要抢回的 system property。加新电视只改 JSON，不改 Java。详见 `guizang-tv/docs/DEVICE-PROFILES.md`。
2. **删除 `XiaomiControls`**：小米逻辑原样搬进 `xiaomi-mitv-mtsu0` 配置，行为保持不变。
3. **新增 `skyworth-coocaa-bc20` 配置**：这台用的。首页只留「哔哩哔哩 + 创维原桌面」，原桌面指向 `com.tianci.movieplatform/com.coocaa.homepage.vast.RealHomePageActivity`；没有可用的 HDMI / 信号源组件；靠 `claims` 抢回 Home 键。
4. **首页默认项来自配置**：未知机型回落到「自动取前 6 个已安装应用」，不再是小米那 6 个空包名。
5. **设置页文案**：`homeLabel` 决定显示「创维原桌面」还是「小米原桌面」。
6. **新增 `BootReceiver`**：开机广播兜底，本机被厂商拦截未生效，保留给其他固件。
7. **壁纸**：自有照片打进 APK（图片本身不入库），内置那套仍保留。

## 壁纸

本机用了 5 张自有风景照，**图片不进仓库**（`wallpapers/*.jpg` 已在 `.gitignore` 里）。要复现同样的桌面，把自己的图片放进 `wallpapers/`，文件名即电视上显示的名字，然后运行上游的壁纸脚本：

```sh
cd guizang-tv
python3 scripts/prepare_wallpapers.py ../wallpapers     # 需要 Pillow
```

脚本输出最长边 1920 的 JPEG（质量 88）和 320×180 缩略图，写到 `app/src/main/assets/wallpapers/local/`；重复运行会把上一份移到 `.wallpaper-backups/`。生成物同样不入库。

换图后重新构建安装即可，`adb install -r` 会保留应用内设置。壁纸选择在「设置 → 背景 → 壁纸」，轮播默认 5 分钟。

## 开机和 Home 都进归藏（关键机制）

这台电视不走 Android 标准的主页机制，两层都是厂商私有的：

| 场景 | 厂商机制 | 怎么改的 |
| --- | --- | --- |
| Home 键 | 系统读 `persist.service.homepage.pkg` / `.cls` 直接启动，忽略默认 HOME | 应用回到前台时自己 `setprop` 改成归藏 |
| 开机首页 | `/system/etc/bootintent.xml` 按顺序匹配条件，命中就启动对应组件 | 用 `modelhome` 规则：`persist.service.model.pkg` / `.cls` 同时存在就启动它们 |

```sh
# 开机首页：bootintent.xml 的 modelhome 规则（persist 属性，跨重启保留）
adb shell setprop persist.service.model.pkg io.github.op7418.guizangtv
adb shell setprop persist.service.model.cls io.github.op7418.guizangtv.FrameHomeActivity
```

另外把系统默认 HOME 也设成了归藏（`pm set-home-activity`），作为 bootintent 全部不匹配时的回落。

开机顺序是：bootintent 命中 modelhome → 启动归藏 → 归藏 onResume 里把 homepage 属性占回来 → 之后 Home 键也回归藏。

恢复原样：`TV=<电视IP> ./restore-stock.sh`。

## 重新构建

```sh
export JAVA_HOME=$PWD/build-env/jdk-17.0.21+9/Contents/Home
export ANDROID_HOME=$PWD/build-env/sdk
build-env/gradle-8.9/bin/gradle -p guizang-tv :app:assembleDebug
```

`build-env/`（JDK 17.0.21 / Android SDK 34 / Gradle 8.9）不进 git。
`local.properties` 里的 `sdk.dir` 指向 `${PWD}/build-env/sdk`。

注意：debug 签名与官方 release 签名不同，覆盖安装前需要先 `adb uninstall`，会清掉应用内设置。

## 已验证 / 未验证

已验证（实机截图 + `mCurrentFocus`）：

- 首页只剩「哔哩哔哩 / 创维原桌面」两项，渲染正常。
- 哔哩哔哩 → 云视听小电视启动。
- 创维原桌面 → `RealHomePageActivity` 启动。
- 主列表按返回 → 创维原桌面。
- 完整重启后直接进归藏桌面，重启后属性仍是归藏（`persist.service.model.*` 未被重置）。
- 重启后从云视听小电视按 Home → 回归藏桌面。
- 5 张自有壁纸已打进 APK，设置里的壁纸列表显示名称和缩略图正确。

未验证 / 已知限制：

- 右下角「其他信号源」仍走小米 `SettingsBroadcastReceiver`，在这台电视上只会弹提示，没有接创维的信号源组件。
- 壁纸轮播（默认 5 分钟）没有连续观察整轮切换。
- `hdmi:1/2` 入口已从默认项移除，但 `validId` 仍接受该 id（不影响当前使用）。
- `BootReceiver` 在这台上被厂商拦截，实际生效的是 `modelhome` 属性，不是广播。
- 改的是 `persist.*` 系统属性，固件 OTA 后 `bootintent.xml` 或开机流程若变化，需要重新确认。

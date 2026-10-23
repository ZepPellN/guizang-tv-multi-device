# 设备配置（Device Profiles）

上游 `guizang-tv` 把小米的包名、HDMI 组件和启动逻辑写死在 `XiaomiControls.java` 里。这个分支把它抽成配置：**加一台新电视只改 `assets/devices.json`，不改 Java**。

## 怎么选配置

`DeviceProfile` 在应用启动时读取 `app/src/main/assets/devices.json`，按数组顺序拿每条 profile 的 `match` 去比 `Build.MANUFACTURER` / `Build.BRAND` / `Build.MODEL`：

- 大小写不敏感
- 支持 `*` 通配，例如 `"MiTV-*"`
- 三条都匹配才算命中（某条没写则该维度不限制）
- 第一个命中的生效；都不命中就用 `id = "generic"` 那条
- 连 `devices.json` 都读不到时退化成空配置，不崩溃

命中的 profile 会打一条日志：`TvHome.Profile: profile <id> for <brand>/<model>`。

## 字段

| 字段 | 说明 |
| --- | --- |
| `id` / `name` | 标识和人类可读名，`name` 会出现在设置页提示里 |
| `match` | `manufacturer` / `brand` / `model`，每项是字符串数组 |
| `homeLabel` | 设置页里「打开原来的电视首页」那一项显示的名字，如 `创维原桌面` |
| `bootStart` | 是否支持应用内置的「开机自动进入」（小米专用钩子） |
| `home` | 首页默认入口，`[{id, label}]`。`id` 可以是包名、`hdmi:1`、`hdmi:2`。留空则自动取前 6 个已安装应用 |
| `labels` | 包名 → 显示名覆盖，例如把 `com.xiaodianshi.tv.yst` 显示成「哔哩哔哩」 |
| `stockHome` | 原桌面组件 `{component, action}`；首页里出现这个包名时走它 |
| `hdmi` | `{component, action, extra, base, ports}`。切换 `hdmi:N` 时发送 `action` 到 `component`，并带上 `extra: base + N` |
| `inputs` | 「其他信号源」按钮：`{action, component}`，发广播给该组件 |
| `settings` | 标准设置 action → `{action, component}` 的映射，例如 `android.settings.SETTINGS` |
| `claims` | 启动时要抢回的 system property，值里可用 `$package` 占位当前包名 |

## 已内置的配置

### `xiaomi-mitv-mtsu0`（上游行为，原样保留）

对应上游已验证的 MiTV-MTSU0 / Android 14。HDMI 走 `com.xiaomi.mitv.tvplayer.ExternalSourceActivity`，`input = 22 + 端口`；设置页走 `com.xiaomi.mitv.settings`；信号源走 `SettingsBroadcastReceiver` 广播；开机自启钩子可用。

> 只匹配 `MiTV-MTSU0`，和上游一致。其他小米机型仍走 `generic`，不会把一个没验证过的 HDMI 参数套上去。

### `skyworth-coocaa-bc20`（本分支新增）

创维 1AA02_BC20 商用电视（Skyworth / Coocaa，Android 9，MTK T963）。这台的行为和 Android 标准差得比较多：

- **Home 键不走默认桌面**：系统读 `persist.service.homepage.pkg/cls` 直接启动，忽略 `pm set-home-activity`。所以 profile 用 `claims` 在应用回到前台时把这两个属性写回自己。
- **开机首页也不走默认桌面**：`/system/etc/bootintent.xml` 按条件匹配启动项。命中 `modelhome`（`persist.service.model.pkg/cls` 都存在）就会启动指定的组件。这两个属性只能靠 adb 在首次配置时写入，见仓库根目录的 README。
- 没有可用的 HDMI / 信号源组件，所以 `hdmi` 和 `inputs` 都留空，界面上点会提示「暂时无法打开」。
- 固件会在开机时拦截第三方应用的 `BOOT_COMPLETED`（`process is bad`），所以 `bootStart = false`，开机入口靠 `bootintent.xml` 的 `modelhome`。

## 加一台新电视

1. 在 `devices.json` 里加一条 profile，`id` 用「品牌-型号」。
2. 用 adb 查真实组件：
   ```sh
   adb shell getprop ro.product.manufacturer; adb shell getprop ro.product.brand; adb shell getprop ro.product.model
   adb shell pm list packages | grep -i <厂商>
   adb shell dumpsys package <包名> | sed -n '/Activity Resolver Table/,/^ *$/p'
   ```
3. 把 `home` / `labels` / `stockHome` 填成这台机器上真实存在的包和组件。
4. 有 HDMI 或信号源组件的机型再补 `hdmi` / `inputs`；`extra` 的数值逻辑要用实机验证，不要照抄小米的 `22 + port`。
5. 重新构建安装，逐项验证：首页渲染、每个入口能打开、返回键、Home 键、重启后的行为。
6. 只有验证过的行为才写进 `docs/COMPATIBILITY.md` 一类文档，别把"配置里写了"当成"已支持"。

## 还没有配置化的部分

以下仍是代码，不在 JSON 里：

- 壁纸打包与轮播（`FrameWallpapers` / `WallpaperCycle`）
- 布局、字体、焦点样式（`HomeAppearance` / `HomeSurface`）
- Codex 对话实验（`HomeAssistant`）

这些和电视品牌无关，换机型不用动。真正随品牌变化的只有本文件描述的启动、信号源、设置和首页默认项。

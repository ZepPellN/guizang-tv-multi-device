# 相对上游的改动（patch）

`0001-skyworth-bc20-device-profiles.patch` 是 `guizang-tv` 当前源码相对上游的完整差异。

| 项 | 值 |
| --- | --- |
| 上游仓库 | https://github.com/op7418/guizang-tv |
| 基线提交 | `312c011` (2026-10-22) `Adapt frosted white and dark chat capsules from Web materials` |
| 改动规模 | 10 个文件，+551 / −128 |
| 许可证 | MIT（沿用上游） |

## 应用方式

```sh
git clone https://github.com/op7418/guizang-tv.git
cd guizang-tv
git checkout 312c011
git apply /path/to/0001-skyworth-bc20-device-profiles.patch
```

已验证：在 `312c011` 的干净副本上 `git apply --check` 通过。

## 改了什么

| 文件 | 改动 |
| --- | --- |
| `app/src/main/assets/devices.json` | **新增**。按机型描述首页默认项、原桌面组件、HDMI、信号源、设置 Intent、需要抢回的 system property |
| `.../DeviceProfile.java` | **新增**。读取并匹配 `devices.json`，提供 `openStockHome` / `openHdmi` / `showInputs` / `settings` / `claim` 等方法 |
| `.../BootReceiver.java` | **新增**。开机广播里重新认领设备属性并拉起桌面（本机被厂商拦截，见下） |
| `.../XiaomiControls.java` | **删除**。全部逻辑搬进 `devices.json` 的 `xiaomi-mitv-mtsu0` 配置，行为保持不变 |
| `.../FrameHomeActivity.java` | 改为走 `DeviceProfile`：HDMI、信号源、原桌面、设置、返回键、属性认领 |
| `.../HomeItems.java` | 首页默认项来自 profile；未知机型回落到「自动取前 6 个已安装应用」；原桌面入口固定在列表底部  |
| `.../HomeSettings.java` | 「打开原来的电视首页」的文案用 `profile.homeLabel` |
| `.../BootStart.java` | 开机入口的支持判断从「是不是小米」改成 `profile.bootStart`，提示文案用 `profile.name` |
| `AndroidManifest.xml` | 加 `RECEIVE_BOOT_COMPLETED` 和 `BootReceiver` |
| `docs/DEVICE-PROFILES.md` | **新增**。配置 schema、内置机型、怎么加新电视 |

## 内置的两套配置

- `xiaomi-mitv-mtsu0`：上游原有行为，原样搬进 JSON。HDMI 用 `22 + port` 作为 `input`，设置页走 `com.xiaomi.mitv.settings`。
- `skyworth-coocaa-bc20`：本分支新增。首页只留「哔哩哔哩 / 创维原桌面」，HDMI 和信号源留空（这台没有可用组件），靠 `claims` 抢回 `persist.service.homepage.pkg/cls` 来接管 Home 键。

## 不包含在 patch 里的东西

- `app/src/main/assets/wallpapers/local/`：本机壁纸，用 `scripts/prepare_wallpapers.py` 从 `../wallpapers/` 生成，属于个人素材，不进 patch。
- `build-env/`、`platform-tools/`、`local.properties`、构建产物：本机构建环境。

## 已知问题

- `BootReceiver` 在这台创维上不会执行：固件在开机阶段拒绝第三方应用启动进程（`ActivityManager: xxxxxxxxxx stop start proc ... process is bad`）。保留它只是给其他固件兜底，真正生效的是 `bootintent.xml` 的 `modelhome` 属性。
- 创维的 Home 键和开机首页逻辑写在本 patch 之外：需要一次性用 adb 写入 `persist.service.model.pkg/cls`，见仓库根目录 `README.md`。

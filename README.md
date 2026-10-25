# 归藏电视 · 多机型适配（guizang-tv fork）

**这是 [op7418/guizang-tv](https://github.com/op7418/guizang-tv)（归藏电视）的 fork。** 上游是一个给国产电视做的极简桌面：大字列表、壁纸轮播、遥控器操作、可选的 Codex 对话实验。界面、壁纸、设置页、主题和许可证都来自上游，没有改产品形态。

本 fork 只做一件事：**把上游写死的小米适配，改成可配置的多机型版本。**

上游把小米电视的包名、HDMI 组件、启动逻辑写死在 `XiaomiControls.java` 里。这个分支把它抽成 `app/src/main/assets/devices.json`：按 `厂商 / 品牌 / 型号` 匹配机型，内置**小米 MiTV-MTSU0**（上游原样）和**创维/酷开 BC20**（本分支新增）两条配置，**加新电视只改 JSON，不改 Java**。

| | |
| --- | --- |
| 上游 | <https://github.com/op7418/guizang-tv> |
| fork 基线 | `312c011`（2026-10-22，`Adapt frosted white and dark chat capsules from Web materials`） |
| 与上游的差异 | 10 个文件，+551 / −128，完整 patch 见 [`patches/`](patches/) |
| 许可证 | MIT（上游与本分支一致，见 [NOTICE.md](NOTICE.md)） |

---

## 一键安装

### 交给 AI Agent（推荐）

在**能连到电视的电脑**上，把下面整段复制给你常用的 AI（Codex / Claude Code / DeepSeek Harness / Cursor 都行）：

> 请帮我在局域网里的 Android 电视上装这个项目：
> `https://github.com/ZepPellN/guizang-tv-multi-device`
>
> 1. 先读 `README.md` 和 `guizang-tv/docs/DEVICE-PROFILES.md`，确认有没有我这台机型的配置。没有就按文档新增一条 JSON 配置，**不要改 Java**，也不要动壁纸和布局。
> 2. 检查 adb 是否可用、电视 ADB 是否已开（`adb connect <电视IP>:5555`）。没开就告诉我怎么开，不要猜。
> 3. 缺构建环境（JDK 17 / Android SDK 34 / Gradle 8.9）先告诉我怎么装，不要静默下载几个 G。
> 4. 用 `TV=<电视IP> ./install.sh` 安装，装完按脚本最后的清单逐项验证。
> 5. 只做可逆操作：不刷机、不 root、不改 `/system`、不禁用系统应用；每项系统级改动都要说明怎么恢复。
> 6. 最后报告：改了哪些文件、验证了什么、什么没验证、剩余风险。

### 手动

```sh
git clone https://github.com/ZepPellN/guizang-tv-multi-device.git
cd guizang-tv-multi-device
TV=192.168.1.20 ./install.sh
```

脚本做五件事：连 adb → 读机型 → 用现成 APK 或构建 → 安装 → 设默认桌面并按品牌做一次性配置 → 打印验证清单。重复运行是幂等的，已装过就直接覆盖。

---

## 前置条件

1. **电视打开 ADB 调试**，和电脑在同一局域网。
   - 创维/酷开：设置 → 通用 → 关于本机 → 本机信息 → 更多信息 → 遥控器**慢慢**按 `上上下下左右左右` → 工厂菜单 → 通用设置 → 打开「单键开关」和「ADB 开关」→ 长按电源键重启。
   - 其他品牌：搜「品牌 + 开启 ADB」。
2. **电脑有 adb**：把 Android Platform Tools 解压到 `platform-tools/`，脚本会自动用它；或者装到 PATH 后 `ADB=/path/to/adb`。
3. **需要重新构建时**：JDK 17、Android SDK Platform 34、Build Tools 34.0.0、Gradle 8.9。仓库里没有 gradle wrapper。

---

## 支持机型

| 配置 id | 机型 | 验证状态 |
| --- | --- | --- |
| `xiaomi-mitv-mtsu0` | 小米电视 S55 Mini LED 2026 至尊版（MiTV-MTSU0 / Android 14） | 上游已验证；本分支只是把逻辑原样搬进 JSON，**没有**重新实机验证 |
| `skyworth-coocaa-bc20` | 创维 `1AA02_BC20` 商用电视（Skyworth / Coocaa，Android 9，MTK T963） | 本分支实机验证 |
| `generic` | 其他 Android 8+ 电视 | 兜底：自动取前 6 个已安装应用，HDMI / 信号源不可用 |

新增机型不用改代码，见 [guizang-tv/docs/DEVICE-PROFILES.md](guizang-tv/docs/DEVICE-PROFILES.md)。

---

## 和上游的关系

- **没有重写界面和功能**。布局、字体、焦点样式、壁纸轮播、Dock、分组设置、Codex 实验都保持上游原样；`XiaomiControls.java` 的内容被原样搬进 JSON，行为等价。
- **只替换了「跟机型绑定」的那一层**：首页默认项、原桌面组件、HDMI 切换、信号源弹窗、系统设置 Intent、开机 / Home 接管方式。
- **同步上游更新**：

  ```sh
  git remote add upstream https://github.com/op7418/guizang-tv.git
  git fetch upstream
  git rebase upstream/main
  ```

  `patches/` 里的补丁可能因为上游改动需要重解冲突，冲突通常集中在 `FrameHomeActivity`、`HomeItems`、`BootStart`、`HomeSettings` 这几个文件。
- **想给上游提 PR**：设备配置化本身是通用改动，适合直接提；创维那套 `claims`（`setprop` 抢系统属性）和 `bootintent.xml` 依赖比较特定，建议作为第二个 PR，或者就留在 fork 里。

---

## 创维 BC20 为什么特殊

这台电视两层都不走 Android 标准机制：

| 场景 | 厂商实际逻辑 | 本项目的做法 |
| --- | --- | --- |
| Home 键 | 系统读 `persist.service.homepage.pkg` / `.cls` 直接启动，**忽略** `pm set-home-activity` | 桌面回到前台时自己 `setprop` 认领 |
| 开机首页 | `/system/etc/bootintent.xml` 按条件匹配启动项；命中 `modelhome` 规则就启动 `persist.service.model.pkg` / `.cls` 指定的组件 | `install.sh` 一次性写入这两个属性 |

开机顺序：bootintent 命中 `modelhome` → 启动归藏 → 归藏 onResume 把 homepage 属性占回来 → 之后 Home 键也回桌面。

几个坑，写下来免得下次重踩：

- 固件每次开机都会从只读分区 `/factory/.../general_config.xml` 把 `DEFAULT_HOMEPAGE` 刷回属性，改属性撑不过重启，所以要走 `bootintent.xml` 这条。
- `BOOT_COMPLETED` 广播被固件拦截（`ActivityManager: xxxxxxxxxx stop start proc ... process is bad`），第三方应用开机阶段起不来进程；`BootReceiver` 保留只作其他固件的兜底。
- 这台固件 SELinux 是 **Permissive**，普通应用也能 `setprop`，所以不需要 root。
- 没有可用的 HDMI / 信号源组件，「其他信号源」按钮点了只会提示不可用，配置里对应字段留空。

恢复原厂：`TV=<电视IP> ./restore-stock.sh`（把属性写回创维首页、默认桌面改回去），彻底还原再 `adb uninstall io.github.op7418.guizangtv`。

---

## 相对上游的改动

完整差异：[`patches/0001-skyworth-bc20-device-profiles.patch`](patches/0001-skyworth-bc20-device-profiles.patch)，基线是上游 `312c011`，10 个文件 +551/−128，`git apply --check` 已验证。说明见 [patches/README.md](patches/README.md)。

1. 新增 `DeviceProfile` + `assets/devices.json`：设备能力全部配置化。
2. 删除 `XiaomiControls`：小米逻辑原样搬进 `xiaomi-mitv-mtsu0` 配置，行为不变。
3. 新增 `skyworth-coocaa-bc20` 配置：首页「哔哩哔哩 + 创维原桌面」，原桌面指向 `com.tianci.movieplatform/com.coocaa.homepage.vast.RealHomePageActivity`，靠 `claims` 接管 Home 键。
4. 首页默认项来自配置；未知机型回落成「自动取前 6 个已安装应用」，不再是小米那几个空包名。
5. 设置页文案用 `homeLabel`（创维原桌面 / 小米原桌面）。
6. 新增 `BootReceiver`（本机被拦截，保留兜底）。
7. 壁纸：自有照片打进 APK，内置那套仍保留。**照片本身不进仓库。**
8. 原桌面入口固定排在首页最底部（`HomeItems.pinStockHomeLast`）：新加的入口会插到它上面，把它挤到下面的操作也会被纠正回来。

---

## 构建

```sh
export JAVA_HOME=/path/to/jdk-17
export ANDROID_HOME=/path/to/android-sdk
gradle -p guizang-tv :app:assembleDebug
```

产物：`guizang-tv/app/build/outputs/apk/debug/app-debug.apk`。

`install.sh` 会自动用 `build-env/`（如果存在）里的 JDK / SDK / Gradle。debug 签名与官方 release 签名不同，覆盖安装前需要 `adb uninstall`，会清掉应用内设置——脚本遇到签名冲突会提示并自动处理。

---

## 已验证 / 未验证

创维 BC20 实机验证：

- 首页只剩「哔哩哔哩 / 创维原桌面」两项，渲染正常
- 哔哩哔哩 → 云视听小电视
- 创维原桌面 → `RealHomePageActivity`
- 主列表按返回 → 创维原桌面
- 完整重启 → 直接进归藏，没有先闪创维首页
- 重启后从别的应用按 Home → 归藏
- 壁纸列表显示自定义 5 张，轮播按 5 分钟正常切换

未验证：

- 小米那条配置没有实机跑过（只是逻辑等价搬迁）
- 壁纸轮播没有连续观察完整多轮
- 「其他信号源」在这台上不可用
- 固件 OTA 后 `bootintent.xml` 或属性机制若变化，需要重新确认

---

## 仓库结构

| 路径 | 内容 |
| --- | --- |
| `install.sh` | 一键安装：连 adb → 构建/复用 APK → 安装 → 按品牌配置 |
| `restore-stock.sh` | 把创维的 Home / 开机属性还原成原桌面 |
| `pickup.sh` `boot-timeline.sh` `reboot-test.sh` | 调试用辅助脚本 |
| `guizang-tv/` | 定制源码，设备配置见 `guizang-tv/docs/DEVICE-PROFILES.md` |
| `patches/` | 相对上游的完整 patch 与说明 |
| `wallpapers/` | 壁纸原图目录，**图片不入库**（见目录内 README） |
| `build-env/` `platform-tools/` | 本机构建环境与 adb，不入库 |

脚本里的电视地址用环境变量传：`TV=192.168.1.20 ./install.sh`。

---

## 许可与隐私

- `guizang-tv/` 是 [op7418/guizang-tv](https://github.com/op7418/guizang-tv) 的 fork，MIT，版权归原作者；见 `guizang-tv/LICENSE`。新增部分（文档、脚本、patch）同为 MIT。详见 [NOTICE.md](NOTICE.md)。
- 本仓库**不含**任何个人照片、设备序列号或局域网地址。

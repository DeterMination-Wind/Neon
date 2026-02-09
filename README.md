# Neon / 氖 (Mindustry Mod)

- [中文](#中文)
- [English](#english)

## 中文

### 简介

Neon 是一个 Mindustry 纯客户端工具集（Java 模组），将以下 5 个模组合并为一个安装包：

- Power Grid Minimap（电网小地图）
- Stealth Path（偷袭小道 / 安全路径）
- Radial Build Menu（圆盘快捷建造）
- betterMiniMap（增强小地图单位/建筑显示）
- ServerPlayerDataBase（玩家数据库 / 聊天记录查询）

如果你只想安装一次、一次性获得这五类常用“信息叠加 + 操作效率 + 数据查询”功能，Neon 会更省事。

提示：Neon 已包含上述 5 个模块的功能，建议不要与它们的独立版本同时启用，避免重复功能或 UI 冲突。

### 功能一览

#### 1) 电网小地图（Power Grid Minimap）

- 小地图/全屏大地图电网着色：每个独立电网用不同颜色标识，快速定位断网与“跨网误接”。
- 电力盈亏标记：在电网中心显示净盈亏数值（支持字号/颜色/透明度等调整）。
- 断网告警与建议连接点：大电网分裂且出现负电时，提示并标记建议重连位置。
- 缺电救援建议（Beta）：缺电持续时可显示“正电岛”隔离建议、以及可能的冲击反应堆禁用提示。
- 电力表：以列表汇总大电网的概览信息（当前盈亏/近期最低等），便于快速找出最糟糕的电网。

#### 2) 偷袭小道（Stealth Path）

- 路线叠加预览：在地图上绘制更安全/受伤更少的路线；线宽/透明度/显示时长可调。
- 多模式与过滤：可切换显示模式与威胁过滤（陆军/空军/全部），更贴合不同单位。
- 自动模式（单位集群）：自动规划单位集群到鼠标/聊天坐标，并可使用“自动移动”热键下达沿路线前进的移动指令（可在设置中开关）。
- 可选信息窗：在安装 MindustryX 时，可通过 OverlayUI 显示模式/伤害/控制等窗口；未安装则回退 HUD。

#### 3) 圆盘快捷建造（Radial Build Menu）

- 长按热键弹出圆盘 HUD：松开即可切换建造方块。
- 16 槽位（内圈 8 + 外圈 8）：可按需要配置/清空；外圈在配置后自动显示。
- 多套槽位配置与切换：支持按时长/星球/条件切换；也支持槽位组 A/B 通过热键即时切换。
- 外观与交互可调：缩放、透明度、半径、图标大小、方向选择等；支持 JSON 导入/导出。

#### 4) betterMiniMap

- 在小地图叠加单位与建筑图标，支持朝向、透明度、缩放、聚合间距等参数。
- 支持敌我单位/建筑独立开关与筛选，快速定制战场信息密度。
- 提供单位/建筑筛选对话框，可按名称搜索、全选、清空、反选。

#### 5) 玩家数据库（ServerPlayerDataBase）

- 本地采集在线玩家信息（名称、UID、服务器、IP 追踪结果）并可持续更新。
- 可选记录聊天日志，支持导入/导出、完整性校验与异常提示。
- 在支持 MindustryX OverlayUI 时提供查询/调试窗口；无 OverlayUI 时回退到普通对话框。

### 快速上手

1) 安装：将 `Neon.zip` 放入 Mindustry 的 `mods` 目录并在游戏内启用。

2) 改键：在 `设置 → 控制` 中找到对应条目并改成你习惯的按键：

- 偷袭小道：`X/Y/N/M/K/L` 等（条目名以游戏语言显示为准）
- 圆盘快捷建造：打开圆盘 HUD 的热键（以及可选的“切换槽位组”热键）

3) 设置：在 `设置 → 模组` 下分别进入各模块分类进行调整：

- 电网小地图（Power Grid Minimap）
- 偷袭小道（Stealth Path）
- 圆盘快捷建造（Radial Build Menu）
- betterMiniMap
- 玩家数据库（ServerPlayerDataBase）

### 多人游戏

Neon 为客户端侧叠加显示与操作辅助，不需要服务器安装；适合多人游戏环境。

### 安卓

安卓端需要包含 `classes.dex` 的 mod 包。请下载 Release 中的 `Neon.jar` 并放入 Mindustry 的 `mods` 目录。

### 反馈

【BEK辅助mod反馈群】：https://qm.qq.com/q/cZWzPa4cTu

![BEK辅助mod反馈群二维码](docs/bek-feedback-group.png)

### 构建（可选，开发者）

构建桌面端 zip：

```bash
./gradlew jar
```

输出：`build/libs/Neon.zip`

构建安卓 jar（含 classes.dex）：

```bash
./gradlew jarAndroid
```

输出：`build/libs/Neon.jar`

---

## English

### Overview

Neon is a client-side Mindustry toolkit (Java mod) that bundles five popular QoL modules into a single install:

- Power Grid Minimap
- Stealth Path
- Radial Build Menu
- betterMiniMap
- Server Player DataBase

If you prefer installing once and getting all five “overlay + workflow + data tooling” features together, Neon is the convenient option.

Note: since Neon already includes these modules, you should avoid enabling the standalone versions at the same time to prevent duplicated UI or conflicts.

### What You Get

#### 1) Power Grid Minimap

- Colors each separate power network on the minimap/full map for quick grid visibility.
- Shows net power balance markers at grid centers (configurable size/color/opacity).
- Split alerts with reconnect hints when a large grid breaks and goes negative.
- Power rescue hints (Beta) for sustained deficits (e.g. outlining positive islands or suggesting disabling Impact Reactors when relevant).
- A compact power table listing large grids (current balance / recent minimum, etc.) to help locate the worst grid quickly.

#### 2) Stealth Path

- Route overlay previews for safer / lower-damage paths, with configurable visuals.
- Multiple modes and threat filters (ground/air/both).
- Auto modes for unit clusters (to mouse or to chat coordinates), plus an optional auto-move keybind to command units along the preview path.
- Optional overlay windows: when MindustryX is installed, mode/damage/controls windows can be shown via OverlayUI; otherwise it falls back to regular HUD.

#### 3) Radial Build Menu

- Hold a hotkey to open a radial HUD; release to switch the selected build block.
- Up to 16 configurable slots (8 inner + 8 outer; outer appears when configured).
- Multiple profiles and switching rules (time / planet / conditional), plus optional Slot Group A/B instant toggling via a hotkey.
- Customizable appearance and interaction; JSON import/export for sharing.

#### 4) betterMiniMap

- Draws extra unit/building markers on the minimap with direction, scale, alpha, and cluster spacing controls.
- Supports friendly/enemy filters independently for units and buildings.
- Includes searchable unit/block selection dialogs with select-all, clear, and invert actions.

#### 5) Server Player DataBase

- Collects local player history (name, UID, server, trace-enriched IP records) while you play.
- Optional chat logging with import/export and integrity verification.
- Uses MindustryX OverlayUI query/debug windows when available, with fallback dialogs on vanilla clients.

### Quick Start

1) Install: put `Neon.zip` into Mindustry's `mods` folder and enable it in-game.

2) Rebind keys in `Settings → Controls`:

- Stealth Path hotkeys (X/Y/N/M/K/L, etc.)
- Radial Build Menu hotkey (and optional “Toggle Slot Group” hotkey)

3) Configure settings under `Settings → Mods`:

- Power Grid Minimap
- Stealth Path
- Radial Build Menu
- betterMiniMap
- Server Player DataBase

### Multiplayer

Neon is client-side overlay and assistance; no server install required.

### Android

Android requires a mod package that contains `classes.dex`. Download `Neon.jar` from Releases and put it into Mindustry's `mods` folder.

### Feedback

Discord: https://discord.com/channels/391020510269669376/1467903894716940522

### Build (Optional)

Build desktop zip:

```bash
./gradlew jar
```

Output: `build/libs/Neon.zip`

Build Android jar (with classes.dex):

```bash
./gradlew jarAndroid
```

Output: `build/libs/Neon.jar`

# Neon / 氖 (Mindustry Mod)

- [中文](#中文)
- [English](#english)

## 中文

### 简介

Neon 是一个 Mindustry 纯客户端工具集（Java 模组），将以下 15 个模组合并为一个安装包：

- Power Grid Minimap（电网小地图）
- Stealth Path（偷袭小道 / 安全路径）
- customMarker（自定义标记）
- BetterScreenShot（更好的截图，BSS 核心代码来自 Miner）
- Radial Build Menu（圆盘快捷建造）
- betterMiniMap（增强小地图单位/建筑显示）
- ServerPlayerDataBase（玩家数据库 / 聊天记录查询）
- betterMapEditor（地图编辑增强）
- Better Projector Overlay（超速投影叠加）
- betterLogisticsSpeed（物流速率增强）
- betterHotKey（快捷键增强）
- modUpdater（模组更新中心）
- WhoUsesThisBuilding（谁在用这个建筑）
- PatchViewer（补丁查看器）
- PinyinSearchSupport（拼音搜索支持）

如果你只想安装一次、一次性获得这十五类常用"信息叠加 + 操作效率 + 数据查询 + 自动更新 + 反向逻辑排查 + 数据补丁对比 + 拼音搜索"功能，Neon 会更省事。

提示：Neon 已包含上述 15 个模块的功能，建议不要与它们的独立版本同时启用，避免重复功能或 UI 冲突。

### 功能一览

#### 1) 电网小地图（Power Grid Minimap）

- 小地图/全屏大地图电网着色：每个独立电网用不同颜色标识，快速定位断网与“跨网误接”。
- 电力盈亏标记：在电网中心显示净盈亏数值（支持字号/颜色/透明度等调整）。
- 断网告警与建议连接点：大电网分裂且出现负电时，提示并标记建议重连位置。
- 缺电救援建议（Beta）：缺电持续时可显示“正电岛”隔离建议、以及可能的冲击反应堆禁用提示。
- 电力表：以列表汇总大电网的概览信息（当前盈亏/近期最低等），便于快速找出最糟糕的电网。

#### 2) 偷袭小道（Stealth Path）

- 默认关闭：该功能目前有部分缺陷，正在修复；需要时可在 Neon 设置中手动开启。
- 路线叠加预览：在地图上绘制更安全/受伤更少的路线；线宽/透明度/显示时长可调。
- 多模式与过滤：可切换显示模式与威胁过滤（陆军/空军/全部），更贴合不同单位。
- 自动模式（单位集群）：自动规划单位集群到鼠标/聊天坐标，并可使用“自动移动”热键下达沿路线前进的移动指令（可在设置中开关）。
- 可选信息窗：在安装 MindustryX 时，可通过 OverlayUI 显示模式/伤害/控制等窗口；窗口开关已回归 OverlayUI 原生界面管理。

#### 3) 自定义标记（customMarker）

- 模仿 MindustryX 标记面板工作流，支持“按钮 → 全屏点位 → 面板确认”流程。
- 支持 5 组标记模板编辑，消息格式固定为 `<内容><内容>(x,y)`。
- 集成聊天坐标捕获窗口，可快速定位并聚焦聊天中的坐标点。

#### 4) BetterScreenShot（更好的截图）

- 一键生成高分辨率世界截图，支持 OverlayUI 按钮和快捷键触发。
- 大地图自动分块渲染，规避纹理尺寸限制并提供尺寸预估与进度状态。
- BSS 核心代码来自 Miner，并在 Neon 内完成整合与设置统一。

#### 5) 圆盘快捷建造（Radial Build Menu）

- 长按热键弹出圆盘 HUD：松开即可切换建造方块。
- 16 槽位（内圈 8 + 外圈 8）：可按需要配置/清空；外圈在配置后自动显示。
- 多套槽位配置与切换：支持按时长/星球/条件切换；也支持槽位组 A/B 通过热键即时切换。
- 外观与交互可调：缩放、透明度、半径、图标大小、方向选择等；支持 JSON 导入/导出。

#### 6) betterMiniMap

- 在小地图叠加单位与建筑图标，支持朝向、透明度、缩放、聚合间距等参数。
- 支持敌我单位/建筑独立开关与筛选，快速定制战场信息密度。
- 提供单位/建筑筛选对话框，可按名称搜索、全选、清空、反选。

#### 7) 玩家数据库（ServerPlayerDataBase）

- 本地采集在线玩家信息（名称、UID、服务器、IP 追踪结果）并可持续更新。
- 可选记录聊天日志，支持导入/导出、完整性校验与异常提示。
- 在支持 MindustryX OverlayUI 时提供查询/调试窗口；无 OverlayUI 时回退到普通对话框。

#### 8) 地图编辑增强（betterMapEditor）

- 在地图生成器预览里直接拖动镜像轴，快速微调对称地图。
- 同步替换镜像滤镜为可拖动实现，不改变你已有的生成器流程。

#### 9) 投影叠加（Better Projector Overlay）

- 手持超速投影时显示“放下后电网正/负”预判圈与实时数值提示。
- 自动扫描并标记被高布/穹顶覆盖的普通超速，支持可选聊天提醒。

#### 10) 物流速率增强（betterLogisticsSpeed）

- 为建筑物流信息增加更长时间窗的移动平均吞吐显示。
- 支持总吞吐行显示、窗口时长与小数位调整，减少短时抖动造成的误判。

#### 11) 快捷键增强（betterHotKey）

- 支持双键组合热键与分组配置，适配更复杂的建造习惯。
- 可配置忽略地形编号冲突、保留被 ban 建筑槽位，并提供可视化配置面板。

#### 12) 模组更新中心（modUpdater）

- 启动自动检查 GitHub Release，集中展示可更新/已最新/黑名单/无仓库状态。
- 支持单个或批量更新、仓库覆盖、黑名单管理与镜像下载切换。

#### 13) WhoUsesThisBuilding（谁在用这个建筑）

- 按住触发热键并悬停任意建筑时，反向高亮所有引用该建筑的逻辑处理器。
- 标签显示 `Lxx(opcode)`，可直接定位逻辑代码行号与引用类型。
- 支持显式链接和 `getblock(x,y)` 间接引用，适合大型逻辑图快速排查依赖。

#### 14) PatchViewer（补丁查看器）

- 在数据库界面直接内联显示 datapatch 前后差异（before/after）。
- 对属性中的修改、删除、新增项做可视化高亮，便于快速审查补丁影响。
- 提供颜色设置与预览项，并已纳入 Neon 聚合设置页统一管理。

#### 15) PinyinSearchSupport（拼音搜索支持）

- 在搜索框输入拼音来匹配中文条目。
- 支持模糊拼音、首字母、多音字读音与搜索延迟设置。
- 已纳入 Neon 聚合设置页统一管理，不再单独执行独立更新检查。

### 快速上手

1) 安装：将 `Neon.zip` 放入 Mindustry 的 `mods` 目录并在游戏内启用。

2) 改键：在 `设置 → 控制` 中找到对应条目并改成你习惯的按键：

- 偷袭小道：`X/Y/N/M/K/L` 等（条目名以游戏语言显示为准）
- 快捷键增强（betterHotKey）：组合键/数字键相关条目
- BetterScreenShot：世界截图快捷键（默认 `F8`）
- 圆盘快捷建造：打开圆盘 HUD 的热键（以及可选的“切换槽位组”热键）

3) 设置：在 `设置 → 模组 → Neon` 下按分组统一调整：

- 电网小地图（Power Grid Minimap）
- 偷袭小道（Stealth Path）
- 自定义标记（customMarker）
- BetterScreenShot
- 圆盘快捷建造（Radial Build Menu）
- betterMiniMap
- 玩家数据库（ServerPlayerDataBase）
- 地图编辑增强（betterMapEditor）
- 投影叠加（Better Projector Overlay）
- 物流速率增强（betterLogisticsSpeed）
- 快捷键增强（betterHotKey）
- 模组更新中心（modUpdater）
- 谁在用这个建筑（WhoUsesThisBuilding）
- 补丁查看器（PatchViewer）
- 拼音搜索支持（PinyinSearchSupport）

### 多人游戏

Neon 为客户端侧叠加显示与操作辅助，不需要服务器安装；适合多人游戏环境。

### 安卓

安卓端需要包含 `classes.dex` 的 mod 包。请下载 Release 中的 `Neon.jar` 并放入 Mindustry 的 `mods` 目录。

### 反馈

【BEK辅助mod反馈群】：https://qm.qq.com/q/cZWzPa4cTu

![BEK辅助mod反馈群二维码](docs/bek-feedback-group.png)

### 构建（可选，开发者）

一键构建桌面+安卓通用包（含 `classes.dex`）：

```bash
./gradlew deploy
```

主要输出：

- `dist/Neon.zip`
- `dist/Neon.jar`
- `../构建/Neon/Neon-<version>.zip`
- `../构建/Neon/Neon-<version>.jar`

---

## English

### Overview

Neon is a client-side Mindustry toolkit (Java mod) that bundles fifteen modules into one package:

- Power Grid Minimap
- Stealth Path
- customMarker
- BetterScreenShot
- Radial Build Menu
- betterMiniMap
- Server Player DataBase
- betterMapEditor
- Better Projector Overlay
- betterLogisticsSpeed
- betterHotKey
- modUpdater
- WhoUsesThisBuilding
- PatchViewer
- PinyinSearchSupport

If you want one install that covers overlays, workflow tools, utility features, logic debugging, datapatch diffs, and pinyin search, Neon is the all-in-one option.

Note: Neon already contains these fifteen modules. Do not enable standalone versions at the same time, or you may get duplicate UI and feature conflicts.

### Module Highlights

#### 1) Power Grid Minimap

- Colors independent power grids on minimap and full map.
- Shows per-grid net power balance with configurable visual style.
- Marks reconnect hints when large grids split and go negative.
- Includes rescue hints for sustained deficit scenarios.

#### 2) Stealth Path

- Disabled by default (feature has known defects and is being fixed).
- Draws lower-risk path previews with mode and threat filters.
- Supports auto routing for unit clusters to mouse or chat coordinates.
- Overlay windows are managed in native OverlayUI when MindustryX is installed.

#### 3) customMarker

- Marker workflow: button -> fullscreen pick -> radial confirm.
- Editable template messages in `<text><text>(x,y)` format.
- Chat coordinate capture list for quick camera focus jumps.

#### 4) BetterScreenShot

- One-click high-resolution world screenshot capture.
- Trigger by hotkey (`F8`) or OverlayUI button.
- Uses chunked rendering for large maps to avoid texture-size limits.
- Includes size estimation and optional progress status.
- BSS core code is from Miner, integrated into Neon.

#### 5) Radial Build Menu

- Hold a hotkey to open radial build HUD; release to select.
- Up to 16 configurable slots (inner + outer ring).
- Rule-based profile switching (time/planet/condition).
- JSON import/export for quick config sharing.

#### 6) betterMiniMap

- Enhanced minimap icons for units/buildings with scale/alpha/spacing controls.
- Independent friendly/enemy filters.
- Searchable selection dialogs with select-all/clear/invert.

#### 7) Server Player DataBase

- Local player and chat history collection.
- Import/export and integrity checks.
- OverlayUI query/debug windows with fallback dialogs on vanilla clients.

#### 8) betterMapEditor

- Draggable mirror axis directly in generator preview.
- Keeps existing map generation workflow while improving control.

#### 9) Better Projector Overlay

- Placement-time power impact preview for overdrive projectors.
- Scans and marks risky projector placements with optional alerts.

#### 10) betterLogisticsSpeed

- Adds long-window moving-average throughput rows to logistics stats.
- Adjustable window length, decimal precision, and total-throughput row.

#### 11) betterHotKey

- Supports combo hotkeys and grouped binding configuration.
- Provides terrain-number conflict handling and ignored-block controls.

#### 12) modUpdater

- Startup GitHub release checks with a centralized update center UI.
- Supports single/batch update, blacklist, repo override, and mirror switch.

#### 13) WhoUsesThisBuilding

- Hold the trigger hotkey while hovering a building to reverse-highlight all processors that reference it.
- Labels display `Lxx(opcode)` for quick line-level tracing.
- Supports both explicit processor links and `getblock(x,y)` indirect references.

#### 14) PatchViewer

- Shows inline before/after diffs for datapatch-modified database entries.
- Highlights modified/removed/added stat rows directly in content panels.
- Color settings are included in Neon grouped settings with live preview.

#### 15) PinyinSearchSupport

- Lets search fields match Chinese entries from typed pinyin.
- Supports fuzzy pinyin, initials, heteronyms, and delay settings.
- Integrated into Neon grouped settings without its standalone update checker.

### Quick Start

1) Install `Neon.zip` to Mindustry `mods` folder and enable it in game.

2) Rebind keys in `Settings -> Controls`:

- Stealth Path hotkeys
- betterHotKey combo/number hotkeys
- BetterScreenShot capture hotkey (`F8` by default)
- Radial Build Menu hotkey (plus optional slot-group toggle)

3) Configure all modules in `Settings -> Mods -> Neon`, including WhoUsesThisBuilding, PatchViewer, and PinyinSearchSupport groups.

### Multiplayer

Neon is fully client-side; server installation is not required.

### Android

Android requires a mod package that includes `classes.dex`.
Use `Neon.jar` from Releases and place it in the Mindustry `mods` folder.

### Feedback

Discord: https://discord.com/channels/391020510269669376/1467903894716940522

### Build (Optional)

Build merged desktop+android artifacts (with `classes.dex`):

```bash
./gradlew deploy
```

Outputs:

- `dist/Neon.zip`
- `dist/Neon.jar`
- `../构建/Neon/Neon-<version>.zip`
- `../构建/Neon/Neon-<version>.jar`


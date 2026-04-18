# Neon / 氖 (Mindustry Mod)

- [中文](#中文)
- [English](#english)

## 中文

### 简介

Neon 是一个 Mindustry 纯客户端工具集（Java 模组），将以下 16 个模组合并为一个安装包：

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
- hiddenMessage（隐藏消息握手/投递）
- UpdateScheme（蓝图更新订阅 / 可更新发布）
- WhoUsesThisBuilding（谁在用这个建筑）
- PatchViewer（补丁查看器）

如果你只想安装一次、一次性获得这十六类常用“信息叠加 + 操作效率 + 数据查询 + 自动更新 + 蓝图发布订阅 + 反向逻辑排查 + 数据补丁对比”功能，Neon 会更省事。

提示：Neon 已包含上述 16 个模块的功能，建议不要与它们的独立版本同时启用，避免重复功能或 UI 冲突。

### 本次更新（v7.0.0）

- 新并入 `WhoUsesThisBuilding` 与 `PatchViewer`，Neon 升级为 16 合 1。
- 两个新模块均完成 `bekBundled / bekBuildSettings` 接口改造，接入 Neon 聚合设置分组，不再在 bundled 模式下注册独立设置分类。
- `tools/update_submods.py` 新增对 `src + assets/bundles` 目录结构的同步支持，后续可持续跟进这两个仓库上游更新。

### 上一版更新（v6.3.0）

- 修复 Neon 中剩余几处 MindustryX OverlayUI 窗口首次注册逻辑：玩家若用原生 OverlayUI 关闭窗口，重启后不会再被 `StealthPath`、`ServerPlayerDataBase`、`Radial Build Menu`、`betterHotKey` 强制重新打开。
- 新并入 `UpdateScheme`，统一纳入 Neon 聚合设置入口。
- `UpdateScheme` 在本次并入中升级为全新 v2 协议：舍弃 `PrivateBin`，改为 `0x0.st` 版本正文 + GitHub manifest / 作者索引。

### 更早更新（v6.2.0）

- 检查 Neon 当前包含的子模组来源后，除 `betterHotKey` 外其余子模组本地源仓库均无新更新需要并入。
- 同步并入最新 `betterHotKey` 子模组改动：建筑图标角标显示、角标字号调节、角标编号适配“忽略地形”、字号输入过程不再因先输入 `0` 而中途报错。
- 为 `betterHotKey` 保留 `bekBundled / bekBuildSettings` 聚合设置接口，确保 Neon 的统一设置入口与打包流程继续正常工作。

### 更早更新（v6.1.0）

- 修复 Neon 内所有已接入 MindustryX OverlayUI 的窗口：如果玩家用原生 mdtx/OverlayUI 关闭窗口，重启后不会再被模组强制重新打开。
- 同步并入最新 betterHotKey 子模组改动到 Neon。
- 修复合并子模组后 Neon 聚合设置入口所需的 bekBundled / bekBuildSettings 接口，保证打包与设置页正常工作。

### 更早更新（v6.0.3）

- 修复 Neon 启动崩溃：`PowerGridMinimap` 的电力表 UI 不再在模组构造期创建，改为主线程延迟创建（修复 `UI should be created in main Thread`）。
- 修复 Stealth Path：空军单位规划时错误按地面可通行规则绕液体的问题，路径机动模式改为按选中单位机动能力判定。
- 新并入 4 个客户端模块：`betterLogisticsSpeed`、`betterHotKey`、`modUpdater`、`hiddenMessage`。
- Neon 由原先 9 合 1 升级为 13 合 1，统一在一个设置入口下管理。
- 更新构建说明：推荐使用 `gradle deploy` 直接生成 `dist/` 与 `构建/Neon` 产物。

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

#### 13) 隐藏消息（hiddenMessage）

- 提供基于握手校验的隐藏消息通道与投递提示。
- 适用于需要在客户端侧进行额外消息验证与展示的场景（无独立设置页）。
- 需要安装对应的服务端脚本（找bek要）

#### 14) UpdateScheme

- 订阅在线蓝图清单，定时检查并应用更新。
- 支持发布可更新蓝图，分享文本格式为 `UpdateScheme:v2:<repo>:<manifestId>`。
- 新版协议使用 `0x0.st` 存放版本正文，使用 GitHub 仓库存放 manifest 与作者索引，不再依赖 `PrivateBin`。

#### 15) WhoUsesThisBuilding（谁在用这个建筑）

- 按住触发热键并悬停任意建筑时，反向高亮所有引用该建筑的逻辑处理器。
- 标签显示 `Lxx(opcode)`，可直接定位逻辑代码行号与引用类型。
- 支持显式链接和 `getblock(x,y)` 间接引用，适合大型逻辑图快速排查依赖。

#### 16) PatchViewer（补丁查看器）

- 在数据库界面直接内联显示 datapatch 前后差异（before/after）。
- 对属性中的修改、删除、新增项做可视化高亮，便于快速审查补丁影响。
- 提供颜色设置与预览项，并已纳入 Neon 聚合设置页统一管理。

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
- 隐藏消息（hiddenMessage）
- 谁在用这个建筑（WhoUsesThisBuilding）
- 补丁查看器（PatchViewer）

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

Neon is a client-side Mindustry toolkit (Java mod) that bundles sixteen modules into one package:

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
- hiddenMessage
- UpdateScheme
- WhoUsesThisBuilding
- PatchViewer

If you want one install that covers overlays, workflow tools, utility features, and schematic publish/subscribe updates, Neon is the all-in-one option.

Note: Neon already contains these sixteen modules. Do not enable standalone versions at the same time, or you may get duplicate UI and feature conflicts.

### What's New (v7.0.0)

- Bundled `WhoUsesThisBuilding` and `PatchViewer`; Neon is now a 16-in-1 package.
- Refactored both modules with `bekBundled / bekBuildSettings` hooks and merged them into Neon grouped settings style.
- Extended `tools/update_submods.py` to support `src + assets/bundles` style repositories for future upstream sync.

### Previous Update (v6.3.0)

- Fixed the remaining MindustryX OverlayUI windows that were still being force-reopened on startup. If you close them through native OverlayUI, Neon now respects that persisted closed state for `StealthPath`, `ServerPlayerDataBase`, `Radial Build Menu`, and `betterHotKey`.
- Bundled `UpdateScheme` into Neon and exposed it through the shared Neon settings category.
- `UpdateScheme` is now the new v2 protocol: no `PrivateBin`, using `0x0.st` for immutable version blobs and GitHub manifests/author indexes for mutable publish state.

### Earlier Update (v6.2.0)

- Checked all bundled submodule sources: no new upstream local changes were found except `betterHotKey`.
- Merged the latest `betterHotKey` updates into Neon: icon-corner hotkey badges, configurable badge font scale, badge numbering that matches "ignore terrain", and safe in-progress numeric input that no longer breaks when the user types `0` first.
- Kept the `bekBundled / bekBuildSettings` hooks for `betterHotKey` so Neon settings aggregation and release builds continue to work.

### Earlier Update (v6.1.0)

- Fixed all Neon modules that integrate with MindustryX OverlayUI: if a player closes a window via native mdtx/OverlayUI controls, Neon now respects that persisted closed state after restart.
- Merged the latest betterHotKey submodule changes into Neon.
- Restored the bundled-module bekBundled / bekBuildSettings hooks required by Neon settings aggregation and release builds.

### Earlier Update (v6.0.3)

- Fixed Neon startup crash: `PowerGridMinimap` power-table UI is now created lazily on the main thread (fixes `UI should be created in main Thread`).
- Fixed Stealth Path: flying units no longer wrongly use ground passability and detour around liquid tiles.
- Added 4 newly bundled client modules: `betterLogisticsSpeed`, `betterHotKey`, `modUpdater`, `hiddenMessage`.
- Neon is now a 13-in-1 package instead of 9-in-1.
- Build instructions now recommend `gradle deploy` for release artifacts.

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

#### 13) hiddenMessage

- Adds a lightweight handshake-based hidden message channel.
- Useful for client-side validated message delivery workflows (no standalone settings page).
- Need server script,ask BEK(DeterMination) for it.

#### 14) UpdateScheme

- Subscribe to online schematic manifests and periodically check/apply updates.
- Publish updateable schematics with share text in the form `UpdateScheme:v2:<repo>:<manifestId>`.
- The new protocol stores immutable version blobs on `0x0.st` and mutable manifests/author indexes in GitHub, with no `PrivateBin` dependency.

#### 15) WhoUsesThisBuilding

- Hold the trigger hotkey while hovering a building to reverse-highlight all processors that reference it.
- Labels display `Lxx(opcode)` for quick line-level tracing.
- Supports both explicit processor links and `getblock(x,y)` indirect references.

#### 16) PatchViewer

- Shows inline before/after diffs for datapatch-modified database entries.
- Highlights modified/removed/added stat rows directly in content panels.
- Color settings are included in Neon grouped settings with live preview.

### Quick Start

1) Install `Neon.zip` to Mindustry `mods` folder and enable it in game.

2) Rebind keys in `Settings -> Controls`:

- Stealth Path hotkeys
- betterHotKey combo/number hotkeys
- BetterScreenShot capture hotkey (`F8` by default)
- Radial Build Menu hotkey (plus optional slot-group toggle)

3) Configure all modules in `Settings -> Mods -> Neon`, including WhoUsesThisBuilding and PatchViewer groups.

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


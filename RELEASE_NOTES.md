# Neon / 氖 — Release Notes

## Local dev

### 中文

- 并入 `ForeignServerTranslator`、`Tripwire`、`BetterPolyAi`、`AdvancedReplace`，Neon 本地开发包变为 19 合 1。
- 四个新模块均接入 `bekBundled / bekBuildSettings`，设置项统一挂入 Neon 折叠分组。
- 除 `Mod Updater` 外，bundled 子模组不再执行各自独立的 GitHub 更新检查，也不在 Neon 设置页暴露独立更新检查项。
- `PatchViewer` 同步到本地独立仓库 HEAD，并补上异常 datapatch 快照保护。
- Neon 设置页改为各模块独立折叠分组，打开设置分类时默认全部折叠。
- 移除 Neon 独立“更新”设置分组，`WhoUsesThisBuilding` 触发键迁移到原版控制设置，Profiler 增加设置页一键启停查看按钮，并补齐 Mod Updater 内置 repo 映射。

### English

- Bundled `ForeignServerTranslator`, `Tripwire`, `BetterPolyAi`, and `AdvancedReplace`; the local Neon dev build is now 19-in-1.
- Added `bekBundled / bekBuildSettings` hooks for all four modules and mounted their settings into Neon grouped settings.
- Except for `Mod Updater`, bundled submodules no longer run their own GitHub update checks or expose standalone update-check settings inside Neon.
- Synced `PatchViewer` with the local standalone repository HEAD and added malformed datapatch snapshot protection.
- Changed the Neon settings page to independently collapsible module groups, collapsed by default whenever the category opens.
- Removed Neon's standalone Update settings group, moved the `WhoUsesThisBuilding` trigger to vanilla Controls, added a one-click profiler toggle/view button, and filled Mod Updater built-in repo mappings.

---

## v8.3.0

### 中文

- 移除 `hiddenMessage` 和 `UpdateScheme` 两个子模组，Neon 由 16 合 1 降级为 14 合 1。
- 同步更新整合包版本到 `v8.3.0`，并对齐子模组锁定记录。

### English

- Removed `hiddenMessage` and `UpdateScheme` submodules; Neon is now a 14-in-1 package instead of 16-in-1.
- Bumped the bundled pack to `v8.3.0` and refreshed the synced submodule lock.

---

## v7.1.1

### 中文

- 修复 `Radial Build Menu` 常驻 HUD 相关设置在 Neon 设置菜单中未正常显示的问题。
- 常驻开关与未触发透明度现在以明确的独立设置项呈现，更容易发现和调整。
- 同步更新整合包版本到 `v7.1.1`，并对齐子模组锁定记录。

### English

- Fixed the `Radial Build Menu` persistent HUD settings so they now appear correctly inside Neon's settings menu.
- The persistent toggle and idle-opacity controls are now shown as explicit standalone setting rows for easier discovery.
- Bumped the bundled pack to `v7.1.1` and refreshed the synced submodule lock.

---

## v7.1.0

### 中文

- 新并入 `WhoUsesThisBuilding` 与 `PatchViewer`，Neon 升级为 16 合 1。
- 两个新模块均完成 `bekBundled / bekBuildSettings` 接口改造，接入 Neon 聚合设置分组，不再在 bundled 模式下注册独立设置分类。
- `tools/update_submods.py` 新增对 `src + assets/bundles` 目录结构的同步支持，后续可持续跟进这两个仓库上游更新。

### English

- Bundled `WhoUsesThisBuilding` and `PatchViewer`; Neon is now a 16-in-1 package.
- Refactored both modules with `bekBundled / bekBuildSettings` hooks and merged them into Neon grouped settings style.
- Extended `tools/update_submods.py` to support `src + assets/bundles` style repositories for future upstream sync.

---

## v6.3.0

### 中文

- 修复 Neon 中剩余几处 MindustryX OverlayUI 窗口首次注册逻辑：玩家若用原生 OverlayUI 关闭窗口，重启后不会再被 `StealthPath`、`ServerPlayerDataBase`、`Radial Build Menu`、`betterHotKey` 强制重新打开。
- 新并入 `UpdateScheme`，统一纳入 Neon 聚合设置入口。
- `UpdateScheme` 在本次并入中升级为全新 v2 协议：舍弃 `PrivateBin`，改为 `0x0.st` 版本正文 + GitHub manifest / 作者索引。

### English

- Fixed the remaining MindustryX OverlayUI windows that were still being force-reopened on startup. If you close them through native OverlayUI, Neon now respects that persisted closed state for `StealthPath`, `ServerPlayerDataBase`, `Radial Build Menu`, and `betterHotKey`.
- Bundled `UpdateScheme` into Neon and exposed it through the shared Neon settings category.
- `UpdateScheme` is now the new v2 protocol: no `PrivateBin`, using `0x0.st` for immutable version blobs and GitHub manifests/author indexes for mutable publish state.

---

## v6.2.0

### 中文

- 检查 Neon 当前包含的子模组来源后，除 `betterHotKey` 外其余子模组本地源仓库均无新更新需要并入。
- 同步并入最新 `betterHotKey` 子模组改动：建筑图标角标显示、角标字号调节、角标编号适配"忽略地形"、字号输入过程不再因先输入 `0` 而中途报错。
- 为 `betterHotKey` 保留 `bekBundled / bekBuildSettings` 聚合设置接口，确保 Neon 的统一设置入口与打包流程继续正常工作。

### English

- Checked all bundled submodule sources: no new upstream local changes were found except `betterHotKey`.
- Merged the latest `betterHotKey` updates into Neon: icon-corner hotkey badges, configurable badge font scale, badge numbering that matches "ignore terrain", and safe in-progress numeric input that no longer breaks when the user types `0` first.
- Kept the `bekBundled / bekBuildSettings` hooks for `betterHotKey` so Neon settings aggregation and release builds continue to work.

---

## v6.1.0

### 中文

- 修复 Neon 内所有已接入 MindustryX OverlayUI 的窗口：如果玩家用原生 mdtx/OverlayUI 关闭窗口，重启后不会再被模组强制重新打开。
- 同步并入最新 betterHotKey 子模组改动到 Neon。
- 修复合并子模组后 Neon 聚合设置入口所需的 bekBundled / bekBuildSettings 接口，保证打包与设置页正常工作。

### English

- Fixed all Neon modules that integrate with MindustryX OverlayUI: if a player closes a window via native mdtx/OverlayUI controls, Neon now respects that persisted closed state after restart.
- Merged the latest betterHotKey submodule changes into Neon.
- Restored the bundled-module bekBundled / bekBuildSettings hooks required by Neon settings aggregation and release builds.

---

## v6.0.3

### 中文

- 修复 Neon 启动崩溃：`PowerGridMinimap` 的电力表 UI 不再在模组构造期创建，改为主线程延迟创建（修复 `UI should be created in main Thread`）。
- 修复 Stealth Path：空军单位规划时错误按地面可通行规则绕液体的问题，路径机动模式改为按选中单位机动能力判定。
- 新并入 4 个客户端模块：`betterLogisticsSpeed`、`betterHotKey`、`modUpdater`、`hiddenMessage`。
- Neon 由原先 9 合 1 升级为 13 合 1，统一在一个设置入口下管理。
- 更新构建说明：推荐使用 `gradle deploy` 直接生成 `dist/` 与 `构建/Neon` 产物。

### English

- Fixed Neon startup crash: `PowerGridMinimap` power-table UI is now created lazily on the main thread (fixes `UI should be created in main Thread`).
- Fixed Stealth Path: flying units no longer wrongly use ground passability and detour around liquid tiles.
- Added 4 newly bundled client modules: `betterLogisticsSpeed`, `betterHotKey`, `modUpdater`, `hiddenMessage`.
- Neon is now a 13-in-1 package instead of 9-in-1.
- Build instructions now recommend `gradle deploy` for release artifacts.

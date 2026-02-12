# Changelog

All notable changes to Neon are documented in this file.

## v5.2.2 - 2026-02-13

### Fixed
- Fixed a global UI focus-clearing regression introduced by the bundled ServerPlayerDataBase overlay integration.
- Neon now clears focus only when SPDB-owned overlay elements actually hold focus, preventing non-responsive clicks in unrelated game UI areas.

### Updated
- Synced bundled `ServerPlayerDataBase` to the latest local source snapshot while preserving Neon bundled behavior.

## v5.2.0 - 2026-02-12

### Updated
- Rechecked all bundled module upstream states and confirmed no newer upstream commits to merge for current tracked repos.
- Completed missing English bundle keys used by Neon settings/category labels (`bektools.section.update`, `pgmm.category`).

### Docs
- Refreshed release metadata version to `5.2.0`.

## v5.1.0 - 2026-02-11

### Added
- Integrated `BetterScreenShot` into Neon as the 9th bundled module.
- Added high-resolution world screenshot capture panel/hotkey support from BSS.

### Updated
- Explicitly marked in Neon settings that BSS core code comes from Miner.

### Docs
- Updated `README.md` and module metadata for the 9-module bundle.

## v5.0.0 - 2026-02-11

### Added
- Integrated `customMarker` into Neon as the 8th bundled module.

### Updated
- Checked all bundled module upstream repos and merged current latest snapshots where applicable.
- Restyled `customMarker` settings and marker-related windows to match Neon visual language.
- Center-aligned Neon settings groups for cleaner multi-module layout.

### Changed
- Stealth Path is now disabled by default and marked with a known-issues notice.
- Removed Stealth Path OverlayUI window toggle options from Neon settings; window visibility control is now delegated to native OverlayUI.

### Docs
- Updated `README.md` (Chinese/English) for the 8-module bundle and current behavior changes.

## v4.0.1 - 2026-02-11

### Updated
- Rewrote Neon settings UI style to a unified VSCode-inspired visual language across bundled setting widgets.
- Removed emoji/icon-like prefixes from setting and chat hint text in bundled modules.
- Disabled bundled sub-mod update popups; only Neon update check now prompts users.
- Renamed all OverlayUI window titles to Chinese labels.

### Fixed
- Aligned mobile/desktop settings row rendering and rebuilt desktop+Android bundles for the style rewrite.

## v4.0.0 - 2026-02-10

### Added
- Integrated latest `betterMapEditor` into Neon.
- Integrated latest `BetterProjectorOverlay` into Neon.

### Updated
- Synced latest code from `Power-Grid-Minimap-repo-clone`, `Radial-Build-Menu-hud-`, `ServerPlayerDataBase`, `StealthPath`, and `betterMiniMap`.
- Unified setting-name style to current `betterMiniMap` naming (removed icon/emoji-like prefixes for Neon overrides).
- Unified OverlayUI display names to localized Chinese labels and aligned overlay window color language across modules.
- Checked desktop/Android bundle outputs and tuned overlay layout behavior for mobile-friendly window usage.

### Docs
- Updated `README.md` in both Chinese and English for the 7-module bundle.

## v3.0.0 - 2026-02-09

### Added
- Integrated latest `betterMiniMap` into Neon.
- Integrated latest `ServerPlayerDataBase` into Neon.

### Updated
- Synced latest code from `Power-Grid-Minimap-repo-clone`, `StealthPath`, and `Radial-Build-Menu-hud-`.
- Unified settings naming style with current `betterMiniMap` conventions, including removal of icon/emoji prefixes in labels.
- Unified OverlayUI window naming across bundled modules with a consistent `neon-*` namespace.

### Docs
- Updated `README.md` in both Chinese and English for the 5-module bundle.

---

## 变更日志

## v5.2.2 - 2026-02-13

### 修复
- 修复由整合版 ServerPlayerDataBase 覆盖层引入的全局焦点清理回归问题。
- 现在仅在 SPDB 自身覆盖层元素持有焦点时才清理焦点，避免游戏中其他界面区域点击无响应。

### 更新
- 同步整合版 `ServerPlayerDataBase` 到本地最新代码快照，并保留 Neon 的整合行为约束。

## v5.2.0 - 2026-02-12

### 更新
- 再次检查 Neon 已整合模组的上游状态，当前已跟踪仓库均无可合并的新提交。
- 补齐英文 bundle 中 Neon 设置/分类相关缺失键（`bektools.section.update`、`pgmm.category`）。

### 文档
- 更新发布元数据版本为 `5.2.0`。

## v5.1.0 - 2026-02-11

### 新增
- 集成 `BetterScreenShot`，Neon 升级为九合一。
- 新增 BSS 高分辨率世界截图能力（OverlayUI 按钮 + 热键）。

### 更新
- 在 Neon 设置项中明确标注：BSS 核心代码来自 Miner。

### 文档
- 更新 `README.md` 与模块元数据，覆盖九合一说明。

## v5.0.0 - 2026-02-11

### 新增
- 集成 `customMarker`，Neon 升级为八合一。

### 更新
- 检查 Neon 已包含模块的上游仓库状态，并在可用情况下合并当前最新代码快照。
- 重绘 `customMarker` 设置界面与标记相关窗口风格，使其与 Neon 视觉语言一致。
- 调整 Neon 设置分组布局为居中显示。

### 调整
- 偷袭小道默认关闭，并新增“功能有部分缺陷，正在修复”提示。
- 从 Neon 设置中移除偷袭小道 OverlayUI 窗口开关项，窗口显示权限回归 OverlayUI 原生操作界面。

### 文档
- 更新 `README.md`（中英双语），覆盖八合一模块说明与行为变更。

## v4.0.1 - 2026-02-11

### 更新
- 重写 Neon 设置界面样式，统一为 VSCode 风格设计语言。
- 去除整合模块设置与聊天提示中的 emoji/图标前缀。
- 关闭被整合子模组的更新弹窗，仅保留 Neon 自身更新提示。
- OverlayUI 窗口标题统一改为中文。

### 修复
- 对齐手机端/桌面端设置行布局表现，并完成桌面与安卓打包校验。

## v4.0.0 - 2026-02-10

### 新增
- 集成 `betterMapEditor` 最新版。
- 集成 `BetterProjectorOverlay` 最新版。

### 更新
- 同步 `Power-Grid-Minimap-repo-clone`、`Radial-Build-Menu-hud-`、`ServerPlayerDataBase`、`StealthPath`、`betterMiniMap` 最新代码。
- 设置项命名风格对齐当前 `betterMiniMap`，移除图标/emoji 风格前缀。
- OverlayUI 窗口显示名统一为中文，并统一窗口配色设计语言。
- 校验桌面/安卓打包产物，并检查移动端窗口布局适配。

### 文档
- 更新 `README.md`（中英双语），覆盖七合一模块说明。

## v3.0.0 - 2026-02-09

### 新增
- 集成 `betterMiniMap` 最新版。
- 集成 `ServerPlayerDataBase` 最新版。

### 更新
- 同步 `Power-Grid-Minimap-repo-clone`、`StealthPath`、`Radial-Build-Menu-hud-` 最新代码。
- 设置项命名风格对齐当前 `betterMiniMap`，移除图标/emoji 前缀。
- 统一各模块 OverlayUI 窗口命名为一致的 `neon-*` 风格。

### 文档
- 更新 `README.md`（中英双语），覆盖五合一模块说明。

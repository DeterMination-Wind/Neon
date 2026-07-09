# Neon Release Notes

## v10.1.0

### 中文

- 同步并合并最近一批子模组改动，更新了 PatchViewer、PinyinSearchSupport、Tripwire、BetterPolyAi 等聚合模块，并刷新 Neon 内部锁文件记录。
- 统一 Neon 聚合设置页样式，收紧多处设置面板布局与控件表现，减少不同子模组混搭时的视觉割裂。
- 升级到 Mindustry v159 发布基线，修复文件选择器、世界标签显示与 StealthPath 威胁模型在 v159 下的编译兼容问题。
- 移除 Neon 对 OverlayCompatBridge 的模组依赖声明；Neon 不再把它标记为软依赖，但仍保留运行时兼容探测逻辑。

### English

- Synced and merged the latest bundled submod updates, including PatchViewer, PinyinSearchSupport, Tripwire, BetterPolyAi, and refreshed Neon's internal lockfile state.
- Unified the Neon aggregate settings style and tightened several settings panels so the bundled modules feel more consistent inside one UI.
- Moved the release baseline to Mindustry v159 and fixed the compile compatibility issues around file choosers, world labels, and the StealthPath threat model.
- Removed Neon's declared OverlayCompatBridge mod dependency; Neon no longer advertises it as a soft dependency while keeping runtime compatibility detection in place.

---

## v10.0.2

### 中文

- 修复 v10.0.0 升级后拼音搜索失效的问题；蓝图搜索和方块搜索不再因为重复接管搜索框而出现“空结果”或“闪一下后恢复全列表”。
- 将 Neon 的内部模组名恢复为稳定的 `Neon`，避免旧版 `Neon` 与新版 `bek-tools` 在 `mods` 目录中被 Mindustry 误判为两个不同模组并同时加载。
- 为 PinyinSearchSupport 增加重复加载保护与诊断日志；如果玩家残留旧版 Neon 或独立拼音搜索模组，Neon 会给出明确提示而不是继续把搜索框补丁叠加到同一界面上。
- 保留对旧 `bek-tools` 名称的仓库解析兼容，已安装环境在更新后仍能正确识别 Neon 的更新源。

### English

- Fixed the pinyin search regression introduced after upgrading to v10.0.0; schematic search and block search no longer fall into empty results or flash back to the full list due to duplicate field patching.
- Restored Neon's stable internal mod name to `Neon`, preventing old `Neon` builds and newer `bek-tools` builds from being treated as two different mods and loaded together.
- Added duplicate-load protection and diagnostic logging to PinyinSearchSupport, so leftover old Neon builds or standalone pinyin-search mods now produce a clear warning instead of stacking multiple search-field patches onto the same UI.
- Kept repository resolution compatibility for the legacy `bek-tools` name so existing installs can still resolve Neon updates correctly after upgrading.

---

## v10.0.1

### 中文

- 修复 Neon 在原生具有 OverlayUI 的 MindustryX 中仍错误加载 overlaycompatbridge 的问题。
- 修复 Neon 设置页在部分运行时因为初始化失败而整栏消失的问题。
- 修复发布产物缺失 `mod.json` / `mod.hjson` 导致 `No Mod Json Found` 的问题。

### English

- Fixed Neon incorrectly loading `overlaycompatbridge` even when running on a native MindustryX build that already provides OverlayUI.
- Fixed the Neon settings section disappearing when initialization failed in some runtimes.
- Fixed release artifacts missing `mod.json` / `mod.hjson`, which caused `No Mod Json Found`.

---

## v10.0.0

### 中文

- Neon 现在内置 OverlayUI 兼容层，在非 MindustryX 客户端中也能直接提供 Overlay 窗口编辑器，无需再额外安装单独的 overlay 兼容模组。
- OverlayUI 齿轮按钮固定在屏幕左侧中部，更容易发现；窗口锁定图标切换逻辑修复，开关状态与图标显示保持一致。
- 内置兼容层补充了更稳的自动初始化与恢复逻辑，避免窗口已注册但编辑器未真正挂到界面的情况。
- README 与发布说明同步更新，版本号提升到 `v10.0.0`。

### English

- Neon now ships with a built-in OverlayUI compatibility layer, so vanilla-style clients can use overlay windows without installing a separate overlay bridge mod.
- The OverlayUI gear button is now fixed at the left-middle edge of the screen, and the lock icon state now switches correctly with the actual pinned state.
- The embedded compatibility layer now performs more reliable auto-initialization and recovery, preventing cases where windows were registered but the editor never attached to the scene.
- README and release notes were refreshed, and the package version is now `v10.0.0`.

---

## v9.0.2

### 中文

- 补全 Neon 设置页里仍显示英文的模块入口名，中文环境现在会显示对应中文标题。
- `betterMiniMap` 的开关标题改为直接显示“启用”。
- 保持 `BetterScreenShot` 中文标题逻辑不变，并沿用 GitHub Release workflow 的 JDK 修复。

### English

- Filled in the remaining English module titles in the Neon settings page so Chinese users now see localized section names.
- Changed the `betterMiniMap` toggle title to plain `Enable` in the translated UI.
- Kept the BetterScreenShot Chinese-title behavior and the GitHub Release workflow JDK fix.

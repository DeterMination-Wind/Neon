# Neon B11.2

本说明汇总 `B11.1` 之后至 `B11.2` 的全部提交。

## 中文

- 修复 MindustryX 环境下 Neon 设置页的运行时问题：总开关切换后模块不再实时移动、嵌套/折叠组状态卡死且重进设置页不重绘、折叠组内设置项右侧被截断。改用与 vanilla 行为等价的整表重绘方案绕开 MindustryX `SettingsTable` 的 `rebuild` 短路，为嵌套设置表短路自动重建，并将组布局改为随可用宽度自适应；同时为设置页增加宽度锚定，避免折叠状态下页面整体缩窄、展开时突然重绘。
- 全面降低内置子模组的运行时资源消耗：按需缓存分类查询与计算结果并在相关事件时失效（BetterHotKey 方块分类、WhoUsesThisBuilding 遮挡/标注计算、ServerPlayerDataBase 等），Tripwire 渲染、StealthPath、ForeignServerTranslator 翻译缓存、CustomMarker、PinyinSearchSupport、Random、PatchViewer、LongWindowFlow 等模块均减少不必要的每帧重算与空转。

## English

This release summarizes every commit after `B11.1` through `B11.2`.

- Fixes runtime issues on the Neon settings page under MindustryX: toggling a module master switch no longer relocates the module in real time, nested/collapsed group state could freeze and the page did not repaint after re-entering settings, and settings inside collapsed groups were clipped on the right edge. The fix replaces the rebuild path with a full table redraw equivalent to vanilla behavior (bypassing MindustryX's short-circuited `SettingsTable.rebuild`), short-circuits the auto-build on nested settings tables, makes group layout adapt to the available width, and anchors the settings page width so it no longer shrinks while collapsed or suddenly repaints on expansion.
- Reduces runtime resource consumption across bundled sub-mods: category lookups and computation results are now cached and invalidated on relevant events (BetterHotKey block categories, WhoUsesThisBuilding occlusion/label computation, ServerPlayerDataBase and more), and Tripwire rendering, StealthPath, ForeignServerTranslator translation cache, CustomMarker, PinyinSearchSupport, Random, PatchViewer, and LongWindowFlow all avoid unnecessary per-frame recomputation and idle work.

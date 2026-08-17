# Neon N12

本说明汇总 `B11.8` 之后至 `N12` 的全部更新：本次聚合了 21 个子模组的最新版本。

## 中文

**逻辑辅助（LogicSugar v2.1.4）**
- 新增 **if / elif / else 多条件分支**：在逻辑编辑器里可以直接搭出完整的条件分支链，逻辑更清晰；
- 新增 **continue 关键字**：for 与 while 循环内可跳过本轮剩余代码继续下一轮；
- 修复 for 循环变量问题：循环被跳出后再执行时，循环变量现在会正确重置；
- while 与 if 的条件编辑方式与跳转指令统一，更直观、更不易填错；
- 代码块与文本互相转换时不再破坏含下划线的变量名。

**界面优化**
- 多个模组新增**标题栏徽章条**：Radial Build Menu、betterLogisticsSpeed、betterHotKey、WhoUsesThisBuilding、Tripwire、BetterPolyAi；
- betterHotKey 自定义组合键现在支持第三个按键；
- LockAttack 作为辅助模组并入，随 Neon 统一管理。

**流畅度提升**
- 小地图关闭时不再空转，更省资源（betterMiniMap）；
- 补丁查看器、拼音搜索、谁在用建筑、地理围栏、随机化等模组的显示与查询缓存优化，操作更跟手（PatchViewer、PinyinSearchSupport、WhoUsesThisBuilding、Tripwire、Random）；
- 外语服务器翻译启动更快、翻译请求更节制（ForeignServerTranslator v1.2.1）；
- 电网小地图、物流速率等模组的性能与兼容性修复（Power Grid Minimap v1.18.3、betterLogisticsSpeed v2.0.1）。

**其它版本更新**
- Tripwire v1.1.1：围栏渲染优化、过期单位自动清理；
- PatchViewer v2.3.2、PinyinSearchSupport v2.3.2、Random v1.1.1、BetterPolyAi v0.1.2、BetterTerrainGen-V2 v1.1.3；
- 多模组的中文文案与细节完善。

## English

This release bundles the latest versions of 21 sub-mods since `B11.8`.

**LogicSugar v2.1.4 (logic editor)**
- New **if / elif / else branching** for the logic editor — build full condition chains visually;
- New **`continue` keyword** for for/while loops;
- Fixed loop variable reset when a for loop is re-entered after being exited;
- Unified, clearer condition editing for while/if, matching jump statements;
- Block-to-text conversion no longer corrupts variable names containing underscores.

**UI improvements**
- A new **badge bar below the title** in Radial Build Menu, betterLogisticsSpeed, betterHotKey, WhoUsesThisBuilding, Tripwire and BetterPolyAi;
- betterHotKey custom combos now support an optional third key;
- LockAttack is bundled as a helper mod.

**Smoother experience**
- Minimap no longer does idle work while disabled (betterMiniMap);
- Faster, cached lookups and drawing in PatchViewer, PinyinSearchSupport, WhoUsesThisBuilding, Tripwire and Random;
- Faster startup and throttled translation requests in ForeignServerTranslator v1.2.1;
- Performance and compatibility fixes for Power Grid Minimap v1.18.3 and betterLogisticsSpeed v2.0.1.

**Other version updates**
- Tripwire v1.1.1 (fence rendering cache, stale unit cleanup);
- PatchViewer v2.3.2, PinyinSearchSupport v2.3.2, Random v1.1.1, BetterPolyAi v0.1.2, BetterTerrainGen-V2 v1.1.3;
- Various text and detail polish across mods.

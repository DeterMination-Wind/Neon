# src/main/java/bektools/ 代理说明

`bektools` 是 Neon 的聚合壳：双入口、模块生命周期、统一设置页、遥测与共享 UI 组件。
具体实现以本目录代码为准；架构长文见 `../../../../docs/subsystems/bektools-core.md`。

## 入口

- `mod.json`：`main = bektools.BekToolsMod`，`mainX = bektools.BekToolsModX`。
- `BekToolsModX` 只向父类构造器传 MindustryX 桥与六个 `*ModX` 工厂；业务逻辑留在基类。
- 新模块需要 X 定制行为时，写 `*ModX` 变体并在 `BekToolsModX` 构造器替换工厂；
  不要在子模组包里直接探测 MindustryX。
- 全局 UI/事件型 feature（CustomMarker、BetterScreenShot、Profiler）必须在所有模块初始化后挂载，
  避免后续失败留下幽灵窗口。

## 生命周期与隔离

- `moduleGates` 是加载期总开关，语义是重启生效；关闭的模块不构造、不初始化、不注册钩子。
- `markBundled` / `initializeModule` / `initializeFeature` 捕获 `Throwable`，记入 `moduleFailures`；
  同一模块的第一次失败会短路后续初始化。`gatedModules`（用户关闭）与失败是两回事。
- 失败模块在设置页显示 `@bektools.module.failed`；不要静默吞掉，也不要把初始化移出隔离层。
- 模块字段可能为 null；所有消费点都要判空。

## 设置契约

- 子模组主类提供 `public static boolean bekBundled` 与
  `public void bekBuildSettings(SettingsMenuDialog.SettingsTable table)`。
- `bekBundled=true` 时不得再注册独立 `ui.settings.addCategory(...)`；有 `registerSettings()`
  的模块在 bundled 时必须 `if(bekBundled) return;`。
- 聚合入口在 `buildModuleEntries()` 用 `ModuleEntry` 挂载（标题、图标、`enableKey`、builder）；
  有主开关的模块关闭时收进“已禁用”折叠区；没有独立设置项的模块也要给占位
  `bektools.section.<id>.none`。
- 分组标题/说明写入 `tools/bektools-bundles/bundle.properties` 与 `bundle_zh_CN.properties`，
  确认合并后的 `src/main/resources/bundles/bundle*.properties` 可用。
- 样式统一用 `bektools.ui.RbmStyle`；`brf` 通过 `BetterRTSFormationSettings.configure()` 回调
  把设置交给 Neon，这是等价形态。

## 更新与遥测

- bundled 子模组必须短路自己的 `GithubUpdateCheck`，更新统一由 `modupdater` 负责。
- `PostHogUsageReporter` 在会话结束时上报 `mod_session_end` 使用快照：稳定玩家标识（UUID）/玩家名、
  游戏与模组版本、使用分钟数、模块可用性。**其中含标识符，不是匿名遥测**；改聚合入口时保持
  `snapshotSubmodStates()` 与实际模块列表一致，改动上报内容前先确认隐私边界。

## 设置页渲染陷阱

MindustryX 的 `SettingsTable.act()` 在 `list.size` 变化时会自动重建（`lastSize` 短路），
与原版不同。`NestedSettingsTable.build()` 覆盖、`redrawSettings()` 手动重建、
`addModuleGroupPref()` 走 `pref()`、`finishBuild()` 清空并重放 `pref` 项，都是为此存在。

- `build()` 故意不加 `@Override`：原版没有这个可覆盖方法，MindustryX 运行时才会分派。
- 展开分组时 `list` 从 0 增长到 N；不抑制会重放内容、折叠子分组并追加原版 reset 按钮。
- 改设置页渲染前先读这些注释；不要为“统一”删掉覆盖。

## 改动守则

- 不在核心里写业务逻辑；核心只做装配、设置汇总、隔离与指令/遥测挂接。
- 最小改动；复用现有入口（桥、`RbmStyle`、隔离层），不新增平行 helper 或包装层。
- 初始化副作用必须显式可见；日志用 `arc.util.Log` 并带 `Neon: ` 前缀。
- 用户可见文案走 bundle，中英同步。

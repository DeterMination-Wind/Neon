# 子系统：聚合核心（bektools）

`bektools` 包是 Neon 的壳：它不实现具体玩法功能，而是负责装载 29 个同步子模组与 Neon 内建 feature（customMarker、bss、profiler、usage reporter），收口设置、隔离故障、注册指令。相关页面：[架构总览](../architecture.md)、[开发指南的接入清单](../development.md)。

## 入口与构造

```text
BekToolsMod()                        ← 原版入口（main）
  └─ this(vanillaOverlayUi(), MarkerBridge.UNSUPPORTED, 各模块默认工厂)

BekToolsModX extends BekToolsMod     ← MDtX 入口（mainX）
  └─ super(new MindustryXOverlayUiBridge(), new MindustryXMarkerBridge(), X 工厂…)
```

- `vanillaOverlayUi()` 先经 `LegacyMindustryXGuard.rejectLegacyMindustryX("Neon")` 检查：若真 MindustryX 运行时误入原版 `main` 类（正常 X 走 `mainX`），直接抛出升级/回退错误，**不**吞成单个模块失败——这是故意的；守卫不做版本字符串比较。
- 构造函数签名中的可替换位只有六个：OverlayUi 桥、Marker 桥，以及 pgmm / sp / rbm / spdb / bpo / bhk 六个模块工厂（`ModSupplier<T>`）。其余模块直接在基类中固定构造。

## 启动时序

构造阶段按序做四类事情：`DataImagePackerCompat.installHooks()` 最先直接执行（不经加载门，也没有 try/catch 包裹）；随后的 `markBundled` / `initializeModule` / `initializeFeature` 三项都先过加载门并包裹故障隔离（见下）：

1. `DataImagePackerCompat.installHooks()` 安装数据图兼容钩子。
2. `markBundled(moduleId, …)`：把参与聚合的子模组主类的静态 `bekBundled` 置 `true`；个别模块附带一次性配置（如 `BetterRTSFormationSettings.configure()`）。ocb / customMarker / bss / profiler / usage reporter 不是子模组，不走这里。
3. `initializeModule(moduleId, supplier)`：实例化各模块对象并存为字段。工厂返回 `null` 视为失败。字段可能为 `null`，后续使用前都要判空。
4. `initializeFeature(moduleId, runnable)`：执行无对象产物的初始化（customMarker / bss / profiler），此时 OverlayUi 桥已就绪，可传入 `configureCompat/configureOverlayUi`。

### 加载门（总开关）

`moduleGates` 是每个模块的加载期总开关（moduleId → 开关键、默认值、开关行是否由 Neon 渲染）：

- `markBundled` / `initializeModule` / `initializeFeature` 入口先查 `isModuleGatedOut(moduleId)`：开关关闭时直接短路，不构造对象、不 `init()`、不注册任何钩子/监听器，并记入 `gatedModules`。它与 `moduleFailures` 是两个集合：前者是用户主动关闭，后者是初始化失败。
- 语义是**重启生效**：Java 类无法卸载，所以关闭开关只保证“下次启动不加载”；本次运行仍靠模块自己的运行期检查（如果它实现了）停止行为。
- 只有 usage-reporter 没有 gate：它是纯遥测基础设施，始终加载。`ocb`（OverlayUI 兼容层）也有总开关（`ocb-enabled`，默认开）：它在原生客户端为其它模块绑定 `mindustryX.features.ui.OverlayUI`；关掉后原版客户端完全不再提供悬浮窗口（无齿轮/无 `Z`/`registerWindow` 为空操作），内置冻结副本 `neoncompat.overlay` 只在 ocb 构造失败时兜底（详见 [overlay-compat.md](overlay-compat.md)）。
- 没有自带开关（或完全没有设置项）的模块由 `ModuleEntry.neonSwitch` 标记，Neon 用 `RbmStyle.IconCheckSetting` 在分组首行渲染总开关；键沿用各模块自身的设置前缀（多为 `<prefix>-enabled`，如 `smp-enabled`、`ls-enabled`、`random-enabled`、`lockattack-enabled`）。

之后在 `ClientLoadEvent` 中：

- `postHogUsageReporter.onClientLoad()`（usage-reporter，单独 try/catch）；
- `registerSettings()` 注册统一设置页，另有一层 try/catch——设置挂载失败也不能影响游戏加载。

## 故障隔离模型

三个原语共用一个 `LinkedHashMap<String, Throwable> moduleFailures`：

| 原语 | 输入 | 行为 |
| --- | --- | --- |
| `markBundled` | 一个副作用 | 抛错 → 记录失败 |
| `initializeModule` | 模块工厂 | 抛错/返回 null → 记录失败并返回 null |
| `initializeFeature` | 一段初始化代码 | 抛错 → 记录失败 |

关键语义：

- 加载门先于失败判定：开关关闭的模块不会进入原语，也不会被记成失败（见上文的 `gatedModules`）。
- `isModuleFailed(moduleId)` 为真时，后续针对同一模块的所有初始化/注册调用**直接短路**——第一个异常是唯一被记录的。
- `recordModuleFailure` 保证每个模块只记第一次异常，并打日志 `"failed; continuing without it"`。
- 使用顺序敏感的资源时要小心：profiler 窗口必须在 customMarker/bss 之后初始化或同等隔离，避免"后续模块失败留下幽灵窗口"（源码注释明确点名了这个坑）。
- 失败可见性：设置页尾追加 `@bektools.module.failed` 分组展示各模块失败信息，而不是静默消失。

## 设置系统

`registerSettings()` 只执行一次（`settingsRegistered` 标志防重入）：

1. 收集各模块状态做诊断快照 `snapshotSubmodStates()`（模块可用性 + 当前启用与否，供会话结束遥测快照；像"建造透视是否启用"、"翻译未标记服务器则视为未启用"、"地理围栏是否已配置控制键"这类动态值在这里计算）。
2. 按 module id 逐个把模块的 `bekBuildSettings(SettingsTable)` 内容加进统一分组；分组标题与说明走 bundle key（无设置项的模块用 `bektools.section.<id>.none` 占位），样式统一 `bektools.ui.RbmStyle`（子标题、缩进、间距组件，见 `bektools/ui/VscodeSettingsStyle.java` 等）。
3. 启动时被加载门短路（`gatedModules`）的模块不调用它的 builder：改由 `addUnloadedPlaceholder` 渲染 Neon 自绘的总开关加 `@bektools.module.disabled` / `@bektools.module.restart` 说明；`neonSwitch` 模块在正常分组首行也由 Neon 渲染开关。
4. 失败模块追加错误分组。

## 设置页渲染陷阱（MindustryX）

MindustryX 的 `SettingsTable.act()` 在 `list.size` 变化时会自动重建（`lastSize` 短路），与原版行为不同。
现有几处写法都是为此服务，改动前先读代码注释：

- `NestedSettingsTable.build()` 是故意的运行期覆盖，**不加 `@Override`**：原版没有这个可覆盖方法，MindustryX 运行时才会分派到它。
- `finishBuild()` 会把 `list` 从 0 增长到 N 并只重放 `pref()` 项；不抑制重建会重放内容、折叠子分组并追加原版 reset 按钮。
- `redrawSettings()` 手动清空并重建；`addModuleGroupPref()` 必须走 `pref()`，直接 `add()` 会被清掉。
- 不要为“统一”删掉这些覆盖或 `suppressRebuild`；改动设置页渲染后按原版与 MindustryX 两条路径手测。

## 客户端指令

`registerClientCommands(CommandHandler)` 对带指令的模块逐一走 `registerModuleCommands(moduleId, available, registration)`：

- 当前有指令的模块：`profiler`（性能分析）、`pgmm`（电网小地图）、`sp`(偷袭小道)、`rbm`（圆盘建造）、`spdb`（玩家数据库）。
- available 或失败任一不满足就跳过；注册过程抛错同样记录为模块失败。

## 内部模块

不属于玩家功能但由核心管理的两个内部单元：

- `moduleProfiler`：`NeonProfilerFeature`，提供运行时性能剖析指令。
- `moduleUsageReporter`：`PostHogUsageReporter`，会话结束时上报 `mod_session_end` 使用快照（玩家标识/名称、游戏与模组版本、使用分钟数、模块可用性）；含标识符，**不是匿名遥测**。独立捕获异常（不上报 ≠ 不可玩）。

## 改动守则

- 新模块按[开发指南清单](../development.md)接入；不改 `BekToolsModX` 除非该模块真的需要 X 变体。
- 不要在核心里写业务逻辑；核心只做装配、设置汇总和隔离。
- 所有对模块字段的消费点都必须容忍 `null`（初始化失败时它们就是 null）。

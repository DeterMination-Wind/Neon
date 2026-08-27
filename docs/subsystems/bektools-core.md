# 子系统：聚合核心（bektools）

`bektools` 包是 Neon 的壳：它不实现具体玩法功能，而是负责装载 29 个功能模块、收口设置、隔离故障、注册指令。相关页面：[架构总览](../architecture.md)、[开发指南的接入清单](../development.md)。

## 入口与构造

```text
BekToolsMod()                        ← 原版入口（main）
  └─ this(vanillaOverlayUi(), MarkerBridge.UNSUPPORTED, 各模块默认工厂)

BekToolsModX extends BekToolsMod     ← MDtX 入口（mainX）
  └─ super(new MindustryXOverlayUiBridge(), new MindustryXMarkerBridge(), X 工厂…)
```

- `vanillaOverlayUi()` 先经 `LegacyMindustryXGuard.rejectLegacyMindustryX("Neon")` 检查：若旧版 MindustryX 加载器误入原版 main 类，直接抛出升级/回退错误，**不**吞成单个模块失败——这是故意的。
- 构造函数签名中的可替换位只有六个：OverlayUi 桥、Marker 桥，以及 pgmm / sp / rbm / spdb / bpo / bhk 六个模块工厂（`ModSupplier<T>`）。其余模块直接在基类中固定构造。

## 启动时序

构造阶段按序做四类事情，全部包裹故障隔离：

1. `DataImagePackerCompat.installHooks()` 安装数据图兼容钩子。
2. `markBundled(moduleId, …)`：把每个模块主类的静态 `bekBundled` 置 `true`；个别模块附带一次性配置（如 `BetterRTSFormationSettings.configure()`）。
3. `initializeModule(moduleId, supplier)`：实例化各模块对象并存为字段。工厂返回 `null` 视为失败。字段可能为 `null`，后续使用前都要判空。
4. `initializeFeature(moduleId, runnable)`：执行无对象产物的初始化（customMarker / bss / profiler），此时 OverlayUi 桥已就绪，可传入 `configureCompat/configureOverlayUi`。

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

- `isModuleFailed(moduleId)` 为真时，后续针对同一模块的所有初始化/注册调用**直接短路**——第一个异常是唯一被记录的。
- `recordModuleFailure` 保证每个模块只记第一次异常，并打日志 `"failed; continuing without it"`。
- 使用顺序敏感的资源时要小心：profiler 窗口必须在 customMarker/bss 之后初始化或同等隔离，避免"后续模块失败留下幽灵窗口"（源码注释明确点名了这个坑）。
- 失败可见性：设置页尾追加 `@bektools.module.failed` 分组展示各模块失败信息，而不是静默消失。

## 设置系统

`registerSettings()` 只执行一次（`settingsRegistered` 标志防重入）：

1. 收集各模块状态做诊断快照 `snapshotSubmodStates()`（模块可用性 + 当前启用与否，供设置页状态展示；像"偷袭小道默认关"、"翻译未标记服务器则视为未启用"这类动态值在这里计算）。
2. 按 module id 逐个把模块的 `bekBuildSettings(SettingsTable)` 内容加进统一分组；分组标题与说明走 bundle key（无设置项的模块用 `bektools.section.<id>.none` 占位），样式统一 `bektools.ui.RbmStyle`（子标题、缩进、间距组件，见 `bektools/ui/VscodeSettingsStyle.java` 等）。
3. 失败模块追加错误分组。

## 客户端指令

`registerClientCommands(CommandHandler)` 对带指令的模块逐一走 `registerModuleCommands(moduleId, available, registration)`：

- 当前有指令的模块：`profiler`（性能分析）、`pgmm`（电网小地图）、`sp`(偷袭小道)、`rbm`（圆盘建造）、`spdb`（玩家数据库）。
- available 或失败任一不满足就跳过；注册过程抛错同样记录为模块失败。

## 内部模块

不属于玩家功能但由核心管理的两个内部单元：

- `moduleProfiler`：`NeonProfilerFeature`，提供运行时性能剖析指令。
- `moduleUsageReporter`：`PostHogUsageReporter`，客户端加载时的匿名使用统计上报，独立捕获异常（不上报 ≠ 不可玩）。

## 改动守则

- 新模块按[开发指南清单](../development.md)接入；不改 `BekToolsModX` 除非该模块真的需要 X 变体。
- 不要在核心里写业务逻辑；核心只做装配、设置汇总和隔离。
- 所有对模块字段的消费点都必须容忍 `null`（初始化失败时它们就是 null）。

# 子系统：聚合核心（bektools）

`bektools` 包是 Neon 的聚合外壳：它本身不实现具体玩法功能，而是负责装载 29 个同步子模组及 Neon 内置功能（`customMarker`、`bss`、`profiler`、`usage reporter`），并统一完成设置收口、故障隔离与客户端指令注册。相关文档：[架构总览](../architecture.md)、[开发指南接入清单](../development.md)。

## 入口与构造

```text
BekToolsMod()                        ← 原版入口（main）
  └─ this(vanillaOverlayUi(), MarkerBridge.UNSUPPORTED, 各模块默认工厂)

BekToolsModX extends BekToolsMod     ← MDtX 入口（mainX）
  └─ super(new MindustryXOverlayUiBridge(), new MindustryXMarkerBridge(), X 工厂…)
```

- `vanillaOverlayUi()` 会先执行 `LegacyMindustryXGuard.rejectLegacyMindustryX("Neon")`：一旦真 MindustryX 运行时误入原版 `main` 类（正常 X 客户端应走 `mainX`），直接抛出升级/回退错误，**不会**吞并成单个模块失败——这是有意设计，且守卫不做版本字符串比较。
- 构造函数签名中的可替换位共有 6 个：`OverlayUiBridge`、`MarkerBridge`，以及 `pgmm`、`sp`、`rbm`、`spdb`、`bpo`、`bhk` 六个模块工厂（`ModSupplier<T>`）。其余模块均在基类中固定构造。

## 启动时序

构造阶段按序执行四类操作：其中 `DataImagePackerCompat.installHooks()` 最先直接运行（不经过加载门，也不包裹 `try/catch`）；随后的 `markBundled`、`initializeModule`、`initializeFeature` 三项均先通过加载门检查，再包裹故障隔离：

1. `DataImagePackerCompat.installHooks()`：安装数据图兼容钩子。
2. `markBundled(moduleId, …)`：将参与聚合的子模组主类上的静态 `bekBundled` 置为 `true`；个别模块附带一次性配置（如 `BetterRTSFormationSettings.configure()`）。`ocb`、`customMarker`、`bss`、`profiler` 与 `usage reporter` 不属于此类子模组，不走这一步。
3. `initializeModule(moduleId, supplier)`：实例化各模块对象并保存为字段。若工厂返回 `null`，直接判定为初始化失败。模块字段可能为 `null`，所有后续使用点都必须判空。
4. `initializeFeature(moduleId, runnable)`：执行无对象产物的内置功能初始化（`customMarker`、`bss`、`profiler`）；此时 `OverlayUiBridge` 已就绪，可直接传入 `configureCompat` / `configureOverlayUi`。

### 加载门（模块主开关）

`moduleGates` 管理每个模块的加载期主开关（映射 `moduleId` → 开关键、默认值、主开关行是否由 Neon 渲染）：

- `markBundled`、`initializeModule` 与 `initializeFeature` 在入口处均会先检查 `isModuleGatedOut(moduleId)`：当主开关关闭时直接短路，不构造对象、不调用 `init()`、不注册任何钩子或监听器，并将该模块记入 `gatedModules`。`gatedModules` 与 `moduleFailures` 彼此独立：前者代表用户主动关闭，后者代表初始化失败。
- 语义为**重启生效**：Java 类无法在运行期卸载，因此关闭主开关保证“下次启动不加载”；本次会话内则依赖模块自身的运行期开关检查（若已实现）停止行为。
- 只有 `usage-reporter` 没有加载门，它作为纯遥测基础设施始终加载。`ocb`（OverlayUI 兼容层）同样受主开关控制（`ocb-enabled`，默认开启）：它在原版客户端为其他模块绑定 `mindustryX.features.ui.OverlayUI`；关闭后原版客户端不再提供任何悬浮窗口（无齿轮按钮、无 `Z` 键，且 `registerWindow` 退化为空操作），内嵌冻结副本 `neoncompat.overlay` 仅在 ocb 构造失败时兜底（详见 [overlay-compat.md](overlay-compat.md)）。
- 没有自带开关（或完全没有设置项）的模块通过 `ModuleEntry.neonSwitch` 标记，由 Neon 使用 `RbmStyle.IconCheckSetting` 在对应分组首行渲染主开关；配置键沿用各模块自身前缀（多为 `<prefix>-enabled`，如 `smp-enabled`、`ls-enabled`、`random-enabled`、`lockattack-enabled`）。

随后在 `ClientLoadEvent` 阶段执行：

- `postHogUsageReporter.onClientLoad()`（`usage-reporter`，单独 `try/catch` 隔离）。
- `registerSettings()` 注册统一设置页，外层再包一层 `try/catch`，确保即使设置挂载异常也不会阻塞游戏加载。

## 故障隔离模型

三个初始化原语共用同一个 `LinkedHashMap<String, Throwable> moduleFailures`：

| 原语 | 输入 | 行为 |
| --- | --- | --- |
| `markBundled` | 副作用操作 | 抛错 → 记录失败 |
| `initializeModule` | 模块工厂 | 抛错或返回 `null` → 记录失败并返回 `null` |
| `initializeFeature` | 初始化代码块 | 抛错 → 记录失败 |

关键语义：

- 加载门优先于失败判定：主开关关闭的模块不会进入初始化原语，也不会被误记为失败（归入 `gatedModules`）。
- 当 `isModuleFailed(moduleId)` 为 `true` 时，后续针对同一模块的所有初始化和注册调用都会**直接短路**，首个异常即为唯一记录的根因。
- `recordModuleFailure` 确保每个模块只记录首次异常，并输出日志 `"failed; continuing without it"`。
- 涉及顺序敏感资源时需格外谨慎：例如 `profiler` 窗口必须在 `customMarker` / `bss` 之后初始化或做同等隔离，避免“后续模块失败却留下幽灵窗口”（源码注释已明确标出此坑）。
- 失败可见性：所有初始化失败的模块都会在设置页尾部追加 `@bektools.module.failed` 错误分组并展示原因，绝不静默消失。

## 设置系统

`registerSettings()` 通过 `settingsRegistered` 标志防重入，整个生命周期只执行一次：

1. 调用 `snapshotSubmodStates()` 收集各模块诊断快照（包含模块可用性与当前启用状态，供会话结束遥测使用；例如建造透视是否启用、翻译功能未标记外语服务器则视为未启用、地理围栏是否已配置控制键等动态状态均在此计算）。
2. 按 `moduleId` 依次将各模块的 `bekBuildSettings(SettingsTable)` 内容挂载进统一分组；分组标题与说明走 bundle key（无设置项的模块使用 `bektools.section.<id>.none` 占位），样式统一使用 `bektools.ui.RbmStyle`（子标题、勾选框、滑块、文本行、间距组件等，见 `bektools/ui/VscodeSettingsStyle.java`）。
3. 启动期被加载门短路的模块（`gatedModules`）不调用其 builder，改由 `addUnloadedPlaceholder` 渲染 Neon 自绘的主开关及 `@bektools.module.disabled` / `@bektools.module.restart` 提示；`neonSwitch` 模块在正常分组首行同样由 Neon 渲染主开关。
4. 在设置页尾部追加所有失败模块的错误分组。

## 设置页渲染陷阱（MindustryX）

MindustryX 的 `SettingsTable.act()` 会在 `list.size` 发生变化时自动触发重建（`lastSize` 短路），这与原版行为不同。以下写法均为适配该行为，修改前请先阅读源码注释：

- `NestedSettingsTable.build()` 属于有意的运行期覆盖，**不要添加 `@Override`**：原版并不存在该可覆盖方法，仅在 MindustryX 运行时会分派到它。
- `finishBuild()` 会使 `list` 从 0 增长至 N，并只重放 `pref()` 项；若不抑制重建，会导致内容重放、子分组折叠并额外追加原版 reset 按钮。
- `redrawSettings()` 负责手动清空并重建；`addModuleGroupPref()` 必须走 `pref()`，直接 `add()` 的元素会在重建时被清空。
- 切勿为了“风格统一”删除上述覆盖或 `suppressRebuild`；改动设置页渲染后，务必在原版与 MindustryX 两条路径上分别手测。

## 客户端指令

`registerClientCommands(CommandHandler)` 会对所有带客户端指令的模块逐一调用 `registerModuleCommands(moduleId, available, registration)`：

- 当前提供客户端指令的模块：`profiler`（性能分析）、`pgmm`（电网小地图）、`sp`（偷袭小道）、`rbm`（圆盘建造）、`spdb`（玩家数据库）。
- 只要 `available` 为假或模块已失败，就跳过注册；注册过程抛出的异常同样记录为模块失败。

## 内部模块

以下两个内部单元不属于玩家功能，但由聚合核心统一管理：

- `moduleProfiler`：`NeonProfilerFeature`，提供运行时性能剖析指令。
- `moduleUsageReporter`：`PostHogUsageReporter`，在会话结束时上报 `mod_session_end` 使用快照（包含玩家标识/名称、游戏与模组版本、使用时长、模块可用性）；由于包含玩家标识符，它**不属于匿名遥测**。该模块独立捕获异常，上报失败不影响正常游玩。

## 改动守则

- 新增模块严格按照 [开发指南接入清单](../development.md) 接入；除非模块确实需要 X 变体，否则不要改动 `BekToolsModX`。
- 不要在聚合核心中掺入具体玩法业务逻辑；核心只负责装配、设置汇总与故障隔离。
- 所有消费模块字段的位置都必须容忍 `null`（模块初始化失败时对应字段即为 `null`）。

# 架构总览

修改 `src/` 下的任何代码前，请先阅读本文。本文说明 Neon 作为“聚合模组”的整体组织方式，包括功能装载、设置收口与 MindustryX 兼容机制。面向玩家的逐功能说明见 [用户指南](user/index.md)。

## 聚合原则

Neon 由大量原本独立的子模组（如 AutoPruner、betterMiniMap、LogicSugar 等）合并而成，合并过程遵守三条不变式：

1. **一个安装包**：所有功能打包进同一个 JAR，发布产物只有 `Neon.jar` / `Neon.zip`（均含 Android `classes.dex`）。
2. **一个设置入口**：所有子模组设置统一并入 Neon 总设置页（`bektools.BekToolsMod#registerSettings()`），聚合态下子模组不得再注册独立的 `ui.settings.addCategory(...)`。
3. **故障隔离**：任意单个子模组初始化失败，都不能影响其他功能或游戏本体的正常加载。

## 双入口：main 与 mainX

原版 Mindustry 只加载 `mod.json` 中 `main` 指向的类；当 MindustryX 类加载器存在时，则会优先加载 `mainX`。

| 入口 | 类 | 运行环境 | 差异 |
| --- | --- | --- | --- |
| `main` | `bektools.BekToolsMod` | 原版客户端 | 默认使用 ocb 同步提供的同 FQCN `mindustryX.features.ui.OverlayUI`；仅当 ocb 构造失败时回退到冻结副本 `neoncompat.overlay`；桥实现走 `autoDetect()` |
| `mainX` | `bektools.BekToolsModX` | MindustryX 或兼容加载器 | 注入 `MindustryXOverlayUiBridge` 与 `MindustryXMarkerBridge`，并将若干模块替换为 `*ModX` 变体（PowerGridMinimap、StealthPath、RadialBuildMenu、ServerPlayerDataBase、BetterProjectorOverlay、BetterHotKey） |

两个入口共享同一套模块代码，差异仅体现在构造函数传入的桥实现以及被覆盖的模块工厂上。所有业务逻辑必须写在非 X 基类中，X 变体只负责环境适配。

## 模块生命周期

核心装载流程全部位于 `BekToolsMod` 中（逐段详解见 [subsystems/bektools-core.md](subsystems/bektools-core.md)）：

1. **构造**：基类接收 `OverlayUiBridge`、`MarkerBridge` 实例以及一组默认模块工厂，X 入口可按位替换其中工厂。除 usage reporter 外，每个模块都会先检查加载门（`moduleGates`，即重启后生效的模块主开关）：处于关闭状态的模块不构造、不初始化、不注册钩子。
2. **初始化**：每个模块通过 `initializeModule` / `initializeFeature` 包装执行。任意 `Throwable` 都会被捕获并记入 `moduleFailures`（`LinkedHashMap<moduleId, Throwable>`），不向外传播。
3. **注册设置**：在 `ClientLoadEvent` 中调用 `registerSettings()`，将各模块的 `bekBuildSettings(SettingsTable)` 汇总到统一分组中；初始化失败的模块会在设置页尾部追加 `@bektools.module.failed` 错误分组，不会静默消失。
4. **注册指令**：`registerClientCommands()` 对每个带客户端指令的模块执行相同的失败守卫后逐一挂载（`profiler` / `pgmm` / `sp` / `rbm` / `spdb`）。

## 设置系统

- 子模组主类契约（由同步脚本自动断言）：必须提供 `public static boolean bekBundled` 与 `public void bekBuildSettings(SettingsMenuDialog.SettingsTable table)` 两个成员。
- 所有 `ui.settings.addCategory(...)` 调用必须包裹在 `if(!bekBundled)` 中；若模块存在 `registerSettings()`，开头必须使用 `if(bekBundled) return;` 早退。
- 分组标题与说明文案全部使用 bundle key，UI 样式统一使用 `bektools.ui.RbmStyle`；没有独立设置项的模块也必须保留占位分组（`bektools.section.<id>.none`）。
- 文案真源位于 `tools/bektools-bundles/bundle*.properties`，由同步脚本合并至 `src/main/resources/bundles/bundle*.properties`。

## 兼容层

Neon 原生代码不直接 `import` MindustryX 类，而是通过桥接口隔离外部依赖（同步产物中遗留的历史反射属于例外，不得继续扩散）：

| 接口 | 作用 | 实现 |
| --- | --- | --- |
| `mdtxcompat.OverlayUiBridge` | OverlayUI 能力来源 | `MindustryXOverlayUiBridge`（复用 MDtX）/ `NeonEmbeddedOverlayUiBridge`（ocb 失败时的冻结兜底）/ `NoopOverlayUiBridge` |
| `mdtxcompat.MarkerBridge` | 标记能力 | `MindustryXMarkerBridge` / `NoopMarkerBridge` |
| `mdtxcompat.SchematicShareBridge` | 蓝图分享 | `MindustryXSchematicShareBridge` / `NoopSchematicShareBridge` |
| `mdtxcompat.AutoDetectingOverlayUiBridge` | 首次使用时锁定委托 | 运行时探测 |

配套守卫与兜底机制：

- `LegacyMindustryXGuard` 通过核心标记类（`mindustryX.VarsX` / `mindustryX.loader.Main`）判定真 MindustryX 运行时；若真 X 误入原版 `main` 入口，直接抛出升级/回退指引，不静默降级。`MINIMUM_VERSION`（当前为 `2026.04.03.B439`）是声明的最低版本与拒绝信息中的升级目标，但当前代码**不比较版本字符串**；正常 X 客户端应走 `mainX`。
- `mdtxcompat.AutoDetectingOverlayUiBridge` 在首次真正使用时锁定委托：优先真 X，其次为 ocb 提供的 OverlayUI，最后回退到冻结副本（详见 [subsystems/overlay-compat.md](subsystems/overlay-compat.md)）。
- `neoncompat.overlay` 是 ocb 接入前的手工冻结副本（`OverlayUI` + `AdsorptionSystem` + `NeonOverlayBootstrap`），仅在 **ocb 构造失败**时作为兜底；正常原版体验由 ocb 提供，切勿手工同步这两份实现。

## 构建与依赖形态

- 目标字节码为 Java 17（`options.release.set(17)`），Kotlin 仅用于 `advancedreplace` 包。
- 编译期优先使用工作区本地的 `Mindustry-master` core/desktop JAR 与 Arc JAR；缺失时回退到 JitPack `v160.1`，并排除其传递的 Arc 依赖，改用 `tools/deps/` 下的 `arc-core` / `arcnet` JAR。
- `build.gradle` 默认不将 MindustryX 加入编译类路径；X 定制通过 `mdtxcompat` 与 `*ModX` 完成，编译期不要直接引用 `mindustryX.*`。
- 运行时仅引入两项外部依赖：`pinyin4j`（拼音搜索）与 `sqlite-jdbc`（SPDB 存储），二者均打入最终 JAR；D8 输入还会额外包含 `kotlin-stdlib`。`sqlite-jdbc` 仅保留 Windows / Linux / macOS 的 `x86_64` 与 `aarch64` 本机库，其余平台（FreeBSD、musl、32 位、Android）由 JSON 分片后端兜底。
- SPDB 语义搜索使用本地 GGUF 嵌入模型（`bge-base-zh-v1.5-q8_0.gguf`）；`downloadEmbeddingModel` 为按需手动任务，并非默认构建依赖。该模型体积超过 100 MB，已通过 `.gitignore` 排除。
- 发布管线（`deploy`）产出桌面与 Android 合并包，细节见 [release.md](release.md) 与 [development.md](development.md)。

## 目录速查

```text
src/main/java/
|-- bektools/            聚合核心：入口、设置系统、RbmStyle、profiler
|-- mdtxcompat/          对外兼容桥接口与 MDtX 实现
|-- overlaycompat/       ocb 同步产物：原版 Overlay 兼容层
|-- mindustryX/features/ui/  ocb 同步产物：同 FQCN 的 OverlayUI 副本
|-- neoncompat/overlay/  冻结兜底（仅 ocb 构造失败时使用）
|-- mindustry/           需要触碰游戏内部包时的扩展点（logic/maps/ui；同步产物）
|-- <feature>/           其余每目录一个功能模块（见根目录 AGENTS.md 的「目录地图」）
src/main/kotlin/advancedreplace/   高级替换（Kotlin，同步产物）
tools/                   子模组同步、版本码、bundle 合并脚本
```

## 设计约束（务必保持）

- 纯客户端：不要求服务器安装；涉及单位命令的功能只能走 Mindustry 原有客户端命令路径。
- 文案解耦：所有用户可见文案一律走 bundle，禁止硬编码。
- 克制改动：变更聚焦性能与可读性，不顺手重构无关模块。

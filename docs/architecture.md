# 架构总览

改 `src/` 下的任何代码前先读本文。它解释 Neon 作为"聚合模组"的组织方式：功能如何被装载、设置如何被收口、MindustryX 兼容如何做。逐功能的玩家视角说明见[用户指南](user/index.md)。

## 聚合原则

Neon 由大量原本独立的子模组（AutoPruner、betterMiniMap、LogicSugar……）合并而成。合并遵守三条不变式：

1. **一个安装包**：所有功能打进同一个 jar，发布产物只有 `Neon.jar` / `Neon.zip`（含安卓 `classes.dex`）。
2. **一个设置入口**：所有子模组设置并入 Neon 总设置页（`bektools.BekToolsMod#registerSettings()`），子模组不得在聚合态再注册独立的 `ui.settings.addCategory(...)`。
3. **故障隔离**：任何一个子模组初始化失败都不能拖垮其他功能和游戏本体。

## 双入口：main 与 mainX

Mindustry 只会加载 `mod.json` 里 `main` 指向的类；MindustryX 类加载器存在时则优先加载 `mainX`。

| 入口 | 类 | 环境 | 差异 |
| --- | --- | --- | --- |
| `main` | `bektools.BekToolsMod` | 原版客户端 | 默认使用 ocb 同步来的同 FQCN `mindustryX.features.ui.OverlayUI`；ocb 构造失败时回退冻结副本 `neoncompat.overlay`；桥走 `autoDetect()` |
| `mainX` | `bektools.BekToolsModX` | MindustryX 或兼容加载器 | 注入 `MindustryXOverlayUiBridge`、`MindustryXMarkerBridge`，并把若干模块换成 `*ModX` 变体（PowerGridMinimap、StealthPath、RadialBuildMenu、ServerPlayerDataBase、BetterProjectorOverlay、BetterHotKey） |

两个入口共享同一套模块代码；差异只体现在构造函数传入的桥实现和被覆盖的模块工厂上。逻辑必须写在非 X 类中，X 变体只做环境适配。

## 模块生命周期

核心流程都在 `BekToolsMod` 中（见 [subsystems/bektools-core.md](subsystems/bektools-core.md) 的逐段说明）：

1. **构造**：基类接收 `OverlayUiBridge` 与 `MarkerBridge` 实例和一组"默认模块工厂"，X 入口可以按位替换其中的工厂。每个模块（usage reporter 除外）先查加载期总开关（`moduleGates`，重启后生效）：关闭的模块不构造、不初始化、不注册钩子。
2. **初始化**：每个模块经 `initializeModule` / `initializeFeature` 包装执行。任何 `Throwable` 都会被捕获并记入 `moduleFailures`（`LinkedHashMap<moduleId, Throwable>`），不会向上传播。
3. **注册设置**：在 `ClientLoadEvent` 中调用 `registerSettings()`，把各模块的 `bekBuildSettings(SettingsTable)` 汇总进统一分组；失败的模块会在设置页尾追加 `@bektools.module.failed` 错误分组，而不是静默消失。
4. **注册指令**：`registerClientCommands()` 对每个带客户端指令的模块做同样的"是否失败"守卫后逐一挂载（profiler / pgmm / sp / rbm / spdb）。

## 设置系统

- 子模组主类契约（由同步脚本断言）：提供 `public static boolean bekBundled` 与 `public void bekBuildSettings(SettingsMenuDialog.SettingsTable table)` 两个成员。
- 所有 `ui.settings.addCategory(...)` 调用必须包在 `if(!bekBundled)` 里；存在 `registerSettings()` 时必须以 `if(bekBundled) return;` 早退。
- 分组标题、说明文案走 bundle key，样式统一用 `bektools.ui.RbmStyle`；没有独立设置项的模块也要放一个占位分组（`bektools.section.<id>.none`）。
- 文案源头是 `tools/bektools-bundles/bundle*.properties`，由同步脚本合并到 `src/main/resources/bundles/bundle*.properties`。

## 兼容层

Neon 原生代码不直接 import MindustryX 类，而是通过桥接口隔离（同步产物里的历史反射是例外，不要扩散）：

| 接口 | 作用 | 实现 |
| --- | --- | --- |
| `mdtxcompat.OverlayUiBridge` | OverlayUI 能力来源 | `MindustryXOverlayUiBridge`（复用 MDtX）/ `NeonEmbeddedOverlayUiBridge`（ocb 失败时的冻结兜底）/ `NoopOverlayUiBridge` |
| `mdtxcompat.MarkerBridge` | 标记能力 | `MindustryXMarkerBridge` / `NoopMarkerBridge` |
| `mdtxcompat.SchematicShareBridge` | 蓝图分享 | `MindustryXSchematicShareBridge` / `NoopSchematicShareBridge` |
| `mdtxcompat.AutoDetectingOverlayUiBridge` | 首次使用时锁定委托 | 运行时探测 |

配套守卫：

- `LegacyMindustryXGuard` 用核心标记类（`mindustryX.VarsX` / `mindustryX.loader.Main`）判定真 MindustryX；真 X 运行时误入原版 `main` 时直接抛出升级/回退指引，不静默降级。`MINIMUM_VERSION`（当前 `2026.04.03.B439`）是声明的最低版本与拒绝信息中的升级目标，但当前代码**不比较版本字符串**；正常 X 客户端走 `mainX`。
- `mdtxcompat.AutoDetectingOverlayUiBridge` 首次真正使用时锁定委托：真 X 优先，其次是 ocb 提供的 OverlayUI，最后回退冻结副本（详见 [subsystems/overlay-compat.md](subsystems/overlay-compat.md)）。
- `neoncompat.overlay` 是 ocb 接入前的手工冻结副本（`OverlayUI` + `AdsorptionSystem` + `NeonOverlayBootstrap`），仅在 **ocb 构造失败**时兜底；正常原版体验由 ocb 提供，两者不要手工同步。

## 构建与依赖形态

- Java 17 字节码（`options.release.set(17)`），Kotlin 仅用于 `advancedreplace` 包。
- 编译期优先使用工作区本地的 `Mindustry-master` core/desktop jar 与 Arc jar；缺省时退回 JitPack `v160.1` 并排除其可传递的 arc 依赖，改用 `tools/deps/` 里的 arc-core / arcnet jar。
- `build.gradle` 默认不把 MindustryX 挂进编译类路径；X 定制走 `mdtxcompat` / `*ModX`，编译期不要引用 `mindustryX.*`。
- 运行时依赖仅两件外部库：`pinyin4j`（拼音搜索）与 `sqlite-jdbc`（SPDB 存储）。二者都会被打进最终 jar；D8 输入还额外包含 `kotlin-stdlib`。`sqlite-jdbc` 只保留 Windows / Linux / macOS 的 x86_64 与 aarch64 本机库，其余平台（FreeBSD、musl、32 位、Android）由 JSON 分片后端兜底。
- SPDB 的语义搜索使用嵌入 GGUF 模型（`bge-base-zh-v1.5-q8_0.gguf`）；`downloadEmbeddingModel` 是按需手动任务，不是构建依赖。模型 >100MB，已被 `.gitignore` 排除。
- 发布管线（`deploy`）产出桌面+安卓合并包，细节见 [release.md](release.md) 与 [development.md](development.md)。

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
- 用户可见文案一律走 bundle，不硬编码。
- 变更聚焦性能与可读性，不顺手重构无关模块。

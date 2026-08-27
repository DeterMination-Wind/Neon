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
| `main` | `bektools.BekToolsMod` | 原版客户端 | 使用 Neon 内嵌的 Overlay 实现（`neoncompat.overlay`）与 Noop/Mdetector 自动探测桥 |
| `mainX` | `bektools.BekToolsModX` | MindustryX 或兼容加载器 | 注入 `MindustryXOverlayUiBridge`、`MindustryXMarkerBridge`，并把若干模块换成 `*ModX` 变体（PowerGridMinimap、StealthPath、RadialBuildMenu、ServerPlayerDataBase、BetterProjectorOverlay、BetterHotKey） |

两个入口共享同一套模块代码；差异只体现在构造函数传入的桥实现和被覆盖的模块工厂上。逻辑必须写在非 X 类中，X 变体只做环境适配。

## 模块生命周期

核心流程都在 `BekToolsMod` 中（见 [subsystems/bektools-core.md](subsystems/bektools-core.md) 的逐段说明）：

1. **构造**：基类接收 `OverlayUiBridge` 与 `MarkerBridge` 实例和一组"默认模块工厂"，X 入口可以按位替换其中的工厂。
2. **初始化**：每个模块经 `initializeFeature(moduleId, initializer)` 包装执行。任何 `Throwable` 都会被捕获并记入 `moduleFailures`（`LinkedHashMap<moduleId, Throwable>`），不会向上传播。
3. **注册设置**：在 `ClientLoadEvent` 中调用 `registerSettings()`，把各模块的 `bekBuildSettings(SettingsTable)` 汇总进统一分组；失败的模块会在设置页尾追加 `@bektools.module.failed` 错误分组，而不是静默消失。
4. **注册指令**：`registerClientCommands()` 对每个带客户端指令的模块做同样的"是否失败"守卫后逐一挂载（profiler / pgmm / sp / rbm / spdb）。

## 设置系统

- 子模组主类契约（由同步脚本断言）：提供 `public static boolean bekBundled` 与 `public void bekBuildSettings(SettingsMenuDialog.SettingsTable table)` 两个成员。
- 所有 `ui.settings.addCategory(...)` 调用必须包在 `if(!bekBundled)` 里；存在 `registerSettings()` 时必须以 `if(bekBundled) return;` 早退。
- 分组标题、说明文案走 bundle key，样式统一用 `bektools.ui.RbmStyle`；没有独立设置项的模块也要放一个占位分组（`bektools.section.<id>.none`）。
- 文案源头是 `tools/bektools-bundles/bundle*.properties`，构建时合并到 `src/main/resources/bundles/bundle*.properties`。

## 兼容层

Neon 不直接 import MindustryX 类，而是通过桥接口隔离：

| 接口 | 作用 | 实现 |
| --- | --- | --- |
| `mdtxcompat.OverlayUiBridge` | OverlayUI 能力来源 | `MindustryXOverlayUiBridge`（复用 MDtX）/ `NeonEmbeddedOverlayUiBridge`（原版内嵌实现）/ `NoopOverlayUiBridge` |
| `mdtxcompat.MarkerBridge` | 标记能力 | `MindustryXMarkerBridge` / `NoopMarkerBridge` |
| `mdtxcompat.SchematicShareBridge` | 蓝图分享 | `MindustryXSchematicShareBridge` / `NoopSchematicShareBridge` |
| `mdtxcompat.AutoDetectingOverlayUiBridge` | 启动时自动挑桥 | 运行时探测 |

配套守卫：

- `LegacyMindustryXGuard` 设定 MDtX 最低可用版本（当前 `2026.04.03.B439`），识别旧版/不兼容环境并阻止接入，回退到内嵌 Overlay 或 Noop。
- `mdtxcompat.AutoDetectingOverlayUiBridge` 在运行时对多个类加载器尝试解析 MDtX 类（详见 [subsystems/overlay-compat.md](subsystems/overlay-compat.md)）。
- `neoncompat.overlay` 提供 `OverlayUI` + `AdsorptionSystem` + `NeonOverlayBootstrap`，让纯原版客户端也有一致体验的悬浮窗管理器（齿轮按钮或默认快捷键 `Z`）。

## 构建与依赖形态

- Java 17 字节码（`options.release.set(17)`），Kotlin 仅用于 `advancedreplace` 包。
- 编译期优先使用工作区本地的 `Mindustry-master` core/desktop jar 与 Arc jar；缺省时退回 JitPack `v159` 并排除其坏传递依赖，改用 `tools/deps/` 里的 arc-core / arcnet jar。
- 运行时依赖仅两件外部库：`pinyin4j`（拼音搜索）与 `sqlite-jdbc`（SPDB 存储）。二者都会被打进最终 jar；D8 输入还额外包含 `kotlin-stdlib`。
- SPDB 的语义搜索会在打包阶段下载嵌入 GGUF 模型（`bge-base-zh-v1.5-q8_0.gguf`，任务 `downloadEmbeddingModel`）。
- 发布管线（`deploy`）产出桌面+安卓合并包，细节见 [release.md](release.md) 与 [development.md](development.md)。

## 目录速查

```text
src/main/java/
|-- bektools/            聚合核心：入口、设置系统、RbmStyle、profiler
|-- mdtxcompat/          对外兼容桥接口与 MDtX 实现
|-- neoncompat/overlay/  原版可用的内嵌 OverlayUI
|-- mindustry/           需要触碰游戏内部包时的扩展点（logic/maps/ui）
|-- <feature>/           其余每目录一个功能模块（见 AGENTS.md 文件结构表）
src/main/kotlin/advancedreplace/   高级替换（Kotlin）
tools/                   子模组同步、版本码、bundle 合并脚本
```

## 设计约束（务必保持）

- 纯客户端：不要求服务器安装；涉及单位命令的功能只能走 Mindustry 原有客户端命令路径。
- 用户可见文案一律走 bundle，不硬编码。
- 变更聚焦性能与可读性，不顺手重构无关模块。

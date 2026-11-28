# 开发指南

本文汇总 Neon 的开发环境、常用构建命令、代码风格与新子模组接入清单。实现与命令事实以 `build.gradle`、`tools/` 和本文为准；根目录 [AGENTS.md](../AGENTS.md) 只约束代理行为与流程。若发现文档过时，应在同一次改动中同步修正。

## 环境要求

- **JDK 17**：Mindustry v160.1 运行时要求 Java 17，请在 `gradle.properties` 中将 `org.gradle.java.home` 配置为本机 JDK 路径。
- **Android D8 工具**：构建 Android 包时按以下顺序自动探测：`D8_PATH` 环境变量 → `ANDROID_SDK_ROOT` / `ANDROID_HOME` 下的 `build-tools` → 工作区根目录的 `commandlinetools-win-*` 目录。若均不存在，`dexAndroid` 任务会直接失败。
- **Mindustry / Arc 编译类路径**：优先使用本地检出的 JAR：
  - `../Mindustry-master/core/build/libs/core-release.jar`
  - `../Arc/arc-core/build/libs/arc-core-1.0.jar`
  - `../Mindustry-master/desktop/build/libs/Mindustry.jar`（可选）

  本地文件不存在时自动回退至 JitPack 的 `v160.1`，并改用 `tools/deps/arc-core.jar` 与 `tools/deps/arcnet.jar` 作为 Arc 类路径。
- **终端环境**：Windows 下请统一使用 PowerShell 7（`pwsh`）。
- **语言目标版本**：Java 目标为 17（`options.release.set(17)`），Kotlin 为 2.2.0（`jvmTarget = JVM_17`）；不要引入高于 Java 17 的 API。
- **本地基线差异排查**：本地 `../Mindustry-master` 的 JAR 可能高于 v160.1 基线；若出现因上游移除或重构 API 导致的“找不到符号”错误，应先按环境问题处理：临时隐藏本地 JAR 让构建回退至 JitPack，验证通过后再恢复。
- **JitPack 传递依赖陷阱**：JitPack 的 Mindustry POM 会传递引用无法构建的 Arc 提交；`build.gradle` 已排除该传递依赖并以 `tools/deps/` 兜底，切勿移除相关注释或重新引入传递依赖。
- **瞬时网络故障**：Gradle 依赖下载失败、GitHub API 瞬时 `EOF` 或 `gh` 鉴权抖动应优先按环境问题重试；先区分环境失败与代码失败，再下结论。

## 常用命令

```powershell
# 完整发布产物（桌面 + Android 合并包），日常验证首选
.\gradlew.bat clean deploy

# 仅编译检查
.\gradlew.bat compileJava

# 运行全部测试（含三个已接入的回归任务）
.\gradlew.bat test

# 单独运行指定回归任务
.\gradlew.bat dataImagePackerCompatTest
.\gradlew.bat pinyinScopeContextTest
.\gradlew.bat versionUtilTest

# 仅生成 Android classes.dex
.\gradlew.bat dexAndroid
```

`deploy` 等价于 `jarMerged` + `zipMerged` + `jarLocalDev`：

| 任务 | 产物 | 说明 |
| --- | --- | --- |
| `jarMerged` | `build/libs/Neon.jar` → 复制到 `dist/Neon.jar`、`../构建/Neon/Neon.jar` | 桌面 + Android 合并包：class + 依赖解包 + `classes.dex` + 描述文件 |
| `zipMerged` | 同上，文件名为 `Neon.zip` | 与合并 JAR 内容一致 |
| `jarLocalDev` | `../构建/Neon/Neon-dev.jar` | 本地开发用聚合 fat JAR（不含 `classes.dex`） |

> [!WARNING]
> `jar` 任务产出的 `Neon-desktop.zip` 属于**桌面中间产物**，切勿当作可分发文件；Android 端可用性一律以是否包含 `classes.dex` 为准。

完整打包管线（D8 输入裁剪、`--lib arc-core` 防 `AbstractMethodError` 等）详见 [release.md](release.md)。

## 命令约定

- Windows 下统一使用 PowerShell 7（`pwsh`）执行命令；Gradle 统一调用 `.\gradlew.bat`（CI 环境亦兼容系统安装的 `gradle`），不要擅自改写命令形态。
- Python 工具统一使用 `python tools/<name>.py`；`update_submods.py` 的代理操作规范见 [tools/AGENTS.md](../tools/AGENTS.md)。
- GitHub 交互统一使用 `gh` CLI（如 `gh api`、`gh release view`）；若本机 `git` 直连 `github.com:443` 失败，不要反复执行 `git fetch`。

## 并入新子模组清单

1. **建立独立包**：在 `src/main/java/<feature>/` 下创建独立包（Kotlin 代码放 `src/main/kotlin/<feature>/`），保持与其他功能模块零横向依赖；共享能力下沉到 `bektools` 或 `mdtxcompat`。
2. **实现主类契约**：子模组主类必须提供以下两个成员：
   ```java
   public static boolean bekBundled;
   public void bekBuildSettings(SettingsMenuDialog.SettingsTable table)
   ```
3. **守卫存量设置**：原有 `ui.settings.addCategory(...)` 调用必须包裹在 `if(!bekBundled)` 中；若模块自带 `registerSettings()`，开头必须添加 `if(bekBundled) return;`。
4. **接入总入口**：在 `bektools.BekToolsMod` 中新增 `moduleId` 常量与字段，经 `initializeFeature(moduleId, initializer)`（或 `initializeModule`）初始化；在 `moduleGates` 中登记加载门（`<id>-enabled`、默认值及主开关行是否由 Neon 渲染），并在 `buildModuleEntries()` 中注册 `ModuleEntry`（分组、图标、设置 builder），UI 样式统一使用 `RbmStyle`。
5. **保留占位分组**：即便模块没有独立设置项，也须放置说明占位（bundle key 为 `bektools.section.<id>.none`）。
6. **补齐双语文案**：新增的 bundle key 必须同步写入 `tools/bektools-bundles/bundle.properties` 与 `bundle_zh_CN.properties`，并确认合并后的 `src/main/resources/bundles/bundle*.properties` 正常生效。
7. **隔离环境差异**：需在 MindustryX 下替换行为的模块，应编写 `<Name>ModX` 子类/变体并在 `BekToolsModX` 构造函数中替换对应工厂；所有 Overlay 能力一律通过桥接口获取，禁止直接 `import` MDtX 类。
8. **明确默认状态**：带有破坏风险的模块（如删除建筑、改变单位行为等）默认关闭，并在用户指南中明确标注。
9. **收口更新提示**：移除子模组自带的“启动查更新”逻辑，统一交由 [modUpdater 更新中心](subsystems/modupdater.md) 处理。
10. **执行同步核查**：运行 `python tools/update_submods.py --check` 检查子模组状态；注入结构会由 `assert_injected_structure` 自动断言，失败时将提示需要人工调整的 `inject_bek_hooks()` 分支。如需连同编译一并验证，附加 `--verify-build`。

## 子模组同步模型

- `tools/submods.json` 描述各上游子模组配置（`localPath`、inject 开关等）；`tools/submods.lock.json` 记录上次正式并入时的版本快照（`sync=`）。
- `workspace=` 表示当前本地工作区版本，也是下次同步实际拷入的内容；`upstream=` 仅在启用 `trackUpstream` 时作参考。
- `changed-since-lock` 表示本地子模组在上次并入后又有新改动；`dirty-workspace` 表示存在未提交变更。两种状态下均不应直接发版。
- 同步、注入与 bundle 合并的代理操作规范详见 [tools/AGENTS.md](../tools/AGENTS.md)。

## 代码风格

- 整体遵循上游 Mindustry / Arc 风格：`if(...){`、`class Foo{`、4 空格缩进、禁用 Tab、UTF-8 编码、行尾无多余空白、文件以换行符结尾。
- 同步产物保持各上游子仓库的原有风格，不要为了统一格式而大面积重排；Neon 原生代码（`bektools`、`mdtxcompat` 等）严格遵循上游风格。
- 命名约定：包名使用全小写子模组 ID；类名用 PascalCase；设置键采用 `<模块前缀>-<名称>`（如 `sp-path-alpha`、`rbm-enabled`）；bundle 键带模块前缀；常量用 `private static final` + camelCase。
- 浮点字面量显式附加 `f` 后缀（如 `24f`、`0.12f`）。
- 注释重点解释约束与原因（跨版本兼容、MindustryX 行为差异、D8 陷阱等），语言与所在文件一致；修改相关逻辑前先阅读注释，改完同步更新。
- 日志统一使用 `arc.util.Log`，消息带 `Neon: ` 或 `[Neon/...]` 前缀，禁止使用 `System.out`；常规诊断用 `Log.info`，可隔离的模块故障用 `Log.err` 配合 `recordModuleFailure`。
- 用户可见文案一律走 bundle，新增文案必须中英双语同步；UI 颜色与样式优先使用 `.color(...)` / `Draw.color(...)`，避免拼接 `"[accent]" + x + "[]"` 这类富文本标记串（存量代码不要求顺手重写）。
- UI 布局优先复用 `Table` 与 `RbmStyle`，避免硬编码宽高；Kotlin 仅用于 `advancedreplace` 同步产物，Neon 原生代码统一使用 Java。

## 改动与收尾

- 以“行为正确 + 改动边界合理”为目标，不满足于“暂时能跑”。
- 保持最小改动范围，不将顺手重构混入 bugfix；变更聚焦性能与可读性。
- 能在调用点拦截的问题不要扩大影响面；复用现有桥接口、`RbmStyle` 与 `initializeModule` 隔离层，不额外制造平行 helper 或包装层。
- 初始化副作用必须显式可见，避免在隐蔽路径中悄悄注册 hook 或替换全局状态。
- 涉及版本边界（`minGameVersion`、MindustryX 最低版本）的变更须显式声明并保持默认行为稳定；跨版本 API 差异通过反射或兼容层处理。
- 在同步产物中发现的问题，应先在对应子模组工作区修复后再同步回 Neon；只有 Neon 原生聚合层的问题才直接在 Neon 中修改。
- 修改 `build.gradle` 中的 D8 配置、依赖解析或产物链时，必须保留解释历史陷阱的注释，并用 `deploy` 验证最终产物。
- 同步完成后需审查完整 diff（源码、注入产物、bundle、lock 文件），运行 `compileJava` 或 `--verify-build`，再执行 `--check`；修改 Java 源码后必须运行 `test`。
- 收尾流程串行执行：build → 产物复制 → commit。提交信息遵循 `Neon: <概要>`，同步类提交需标明子模组名与版本；开工前确认仓库干净，如遇无关脏改动先如实报告。

## 调试建议

- 先在原版客户端进行冒烟验证（走 ocb 提供的 Overlay 路径；内嵌冻结副本仅在 ocb 构造失败时兜底），再切换到 MindustryX 验证桥接路径，确保两套 Overlay 行为均表现正常。
- 若设置页尾部出现 `@bektools.module.failed` 分组，说明存在模块初始化异常，可直接按该分组下展示的错误信息定位原因。
- 排查性能问题时，优先使用 `neon-profiler` 客户端指令（`bektools.profiler.NeonProfilerFeature`）。

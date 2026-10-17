# 开发指南

环境、日常构建命令、代码风格与接入清单。实现与命令事实以 `build.gradle`、`tools/` 和本文为准；根 [AGENTS.md](../AGENTS.md) 只约束代理行为与流程。发现文档过时，应在同一次改动里修正。

## 环境

- JDK 17（Mindustry v160.1 运行时要求 Java 17；`gradle.properties` 的 `org.gradle.java.home` 按本机 JDK 路径配置）。
- 安卓构建需要 D8：按以下顺序自动探测——`D8_PATH` 环境变量 → `ANDROID_SDK_ROOT` / `ANDROID_HOME` 下的 `build-tools` → 工作区根的 `commandlinetools-win-*` 目录。都没有时 `dexAndroid` 会直接失败。
- Mindustry/Arc 编译类路径优先取本地检出的 jar：
  - `../Mindustry-master/core/build/libs/core-release.jar`
  - `../Arc/arc-core/build/libs/arc-core-1.0.jar`
  - `../Mindustry-master/desktop/build/libs/Mindustry.jar`（可选）
  
  本地不存在时退回 JitPack 的 `v160.1`，并改用 `tools/deps/arc-core.jar` + `tools/deps/arcnet.jar` 作为 Arc 类路径。
- Windows 下命令请用 PowerShell 7（`pwsh`）。
- Java 目标 17（`options.release.set(17)`），Kotlin 2.2.0（`jvmTarget = JVM_17`）；不要引入更高的 Java API。
- 本地 `../Mindustry-master` 的 jar 可能比 v160.1 基线新；若出现上游已移除/重构 API 的“找不到符号”，先按环境问题处理：临时隐藏该 jar，让构建回退 JitPack，验证后恢复。
- JitPack 的 Mindustry POM 会传递引用无法构建的 Arc 提交；`build.gradle` 已排除该传递依赖并用 `tools/deps/` 兜底，不要移除对应注释或重新引入传递依赖。
- Gradle 依赖下载失败、GitHub API 瞬时 `EOF`、`gh` 鉴权抖动优先按环境问题重试；先区分环境失败与代码失败，再下结论。

## 常用命令

```powershell
# 完整发布产物（桌面+安卓合并包），日常验证首选
.\gradlew.bat clean deploy

# 只编译检查
.\gradlew.bat compileJava

# 全部测试（含三个接线好的回归任务）
.\gradlew.bat test

# 单跑某个回归任务
.\gradlew.bat dataImagePackerCompatTest
.\gradlew.bat pinyinScopeContextTest
.\gradlew.bat versionUtilTest

# 只出安卓 dex
.\gradlew.bat dexAndroid
```

`deploy` = `jarMerged` + `zipMerged` + `jarLocalDev`：

| 任务 | 产物 | 说明 |
| --- | --- | --- |
| `jarMerged` | `build/libs/Neon.jar` → 复制到 `dist/Neon.jar`、`../构建/Neon/Neon.jar` | 桌面+安卓合并：classes + 依赖解包 + `classes.dex` + 描述文件 |
| `zipMerged` | 同上，名为 `Neon.zip` | 与合并 jar 内容一致 |
| `jarLocalDev` | `../构建/Neon/Neon-dev.jar` | 本地开发用聚合 fat jar（无 dex） |

注意区分：`jar` 任务的 `Neon-desktop.zip` 是**桌面中间产物**，永远不要当作可分发文件；安卓可用性以是否含 `classes.dex` 为准。

详细打包管线（D8 输入裁剪、`--lib arc-core` 防 `AbstractMethodError` 等）见 [release.md](release.md)。

## 命令约定

- 命令统一用 PowerShell 7（`pwsh`）执行；Gradle 用 `.\gradlew.bat`（CI 也兼容安装版 `gradle`），不要擅自改写命令形态。
- Python 工具统一 `python tools/<name>.py`；`update_submods.py` 的代理操作规则见 [tools/AGENTS.md](../tools/AGENTS.md)。
- GitHub 交互走 `gh` CLI（`gh api`、`gh release view` 等）；若本机 `git` 直连 github.com:443 失败，不要反复 `git fetch`。

## 并入一个新子模组（清单）

1. **建包**：在 `src/main/java/<feature>/` 建独立包（或 Kotlin 放 `src/main/kotlin/<feature>/`），保持与其他模块零横向依赖；共享能力向下沉到 `bektools` 或 `mdtxcompat`。
2. **主类契约**：主类提供
   ```java
   public static boolean bekBundled;
   public void bekBuildSettings(SettingsMenuDialog.SettingsTable table)
   ```
3. **守卫存量设置**：原有 `ui.settings.addCategory(...)` 必须包进 `if(!bekBundled)`；如模块自带 `registerSettings()`，开头 `if(bekBundled) return;`。
4. **接入总入口**：在 `bektools.BekToolsMod` 增加 moduleId 常量与字段，经 `initializeFeature(moduleId, initializer)` 初始化；在 `moduleGates` 登记加载门（`<id>-enabled`、默认值、开关行是否由 Neon 渲染），并在 `buildModuleEntries()` 里挂 `ModuleEntry`（分组、图标、设置 builder），样式走 `RbmStyle`。
5. **占位分组**：没有独立设置项也要放说明占位（bundle key `bektools.section.<id>.none`）。
6. **补文案**：新 bundle key 同步写入 `tools/bektools-bundles/bundle.properties` 和 `bundle_zh_CN.properties` 两份，确认合并后的 `src/main/resources/bundles/bundle*.properties` 可用。
7. **环境差异**：需要在 MindustryX 下换行为的模块，写 `<Name>ModX` 子类/变体并在 `BekToolsModX` 构造函数中替换工厂；Overlay 能力一律通过桥接口取，不直接 import MDtX 类。
8. **默认状态**：对有破坏风险的模块（删除建筑、改变单位行为等）默认关闭并在文档标注。
9. **更新提示收口**：模块自带的"启动查更新"逻辑去掉，交给 [modUpdater 更新中心](subsystems/modupdater.md)。
10. **同步脚本核查**：`python tools/update_submods.py --check` 查看子模组状态；注入结构会被 `assert_injected_structure` 自动断言，失败会指出需要人工修改的 `inject_bek_hooks()` 分支。要连编译一起验证时加 `--verify-build`。

## 子模组同步模型

- `tools/submods.json` 描述各上游子模组（`localPath`、inject 开关等）；`tools/submods.lock.json` 记录上次正式并入的版本（`sync=`）。
- `workspace=` 是当前本地工作区版本，也是下次同步真正拷入的内容；`upstream=` 仅在启用 `trackUpstream` 时参考。
- `changed-since-lock` 表示本地子模组在上次并入后又改过；`dirty-workspace` 表示还有未提交改动。两种状态下都不应直接发版。
- 同步、注入与 bundle 合并的代理操作规则见 [tools/AGENTS.md](../tools/AGENTS.md)。

## 代码风格

- 基调是上游 Mindustry/Arc 风格：`if(...){`、`class Foo{`、4 空格缩进、无 tab、UTF-8；行尾无空白，文件以换行结尾。
- 同步产物保留各子仓库自身的风格，不要为统一风格重排；Neon 原生代码（bektools/mdtxcompat 等）遵循上游风格。
- 命名：包名 = 子模组 id（全小写）；类名 PascalCase；设置键 `<模块前缀>-<名>`（如 `sp-path-alpha`、`rbm-enabled`）；bundle 键用模块前缀；常量 `private static final` camelCase。
- 浮点字面量带 `f` 后缀（如 `24f`、`0.12f`）。
- 注释解释约束与为什么（跨版本兼容、MindustryX 行为差异、D8 陷阱等），语言跟随文件；改相关逻辑前先读注释、改完更新注释。
- 日志用 `arc.util.Log`，消息带 `Neon: `（或 `[Neon/...]`）前缀，禁止 `System.out`；普通诊断 `Log.info`，可隔离的模块故障 `Log.err` + `recordModuleFailure`。
- 用户可见文案走 bundle，新增文案中英双语同步；UI 颜色/样式优先用 `.color(...)` / `Draw.color(...)`，不要拼接 `"[accent]" + x + "[]"` 这类富文本标记串（存量拼接不要求顺手重写）。
- UI 布局优先复用 `Table` 与 `RbmStyle`，不硬编码宽高；Kotlin 仅用于 `advancedreplace` 同步产物，原生代码保持 Java。

## 改动与收尾

- 以“行为正确 + 改动边界合理”为目标，不满足于“现在能跑”。
- 最小改动；不把顺手重构混进 bugfix；变更聚焦性能与可读性。
- 能在调用点拦截就不扩大影响面；复用桥、`RbmStyle`、`initializeModule` 隔离层，不新增平行 helper 或包装层。
- 初始化副作用要显式可见；注册 hook、替换全局状态不要在隐蔽路径偷偷生效。
- 版本边界（`minGameVersion`、MindustryX 最低版本）的改动要显式声明，默认行为保持稳定；跨版本 API 差异走反射或兼容层。
- 同步产物里发现的问题，修在子模组工作区再同步；只有 Neon 原生聚合层的问题才直接改 Neon。
- 改 `build.gradle` 的 D8、依赖解析或产物链时保留解释陷阱的注释，并用 `deploy` 验证产物。
- 同步后审查完整 diff（源码、注入产物、bundle、lock），跑 `compileJava` 或 `--verify-build`，再 `--check`；改 Java 源码后跑 `test`。
- 收尾串行：build → 产物复制 → commit；提交信息 `Neon: <概要>`，同步类带子模组名与版本；开始前仓库应干净，无关脏改动先报告。

## 调试建议

- 先在原版客户端冒烟（ocb 提供的 Overlay 路径；内嵌副本只在 ocb 构造失败时兜底），再切 MindustryX 验证桥接路径；两套 Overlay 行为必须都可接受。
- 设置页尾部出现 `@bektools.module.failed` 分组即代表某模块初始化抛了异常，按分组下挂的错误信息定位。
- 性能问题先用 `neon-profiler` 客户端指令（`bektools.profiler.NeonProfilerFeature`）定位。

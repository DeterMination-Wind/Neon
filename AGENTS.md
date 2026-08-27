# Neon 开发指南（AGENTS.md）

本文件面向在本仓库工作的 AI 代理与人类协作者，风格与约束参考 `MindustryX-main/AGENTS.md`。
面向人类的分类文档（架构 / 开发 / 发布 / 测试 / 术语表 / 子系统详解）在 [`docs/`](docs/README.md)，
两者冲突时以本文件为准。
当本文件与上层工作区 `Documents/codex/AGENTS.md` 冲突时，以更贴近 Mindustry 模组任务的上层规范为准；
本文件负责 Neon 特有的架构、同步与发布纪律。

## 项目与工作流

- Neon 是 Mindustry 客户端辅助模组**聚合仓库**：把 27 个独立子模组（见 `tools/submods.json`）
  的源码合并为一个 jar、一个设置入口、一套悬浮窗（Overlay）体系。纯客户端模组，
  不新增方块/单位，服务器无需安装。
- **本仓库不是子模组源码的唯一真源。** 每个子模组的本地工作区
  （`tools/submods.json` 里 `localPath` 指向的兄弟目录，如 `../StealthPath`）才是真源。
  同步方向永远是：子模组工作区 → Neon（由 `tools/update_submods.py` 执行）。
- 因此，**不要在 Neon 里手改同步产物**：
  - 各子模组包目录（`src/main/java/<package>/`，归属见 `submods.json` 的 `javaPackageDir`/`kotlinPackageDir`/`extraJavaDirs`）；
  - 合并后的 `src/main/resources/bundles/bundle*.properties`（文件头有 `# Auto-merged for Neon` 标记）；
  - 注入产物（`bekBundled` 声明、`bekBuildSettings` 包装、`if(!bekBundled)` 守卫、
    `registerSettings()` 的 `if(bekBundled) return;` 早退——这些是同步时由
    `inject_bek_hooks()` 自动生成/改写的）。
  手改会在下一次同步时被覆盖。要改子模组逻辑：先改子模组工作区并提交，再回 Neon 执行同步。
- Neon **原生代码**（可以直接改）：`src/main/java/bektools/`、`mdtxcompat/`、`neoncompat/`、
  `betterscreenshot/`、`custommarker/`，以及 `tools/`（同步脚本本身）、`build.gradle`、
  `mod.json`/`mod.hjson`、`.github/workflows/`。
- Neon 根仓库和各子模组仓库是**不同 Git 上下文**。执行 `git`、看 diff、提交前先确认当前目录，
  不要把 Neon 提交和子模组仓库提交混为同一次操作。
- 注意 MindustryX 兼容目标：Neon 同时运行在原版 v159 与 MindustryX 上，
  所有依赖 MindustryX 的能力必须走 `mdtxcompat` 反射桥，编译期不得直接引用
  `mindustryX.*` 类（`build.gradle` 默认不把 MindustryX 挂进 classpath）。
- 网络注意：本机 `git` 直连 github.com 443 不通，但 `gh` CLI 可用。
  需要 GitHub 数据（拉取参考文档、查询 Release 资产等）时用 `gh api ...`，不要反复重试 `git fetch`。

### 子模组同步（tools/update_submods.py）

```powershell
python .\tools\update_submods.py --check          # 只检查，不写文件
python .\tools\update_submods.py                  # 全量同步所有子模组
python .\tools\update_submods.py --only pv        # 只同步指定 id，其余保持现状
python .\tools\update_submods.py --verify-build   # 同步后追加 gradlew compileJava 验证
```

- 检查输出语义：
  - `sync=`：`tools/submods.lock.json` 里上次正式同步进 Neon 的版本；
  - `workspace=`：当前本地子模组工作区版本，也是下次同步真正会被拷入 Neon 的版本；
    **本地工作区是同步的真源**；
  - `upstream=`：仅当本地子模组仓库存在 git upstream 且启用 `trackUpstream` 时展示；
  - `changed-since-lock`：本地子模组自上次 Neon 同步后已经变化（Neon 落后）；
  - `dirty-workspace`：本地子模组有未提交改动，**不得丢弃或静默替换**，先回子仓库处理。
- 同步会拷贝 Java/Kotlin 源码与资源、执行注入、合并 bundle、更新锁文件。
  同步后必须立刻审查完整 diff（含生成的 bundle 与 `submods.lock.json`）。
  不要用 `--force`，除非用户明确要求全量重写。
- 注入契约（`injectBekHooks=true` 的子模组）在写入时自动执行结构断言
  （`assert_injected_structure`）：`bekBundled` 声明与 `bekBuildSettings` 方法必须恰好存在一个，
  所有 `ui.settings.addCategory` 调用必须带 `if(!bekBundled)` 守卫，
  存在 `registerSettings()` 时必须含 `if(bekBundled) return;` 早退，括号必须平衡；
  任一失败即中止同步并指明问题，此时应人工更新 `inject_bek_hooks()` 的匹配逻辑。
  多数子模组已改为 `injectBekHooks=false`（子仓库内自行维护契约），注入主要服务少数老模块。
- bundle 合并规则：不同子模组对同一 key 给出不同值会直接报碰撞错误（不允许静默覆盖）；
  `tools/bektools-bundles/bundle*.properties` 中的条目是 Neon 侧**显式覆盖**，优先级最高；
  `ls`（LogicSugar）拥有 `logicsugar.` / `setting.logicsugar.` 前缀的所有权。
- `tools/generate_detail.py`（gitignore，本地工具）与 `tools/generate_dox.py` 生成文档索引，
  与构建链路无关；`tools/deps/` 存放兜底用的 arc-core/arcnet jar。

## 构建

- 要求：**JDK 17**（`gradle.properties` 里 `org.gradle.java.home` 已指向本机 jdk-17）。
- Java 目标为 **17**（`options.release.set(17)`，Mindustry v159 运行时需要 Java 17）；
  Kotlin 2.2.0，`jvmTarget = JVM_17`。不要引入更高版本 Java API。
- 依赖解析优先级（`build.gradle`）：
  1. 本地 `../Mindustry-master/core/build/libs/core-release.jar`（+ 本地 Arc、desktop jar）——
     保证 Neon 与本地测试用的 v159 checkout 一致；
  2. 否则 jitpack `com.github.Anuken.Mindustry:core:v159`，并排除 arc 传递依赖，
     改用 `tools/deps/` 下的 arc jar（jitpack 的 pom 指向已无法构建的 arc 孤儿 commit
     `6aee8e7686`，注释在 `build.gradle` 里，勿删）。
- 常用命令（项目根目录、PowerShell 7）：

```powershell
.\gradlew.bat test          # 编译 + 3 个回归测试（DataImagePacker 兼容 / 拼音搜索 / 版本号工具）
.\gradlew.bat compileJava   # 仅编译主源码（同步后快速验证）
.\gradlew.bat clean deploy  # 完整产物链（见下）
```

- `deploy` 产物链：`jarMerged`/`zipMerged`（桌面 + Android 合并包，含 `classes.dex`）
  → `dist/Neon.jar`、`dist/Neon.zip`、`../构建/Neon/Neon.jar|zip`；
  `jarLocalDev` → `../构建/Neon/Neon-dev.jar`（本地快速测试用）。
- Android：`dexAndroid` 调 D8 生成 `classes.dex`，并通过 `--lib arc-core.jar` 把 arc 作为
  library classpath——否则 `InputProcessor` 接口方法被 dex 成外部方法，设备上
  `touchDragged` 等调用会抛 `AbstractMethodError`（注释在 `build.gradle`，勿删）。
  D8 查找顺序：`D8_PATH` → `ANDROID_SDK_ROOT`/`ANDROID_HOME` build-tools →
  工作区根的 `commandlinetools-win-*`。
- `downloadEmbeddingModel` 为 SPDB 语义搜索下载 GGUF 嵌入模型（>100MB，已被 gitignore）。
- 如果验证被网络、依赖下载或本地环境阻塞，先区分"环境失败"与"代码失败"，再下结论。

## 关键目录

```text
Neon/
|-- src/main/java/
|   |-- bektools/               # Neon 原生：聚合主类、遥测、共享设置组件、性能分析
|   |   |-- profiler/           #   NeonProfiler / NeonProfilerFeature（帧耗分析）
|   |   \-- ui/                 #   RbmStyle（设置行组件）/ VscodeSettingsStyle（配色）
|   |-- mdtxcompat/             # Neon 原生：MindustryX 反射桥（Overlay/标记/蓝图分享）
|   |-- neoncompat/overlay/     # Neon 原生：内嵌 OverlayUI（无 MindustryX 时的悬浮窗）
|   |-- betterscreenshot/       # Neon 原生维护：截图（core by Miner）
|   |-- custommarker/           # Neon 原生维护：自定义标记
|   |-- <子模组包>/              # 同步产物，勿手改（autopruner/stealthpath/logicsugar/...）
|   \-- mindustry/              # 同步产物：子模组对游戏包内类的扩展
|       |-- logic/              #   LogicSugar：SugarCompiler / SugarCanvas
|       |-- maps/filters/       #   BetterMapEditor：地图过滤器
|       \-- ui/                 #   FST：翻译聊天 UI / random：对话框
|-- src/main/kotlin/advancedreplace/   # 同步产物（Kotlin）
|-- src/main/resources/
|   |-- bundles/                # 同步合并产物（# Auto-merged for Neon）
|   \-- fst-bundles/            # ForeignServerTranslator 独立 bundle
|-- src/test/java/              # 3 个 JavaExec 回归测试（挂在 test 任务上）
|-- tools/
|   |-- submods.json            # 子模组注册表（id/localPath/包目录/注入开关）
|   |-- submods.lock.json       # 上次同步快照（syncedHead 等）
|   |-- update_submods.py       # 同步/注入/bundle 合并流水线
|   |-- neon_version.py         # 版本号计算与描述文件同步
|   |-- bektools-bundles/       # Neon 侧 bundle 覆盖（中英双语维护）
|   \-- deps/                   # 兜底 arc-core.jar / arcnet.jar
|-- .github/workflows/release.yml   # tag 触发的发布流水线
|-- docs/                      # 人类向分类文档（architecture/development/release/testing/...）
|-- build.gradle / mod.json / mod.hjson / README.md / FEATURES.md / RELEASE_NOTES.md
```

## 架构与接入契约

### 双入口与模块隔离

- `mod.json` 声明 `main = bektools.BekToolsMod`（原版入口）与
  `mainX = bektools.BekToolsModX`（MindustryX 注入入口）。
  `BekToolsModX` 只做一件事：向父类构造器传入 MindustryX 桥
  （`MindustryXOverlayUiBridge` / `MindustryXMarkerBridge`）与各模块的 `*ModX` 子类。
  新增需要 MindustryX 定制行为的模块时沿用此模式，不要在子模组包里直接探测。
- `BekToolsMod` 构造器内逐模块 `markBundled` + `initializeModule`：任何子模组初始化抛出
  都被隔离记录（`moduleFailures`），其余模块继续加载；失败模块在设置页显示占位
  （`@bektools.module.failed`）。修改聚合逻辑时**不得破坏这一隔离边界**——
  不要把子模组初始化移出 `initializeModule`/`initializeFeature`。
- 全局 UI/事件型 feature（CustomMarker、BetterScreenShot、Profiler）在所有模块初始化
  完成后才挂载，避免"幽灵窗口"残留。

### 子模组设置契约（Neon 风格）

- 并入 Neon 的子模组设置必须进 Neon 总设置入口，`bekBundled=true` 时不得再注册独立
  `ui.settings.addCategory(...)`。
- 注入型子模组主类必须提供：
  - `public static boolean bekBundled`
  - `public void bekBuildSettings(SettingsMenuDialog.SettingsTable table)`
- 聚合入口统一在 `bektools/BekToolsMod.java` 的 `buildModuleEntries()` 里以 `ModuleEntry`
  挂载（分组标题、图标、可选主开关 `enableKey`、设置构建器），保持一致的分组样式（RbmStyle）。
  有主开关的模块在开关关闭时收进"已禁用"折叠区。
- 没有独立设置项的子模组也要给出分组与说明占位（`bektools.section.<id>.none`）。
- 新增分组标题与说明必须同步写入 `tools/bektools-bundles/bundle.properties` 与
  `bundle_zh_CN.properties`（其他语言按现有文件按需补充），并确认合并后的
  `src/main/resources/bundles/bundle*.properties` 可用。
- `no-inject` 子模组（多数）在子仓库内自行满足契约；`brf` 通过
  `bektools.BetterRTSFormationSettings.configure()` 回调把设置渲染交给 Neon 侧，
  这是"设置归 Neon 管"的另一种等价形态。

### MindustryX 兼容桥（mdtxcompat）

- 三组桥接口 + `UNSUPPORTED` Noop 默认实现：`OverlayUiBridge`（悬浮窗注册）、
  `MarkerBridge`（聊天标记）、`SchematicShareBridge`（蓝图分享到聊天/剪贴板）。
- `OverlayUiBridge.autoDetect()`（`AutoDetectingOverlayUiBridge`）在首次真正使用时锁定委托：
  MindustryX 运行时 → `MindustryXOverlayUiBridge`（反射，带重试与失效降级）；
  否则 → `NeonEmbeddedOverlayUiBridge`（`neoncompat/overlay` 内嵌实现）。
- `LegacyMindustryXGuard`：运行时探测 + MindustryX 最低版本门槛
  （`2026.04.03.B439`，不满足直接抛错而非静默降级）+ 跨类加载器
  `loadMindustryXClass`（mod 类加载器环境下访问 `mindustryX.*` 必须走这里）。
- `OverlaySettingsCompat`：悬浮窗状态在原生 `overlayUI.*` 与内嵌
  `neoncompat.overlayUI.*` 设置键之间迁移，保证两种运行时切换不丢窗口布局。
- 修改桥行为时保持"探测失败即降级、失败只记录一次"的现有风格，
  不要让桥初始化异常向外传播。

### 更新与遥测

- 并入 Neon 后子模组必须禁用独立更新检查（`GithubUpdateCheck` 相关调用在
  bundled 时全部短路），统一由 `modupdater`（模组更新中心）负责更新。
- `PostHogUsageReporter` 在会话结束时上报匿名使用事件（模块可用性快照，
  不含个人信息）；改动聚合入口时保持 `snapshotSubmodStates()` 与实际模块列表一致。

## 代码风格

- 基调是**上游 Mindustry/Arc 风格**：花括号紧跟不空格（`if(...){`、`class Foo{`）、
  4 空格缩进、无 tab、UTF-8。仓库内同步产物保留了各子仓库自身的风格
  （部分为 ` {` 风格），**不要为统一风格去重排同步文件**；
  Neon 原生代码（bektools/mdtxcompat/neoncompat 等）新改动遵循上游风格。
- 行尾不留空白；文件以换行结尾。
- 命名：包名 = 子模组 id（全小写）；类名 PascalCase；设置键 `"<模块前缀>-<名>"`
  （如 `sp-path-alpha`、`rbm-enabled`）；bundle 键用模块前缀
  （`sp.*`、`bektools.section.<id>`）；常量 `private static final` camelCase。
- 浮点字面量带 `f` 后缀（Mindustry UI 尺寸惯例，如 `24f`、`0.12f`）。
- 注释解释**约束与为什么**（跨版本兼容、MindustryX 行为差异、D8 陷阱等），
  语言跟随现有文件（原生代码以英文为主）；不要写复述代码的注释。
  仓库里已有大量高价值注释（如 `NestedSettingsTable.build()` 对 MindustryX
  `lastSize` 自动重建的说明），改动相关逻辑前先读注释、改完更新注释。
- 日志用 `arc.util.Log`，消息带 `Neon: `（或 `[Neon/...]`）前缀；禁止 `System.out`。
  普通诊断 `Log.info`，可隔离的模块故障 `Log.err` + `recordModuleFailure`。
- 用户可见文案走 bundle，不硬编码；新增文案中英双语同步。
  UI 颜色/样式优先对组件设置（`.color(...)`）或绘制 API（`Draw.color(...)`），
  不要为了文本表现拼接 `"[accent]" + x + "[]"` 这类富文本标记串
  （与 MindustryX 开发指南同源的规则；存量拼接不要求顺手重写）。
- UI 布局优先复用 `Table` 现有能力与 `RbmStyle` 组件，不硬编码宽高凑布局。
- Kotlin 仅 `advancedreplace`（同步产物）；原生代码保持 Java，不新增 Kotlin 依赖面。

## 修改原则

- 优先做**最小改动 / 最少行数**修复；不把顺手重构混进 bugfix。
- 变更优先聚焦性能与可读性，不做无关重构；能改子工作区就不要改 Neon 侧补丁式 hack。
- 能在调用点拦截，就不扩大影响面；优先复用现有实现与入口
  （桥、`RbmStyle`、`initializeModule` 隔离层），不额外引入平行 helper 或包装层。
- 初始化副作用要显式：注册 hook、替换全局状态必须在模块入口可见地进行，
  不在隐蔽路径偷偷生效。
- 编译目标是 Java 17 + v159 API；同时兼容 MindustryX 运行时（走桥）。
  涉及版本边界（minGameVersion、MindustryX 最低版本）的改动要显式声明，默认行为保持稳定。
- 同步产物里发现的问题，修在子模组工作区再同步回来；只有 Neon 原生聚合层的问题才直接改 Neon。
- 改动 `build.gradle` 构建链（D8、依赖解析、产物任务）时，保留解释陷阱的注释，
  并用 `deploy` 完整验证产物。

## 版本号与发布

- 稳定版 Release 使用 `N<稳定版本号>`（如 `N11`）；预发行版使用
  `B<稳定版本号>.<递增构建号>`（如 `B11.20`）。
- `N11` 在 `mod.json`、`mod.hjson`、`build.gradle` 中统一写为 `110000`；
  `B11.20` 写为 `110020`。发布 tag 只使用 `N*` 或 `B*`。
- **每次发布前必须在本地完成（不依赖 CI 改版本或构建）**：
  1. `python tools/neon_version.py --set-files <版本码>`（如 N12 → `120000`），
     同步 `mod.json` / `mod.hjson` / `build.gradle`；
  2. 本地构建 `gradlew clean deploy`（`ANDROID_SDK_ROOT` 指向完整 SDK 且不设 `D8_PATH`）；
  3. 将 `dist/Neon.jar` 与 `dist/Neon.zip` 复制为 `../构建/Neon/Neon-v<标签>.jar` 与
     `.zip`，并核实两个文件（jar 内须含 mod 描述符、桌面 main 类与 `classes.dex`）。
- CI（`.github/workflows/release.yml`）只在 `N*`/`B*` tag 推送后构建并发布 GitHub Release，
  读取 `RELEASE_NOTES.md` 作为发布正文；不得作为版本号更新或本地构建的替代。
  公开 Release 中只允许一个 `.jar` 资产（最终可安装的合并 jar），
  不要把 `*-android.jar`、`*-desktop.zip` 等中间产物一并上传。
- 更新器必须同时兼容新格式、数字版本码和历史 `vX.Y.Z` 版本
  （回归测试 `modupdater.features.VersionUtilTest`）。
- 本地开发验证产物用 `jarLocalDev` 的 `../构建/Neon/Neon-dev.jar`；
  按工作区规范的 dev 身份（`Neon-dev` / `0.0.0`）只是临时构建手段，
  **不要把 dev 身份提交进仓库**——描述符版本变更只属于发布提交。
- 发布说明只写当前版本内容，双语言（中英），放在 `RELEASE_NOTES.md`，
  不要把更新说明写进 `README.md`。

## Review 与收尾

- 以"行为正确 + 改动边界合理"为目标，不满足于"现在能跑"。
- 同步子模组后：审查完整 diff（源码、注入产物、合并 bundle、锁文件），
  跑 `gradlew compileJava`（或 `--verify-build` 一步到位），再跑 `--check` 确认状态归零。
- 交付前按改动面跑 `gradlew test`（改动 Java 源码时必跑）。
- 收尾操作串行执行：build → 产物复制 → commit，不要并发。
- 提交信息格式 `Neon: <概要>`（如 `Neon: sync LogicSugar v2.2.0 (review fixes + release)`）；
  同步类提交在信息中带上子模组名与版本。
- 仓库处于干净状态再开始新任务；发现无关脏改动（如未提交的历史同步）先向用户报告，
  不要静默并入自己的提交。

## 命令约定

- 命令操作使用 PowerShell 7（`pwsh`），Gradle 用 `.\gradlew.bat`（CI 同样兼容安装版 `gradle`，
  不要擅自统一改写命令形态）。
- Python 工具统一 `python tools/<name>.py`；`update_submods.py` 的参数语义见上文。
- GitHub 交互走 `gh` CLI（`gh api`、`gh release view` 等），git 直连 443 不通。

## 常见边界问题

- **MindustryX 设置表陷阱**：MindustryX 的 `SettingsTable.act()` 会在 `list.size` 变化时
  自动重建（`lastSize` 短路），与原版 v159 行为不同。`BekToolsMod` 中
  `NestedSettingsTable.build()` 覆盖、`redrawSettings()` 手动重建、
  `addModuleGroupPref` 走 `pref()` 等写法都是为此服务，改动设置页渲染前先读懂这些注释。
- **D8 / Android**：`classes.dex` 必须由 `dexAndroid` 产出且带 `--lib arc-core.jar`；
  Android 用户只能用含 dex 的合并 jar，桌面中间产物不可安装。
- **jitpack arc 孤儿 commit**：Mindustry v159 pom 引用的 arc 无法构建，
  依赖必须排除 arc 传递并用 `tools/deps/` 兜底（见 `build.gradle` 注释）。
- **跨类加载器**：mod 运行时访问 `mindustryX.*` 或其他 mod 的类必须用
  `LegacyMindustryXGuard.loadMindustryXClass`（按候选加载器探测），
  直接 `Class.forName` 在 MindustryX 环境下会漏。
- **描述符现状**：`mod.json`/`mod.hjson` 当前 `hidden: true`、`minGameVersion: 159`、
  双 `main`/`mainX` 入口；`mod.json` 与 `mod.hjson` 必须保持字段一致，
  不要只改一份。
- **Release 资产安全**：Mindustry 游戏内安装器取 GitHub Release 返回的第一个 `.jar`，
  不按操作系统选择——公开 Release 永远只放一个可安装 `.jar`（可选 zip）。
- **本地 Mindustry-master 版本漂移**：`build.gradle` 优先使用的
  `../Mindustry-master/core/build/libs/core-release.jar` 来自比 v159 tag 新的 checkout
  （上游在 v159 之后移除/重构了 `mindustry.core.UI` 的 `newMenuDialog`/`showFollowUpMenu`，
  而 fst 的 `TranslatorUI` 直接覆写这些方法），会导致本地路径编译报"找不到符号"。
  此时是环境问题不是代码问题：临时把该 jar 改名隐藏，让构建落到 jitpack v159 兜底路径
  完成验证，结束后恢复 jar 即可。
- **test 依赖 arc-g3d**：jitpack 兜底路径的测试类路径只带 `tools/deps/` 的
  arc-core + arcnet，缺 arc-g3d（`arc.math.geom.Mat3D`），
  `dataImagePackerCompatTest` 初始化 `DataManager` 时会 `NoClassDefFoundError`。
  CI 的 release 流水线只跑 `clean deploy` 不跑 test，该缺口不会在 CI 暴露。
- 遇到 Gradle 依赖下载失败、GitHub API 瞬时 `EOF`、`gh` 鉴权抖动时，
  优先视为环境问题重试，不要直接归因于代码逻辑。

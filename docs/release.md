# 版本与发布

本文规定 Neon 的版本号体系、发版前本地必做步骤、发布正文格式以及 CI 分工。核心原则：**版本号更新与完整构建均在本地完成，CI 只负责在 tag 推送后生成 GitHub Release**（手动 dispatch 仅构建，不创建 Release），不得依赖 CI 修改版本号或替代本地出包。

## 版本号体系

| 形态 | Git 标签 | 版本码（写入 `mod.json` / `mod.hjson` / `build.gradle`） |
| --- | --- | --- |
| 稳定版 | `N<v>`，如 `N12` | `<v>0000`，即 `120000` |
| 预发行版 | `B<v>.<b>`，如 `B12.5` | `<v>0000 + <b>`，即 `120005` |

- Git tag 仅允许使用 `N*` 或 `B*` 格式。
- `mod.json` 与 `mod.hjson` 必须保持字段完全一致（当前包含 `hidden: true`、`minGameVersion: "160.1"` 以及 `main` / `mainX` 双入口），切勿只改其中一份。
- 本地开发任务默认的 dev 身份（`Neon-dev` / `0.0.0`）仅用于临时构建，提交前须还原；描述文件的版本变更只应出现在发布提交中。
- 更新器必须同时兼容三种版本格式：新数字版本码、预发行版本码，以及历史 `vX.Y.Z` 字符串。改动 [modUpdater](subsystems/modupdater.md) 时必须运行 `versionUtilTest` 回归验证。

## 发布前本地必做步骤

1. **更新本地版本号**：
   ```powershell
   python tools/neon_version.py --set-files 120005   # 示例：B12.5
   ```
   该命令会同步修改 `mod.json`、`mod.hjson` 与 `build.gradle` 三处版本号。
2. **执行本地完整构建与测试**：
   ```powershell
   .\gradlew.bat test
   .\gradlew.bat clean deploy
   ```
   环境要求：`ANDROID_SDK_ROOT` 指向完整的 Android SDK，不设置 `D8_PATH`，且不再走工作区 `commandlinetools-win-*` 兜底路径；先确保 `test` 回归任务全绿，再执行 `clean deploy`。
3. **撰写 `RELEASE_NOTES.md`**：中英对照、仅写当前版本，格式遵循下文「发布正文风格」；CI 会直接将其用作 GitHub Release 正文。发布说明只写入 `RELEASE_NOTES.md`，不要写进 `README.md`。
4. **拷贝并核实发布产物**：
   - `dist/Neon.jar` → `../构建/Neon/Neon-v<标签>.jar`
   - `dist/Neon.zip` → `../构建/Neon/Neon-v<标签>.zip`

   确认两个文件均存在且体积合理；桌面中间 JAR（不含 `classes.dex`）严禁进入发布流程。同时核实 JAR 内包含 `mod.json` 描述符、桌面主类与 `classes.dex`。

## 发布正文风格（Release body）

`RELEASE_NOTES.md` 即 GitHub Release 的正文原文（CI 直接读取，配置为 `generate_release_notes: false`），**仅写当前版本**且保持中英对照。当前范式于 2026-10-11 同步自 LogicSugar v5.9.0（其 `docs/release.md` 同名小节为上游原文，下方为 N17 范例）：

````markdown
> [!NOTE]
> 需要 **Mindustry v160.1+**（桌面 / Android）或对应的 MindustryX；服务器不需要安装。
> Requires **Mindustry v160.1+** (desktop / Android) or a matching MindustryX. Servers do not need it.

## 中文

### 本次新增

- **内置 LogicSugar 升级到 v5.9.0**
- **变量界面的显示开关会被记住**
- **性能分析器支持百分比视图**
- **内置建造透视升级到 v1.2，效果淡入淡出且钻头光束也会透明**

### 本次修复

- **原版积木的悬停说明恢复显示**
- **失败的断言与 `error` 指令现在会停机**

## English

### Added

- **Bundled LogicSugar is now v5.9.0**
- **The Vars screen remembers your display switches**
- **Profiler statistics support percentage view**
- **Bundled Build X-ray is now v1.2, with fade in/out and transparent plasma bore beams**

### Fixed

- **Vanilla statements show their hover hints again**
- **Failed assertions and `error` now stop the processor**
````

写作规则：

1. **结构固定**：顶部仅保留一个两行 `> [!NOTE]` 引用块（中文一行 + 英文一行）说明版本要求，随后依次为 `## 中文` 与 `## English`。不使用 `> [!IMPORTANT]` 或 `> [!WARNING]`，也不写构建命令、产物路径、测试数量和 commit 细节。
2. **小节精简**：默认只使用 `### 本次新增` / `### 本次修复`（英文 `### Added` / `### Fixed`）；仅当本版存在行为变化且不归属「本次修复」时，才增加 `### 本次改动` / `### Changed`。**不写「已知问题 / 兼容性」小节**：跨版本格式约定与调试产物事实统一记录在 [architecture.md](architecture.md) 与 [glossary.md](glossary.md)，不逐版重复。中英小节须一一对应。聚合版中的“内置子模组升级到 vX”属于玩家可感知变化，直接作为「本次新增」下的普通条目，不单开「本次更新 / Updated」小节。
3. **条目为单行完整粗体句**：整条加粗、一行写完；不附加括号补充、不写“（A / B / C）”式枚举清单，也不在粗体后接解释尾巴；枚举项与实现细节留在设置描述、提交信息与 `docs/` 文档中。
4. **篇幅克制**：全篇最多 3 个小节、条目总数不超过 6 条；常见形态为两小节各 2 条。一条只说一件事，同主题合并，宁可少写。
5. **严格界定「本次修复」**：只记录上一个已发布版本中玩家实际可能遇到的问题；开发阶段反馈的问题或同版本内新功能的内部缺陷从未面向玩家发布，不构成「修复」，不写进 Release，功能首次发布只写新增能力。若无上一版真实缺陷，整节省略「本次修复」。
6. **双语地道**：中文使用全角中文标点与引号，英文使用半角标点；两侧各自通顺自然，不逐字直译。
7. **历史不回填**：新版本一律按本范式编写；历史正文以对应版本的 GitHub Release 为准（N14 及更早版本保持旧范式原样）。

## 打包管线细节

`deploy` 背后的任务执行链如下：

```text
classes ──► d8InputJar ──► dexAndroid ──► jarMerged ──► zipMerged ──► dist/ + 构建/
             (classes+      (d8 --min-api    (合并 jar：       (同内容 zip)
              pinyin4j+      14 --release     classes+依赖解包+
              kotlin-stdlib, --lib arc-core)  classes.dex+描述)
              无副作用)
```

关键要点：

- `d8InputJar` 仅打包 class、`pinyin4j` 与 `kotlin-stdlib`，专供 D8 输入使用，不在 `dist/` 目录产生副作用。
- `dexAndroid` 使用 `--min-api 14 --release` 生成 `classes.dex`，且必须附加 `--lib <arc-core>`；缺少该参数会导致 Arc 接口被当作外部类处理，从而在 Android 设备上触发 `AbstractMethodError`（历史踩坑，切勿移除）。
- `jarMerged` 与 `zipMerged` 是唯一可分发产物；`jar` 任务产出的 `Neon-desktop.zip` 仅作为中间产物留在 `build/` 目录。
- SPDB 语义搜索所需的嵌入模型由 `downloadEmbeddingModel` 按需从 `hf-mirror` 源下载；首次离线打包前请提前执行一次该任务。

## CI（`.github/workflows/release.yml`）

触发条件：推送 `N*` / `B*` tag 或手动 dispatch。执行流程：

1. Checkout 代码，准备 Temurin 17、Gradle 与 Android SDK。
2. 运行 `tools/neon_version.py`（`--code` / `--release-name` / `--set-files` / `--check-files`）从 tag 反算版本码并校验描述文件一致性；`B*` 标签自动标记为 prerelease。
3. 构建合并产物并创建 GitHub Release。

## Release 资产安全规则

Mindustry 游戏内模组安装器会自动拉取 Release API 返回的**第一个 `.jar`**，且不区分操作系统。因此必须严格遵守：

- 每个 Release 有且只能有一个 `.jar` 资产，即最终可安装包（同时包含 `mod.json`、桌面主类与 `classes.dex`）。
- 严禁将 `*-android.jar`、`*-desktop.jar`、D8 输入 JAR 等中间产物上传到 Release。
- 发布完成后立即通过 API 复查资产列表：
  ```bash
  gh release view <tag> --json assets
  ```
  如发现多余的 `.jar` 文件，立即删除。

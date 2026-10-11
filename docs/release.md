# 版本与发布

Neon 的发版规则、本地必做步骤和 CI 的分工。原则：**版本号与构建都在本地完成，CI 只负责在 tag 推送后生成 GitHub Release**，不得依赖 CI 改版本或出包。

## 版本号体系

| 形态 | 标签 | 版本码（写进 `mod.json` / `mod.hjson` / `build.gradle`） |
| --- | --- | --- |
| 稳定版 | `N<v>`，如 `N12` | `<v>0000`，即 `120000` |
| 预发行 | `B<v>.<b>`，如 `B12.5` | `<v>0000 + <b>`，即 `120005` |

- tag 只使用 `N*` 或 `B*`。
- 更新器必须同时兼容三种历史形态：新数字版本码、预发行码、老的 `vX.Y.Z` 字符串。改动 [modupdater](subsystems/modupdater.md) 时用 `versionUtilTest` 回归。

## 发布前本地必做步骤

1. **更新本地版本号**
   ```powershell
   python tools/neon_version.py --set-files 120005   # 例：B12.5
   ```
   会同步修改 `mod.json` / `mod.hjson` / `build.gradle` 三处。
2. **本地完整构建**
   ```powershell
   .\gradlew.bat clean deploy
   ```
   环境要求：`ANDROID_SDK_ROOT` 指向完整 SDK，且不再走工作区 `commandlinetools-win-*` 兜底路径；先跑一遍 `.\gradlew.bat test` 确认回归任务全绿。
3. **撰写 `RELEASE_NOTES.md`**：中英对照、只写当前版本，格式见下节「发布正文风格」；CI 直接把它作为 Release 正文。
4. **拷贝并核实产物**
   - `dist/Neon.jar` → `../构建/Neon/Neon-v<标签>.jar`
   - `dist/Neon.zip` → `../构建/Neon/Neon-v<标签>.zip`
   
   核实两个文件都存在、大小合理；桌面 jar（不含 dex）不允许进入发布流程。

## 发布正文风格（Release body）

`RELEASE_NOTES.md` 就是 GitHub Release 的正文原文（CI 读取它，`generate_release_notes: false`），**只写当前版本**、中英对照。当前范式于 2026-10-11 从 LogicSugar v5.9.0 同步（其 `docs/release.md` 的同名小节是上游原文，范例即下面这份 N17 正文）：

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

1. **结构固定**：顶部只有一个 `> [!NOTE]` 两行引用块（中文一行 + 英文一行）写版本要求，然后 `## 中文`、`## English`。不再用 `> [!IMPORTANT]` / `> [!WARNING]`，也不写构建命令、产物路径、测试数量与 commit 细节。
2. **小节**：默认只有 `### 本次新增` / `### 本次修复`（英文 `### Added` / `### Fixed`）；本版确有行为变化、又归不进「修复」时才加 `### 本次改动` / `### Changed`。**不写「已知问题 / 兼容性」小节**：跨版本线格式与调试产物事实写在 [architecture.md](architecture.md) / [glossary.md](glossary.md)，不逐版复述。中英小节一一对应。聚合版「内置子模组升级到 vX」是用户能感知的事实，写成 `本次新增` 里的一条普通条目，不为它单开「本次更新 / Updated」小节。
3. **条目就是一句完整的粗体话**：整条加粗、一行写完；不写括号补充、不写「（A / B / C）」式枚举清单、也不在粗体后接解释尾巴；枚举与实现细节留在设置描述、提交信息与 `docs/`。
4. **篇幅**：全篇最多 3 个小节、条目总数不超过 6 条；常见形态就是两小节各 2 条。一条一件事，同主题合并，宁可少写。
5. **「本次修复」只写上一个已发布版本里用户能碰到的问题**：开发过程中用户反馈的问题、同一版本内新功能的内部缺陷，用户从未在任何已发布版本里见过，不构成「修复」，不写进 Release；功能首次发布只写它新增的能力。没有上一版真实缺陷时，「本次修复」整节省略。
6. **语言**：中文用中文标点与引号，英文用半角标点；两边各自通顺，不逐字直译。
7. **历史不回填**：新版本一律按本范式写；历史正文以各版本的 GitHub Release 为准（N14 及更早是旧范式，保持原样）。

## 打包管线细节

`deploy` 背后的任务链：

```text
classes ──► d8InputJar ──► dexAndroid ──► jarMerged ──► zipMerged ──► dist/ + 构建/
             (classes+      (d8 --min-api    (合并 jar：       (同内容 zip)
              pinyin4j+      14 --release     classes+依赖解包+
              kotlin-stdlib, --lib arc-core)  classes.dex+描述)
              无副作用)
```

要点：

- `d8InputJar` 只打包 class 与 `pinyin4j` / `kotlin-stdlib`，是 D8 专用输入，不留 dist 副作用。
- `dexAndroid` 用 `--min-api 14 --release` 出 `classes.dex`；必须带 `--lib <arc-core>`——缺了它 Arc 的接口会被当外部类处理，设备上触发 `AbstractMethodError`（曾踩过的坑，别移除）。
- `jarMerged` / `zipMerged` 是唯一可分发形态；`jar` 任务的 `Neon-desktop.zip` 是中间产物，仅存在于 build 目录。
- SPDB 语义搜索的嵌入模型由 `downloadEmbeddingModel` 按需下载（hf-mirror 源），首次离线打包请提前跑一次该任务。

## CI（`.github/workflows/release.yml`）

触发：推送 `N*` / `B*` tag 或手动 dispatch。流程：

1. checkout + Temurin 17 + Gradle + Android SDK。
2. `tools/neon_version.py --code/--release-name/--set-files/--check-files` 从 tag 反算版本码并校验描述文件一致性；`B*` 标记为 prerelease。
3. 构建合并产物并创建 GitHub Release。

## Release 资产安全规则

Mindustry 游戏内安装器会取 Release API 返回的**第一个 `.jar`**，且不按操作系统挑资产。因此：

- 一个 Release 必须有且只有一个 `.jar` 资产，即最终可安装包（含 `mod.json` + 桌面主类 + `classes.dex`）。
- 严禁把 `*-android.jar`、`*-desktop.jar`、D8 输入 jar 等中间产物传上 Release。
- 发布后用 API 复查：
  ```bash
  gh release view <tag> --json assets
  ```
  发现多余 `.jar` 立即删除。

# Neon 代理指南（AGENTS.md）

Neon 是 Mindustry 客户端辅助模组聚合仓库：把 29 个同步子模组（`tools/submods.json`）与 Neon 原生模块
合并为一个 jar、一个设置入口、一套 Overlay 体系；纯客户端，不新增方块/单位，服务器无需安装。

实现与命令事实以代码、`build.gradle` 和 `docs/` 为准。本文件只约束代理行为与流程：
需要细节时读指针，不要在这里复制会漂移的实现说明；发现文档过时，先修正文档或指针。
与上层工作区 `AGENTS.md` 冲突时，以更贴近 Mindustry 模组任务的上层规范为准。

## 单一真源（法律）

- 子模组源码的真源是 `tools/submods.json` 中 `localPath` 指向的兄弟工作区，不是 Neon。
- 同步方向永远：子模组工作区 → Neon，入口 `tools/update_submods.py`。要改子模组逻辑：
  先改子仓库并提交，再回 Neon 同步；不要在 Neon 手改同步产物。
- 同步产物包括：`src/main/java/<子模组包>/`、`src/main/java/mindustry/`、
  `src/main/java/mindustryX/features/ui/`、`src/main/java/overlaycompat/`、
  `src/main/kotlin/advancedreplace/`、`src/main/resources/{bundles,fst-bundles,sprites}/`、
  `tools/submods.lock.json`。这些路径的细节与例外见 `tools/AGENTS.md`。

## 构建入口

- JDK 17；命令在仓库根目录用 PowerShell 7 执行。

```powershell
.\gradlew.bat test
.\gradlew.bat compileJava
.\gradlew.bat clean deploy
```

- `test` 编译并跑 3 个回归任务；`compileJava` 用于同步后快速验证；`clean deploy`
  产出合并 jar/zip 与 `jarLocalDev` 本地包。
- 环境、依赖、产物与发布的完整说明：[开发指南](docs/development.md)、[发布文档](docs/release.md)。

## 硬陷阱（常驻）

- **模块隔离边界**：模块初始化必须继续走 `initializeModule` / `initializeFeature`；
  不要为了“简化”把子模组初始化移出隔离层，或让初始化异常向外传播。
- **MindustryX 编译边界**：不要直接 `import` 或编译期引用 `mindustryX.*`；新增 Neon 原生桥接能力
  一律走 `mdtxcompat`，同步产物里的历史反射探测是例外，不要扩散。新代码运行时跨类加载器取类优先用
  `LegacyMindustryXGuard.loadMindustryXClass`。
- **发布物边界**：只有 `jarMerged` / `zipMerged` 可分发；Android 依赖 `dexAndroid`
  产出的 `classes.dex`（D8 必须带 `--lib arc-core.jar`）。公开 Release 只放一个可安装 `.jar`。
- **临时身份**：本地 mod 任务按上层工作区规范使用 `Neon-dev` / `0.0.0` 的 dev 身份，
  产物走 `jarLocalDev`；这只能是临时构建，**不得提交**。发布版本的描述符变更只属于发布提交。

## 目录地图

```text
AGENTS.md                         本文件：全局规则
tools/AGENTS.md                   同步 / 注入 / bundle 合并
src/main/java/bektools/AGENTS.md  聚合入口、生命周期、设置页
src/main/java/mdtxcompat/AGENTS.md MindustryX 桥与守卫
docs/README.md                    架构 / 开发 / 发布 / 测试 / 子系统 / 用户指南
src/main/java/bektools/           Neon 原生：聚合入口、设置、遥测、profiler
src/main/java/mdtxcompat/         Neon 原生：MindustryX 反射桥
src/main/java/{betterscreenshot,custommarker}/  Neon 原生维护
src/main/java/neoncompat/         冻结兜底（ocb 失败时用，勿手改）
src/main/java/overlaycompat/      ocb 同步产物，勿手改
src/main/java/mindustryX/features/ui/  ocb 同步产物（同 FQCN OverlayUI），勿手改
src/main/java/<子模组包>/          同步产物，勿手改
src/main/java/mindustry/          同步产物，勿手改
src/main/resources/{bundles,fst-bundles,sprites}/  同步产物，勿手改
src/main/kotlin/advancedreplace/  同步产物，勿手改
src/test/java/                    3 个 JavaExec 回归测试
tools/                            Neon 原生：同步/版本脚本
build.gradle / mod.json / mod.hjson / .github/workflows/  Neon 原生维护
```

模块说明：[tools/AGENTS.md](tools/AGENTS.md)、[bektools/AGENTS.md](src/main/java/bektools/AGENTS.md)、
[mdtxcompat/AGENTS.md](src/main/java/mdtxcompat/AGENTS.md)；文档入口 [docs/README.md](docs/README.md)。

## diff 路由

| 改什么 | 先读 |
| --- | --- |
| `tools/update_submods.py`、`submods.json`、lock、bundle 合并 | `tools/AGENTS.md` |
| 同步产物包、`mindustry/`、`overlaycompat/`、`mindustryX/features/ui/` | 对应子模组工作区；`tools/AGENTS.md` |
| `src/main/java/bektools/`、设置页、模块接入 | `src/main/java/bektools/AGENTS.md`；`docs/subsystems/bektools-core.md` |
| `src/main/java/mdtxcompat/`、Overlay 兼容 | `src/main/java/mdtxcompat/AGENTS.md`；`docs/subsystems/overlay-compat.md` |
| `build.gradle`、D8、依赖、发布流水线 | `docs/development.md`；`docs/release.md` |
| 版本描述符、`RELEASE_NOTES.md` | `docs/release.md` |
| `src/test/java/`、回归任务 | `docs/testing.md` |
| 用户可见功能、bundle 文案 | `docs/user/*.md`、`FEATURES.md` |

## Git 与提交

- Neon 与各子模组是不同 Git 上下文；执行 `git`、看 diff、提交前先确认当前目录。
- 开始任务前仓库应干净；发现无关脏改动先报告，不要静默并入自己的提交。
- 提交信息 `Neon: <概要>`；同步类提交带子模组名与版本。不要提交 `dist/`、`build/`
  或临时脚本。

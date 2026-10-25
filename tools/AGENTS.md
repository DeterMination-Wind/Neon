# tools/ 代理说明

本目录是 Neon 的子模组同步、版本码与 bundle 合并工具。它管理同步产物，但不是子模组源码的真源。
实现语义以 `update_submods.py` 为准；本文件只列代理必须知道的入口、契约与禁令。

## 真源与方向

- 真源是 `submods.json` 里 `localPath` 指向的兄弟工作区；同步方向永远是子模组 → Neon。
- 包与资源归属看 `javaPackageDir` / `kotlinPackageDir` / `extraJavaDirs` / `extraResourceDirs`；
  不要把 Neon 侧新增文件塞进这些目录（`preserveJavaFiles` 是唯一例外）。
- 不要手改 `src/main/java/<子模组包>/`、`mindustry/`、`overlaycompat/`、
  `mindustryX/features/ui/`、`src/main/kotlin/advancedreplace/`、
  `src/main/resources/{bundles,fst-bundles,sprites}/` 或 `submods.lock.json`；
  它们是同步产物（`overlaycompat/` 与 `mindustryX/features/ui/` 来自 ocb）。
- 要改子模组逻辑：先在子仓库改并提交，再回 Neon 同步；不要用 Neon 侧补丁绕过子仓库。
- Neon 与子仓库是不同 Git 上下文；脚本不会替你提交，也不要把两边的 diff 混在一起。

## 命令

```powershell
python .\tools\update_submods.py --check            # 只检查，不写文件
python .\tools\update_submods.py --only <id>        # 只同步指定子模组
python .\tools\update_submods.py                    # 全量同步
python .\tools\update_submods.py --verify-build     # 同步后跑 gradlew compileJava
```

- `--check` 输出：`sync=` 是 lock 里上次同步的版本；`workspace=` 是当前本地工作区版本，
  也是下次真正会拷入 Neon 的版本；`upstream=` 仅作参考（`trackUpstream` 且存在 upstream 时有意义）。
- `changed-since-lock` 表示 Neon 落后于本地工作区；`dirty-workspace` 表示子仓库有未提交改动，
  必须回子仓库处理，不得丢弃或静默替换。
- `--check` 即使存在差异也返回 0：不要拿退出码当“无 diff”门禁，必须读输出并检查
  `changed-since-lock` / `dirty-workspace`。
- **不要用 `--force`**。默认同步本来就会重写所选模块；当前 `--force` 参数没有改变同步路径，
  不要把它当安全阀。若需全量重写，用默认全量同步并审查完整 diff。

## 同步后审查

- 先看完整 diff：源码、注入结果、合并后的 bundle、`submods.lock.json`。
- 跑 `.\gradlew.bat compileJava` 或 `--verify-build`；再跑 `--check`，确认状态归零。
- 不要把同步产物里的改动再手改一遍；发现问题回到子仓库。

## 注入契约

`injectBekHooks=true` 的子模组在写入时由 `inject_bek_hooks()` 改写，并由
`assert_injected_structure` 做结构断言：

- `public static boolean bekBundled` 声明恰好一个；
- `public void bekBuildSettings(SettingsMenuDialog.SettingsTable ...)` 方法恰好一个；
- 所有 `ui.settings.addCategory(...)` 调用必须带 `if(!bekBundled)` 守卫；
- 存在 `registerSettings()` 时必须含 `if(bekBundled) return;` 早退；
- 括号必须平衡。任一失败会中止同步并提示更新 `inject_bek_hooks()`；不要绕过断言。

多数子模组已设 `injectBekHooks=false`，由子仓库自行维护契约。`preserveJavaFiles` 目前只用于
`sp` 的 `StealthPathModX.java`（Neon 侧 mainX 入口，子仓库无对应文件），镜像拷贝时不要删。

## bundle 合并

- 全量同步从所有子模组的 `bundle*.properties` 合并；不同子模组对同一 key 给出不同值时
  直接报碰撞，不允许静默覆盖。
- `tools/bektools-bundles/bundle*.properties` 是 Neon 侧显式覆盖，合并后最后应用，优先级最高。
- `--only` 走增量合并（`merge_selected_bundles`）：它从现有合并结果出发，并让 `ls` 对
  `logicsugar.*`、`setting.logicsugar.*`、`lcategory.*`、`la.err.*` 的自家改值不算跨子模组碰撞。
  这个豁免只对 `ls` 成立，且刻意很窄：`instruction.*` 不豁免，`la.*` 的其余键不豁免，
  其他子模组使用 `lcategory.*` 或 `la.err.*` 仍走碰撞检查（未注册的 `../NewHorizonMod`、
  `../logic-assist` 仍在用这些前缀）。判定必须与 `update_submods.py` 的 `child_owns_key` 一致。
- **FST 术语表**：`fst-bundles` 复制时会丢弃与英文 `bundle.properties` 完全相同的行；
  语言覆盖和“base + localized”合并语义不变，不要把这条裁剪去掉。
- 不要直接编辑合并后的 bundle；改子仓库原文，或改 `tools/bektools-bundles/` 的覆盖。

## 其他

- `neon_version.py` 负责 N/B 版本码与 `mod.json` / `mod.hjson` / `build.gradle` 同步；
  规则见 `../docs/release.md`。
- `generate_dox.py` 是辅助文档工具，与构建链路无关；`deps/` 是兜底 arc-core/arcnet。
- 细节：`../docs/development.md`、`../docs/release.md`。

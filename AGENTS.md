# AGENTS.md - Neon 模组说明

## 文件结构（当前仓库）
```text
Neon/
|-- .github/
|   \-- workflows/
|       \-- release.yml
|-- gradle/
|   \-- wrapper/
|       |-- gradle-wrapper.jar
|       \-- gradle-wrapper.properties
|-- src/
|   \-- main/
|       |-- java/
|       |   |-- autopruner/            (智能拆除)
|       |   |-- bektools/              (聚合设置入口 / 核心)
|       |   |   |-- profiler/          (性能分析)
|       |   |   \-- ui/                (设置样式组件)
|       |   |-- betterhotkey/          (快捷键增强)
|       |   |-- betterlogisticsspeed/  (物流速率增强)
|       |   |-- bettermapeditor/       (地图编辑增强)
|       |   |-- betterminimap/         (增强小地图)
|       |   |-- betterpolyai/          (Poly 建造辅助)
|       |   |-- betterprojectoroverlay/(投影叠加)
|       |   |-- betterrtsformation/    (RTS 编队增强)
|       |   |-- betterscreenshot/      (更好的截图)
|       |   |-- betterterraingen/      (更自然的地形生成)
|       |   |-- colortheducts/        (导管染色)
|       |   |-- custommarker/          (自定义标记)
|       |   |-- foreignservertranslator/(外语服务器翻译)
|       |   |-- lockattack/            (锁定攻击)
|       |   |-- logicsugar/            (逻辑辅助)
|       |   |-- mdtxcompat/            (MindustryX 兼容桥接)
|       |   |-- mindustry/             (游戏包内扩展)
|       |   |   |-- logic/             (SugarCompiler / SugarCanvas)
|       |   |   |-- maps/              (地图过滤器)
|       |   |   \-- ui/                (翻译聊天 UI / 对话框)
|       |   |-- modupdater/            (模组更新中心)
|       |   |-- neoncompat/            (Neon Overlay 兼容层)
|       |   |-- patchviewer/           (补丁查看器)
|       |   |-- pinyinsearchsupport/   (拼音搜索支持)
|       |   |-- powergridminimap/      (电网小地图)
|       |   |-- radialbuildmenu/       (圆盘快捷建造)
|       |   |-- random/                (随机化)
|       |   |-- serverplayerdatabase/  (玩家数据库)
|       |   |-- stealthpath/           (偷袭小道)
|       |   |-- tripwire/              (地理围栏报警)
|       |   \-- whousesthisbuilding/   (谁在用这个建筑)
|       |-- kotlin/
|       |   \-- advancedreplace/       (地图编辑高级替换)
|       \-- resources/
|           |-- bundles/
|           \-- fst-bundles/           (ForeignServerTranslator 独立 bundle)
|-- tools/
|   |-- bektools-bundles/
|   |   |-- bundle.properties
|   |   \-- bundle_zh_CN.properties
|   |-- generate_detail.py
|   |-- generate_dox.py
|   |-- neon_version.py
|   |-- submods.json
|   |-- submods.lock.json
|   \-- update_submods.py
|-- .gitattributes
|-- .gitignore
|-- AGENTS.md
|-- build.gradle
|-- gradlew
|-- gradlew.bat
|-- LICENSE
|-- mod.json
|-- mod.hjson
|-- README.md
\-- settings.gradle
```

## 维护约束
- 编译目标为 Java 17（`build.gradle` 使用 `options.release.set(17)`，Mindustry v159 需要 Java 17 运行时）。
- 变更优先聚焦性能与可读性，不做无关重构。
- 用户可见文案优先走 bundle/资源文件，不硬编码。

## 版本号与发布
- 稳定版 Release 使用 `N<稳定版本号>`，例如 `N11`。
- 预发行版 Release 使用 `B<稳定版本号>.<递增构建号>`，例如 `B11.20`。
- `N11` 在 `mod.json`、`mod.hjson` 和 `build.gradle` 中统一写为 `110000`；`B11.20` 写为 `110020`。
- 发布 tag 只使用 `N*` 或 `B*`；CI 通过 `tools/neon_version.py` 计算版本号并同步描述文件。
- **每次发布前必须在本地完成以下步骤（不依赖 CI 改版本或构建）**：
  1. 更新本地版本号：`python tools/neon_version.py --set-files <版本码>`（例如 N12 → `120000`），同步 `mod.json` / `mod.hjson` / `build.gradle`；
  2. 本地构建：`gradlew clean deploy`（`ANDROID_SDK_ROOT` 指向完整 SDK 并取消 `D8_PATH`）；
  3. 将 `dist/Neon.jar` 与 `dist/Neon.zip` 复制为 `构建/Neon/Neon-v<标签>.jar` 与 `构建/Neon/Neon-v<标签>.zip`，并核实两个文件。
  CI 只负责在 tag 推送后生成 GitHub Release，不得作为版本号更新或本地构建的替代。
- 更新器必须同时兼容新格式、数字版本码和历史 `vX.Y.Z` 版本。

## 设置接入规范（Neon 风格）
- 新并入的子模组设置项必须并入 Neon 总设置入口，不允许在 `bekBundled=true` 时再注册独立 `ui.settings.addCategory(...)`。
- 新并入的子模组主类必须提供：
  - `public static boolean bekBundled`
  - `public void bekBuildSettings(SettingsMenuDialog.SettingsTable table)`
- Neon 聚合入口统一在 `src/main/java/bektools/BekToolsMod.java` 的 `registerSettings()` 中通过 `addGroup(...)` 挂载，保持一致的分组标题/缩进/间距样式（RbmStyle）。
- 没有独立设置项的子模组也要在 Neon 设置中给出分组与说明占位（`bektools.section.<id>.none`）。
- 新增分组标题与说明文案必须同步写入：
  - `tools/bektools-bundles/bundle.properties`
  - `tools/bektools-bundles/bundle_zh_CN.properties`
  并确保合并后的 `src/main/resources/bundles/bundle*.properties` 可用。

命令操作请使用 PowerShell 7（`pwsh`）。

## 子模组同步状态
- `tools/update_submods.py --check` 以本地工作区为准检查子模组状态，不会放弃 `localPath` 工作流。
- 注入主类（`injectBekHooks=true`）在同步写入时自动执行结构断言（`assert_injected_structure`）：`bekBundled` 声明与 `bekBuildSettings` 方法必须恰好存在一个，所有 `ui.settings.addCategory` 调用必须带 `if(!bekBundled)` 守卫，存在 `registerSettings()` 时必须含 `if(bekBundled) return;` 早退，括号必须平衡；任一失败即中止同步并指明问题，提示人工更新 `inject_bek_hooks()`。
- 需要验证注入产物整体可编译时，追加 `--verify-build` 参数：同步完成后运行 `gradlew compileJava`。
- `sync=` 表示 `tools/submods.lock.json` 里上次正式同步进 Neon 的版本。
- `workspace=` 表示当前本地子模组工作区版本，也是下次执行同步时真正会被拷入 Neon 的版本。
- `upstream=` 只在本地仓库存在 git upstream 且启用 `trackUpstream` 时展示，用来补充说明远端状态。
- `changed-since-lock` 说明本地子模组自上次 Neon 同步后已经变化；`dirty-workspace` 说明当前目录还有未提交改动。

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
|       |   |-- bektools/              (聚合设置入口 / 核心)
|       |   |-- betterhotkey/          (快捷键增强)
|       |   |-- betterlogisticsspeed/  (物流速率增强)
|       |   |-- bettermapeditor/       (地图编辑增强)
|       |   |-- betterminimap/         (增强小地图)
|       |   |-- betterpolyai/          (Poly 建造辅助)
|       |   |-- betterprojectoroverlay/(投影叠加)
|       |   |-- betterscreenshot/      (更好的截图)
|       |   |-- custommarker/          (自定义标记)
|       |   |-- foreignservertranslator/(外语服务器翻译)
|       |   |-- mdtxcompat/            (MindustryX 兼容桥接)
|       |   |-- mindustry/ui/fragments/(翻译聊天 UI 组件)
|       |   |-- modupdater/            (模组更新中心)
|       |   |-- patchviewer/           (补丁查看器)
|       |   |-- pinyinsearchsupport/   (拼音搜索支持)
|       |   |-- powergridminimap/      (电网小地图)
|       |   |-- radialbuildmenu/       (圆盘快捷建造)
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
|   |-- generate_dox.py
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
- 保持 Java 8 兼容（如本项目包含 Java 源码）。
- 变更优先聚焦性能与可读性，不做无关重构。
- 用户可见文案优先走 bundle/资源文件，不硬编码。

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
- `sync=` 表示 `tools/submods.lock.json` 里上次正式同步进 Neon 的版本。
- `workspace=` 表示当前本地子模组工作区版本，也是下次执行同步时真正会被拷入 Neon 的版本。
- `upstream=` 只在本地仓库存在 git upstream 且启用 `trackUpstream` 时展示，用来补充说明远端状态。
- `changed-since-lock` 说明本地子模组自上次 Neon 同步后已经变化；`dirty-workspace` 说明当前目录还有未提交改动。

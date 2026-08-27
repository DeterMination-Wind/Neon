# 开发指南

环境、日常构建命令、以及把一个子模组并入 Neon 的完整清单。仓库级硬性约束（版本号、文案、同步）在根目录 [AGENTS.md](../AGENTS.md)，两者冲突时以 AGENTS.md 为准。

## 环境

- JDK 17（Mindustry v159 运行时要求 Java 17）。
- 安卓构建需要 D8：按以下顺序自动探测——`D8_PATH` 环境变量 → `ANDROID_SDK_ROOT` / `ANDROID_HOME` 下的 `build-tools` → 工作区根的 `commandlinetools-win-*` 目录。都没有时 `dexAndroid` 会直接失败。
- Mindustry/Arc 编译类路径优先取本地检出的 jar：
  - `../Mindustry-master/core/build/libs/core-release.jar`
  - `../Arc/arc-core/build/libs/arc-core-1.0.jar`
  - `../Mindustry-master/desktop/build/libs/Mindustry.jar`（可选）
  
  本地不存在时退回 JitPack 的 `v159`，并改用 `tools/deps/arc-core.jar` + `tools/deps/arcnet.jar` 作为 Arc 类路径。
- Windows 下命令请用 PowerShell 7（`pwsh`）。

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

## 并入一个新子模组（清单）

1. **建包**：在 `src/main/java/<feature>/` 建独立包（或 Kotlin 放 `src/main/kotlin/<feature>/`），保持与其他模块零横向依赖；共享能力向下沉到 `bektools` 或 `mdtxcompat`。
2. **主类契约**：主类提供
   ```java
   public static boolean bekBundled;
   public void bekBuildSettings(SettingsMenuDialog.SettingsTable table)
   ```
3. **守卫存量设置**：原有 `ui.settings.addCategory(...)` 必须包进 `if(!bekBundled)`；如模块自带 `registerSettings()`，开头 `if(bekBundled) return;`。
4. **接入总入口**：在 `bektools.BekToolsMod` 增加 moduleId 常量与字段，经 `initializeFeature(moduleId, initializer)` 初始化；在 `registerSettings()` 里用 `addGroup(...)` 挂分组，样式走 `RbmStyle`。
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

## 调试建议

- 先在原版客户端冒烟（内嵌 Overlay 路径），再切 MindustryX 验证桥接路径；两套 Overlay 行为必须都可接受。
- 设置页尾部出现 `@bektools.module.failed` 分组即代表某模块初始化抛了异常，按分组下挂的错误信息定位。
- 性能问题先用 `neon profiler` 客户端指令（`bektools.profiler.NeonProfilerFeature`）定位。

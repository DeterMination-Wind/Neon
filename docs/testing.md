# 测试指南

Neon 的自动化测试规模不大，采用 main() 断言式的 JavaExec 回归任务，全部挂接在 `check`/`test` 链路上。任何接线改动都不允许把回归任务从 `test` 的 `dependsOn` 里摘掉。

## 自动化任务

`build.gradle` 中注册了三个回归任务，均依赖 `testClasses`：

| 任务 | 主类 | 覆盖内容 |
| --- | --- | --- |
| `dataImagePackerCompatTest` | `bektools.DataImagePackerCompatTest` | DataImagePacker 兼容性回归 |
| `pinyinScopeContextTest` | `pinyinsearchsupport.ui.PinyinScopeContextTest` | 拼音搜索的场景作用域（哪些界面生效） |
| `versionUtilTest` | `modupdater.features.VersionUtilTest` | 发布标签 / 版本码换算，含历史格式 |

```powershell
.\gradlew.bat test        # 全部
.\gradlew.bat versionUtilTest   # 单跑一个
```

改动对应子系统时必须先跑相关任务；发版前三个都要绿。

## 测试的新增原则

- 纯函数、可离线断言的逻辑（版本码换算、匹配算法、打包兼容）优先补成 JavaExec 回归任务并接进 `test.dependsOn`。
- 依赖游戏运行时的行为（Overlay 渲染、输入交互）不写自动测试，走下面的手测清单。

## 手测清单

发版或大改动前，至少覆盖：

1. **原版客户端**（ocb Overlay 路径；内嵌副本仅作兜底）
   - 模组正常加载，设置 → 模组 → Neon 打开且分组完整。
   - 设置页尾部没有 `@bektools.module.failed` 错误分组。
   - 齿轮按钮 / `Z` 能打开 Overlay 管理器。
2. **MindustryX 客户端**（桥接路径）
   - 确认客户端确实跑了 `mainX`（X 变体模块行为生效）；若 X 运行时误入原版 `main`，应看到明确的升级/回退错误，而不是被吞成单个模块失败。`MINIMUM_VERSION` 是声明的版本门槛，当前代码不做版本字符串比较。
   - 有 X 变体的模块（pgmm、sp、rbm、spdb、bpo、bhk）行为正常。
3. **功能开关**：默认关闭的模块（智能拆除、导管染色、betterMiniMap、Better PolyAI、profiler 等，以设置页当前默认值为准）开启后生效、再次关闭后恢复。
4. **安卓包**：安装 `dist/Neon.jar` 于安卓设备，确认能加载（验证 `classes.dex` 有效），重点点一遍涉及 Arc 输入接口的交互（拖拽等，防 `AbstractMethodError` 回归）。
5. **更新中心**：检查列表能区分 可更新 / 已最新 / 黑名单 / 无仓库 四种状态。

## 已知环境缺口

- JitPack 回退路径的测试类路径只带 `tools/deps/` 的 arc-core + arcnet，缺 arc-g3d（`arc.math.geom.Mat3D`）；`dataImagePackerCompatTest` 初始化 `DataManager` 时可能 `NoClassDefFoundError`。用本地工作区 jar 构建不会触发。
- CI 的 release 流水线只跑 `clean deploy`，不跑 `test`，因此该缺口不会在 CI 暴露；发版前本地跑 `test` 是唯一保障。

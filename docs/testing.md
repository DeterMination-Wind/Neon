# 测试指南

Neon 的自动化测试规模精简，采用 `main()` 断言式的 `JavaExec` 回归任务，全部挂接在 `check` / `test` 链路上。任何构建接线改动均不得将回归任务从 `test` 的 `dependsOn` 中移除。

## 自动化回归任务

`build.gradle` 中共注册了三个回归任务，均依赖 `testClasses`：

| 任务 | 主类 | 覆盖内容 |
| --- | --- | --- |
| `dataImagePackerCompatTest` | `bektools.DataImagePackerCompatTest` | `DataImagePacker` 兼容性回归 |
| `pinyinScopeContextTest` | `pinyinsearchsupport.ui.PinyinScopeContextTest` | 拼音搜索的场景作用域（验证生效界面范围） |
| `versionUtilTest` | `modupdater.features.VersionUtilTest` | 发布标签与版本码换算（含历史格式） |

```powershell
.\gradlew.bat test              # 运行全部回归任务
.\gradlew.bat versionUtilTest   # 单独运行指定任务
```

改动对应子系统时须先运行相关回归任务；发版前三个任务必须全部通过。

## 新增测试原则

- 纯函数、可离线断言的逻辑（如版本码换算、匹配算法、打包兼容等）优先补充为 `JavaExec` 回归任务，并接入 `test.dependsOn`。
- 强依赖游戏运行时的行为（如 Overlay 渲染、键鼠输入交互）不强行编写自动化测试，按下方手测清单覆盖。

## 手测清单

发版或合入大改动前，至少完成以下 6 项手工验证：

1. **原版客户端**（ocb Overlay 路径；内嵌副本仅作兜底）：
   - 模组正常加载，`设置 → 模组 → Neon` 可顺利打开且各分组完整。
   - 设置页尾部没有出现 `@bektools.module.failed` 错误分组。
   - 点击左侧齿轮按钮或按默认快捷键 `Z` 可正常打开 Overlay 管理器。
2. **MindustryX 客户端**（桥接路径）：
   - 确认客户端实际加载了 `mainX`（各 X 变体模块行为生效）；若真 X 运行时误入原版 `main`，应立即弹出明确的升级/回退错误，而非被吞成单个模块失败（`MINIMUM_VERSION` 为声明的版本门槛，当前代码不做版本字符串比较）。
   - 验证全部具备 X 变体的模块（`pgmm`、`sp`、`rbm`、`spdb`、`bpo`、`bhk`）行为正常。
3. **功能主开关**：将默认关闭的模块（智能拆除、导管染色、betterMiniMap、BetterPolyAi、profiler 等，以设置页当前默认值为准）开启后验证生效，再次关闭后验证恢复。
4. **Android 包**：将 `dist/Neon.jar` 安装到 Android 设备并确认可正常加载（验证 `classes.dex` 有效），重点测试涉及 Arc 输入接口的交互（如拖拽操作），防止 `AbstractMethodError` 回归。
5. **模组更新中心**：检查列表能够正确区分「可更新」「已最新」「黑名单」「无仓库」四种状态。
6. **Overlay 窗口尺寸**（验证重启不塌陷）：仅拖动位置、不拖动缩放手柄，重启客户端后窗口仍能按内容自适应尺寸（依次点验 `custommarker` 聊天坐标、`tripwire` 绘制/删除、`pgmm` 电力表、`spdb`、`stealthpath`）；拖动过缩放手柄的 `resizable` 窗口重启后应沿用已保存尺寸；隐藏窗口进入游戏后再启用时能立刻按内容定尺寸；BLS 标记节点列表在内容变窄时能够正常收缩。

## 已知环境缺口

- JitPack 回退路径下的测试类路径仅包含 `tools/deps/` 中的 `arc-core` 与 `arcnet`，缺少 `arc-g3d`（`arc.math.geom.Mat3D`）；因此 `dataImagePackerCompatTest` 在初始化 `DataManager` 时可能触发 `NoClassDefFoundError`。使用本地工作区 JAR 构建不会遇到此问题。
- CI 的 release 流水线只执行 `clean deploy`，不执行 `test`，上述缺口不会在 CI 中暴露；发版前在本地运行 `test` 是唯一保障。

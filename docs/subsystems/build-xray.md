# 子系统：建造透视（Build X-ray）

建造透视是聚合进 Neon 的一个客户端显示模块（module id `bx`），在设置页里叫「Build X-ray (by Miner)」。
它把建造预览遮住的图层变透明，便于对准要放置或拆除的位置。

## 来源与真源

- 玩法逻辑来自 **Miner**（minRi2）的 JavaScript 脚本模组 **build-xray**；原始 `scripts/main.js`、
  `mod.json` 与 bundle 原样保存在子仓库 `../BuildXray/original/` 下。
- Java 移植与其后的维护在子仓库 `../BuildXray`（Neon 里的 `src/main/java/buildxray/` 是同步产物）。
- **不要在 Neon 里手改 `src/main/java/buildxray/`**：改子仓库、提交，再执行
  `python tools/update_submods.py --only bx`。Neon 侧只维护模块注册（`bektools.BekToolsMod`）
  与分组标题（`tools/bektools-bundles/`）。

## 聚合契约

| 契约 | 位置 | 说明 |
| --- | --- | --- |
| `bekBundled` | `buildxray.BuildXrayMod` | 聚合时为 `true`，独立形态下不注册自己的设置分类 |
| `bekBuildSettings(SettingsTable)` | `buildxray.BuildXrayMod` | 把三个设置项交给 Neon 的设置表渲染 |
| 主开关键 | `bx-enabled`（默认开启） | Neon 用它在设置页做"已关闭的子模组"折叠与状态快照 |

`submods.json` 里 `injectBekHooks: false`：该子仓库自己维护上述契约，同步脚本只做源码与 bundle 复制。

## 运行时结构

`buildxray.BuildXrayFeature` 挂在 `EventType.Trigger.draw` 上，每帧按原脚本的三阶段执行：

1. **遮罩**：`transBuffer` 里用 `circle-shadow` 精灵以 `Draw.alpha(transProgress * transparent)` 画出
   选区矩形（`Placement.normalizeDrawArea` + `sqrt2 * 1.5` 放大）或鼠标圆圈。
2. **捕获**：两个图层窗口内的绘制被捕获进 `screenBuffer`：
   `[Layer.legUnit ± 2.001]`（腿部单位）与 `[Layer.flyingUnitLow - 2.001, Layer.flyingUnit + 2.001]`
   （飞行单位、子弹、特效）。建筑与地板不在其中，建造预览本身保持清晰。
3. **合成**：`screenBuffer.blit(TransShader)` 用遮罩 alpha 做 `color.a *= 1.0 - alpha` 回到主画面。

`transProgress` 每帧 0.02 lerp，因此开关建造时是渐入渐出。

## Neon 环境下的注意点

- **延迟创建 GL 资源**：`arc` 的 `Shader` 在构造函数里编译 GLSL，帧缓冲也依赖 GL 上下文，因此
  统一放在第一次绘制帧的 `ensureShader()` 里；`init()` 阶段（模组加载、无绘制帧）不能 new。
- **失败只降级一次**：着色器编译失败或 `shaders/screenspace.vert` 缺失时置 `shaderUnavailable`，
  打印一条 `BuildXray: ...` 错误并停用效果，不会每帧重试或抛出异常打断渲染循环。
- **模块隔离**：构造与 `init()` 由 `BekToolsMod.initializeModule("bx", ...)` 包裹，抛错会记录为
  模块失败并在设置页显示占位（`@bektools.module.failed`）。
- **两个图层的窗口常量来自原脚本**，改它们等于改变效果范围（例如把建筑纳入捕获就不再是"透视"而是
  "整体半透明"），改动前先看子仓库的 `docs/architecture.md`。

## 与原脚本的行为差异

原 JS 的 `sliderPref` 默认值被 Rhino 类型转换破坏（透明度 `0.8` → `0`，鼠标半径 `tilesize * 32` → `256`
且超出滑条范围），移植取变量本意作为默认值（80% / 32 格），并把设置键改成 Neon 规范的前缀式命名。
细节见子仓库 `docs/architecture.md`。

# 子系统：建造透视（Build X-ray）

建造透视是聚合进 Neon 的客户端显示模块（`moduleId` 为 `bx`），在设置页中显示为「Build X-ray (by Miner)」。它会将建造预览遮挡的图层变为半透明，便于玩家对准放置或拆除位置。

## 来源与真源

- 玩法逻辑源自 **Miner**（minRi2）的 JavaScript 脚本模组 **build-xray**；原始 `scripts/main.js`、`mod.json` 与 bundle 文件完整保存在子仓库 `../BuildXray/original/` 下。
- Java 移植及后续维护均在子仓库 `../BuildXray` 中进行，Neon 仓库中的 `src/main/java/buildxray/` 为同步产物。
- **切勿在 Neon 中直接修改 `src/main/java/buildxray/`**：请在子仓库修改并提交，再执行 `python tools/update_submods.py --only bx` 同步。Neon 侧仅维护模块注册（`bektools.BekToolsMod`）与分组标题（`tools/bektools-bundles/`）。

## 聚合契约

| 契约项 | 所在位置 | 说明 |
| --- | --- | --- |
| `bekBundled` | `buildxray.BuildXrayMod` | 聚合环境下为 `true`，跳过独立设置分类注册 |
| `bekBuildSettings(SettingsTable)` | `buildxray.BuildXrayMod` | 将三个设置项交由 Neon 统一设置表渲染 |
| 主开关键 | `bx-enabled`（默认开启） | 供 Neon 在设置页实现“已关闭子模组”折叠与状态快照 |

`submods.json` 中配置了 `injectBekHooks: false`：该子仓库自行维护上述契约，同步脚本只执行源码与 bundle 复制。

## 运行时结构

`buildxray.BuildXrayFeature` 挂载在 `EventType.Trigger.draw` 上，每帧按原脚本的三阶段管线执行：

1. **遮罩**：在 `transBuffer` 中使用 `circle-shadow` 精灵，以 `Draw.alpha(transProgress * transparent)` 绘制选区矩形（`Placement.normalizeDrawArea` 并按 `sqrt2 * 1.5` 放大）或鼠标圆形区域。
2. **捕获**：捕获两个图层窗口内的绘制内容到 `screenBuffer`，分别是 `[Layer.legUnit ± 2.001]`（腿部单位）与 `[Layer.flyingUnitLow - 2.001, Layer.flyingUnit + 2.001]`（飞行单位、子弹、特效）。建筑与地板不在捕获范围内，因此建造预览本身始终清晰。
3. **合成**：通过 `screenBuffer.blit(TransShader)` 使用遮罩 alpha 执行 `color.a *= 1.0 - alpha`，将结果合成回主画面。

`transProgress` 每帧以 `0.02` 进行线性插值（lerp），因此进入与退出建造状态时呈现平滑的淡入淡出效果。

## Neon 环境注意事项

- **延迟创建 GL 资源**：Arc 的 `Shader` 会在构造函数中编译 GLSL，帧缓冲同样依赖 GL 上下文，因此必须在首个绘制帧的 `ensureShader()` 中延迟创建；不能在 `init()` 阶段（模组加载时无绘制帧）直接实例化。
- **失败只降级一次**：若着色器编译失败或缺少 `shaders/screenspace.vert`，立即置 `shaderUnavailable`，打印一条 `BuildXray: ...` 错误日志并停用效果，避免每帧重试或抛出异常打断渲染循环。
- **模块隔离**：构造与 `init()` 均由 `BekToolsMod.initializeModule("bx", ...)` 包裹；若抛错会记录为模块失败并在设置页展示 `@bektools.module.failed` 占位。
- **谨慎改动图层窗口常量**：两个图层窗口常量沿用自原脚本，改动它们会直接改变透视范围（例如将建筑纳入捕获会变成“整体半透明”而非“透视”）；修改前请先阅读子仓库的 `docs/architecture.md`。

## 与原脚本的行为差异

原版 JS 中 `sliderPref` 的默认值受 Rhino 类型转换影响而失效（透明度 `0.8` 变为 `0`，鼠标半径 `tilesize * 32` 变为 `256` 且超出滑条范围）；Java 移植版还原了变量原意作为默认值（80% / 32 格），并将设置键统一改为 Neon 规范的前缀式命名。更多细节见子仓库的 `docs/architecture.md`。

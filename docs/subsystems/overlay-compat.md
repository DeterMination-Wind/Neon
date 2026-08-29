# 子系统：Overlay 兼容层（mdtxcompat / ocb / neoncompat）

Neon 的悬浮窗口（Overlay）体验要在两种环境一致可用：原版客户端和 MindustryX。做法是"接口在内、实现在外"：功能模块只依赖 `mdtxcompat` 里的桥接口，运行时由入口决定接哪套实现。本页解释桥的选型逻辑、守卫和原版回退实现。

**2026-08 起，原版环境的 OverlayUI 由 bundled 子模块 `ocb`（OverlayCompatBridge）提供**：它以同 FQCN（`mindustryX.features.ui.OverlayUI`）的 Java 实现随 Neon 发布，真源在其独立仓库 `../OverlayCompatBridge`（经 `tools/submods.json` 同步）。`neoncompat.overlay` 降级为冻结的历史副本（见下文）。

## 桥接口一览

| 接口 | 方法语义 | MindustryX 实现 | 原版/空实现 |
| --- | --- | --- | --- |
| `OverlayUiBridge` | 创建/管理悬浮窗窗口 | `MindustryXOverlayUiBridge`（反射复用 `mindustryX.features.ui.OverlayUI`——真 MDtX 优先，否则命中 ocb 内嵌副本） | `NeonEmbeddedOverlayUiBridge`（冻结兜底）、`NoopOverlayUiBridge` |
| `MarkerBridge` | 在世界/小地图上放标记 | `MindustryXMarkerBridge` | `NoopMarkerBridge`、`MarkerBridge.UNSUPPORTED` 常量 |
| `SchematicShareBridge` | 蓝图分享通道 | `MindustryXSchematicShareBridge` | `NoopSchematicShareBridge` |

消费方式：模块初始化前核心会把桥传进来（如 `CustomMarkerFeature.configureCompat(overlayUi, markerBridge)`、`BetterScreenShotFeature.configureOverlayUi(overlayUi)`），之后一律通过持有引用调用，**禁止直接 import `mindustryX.*` 类**。

## 选型：从 main 入口进来的自动探测

```text
main 入口 → vanillaOverlayUi()
              ├─ LegacyMindustryXGuard.rejectLegacyMindustryX("Neon")   // 旧 MDtX 加载器误入 → 抛错拒绝
              └─ OverlayUiBridge.autoDetect()                           // 运行时探测
```

- `AutoDetectingOverlayUiBridge`（49 行）包装选型结果：探测到可用的 MDtX 就代理过去，否则落回 Neon 内嵌实现。
- `MindustryXOverlayUiBridge.loadMindustryXClass()` 会依次尝试多个类加载器解析 MDtX 类（MDtX 的加载器与普通 mod 不同），全部失败才抛 `ClassNotFoundException`。**ocb bundled 后，Neon 自己的类加载器里就有 `mindustryX.features.ui.OverlayUI`（`LegacyMindustryXGuard` 把自身 loader 列为候选），因此原版环境必然在此步命中 ocb 副本**。
- 已在 `mainX` 里时不用探测——入口构造函数已注入确定实现。
- ocb 主类 `OverlayCompatBridgeMod`（BekToolsMod 初始化为首个基础设施模块）负责 OverlayUI 的初始化时机、`Z` 键与齿轮按钮；检测到真 MindustryX 运行时会自动休眠（`UNAVAILABLE_CLASS`），把控制权完整让给 MDtX。

## 守卫：LegacyMindustryXGuard

定位：防止"旧版 MindustryX + 新版 Neon"这类半兼容组合静默出错。

- 常量 `MINIMUM_VERSION = "2026.04.03.B439"`。
- 判定运行环境的三条线索：系统属性 `mdtx.loader=1`、属性 `MDTX-loaded`、以及按 mod 名单（`mindustryx`/`mdtx`）和标记类（`mindustryX.VarsX`、`mindustryX.loader.Main`）探测。
- 对旧版本的处理是**显式失败**：reject 时给出升级或回退指引，而不是当作单个模块初始化失败继续跑。

## 原版回退：neoncompat.overlay（已冻结）

`neoncompat.overlay` 是 ocb 真源并入前的**手工改包副本**（`OverlayUI`/`AdsorptionSystem`/`NeonOverlayBootstrap`）。ocb 子模块就位后，正常路径不会再选中它（`AutoDetectingOverlayUiBridge` 总能经 Guard 命中 ocb 副本），它仅作为「ocb 模块初始化失败」时的兜底保留。

| 类 | 状态 |
| --- | --- |
| `neoncompat.overlay.OverlayUI` | 冻结副本，勿与 `mindustryX.features.ui.OverlayUI` 手工同步 |
| `neoncompat.overlay.AdsorptionSystem` | 冻结副本 |
| `neoncompat.overlay.NeonOverlayBootstrap` | 仅兜底路径初始化；正常路径的键位/齿轮由 ocb 主类提供 |

用户侧行为（默认值）：屏幕左侧齿轮按钮或快捷键 `Z` 打开 Overlay 管理器；支持 Overlay 的功能窗口统一在其中出现。这套 UI 是本地客户端界面，与其他玩家无关。

## Marker 能力的降级链

customMarker / Tripwire 这类需要放标记的功能：

1. MindustryX 环境：走 `MindustryXMarkerBridge` 使用 MDtX 标记能力；
2. 原版环境：无 MDtX 标记时不阻断功能本体——不能用桥的部分退化为 Noop 或模组自绘（各功能自行决定），绝不因此崩溃。

## 排障

- **设置页某功能说明写着"需要 MindustryX"却没接上**：先确认客户端真跑了 `mainX`（看日志中入口类）；再确认 MDtX 版本 ≥ 守卫常量。
- **窗口出现在错误位置/不吸附**：原版路径查 `AdsorptionSystem` 状态；MDtX 路径属 MDtX 自身行为。
- **桥抛 ClassNotFoundException**：多为 MDtX 更新后改了内部类名，检查 `MindustryXSchematicShareBridge` 等实现里的目标类是否仍存在。
- **原版环境 OverlayUI 完全缺失**：正常情况 ocb 副本一定存在（随 Neon 打包）；若日志出现"check whether that module failed to initialize"，到模块状态里查 `ocb` 是否初始化失败。

# 子系统：Overlay 兼容层（mdtxcompat / neoncompat）

Neon 的悬浮窗口（Overlay）体验要在两种环境一致可用：原版客户端和 MindustryX。做法是"接口在内、实现在外"：功能模块只依赖 `mdtxcompat` 里的桥接口，运行时由入口决定接哪套实现。本页解释桥的选型逻辑、守卫和原版回退实现。

## 桥接口一览

| 接口 | 方法语义 | MindustryX 实现 | 原版/空实现 |
| --- | --- | --- | --- |
| `OverlayUiBridge` | 创建/管理悬浮窗窗口 | `MindustryXOverlayUiBridge`（复用 MDtX OverlayUI） | `NeonEmbeddedOverlayUiBridge`（内嵌实现）、`NoopOverlayUiBridge` |
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
- `MindustryXOverlayUiBridge.loadMindustryXClass()` 会依次尝试多个类加载器解析 MDtX 类（MDtX 的加载器与普通 mod 不同），全部失败才抛 `ClassNotFoundException`。
- 已在 `mainX` 里时不用探测——入口构造函数已注入确定实现。

## 守卫：LegacyMindustryXGuard

定位：防止"旧版 MindustryX + 新版 Neon"这类半兼容组合静默出错。

- 常量 `MINIMUM_VERSION = "2026.04.03.B439"`。
- 判定运行环境的三条线索：系统属性 `mdtx.loader=1`、属性 `MDTX-loaded`、以及按 mod 名单（`mindustryx`/`mdtx`）和标记类（`mindustryX.VarsX`、`mindustryX.loader.Main`）探测。
- 对旧版本的处理是**显式失败**：reject 时给出升级或回退指引，而不是当作单个模块初始化失败继续跑。

## 原版回退：neoncompat.overlay

内嵌实现让纯原版玩家也有统一悬浮窗：

| 类 | 职责 |
| --- | --- |
| `neoncompat.overlay.OverlayUI` | 内嵌 Overlay 管理器本体 |
| `AdsorptionSystem` | 窗口吸附（边缘/互相吸附排版） |
| `NeonOverlayBootstrap` | 引导装配，挂齿轮按钮与快捷键入口 |

用户侧行为（默认值）：屏幕左侧齿轮按钮或快捷键 `Z` 打开 Overlay 管理器；支持 Overlay 的功能窗口统一在其中出现。这套 UI 是本地客户端界面，与其他玩家无关。

## Marker 能力的降级链

customMarker / Tripwire 这类需要放标记的功能：

1. MindustryX 环境：走 `MindustryXMarkerBridge` 使用 MDtX 标记能力；
2. 原版环境：无 MDtX 标记时不阻断功能本体——不能用桥的部分退化为 Noop 或模组自绘（各功能自行决定），绝不因此崩溃。

## 排障

- **设置页某功能说明写着"需要 MindustryX"却没接上**：先确认客户端真跑了 `mainX`（看日志中入口类）；再确认 MDtX 版本 ≥ 守卫常量。
- **窗口出现在错误位置/不吸附**：原版路径查 `AdsorptionSystem` 状态；MDtX 路径属 MDtX 自身行为。
- **桥抛 ClassNotFoundException**：多为 MDtX 更新后改了内部类名，检查 `MindustryXSchematicShareBridge` 等实现里的目标类是否仍存在。

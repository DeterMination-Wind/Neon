# 子系统：Overlay 兼容层（mdtxcompat / ocb / neoncompat）

Neon 的悬浮窗口（Overlay）需要在原版客户端与 MindustryX 两种环境下保持一致体验。实现策略为“接口在内、实现在外”：业务模块只依赖 `mdtxcompat` 中的桥接口，运行时由入口决定注入哪套实现。本页说明桥的选型逻辑、守卫机制与原版回退方案。

> [!NOTE]
> 自 2026-08 起，原版客户端的 OverlayUI 由聚合子模组 `ocb`（OverlayCompatBridge）提供：它以同 FQCN（`mindustryX.features.ui.OverlayUI`）的 Java 实现随 Neon 发布，真源位于独立仓库 `../OverlayCompatBridge`（经 `tools/submods.json` 同步）。`neoncompat.overlay` 已降级为冻结历史副本（见下文）。

## 桥接口一览

| 接口 | 方法语义 | MindustryX 实现 | 原版 / 空实现 |
| --- | --- | --- | --- |
| `OverlayUiBridge` | 创建与管理悬浮窗口 | `MindustryXOverlayUiBridge`（反射复用 `mindustryX.features.ui.OverlayUI`：真 MDtX 优先，否则命中 ocb 内嵌副本） | `NeonEmbeddedOverlayUiBridge`（冻结兜底）、`NoopOverlayUiBridge` |
| `MarkerBridge` | 在世界或小地图上放置标记 | `MindustryXMarkerBridge` | `NoopMarkerBridge`、`MarkerBridge.UNSUPPORTED` 常量 |
| `SchematicShareBridge` | 蓝图分享通道 | `MindustryXSchematicShareBridge` | `NoopSchematicShareBridge` |

消费约定：模块初始化前，聚合核心会先注入桥实例（如 `CustomMarkerFeature.configureCompat(overlayUi, markerBridge)`、`BetterScreenShotFeature.configureOverlayUi(overlayUi)`），后续一律通过持有引用调用，**严禁直接 `import mindustryX.*` 类**。

## 选型逻辑：从 `main` 入口进入的自动探测

```text
main 入口 → vanillaOverlayUi()
              ├─ LegacyMindustryXGuard.rejectLegacyMindustryX("Neon")   // 真 X 运行时误入原版 main → 抛错拒绝
              ├─ ocb-enabled=false → OverlayUiBridge.UNSUPPORTED        // 主开关关闭：整体不提供服务
              └─ OverlayUiBridge.autoDetect()                           // 运行时探测
```

- `AutoDetectingOverlayUiBridge` 封装探测结果，并在首次真正使用时锁存代理：
  - 核心类加载器可解析标记类 `mindustryX.VarsX` 或 `mindustryX.loader.Main` → 判定为真 MindustryX，选用 `MindustryXOverlayUiBridge`。
  - 否则，若 `bundledOverlayActive && external.isSupported()` 成立 → 使用 `MindustryXOverlayUiBridge` 绑定 ocb 副本。
  - 否则 → 回退至 `NeonEmbeddedOverlayUiBridge`（冻结兜底）。
- `LegacyMindustryXGuard.loadMindustryXClass()` 遵循核心优先策略：真 X 在场时仅通过 `Vars.class.getClassLoader()` 链解析（找不到即抛 `ClassNotFoundException`，绝不回退到 mod realm，防止误命中同名副本）；原版环境下才依次回退至自身加载器（Neon 的 ocb 副本）→ context → `Vars.mods.mainLoader()`（`ModClassLoader` 会遍历全部模组子加载器）→ system。
- 从 `mainX` 入口启动时无需探测，入口构造函数已直接注入确定实现。
- ocb 主类 `OverlayCompatBridgeMod`（`BekToolsMod` 初始化的首个模块）负责控制 OverlayUI 的初始化时机、`Z` 键绑定与左侧齿轮按钮。它仅通过核心标记类判定真 MindustryX，命中即休眠（`UNAVAILABLE_CLASS`）并 `detach()` 已挂载的齿轮。MindustryX 本质是扩展客户端而非名为 `mindustryx` / `mdtx` 的模组，且系统属性经常缺省；旧判据（系统属性或模组名）在“X loader 被跳过或禁用”时会误报，现已全部移除。

## ocb 主开关（`ocb-enabled`，真关断语义）

`ocb` 与其他子模组一样拥有加载期主开关（默认开启，重启生效）。**关闭后，原版客户端整体不再提供 Overlay 服务**：

- `vanillaOverlayUi()` 在 `rejectLegacyMindustryX` 检查通过后直接返回 `OverlayUiBridge.UNSUPPORTED`（no-op 桥），不再执行 `autoDetect()`：界面无齿轮按钮、无 `Z` 键响应，且所有 `registerWindow` 均为空操作。
- `ocb` 模块本身会被加载门短路，不再构造；`neoncompat.overlay` 冻结副本**不会**在主开关关闭时接管（它仅用于“ocb 构造失败”这一兜底路径）。
- MindustryX 客户端不受该开关影响：`mainX` 入口直接注入 `MindustryXOverlayUiBridge`。

实现“真关断”依赖以下两个成对前提：

1. **选型必须感知 ocb 是否真实存活**：ocb 的 `mindustryX.features.ui.OverlayUI` 与 MindustryX 同 FQCN 且始终存在于 Neon 的 JAR 中，仅凭“类能否解析”无法区分“真 MindustryX”与“未初始化的 ocb 副本”。因此 `BekToolsMod` 构造器在 `initializeModule("ocb", ...)` 之后会立即调用 `OverlayUiBridge.setBundledOverlayActive(overlayCompatBridge != null)`，确保探测锁存代理前拿到“ocb 本会话确实构造成功”的状态；真 MindustryX 仍通过核心标记类保持最高优先级。
2. **副本自身具备硬门控**：ocb 主类在确认由自身接管（`isSafeToBindNow()` 通过、即将 bind/init）时置 `serving = true`，休眠或执行 `detachBundledOverlay()` 时置回 `false`。`OverlayUI.init()` 首行检查 `overlayServing()`，为 `false` 时输出一条说明日志后直接返回。如此一来，即便消费模块直接反射调用副本的 `init()`，在主开关关闭或真 X 在场时也不会挂出齿轮按钮；`registerWindow` 不受该门控限制（窗口仍注册进未挂载的 group 并返回句柄，但永不显示）。

## 守卫：`LegacyMindustryXGuard`

职责：阻止真 MindustryX 运行时误入原版 `main` 入口，防止半兼容组合静默出错。

- `MINIMUM_VERSION = "2026.04.03.B439"` 为声明的最低版本及拒绝提示中的升级目标；当前代码**不做**运行期版本字符串比较。
- `isMindustryXRuntime()`（供 `rejectLegacyMindustryX` 调用）采用**核心标记探测**：检查 `Vars.class.getClassLoader()` 能否解析 `mindustryX.VarsX` 或 `mindustryX.loader.Main`。该判定兼容桌面 dist、Android APK 及 loader 重启后的形态，同时能将“原版 + 被跳过或禁用的 X loader 模组”正确识别为原版。
- `rejectLegacyMindustryX("Neon")` 在**原版 `main`** 探测到真 X 运行时即直接抛错并给出升级/回退指引，不会被吞并成单个模块初始化失败。正常 X 客户端走 `mainX` 不会触发此处；旧版 X 加载器误入 `main` 会被拒绝，pre-relaunch 的原版实例也不会被误判。
- `loadMindustryXClass()` 坚持核心优先（见上节）：真 X 环境只认核心加载器，绝不回退 mod realm；仅原版环境按自身 / context / `ModClassLoader` / system 顺序回退。

## 原版回退：`neoncompat.overlay`（已冻结）

`neoncompat.overlay` 是 ocb 真源并入前的**手工改包副本**（包含 `OverlayUI`、`AdsorptionSystem`、`NeonOverlayBootstrap`）。ocb 就位后，它仅在**“ocb 模块构造或初始化失败”**这一条兜底路径上被选中（`bundledOverlayActive = false`）；当 ocb 主开关关闭时整体返回 no-op 桥，不会落入此处。切勿将其与 `mindustryX.features.ui.OverlayUI` 手工同步。

| 类 | 状态 |
| --- | --- |
| `neoncompat.overlay.OverlayUI` | 冻结副本，切勿与 `mindustryX.features.ui.OverlayUI` 手工同步 |
| `neoncompat.overlay.AdsorptionSystem` | 冻结副本 |
| `neoncompat.overlay.NeonOverlayBootstrap` | 仅在兜底路径初始化；正常路径的键位与齿轮按钮由 ocb 主类提供 |

用户侧默认行为：通过屏幕左侧齿轮按钮或快捷键 `Z` 打开 Overlay 管理器，所有支持 Overlay 的悬浮窗口均在此统一管理。该套 UI 为纯本地客户端界面，其他玩家不可见。

### 窗口尺寸兜底

OverlayUI 仅在玩家拖动缩放手柄时持久化窗口 `size`，而预览模式又只在 `size` 存在时恢复尺寸。因此，若一个 `resizable` 窗口从未被手动缩放过，重启后会以 0 宽高启动，直到按 `Z` 打开编辑界面触发一次 `pack()`；由于该次 `pack()` 不会写回设置，问题每次重启都会复发。

为此，`mdtxcompat.OverlayWindowSizeSeed` 会轮询经 Neon 桥注册的窗口：当窗口可见、内容已量出尺寸且当前仍无有效尺寸时，主动调用一次 `pack()`。已经从设置或其他路径获得尺寸的窗口不会被覆盖，隐藏窗口也不会按空壳尺寸打包。该兜底仅对经 Neon 桥注册的窗口生效，直接反射调用 OverlayUI 的子模组（如 BLS）无法享受此兜底，必须自行按内容定尺寸或改走桥接口；MindustryX 自带窗口则需上游在 `Window.act()` 中做同等回退（`size == null` 时采用内容尺寸）。

模块注册窗口时应优先设为 `resizable = false`（让面板按内容自适应）；确有手动缩放需求时再开启 `resizable`，并依靠上述兜底保证首次启动具备有效尺寸。注意：将已有窗口改为 `resizable = false` 会清空已保存的 `size` 并移除编辑模式下的缩放手柄，仅适用于本就无需手调大小的面板，且发版时需在更新日志中说明。

## Marker 能力的降级链

`customMarker`、`Tripwire` 等需要放置地图标记的功能按以下策略降级：

1. **MindustryX 环境**：走 `MindustryXMarkerBridge`，复用 MDtX 的标记能力。
2. **原版环境**：无 MDtX 标记能力时不阻断功能本体；依赖桥的部分退化为 Noop 或由模块自绘（各功能自行决定），绝不因此崩溃。

## 排障指南

- **设置页标注“需要 MindustryX”但未接通**：先确认客户端确实运行了 `mainX`（检查各 X 变体行为是否生效）；若日志中出现 `LegacyMindustryXGuard` 拒绝信息，按提示升级或回退（守卫不做版本比较，不要只核对版本号）。
- **窗口重启后错位、内容挤压或背景消失**：检查该窗口是否为 `resizable` 且从未手动缩放过（`overlayUI.*` 中缺少 `size`）。Neon 注册的窗口由 `OverlayWindowSizeSeed` 兜底；MindustryX 自带窗口属于 MDtX 上游行为，按 `Z` 仅临时 `pack()`，不会持久化。
- **窗口位置异常或不吸附**：原版路径检查 `AdsorptionSystem` 状态；MDtX 路径属于 MDtX 自身行为。
- **桥接口抛出 `ClassNotFoundException`**：多为 MDtX 更新后改动了内部类名，检查 `MindustryXSchematicShareBridge` 等实现中的目标类名是否仍然存在。
- **原版环境 OverlayUI 完全缺失**：常见原因有二——一是 `ocb-enabled = false`（预期行为：本次会话无齿轮、`Z` 键及窗口，在设置中重新开启并重启即可），二是 ocb 模块初始化失败（日志会出现 `"check whether that module failed to initialize"`，此时改由冻结副本接管）。真 MindustryX 客户端不显示 Neon 的齿轮按钮同样属于预期行为（直接使用 X 自带的 OverlayUI）。

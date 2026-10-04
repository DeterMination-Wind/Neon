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
              ├─ ocb-enabled=false → OverlayUiBridge.UNSUPPORTED        // 开关关闭：整体不提供服务
              └─ OverlayUiBridge.autoDetect()                           // 运行时探测
```

- `AutoDetectingOverlayUiBridge` 包装选型结果，首次真正使用时锁存代理：
  - 核心类加载器能解析标记类 `mindustryX.VarsX` / `mindustryX.loader.Main` → 真 MindustryX，用 `MindustryXOverlayUiBridge`；
  - 否则 `bundledOverlayActive && external.isSupported()` → 用 `MindustryXOverlayUiBridge` 绑定 ocb 副本；
  - 否则 → `NeonEmbeddedOverlayUiBridge`（冻结兜底）。
- `LegacyMindustryXGuard.loadMindustryXClass()` 核心优先：真 X 在场时只用 `Vars.class.getClassLoader()` 那条链解析（找不到就抛 `ClassNotFoundException`，绝不回退到 mod realm，避免命中同名副本）；原版时按 自身加载器（Neon 的 ocb 副本）→ context → `Vars.mods.mainLoader()`（ModClassLoader 会遍历所有 mod 子加载器）→ system 回退。
- 已在 `mainX` 里时不用探测——入口构造函数已注入确定实现。
- ocb 主类 `OverlayCompatBridgeMod`（BekToolsMod 初始化的首个模块）负责 OverlayUI 的初始化时机、`Z` 键与齿轮按钮；它只以核心标记类判定真 MindustryX，命中即休眠（`UNAVAILABLE_CLASS`）并 `detach()` 已挂的齿轮。MindustryX 是客户端而不是名为 `mindustryx`/`mdtx` 的模组，系统属性也经常不在——旧判据（属性 / mod 名）在「X loader 被跳过或禁用」时会误报，已全部移除。

## ocb 的总开关（`ocb-enabled`，语义：真关断）

ocb 与其它子模组一样有加载期总开关（默认开，重启生效）。**关闭时原版客户端整体不再提供 Overlay**：

- `vanillaOverlayUi()` 在 `rejectLegacyMindustryX` 之后直接返回 `OverlayUiBridge.UNSUPPORTED`（no-op 桥），不再 `autoDetect()`：没有齿轮按钮、没有 `Z` 键、所有 `registerWindow` 为空操作；
- ocb 模块本身也被加载门短路，不会构造；`neoncompat.overlay` 冻结副本**不**在开关关闭时接管（它只负责「ocb 构造失败」这一条兜底路径）；
- MindustryX 客户端不受影响：`mainX` 入口注入 `MindustryXOverlayUiBridge`，与开关无关。

两个必须成对的前提：

1. **选型必须知道 ocb 是否真的活着**：ocb 的 `mindustryX.features.ui.OverlayUI` 与 MindustryX 同 FQCN 且始终在 Neon 的 jar 里，光靠「类能不能解析出来」分不出「真 MindustryX」和「我们那份没初始化的副本」。入口在 `initializeModule("ocb", ...)` 之后立即调 `OverlayUiBridge.setBundledOverlayActive(overlayCompatBridge != null)`（`BekToolsMod` 构造器），探测锁存代理之前拿到的是「ocb 本会话确实构造成功」。真 MindustryX 仍然优先（核心标记类）。
2. **副本自己有硬门控**：ocb 主类在确认由自己接管（`isSafeToBindNow()` 通过、即将 bind/init）时置 `serving=true`；休眠 / `detachBundledOverlay()` 时置回 false。`OverlayUI.init()` 首行检查 `overlayServing()`，false 时打一条说明日志后直接返回。这样即使消费模块直接反射调用副本的 `init()`，开关关闭或真 X 在场时也不会挂上齿轮；`registerWindow` 不受门控（窗口照常注册进未挂载的 group，拿到句柄但永不显示）。

## 守卫：LegacyMindustryXGuard

定位：防止"旧版 MindustryX + 新版 Neon"这类半兼容组合静默出错。

- 常量 `MINIMUM_VERSION = "2026.04.03.B439"`。
- `isMindustryXRuntime()`（`rejectLegacyMindustryX` 用它）= **核心标记探测**：`Vars.class.getClassLoader()` 能解析 `mindustryX.VarsX` / `mindustryX.loader.Main`。桌面 dist、Android APK、loader 重启后的形态都适用；原版 + 被跳过/被禁用的 X loader mod 正确判为原版。旧 X 全量包仍会被拦住；当前 X 走 `mainX`，pre-relaunch 的原版实例不再误报。
- `loadMindustryXClass()` 核心优先（见上节）：真 X 时只认核心那一份，绝不回退 mod realm；原版时才回退自身/context/ModClassLoader/system。
- 对旧版本的处理是**显式失败**：reject 时给出升级或回退指引，而不是当作单个模块初始化失败继续跑。

## 原版回退：neoncompat.overlay（已冻结）

`neoncompat.overlay` 是 ocb 真源并入前的**手工改包副本**（`OverlayUI`/`AdsorptionSystem`/`NeonOverlayBootstrap`）。ocb 子模块就位后，它只在**「ocb 模块构造/初始化失败」**这一条兜底路径上被选中（`bundledOverlayActive=false`）；ocb 总开关关闭时整体返回 no-op 桥，不会落到这里。勿与 `mindustryX.features.ui.OverlayUI` 手工同步。

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
- **原版环境 OverlayUI 完全缺失**：两种原因——`ocb-enabled=false`（预期行为：本次会话没有齿轮/`Z`/窗口，重启并在设置里打开即可）或 ocb 模块初始化失败（日志出现"check whether that module failed to initialize"，此时由冻结副本接管）。真 MindustryX 客户端不显示 Neon 的齿轮也是预期行为（用 X 自己的 OverlayUI）。

# src/main/java/mdtxcompat/ 代理说明

`mdtxcompat` 隔离 Neon 对 MindustryX 的依赖：三组桥接口、自动探测、版本守卫与设置迁移。
细节见 `../../../../docs/subsystems/overlay-compat.md`；实现以本目录代码为准。

## 桥接口

- `OverlayUiBridge`（悬浮窗）、`MarkerBridge`（标记）、`SchematicShareBridge`（蓝图分享）。
- 每个接口都有 Noop / `UNSUPPORTED` 默认实现；模块只依赖接口，由入口注入实现。
- 不要在 Neon 原生代码里新增 `mindustryX.*` 的编译期引用或反射探测；新增桥接能力一律走
  `mdtxcompat`。同步产物中的历史反射探测是例外，不要扩散。
- 消费模块应在初始化前接收桥（如 `configureCompat` / `configureOverlayUi`），之后只持有引用。

## 自动探测

`main` 入口的 `vanillaOverlayUi()`：

1. `LegacyMindustryXGuard.rejectLegacyMindustryX("Neon")`；
2. `ocb-enabled=false` 时直接返回 `OverlayUiBridge.UNSUPPORTED`（原版没有 Overlay）；
3. 否则 `OverlayUiBridge.autoDetect()`。

`AutoDetectingOverlayUiBridge` 在首次真正使用时锁定委托：

- 核心加载器有 MindustryX 标记类 → `MindustryXOverlayUiBridge`；
- 否则 `bundledOverlayActive && external.isSupported()` → 绑定 ocb 的同 FQCN 副本；
- 否则 → `NeonEmbeddedOverlayUiBridge`（冻结兜底）。

`BekToolsMod` 在 ocb 初始化后调用 `OverlayUiBridge.setBundledOverlayActive(...)`；真 X 优先，
ocb 开关关闭时冻结副本不接管。`mainX` 入口直接注入确定实现，不探测。

## LegacyMindustryXGuard

- `MINIMUM_VERSION = "2026.04.03.B439"` 是声明的最低版本，也是拒绝信息中的升级目标。
- `isMindustryXRuntime()` 只认核心加载器上的 `mindustryX.VarsX` / `mindustryX.loader.Main`
  标记类；不要用系统属性或 mod 名判断。
- `rejectLegacyMindustryX("Neon")` 在**原版 main** 探测到 X 运行时就直接抛错并给出升级/回退指引；
  它不做版本字符串比较。正常的 X 客户端应走 `mainX`，不会走到这里。
- `loadMindustryXClass()` 核心优先：真 X 在场时只认核心加载器，绝不回退 mod realm；
  原版才按自身加载器 → context → `Vars.mods.mainLoader()` → system 回退。不要直接用 `Class.forName`。

## 设置迁移与降级

- `OverlaySettingsCompat` 在原生 `overlayUI.*` 与内嵌 `neoncompat.overlayUI.*` 之间迁移窗口状态；
  改键名要保留迁移路径，避免切换运行时丢布局。
- 桥初始化失败只记录/降级，不让异常向外传播；探测失败不要每帧重试。
- `neoncompat.overlay` 是 ocb 失败时的冻结兜底，只在 `bundledOverlayActive=false` 路径使用；
  不要把它与 `mindustryX.features.ui.OverlayUI` 手工同步。
- 改桥后至少验证原版（ocb / 内嵌兜底）与 MindustryX（mainX）两条路径；无 X 时功能降级但不应崩溃。

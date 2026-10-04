> [!NOTE]
> 需要 **Mindustry v160.1+**（桌面 / Android）或对应的 MindustryX；服务器不需要安装。
> Requires **Mindustry v160.1+** (desktop / Android) or a matching MindustryX. Servers do not need it.

## 中文

### 本次修复

- **MindustryX 上不再冒出 Neon 自己的齿轮**（悬浮窗口统一交给 X 自己的 OverlayUI 管理，物流监控等窗口正常进入 X 的窗口管理器）
- **X 加载器被跳过或禁用时，Neon 正确按原版客户端工作**（悬浮窗口照常显示，而不是把入口让给一个并没有真正运行的 X）

### 本次改动

- **OverlayUI 兼容层总开关改成真关断**：关闭后原版客户端不再显示悬浮窗口（无齿轮、无 `Z` 键，窗口注册变为空操作），重启生效；内置兜底只在兼容层初始化失败时接管，MindustryX 客户端不受影响

## English

### Fixed

- **Neon no longer shows its own gear button on MindustryX** (floating windows are left to X's own OverlayUI, and windows such as the logistics monitor show up in X's window manager)
- **A skipped or disabled X loader now correctly falls back to vanilla behavior** (overlay windows keep working instead of handing control to an X runtime that is not actually active)

### Changed

- **The OverlayUI compat layer's master switch is now a real off switch**: with it off, vanilla clients show no overlay at all (no gear, no `Z` key, window registration is a no-op), restart to apply; the built-in fallback only takes over when the compat layer fails to initialize, and MindustryX clients are unaffected

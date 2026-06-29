# Neon Release Notes

## v10.0.0

### 中文

- Neon 现在内置 OverlayUI 兼容层，在非 MindustryX 客户端中也能直接提供 Overlay 窗口编辑器，无需再额外安装单独的 overlay 兼容模组。
- OverlayUI 齿轮按钮固定在屏幕左侧中部，更容易发现；窗口锁定图标切换逻辑修复，开关状态与图标显示保持一致。
- 内置兼容层补充了更稳的自动初始化与恢复逻辑，避免窗口已注册但编辑器未真正挂到界面的情况。
- README 与发布说明同步更新，版本号提升到 `v10.0.0`。

### English

- Neon now ships with a built-in OverlayUI compatibility layer, so vanilla-style clients can use overlay windows without installing a separate overlay bridge mod.
- The OverlayUI gear button is now fixed at the left-middle edge of the screen, and the lock icon state now switches correctly with the actual pinned state.
- The embedded compatibility layer now performs more reliable auto-initialization and recovery, preventing cases where windows were registered but the editor never attached to the scene.
- README and release notes were refreshed, and the package version is now `v10.0.0`.

---

## v9.0.2

### 中文

- 补全 Neon 设置页里仍显示英文的模块入口名，中文环境现在会显示对应中文标题。
- `betterMiniMap` 的开关标题改为直接显示“启用”。
- 保持 `BetterScreenShot` 中文标题逻辑不变，并沿用 GitHub Release workflow 的 JDK 修复。

### English

- Filled in the remaining English module titles in the Neon settings page so Chinese users now see localized section names.
- Changed the `betterMiniMap` toggle title to plain `Enable` in the translated UI.
- Kept the BetterScreenShot Chinese-title behavior and the GitHub Release workflow JDK fix.

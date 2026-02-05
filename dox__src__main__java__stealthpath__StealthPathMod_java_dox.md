# DOX: `src/main/java/stealthpath/StealthPathMod.java`

- 类型 / Type: **Java source**
- 作用 / Purpose: Mod implementation source code.

## 包与类型 / Package & Types
- `package`: `stealthpath`
- 声明的类型 / Declared types:
  - `StealthPathMod`
  - `PreferAnySize`
  - `MindustryXOverlayUI`

## 对外接口 / Public & Protected API
- `public` 字段/常量:
  - `public static boolean bekBundled`
- `public` 方法:
  - `public StealthPathMod()`
  - `public void bekBuildSettings(SettingsMenuDialog.SettingsTable table)`
  - `public float getMinWidth()`
  - `public float getPrefWidth()`
  - `public float getMinHeight()`
  - `public float getPrefHeight()`

## 维护要点 / Maintainer Notes
- 包含 `bekBuildSettings`：供 Neon 整合设置页调用。
- 包含 OverlayUI（MindustryX）集成：窗口可由 OverlayUI 管理。
- 包含 Trigger 回调：在 update/draw 阶段执行逻辑。

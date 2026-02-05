# DOX: `src/main/java/powergridminimap/PowerGridMinimapMod.java`

- 类型 / Type: **Java source**
- 作用 / Purpose: Mod implementation source code.

## 包与类型 / Package & Types
- `package`: `powergridminimap`
- 声明的类型 / Declared types:
  - `PowerGridMinimapMod`
  - `PgmmConsoleApi`
  - `MinimapOverlay`
  - `Mi2MinimapIntegration`
  - `Mi2Overlay`
  - `PowerGridCache`
  - `ClusterInfo`
  - `ReconnectHint`
  - `RescueCutHint`
  - `ImpactDisableHint`
  - `SplitAlert`
  - `RescueAlert`
  - `PowerTableOverlay`
  - `MindustryXOverlayUI`
  - `SplitWatcher`
  - `PendingSplit`
  - `RescueAdvisor`
  - `BalanceWindow`
  - `ComponentStats`
  - `ReconnectResult`
  - `MindustryXMarkers`

## 对外接口 / Public & Protected API
- `public` 字段/常量:
  - `public static boolean bekBundled`
- `public` 方法:
  - `public PowerGridMinimapMod()`
  - `public MinimapOverlay(Element base, PowerGridCache cache, Color markerColor, SplitAlert alert, RescueAlert rescueAlert, Color rescueColor)`
  - `public void registerClientCommands(CommandHandler handler)`
  - `public String help()`
  - `public String restart()`
  - `public String rescan()`
  - `public String mi2Refresh()`
  - `public String mi2On()`
  - `public String mi2Off()`
  - `public void restartMod()`
  - `public void rescanNow()`
  - `public void refreshMi2Overlay(boolean forceRedetect)`
  - `public void bekBuildSettings(SettingsMenuDialog.SettingsTable table)`
  - `public void act(float delta)`
  - `public void draw()`
  - `public void clear()`
  - `public void invalidateAll()`
  - `public void forceRebuildNow()`
  - `public void updateBasic()`
  - `public void updateFullOverlay()`
  - `public Texture getFullOverlayTexture()`
  - `public float getMinWidth()`
  - `public float getMinHeight()`
  - `public float getPrefWidth()`
  - `public float getPrefHeight()`

## 维护要点 / Maintainer Notes
- 包含 `registerClientCommands`：提供客户端命令入口。
- 包含 `bekBuildSettings`：供 Neon 整合设置页调用。
- 包含 OverlayUI（MindustryX）集成：窗口可由 OverlayUI 管理。
- 包含 Trigger 回调：在 update/draw 阶段执行逻辑。

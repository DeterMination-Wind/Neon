# DOX: `src/main/java/radialbuildmenu/RadialBuildMenuMod.java`

- 类型 / Type: **Java source**
- 作用 / Purpose: Mod implementation source code.

## 包与类型 / Package & Types
- `package`: `radialbuildmenu`
- 声明的类型 / Declared types:
  - `RadialBuildMenuMod`
  - `HeaderSetting`
  - `HotkeySetting`
  - `SlotSetting`
  - `HudColorSetting`
  - `CollapsiblePlanetSetting`
  - `TimeMinutesSetting`
  - `ConditionalSwitchSetting`
  - `IoSetting`
  - `RadialHud`
  - `MindustryXOverlayUI`

## 对外接口 / Public & Protected API
- `public` 字段/常量:
  - `public static boolean bekBundled`
  - `public final KeyBind radialMenu`
- `public` 方法:
  - `public RadialBuildMenuMod()`
  - `public HeaderSetting(String title, arc.scene.style.Drawable icon)`
  - `public HotkeySetting()`
  - `public SlotSetting(int slot, String prefix, String titleKey)`
  - `public HudColorSetting()`
  - `public CollapsiblePlanetSetting(String titleText, arc.scene.style.Drawable icon, String openKey, arc.func.Cons<SettingsMenuDialog.SettingsTable> builder, arc.func.Prov<Color> accent)`
  - `public TimeMinutesSetting()`
  - `public ConditionalSwitchSetting(RadialBuildMenuMod mod)`
  - `public IoSetting()`
  - `public RadialHud(RadialBuildMenuMod mod)`
  - `public void bekBuildSettings(SettingsMenuDialog.SettingsTable table)`
  - `public void add(SettingsMenuDialog.SettingsTable table)`
  - `public boolean touchDown(InputEvent event, float x, float y, int pointer, KeyCode button)`
  - `public void act(float delta)`
  - `public void draw()`

## 维护要点 / Maintainer Notes
- 包含 `bekBuildSettings`：供 BEK-Tools 整合设置页调用。
- 包含 OverlayUI（MindustryX）集成：窗口可由 OverlayUI 管理。

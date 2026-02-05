# DOX: `src/main/java/bektools/BekToolsMod.java`

- 类型 / Type: **Java source**
- 作用 / Purpose: Mod implementation source code.

## 包与类型 / Package & Types
- `package`: `bektools`
- 声明的类型 / Declared types:
  - `BekToolsMod`
  - `GroupSetting`
  - `NestedSettingsTable`

## 对外接口 / Public & Protected API
- `public` 方法:
  - `public BekToolsMod()`
  - `public GroupSetting(float indent, arc.func.Cons<SettingsMenuDialog.SettingsTable> builder)`
  - `public NestedSettingsTable(float indent)`
  - `public void registerClientCommands(CommandHandler handler)`
  - `public void add(SettingsMenuDialog.SettingsTable table)`
  - `public void rebuild()`
  - `public void finishBuild()`

## 维护要点 / Maintainer Notes
- 包含 `registerClientCommands`：提供客户端命令入口。
- 包含 `bekBuildSettings`：供 Neon 整合设置页调用。

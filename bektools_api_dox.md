# Neon 接口说明（API dox）

本文件描述 Neon 对外提供的“接口”：设置入口、子模组聚合方式、以及对整合包维护者有用的内部约定（`bekBundled`、`bekBuildSettings` 等）。

## 1. 玩家接口

### 1.1 设置入口

Neon 在设置中新增一个一级分类（见 `bektools/BekToolsMod.java`）：

- 分类名：`@bektools.category`
- 分类 icon：`Icon.settings`

并在该分类中分组展示 3 个子模组的设置：

- PGMM：`Icon.power`
- StealthPath：`Icon.map`
- RBM：`Icon.list`

### 1.2 热键/功能

热键与功能全部来自三子模组本身；Neon 不会重定义它们，只做“打包与聚合设置”。

## 2. 代码聚合接口（维护者约定）

### 2.1 `bekBundled`

由 `tools/update_submods.py` 注入到子模组主类中：

- `public static boolean bekBundled = false;`

Neon 在构造函数中把三个子模组的该标记设为 `true`：

- `PowerGridMinimapMod.bekBundled = true;`
- `StealthPathMod.bekBundled = true;`
- `RadialBuildMenuMod.bekBundled = true;`

用途：当子模组被打包进 Neon 时，子模组应当避免重复注册自己的设置分类等（以免出现多个入口/重复 UI）。

### 2.2 `bekBuildSettings(SettingsMenuDialog.SettingsTable table)`

`tools/update_submods.py` 会把子模组原本的 `ui.settings.addCategory(..., table -> { ... })` builder 提取为：

- `public void bekBuildSettings(SettingsMenuDialog.SettingsTable table){ ... }`

Neon 使用该方法把三个子模组的设置内容插入到自己的一级分类里。

### 2.3 子模组 client commands 转发

`BekToolsMod.registerClientCommands(...)` 会将命令注册转发给三子模组的 `registerClientCommands`，保证整合包模式下命令仍可用。

## 3. OverlayUI 等可选依赖

各子模组对 MindustryX 的可选集成保持原样（通常使用反射以避免未安装 MindustryX 时崩溃）。Neon 不统一管理 OverlayUI 窗口，只负责把代码打包在一起。


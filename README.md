# Neon / 氖 (Mindustry Mod)
<h1 align="center">
  <a href="https://github.com/DeterMination-Wind/Neon/releases/latest"><img src="https://img.shields.io/github/v/release/DeterMination-Wind/Neon?display_name=release&label=Latest%20Release&color=green"></a>
  <a href="https://github.com/DeterMination-Wind/Neon/releases"><img src="https://img.shields.io/github/downloads/DeterMination-Wind/Neon/total?label=Downloads&color=blue"></a>
  <a href="LICENSE"><img src="https://img.shields.io/github/license/DeterMination-Wind/Neon?label=License"></a>
  <a href="https://github.com/DeterMination-Wind/Neon"><img src="https://img.shields.io/github/stars/DeterMination-Wind/Neon?style=flat&label=Star%20this%20mod!&color=yellow"></a>
</h1>

- [中文](#中文)
- [English](#english)

## 中文

> 看得更清楚，操作更顺手，问题更容易查清。

### Neon 是什么

Neon 是 Mindustry 的客户端辅助工作台。

它把日常游玩、建造、联机、制图和逻辑制作中常用的工具集中到一个模组里：让重要信息更容易看见，让重复操作更顺手，让复杂问题更容易定位。

Neon 的价值不在于堆叠功能，而在于把原本分散的辅助工具用一个安装包、一个设置入口和统一的悬浮界面管理起来。你可以按自己的玩法启用观察、建造、单位控制、地图编辑、逻辑排错、信息查询和交流辅助，而不必单独安装和维护一长串模组。

Neon 是纯客户端模组，不需要服务器安装，适合单机和多人游戏。它不增加新的方块、单位或资源，主要帮助你减少界面切换、重复操作、误判和信息盲区。

### 为什么使用 Neon

- **看清局面**：把电网、物流、单位、投影和威胁等重要信息直接放在游戏画面里。
- **减少操作**：让建造、编队、单位控制、地图编辑和截图更符合自己的工作流。
- **查明原因**：帮助定位逻辑引用、数据补丁、玩家信息和聊天内容中的问题。
- **一次安装，统一管理**：大多数功能可以按需启用，设置、悬浮窗口和更新入口集中在 Neon 中。

### 快速开始

1. 从 [Releases](https://github.com/DeterMination-Wind/Neon/releases) 下载 `Neon.zip`，放入 Mindustry 的 `mods` 目录并在游戏内启用。
2. 在 `设置 → 控制` 中按照需要调整 Neon 提供的快捷键。
3. 在 `设置 → 模组 → Neon` 中按分组启用和配置功能。
4. 支持悬浮窗口的功能会出现在统一的 Overlay 管理器中，可使用屏幕左侧的齿轮按钮或默认快捷键 `Z` 打开。

Neon 已经包含相关独立模组的功能。请不要同时启用对应的独立版本，否则可能出现重复功能或界面冲突。

### 兼容性

- 需要 Mindustry v159 或更高版本。
- Neon 为纯客户端模组，服务器不需要安装。
- 原版客户端可以使用核心功能；安装 MindustryX 或提供兼容接口的环境后，支持的功能可以接入相应的 OverlayUI 和标记能力。
- 安卓端请使用 Release 中包含 `classes.dex` 的 `Neon.jar`，不要使用桌面构建的中间文件。

### 功能详情

完整功能说明、使用场景和术语解释请查看 [FEATURES.md](FEATURES.md)。

### 反馈

- [BEK 辅助 mod 反馈群](https://qm.qq.com/q/cZWzPa4cTu)

### 开发者构建

在项目目录执行：

```powershell
.\gradlew.bat clean deploy
```

主要输出：

- `dist/Neon.zip`
- `dist/Neon.jar`
- `../构建/Neon/Neon-<version>.zip`
- `../构建/Neon/Neon-<version>.jar`

## English

> See more clearly. Build with less friction. Troubleshoot with less guesswork.

### What is Neon?

Neon is a client-side companion toolkit for Mindustry.

It brings together the tools commonly needed for playing, building, multiplayer, map making, and logic work. Important information becomes easier to see, repetitive actions become easier to perform, and complicated problems become easier to trace.

Neon is not just a pile of features. It gives a set of focused client-side tools one package, one settings entry, and one shared overlay workflow. Enable the parts that fit your play style without installing and maintaining a long chain of separate mods.

Neon does not require server installation and works well in both singleplayer and multiplayer. It does not add new blocks, units, or resources; it improves the information and workflows around the game.

### Why use Neon?

- **See the situation**: surface useful power, logistics, unit, projector, and threat information in-game.
- **Work with less friction**: streamline building, formations, unit control, map editing, and screenshots.
- **Find the cause**: trace logic references, datapatch changes, player records, and chat-related problems.
- **Install once, manage centrally**: most features can be enabled as needed, with shared settings, overlays, and update entry points.

### Quick start

1. Download `Neon.zip` from [Releases](https://github.com/DeterMination-Wind/Neon/releases), put it in Mindustry's `mods` folder, and enable it in-game.
2. Rebind the Neon keybinds you need in `Settings -> Controls`.
3. Configure features in `Settings -> Mods -> Neon`.
4. Overlay-capable features appear in the shared Overlay manager. Open it with the gear button on the left side of the screen or the default `Z` hotkey.

Neon already contains the functionality of the related standalone mods. Do not enable their standalone versions at the same time, or duplicate features and UI conflicts may occur.

### Compatibility

- Requires Mindustry v159 or later.
- Neon is fully client-side; servers do not need to install it.
- Core features work on vanilla clients. With MindustryX or a compatible bridge available, supported features can use the corresponding OverlayUI and marker integrations.
- On Android, use the release `Neon.jar` that contains `classes.dex`, not a desktop-only or intermediate build.

### Feature details

See [FEATURES.md](FEATURES.md) for the complete feature guide, use cases, and terminology.

### Feedback

- [Discord feedback channel](https://discord.com/channels/391020510269669376/1467903894716940522)

### Development

From the project directory:

```powershell
.\gradlew.bat clean deploy
```

Main outputs:

- `dist/Neon.zip`
- `dist/Neon.jar`
- `../构建/Neon/Neon-<version>.zip`
- `../构建/Neon/Neon-<version>.jar`

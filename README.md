# Neon / 氖 (Mindustry Mod)

<p align="center">
  <a href="https://github.com/DeterMination-Wind/Neon/releases/latest"><img src="https://img.shields.io/github/v/release/DeterMination-Wind/Neon?display_name=release&label=Latest%20Release&color=green"></a>
  <a href="https://github.com/DeterMination-Wind/Neon/releases"><img src="https://img.shields.io/github/downloads/DeterMination-Wind/Neon/total?label=Downloads&color=blue"></a>
  <a href="LICENSE"><img src="https://img.shields.io/github/license/DeterMination-Wind/Neon?label=License"></a>
  <a href="https://github.com/DeterMination-Wind/Neon"><img src="https://img.shields.io/github/stars/DeterMination-Wind/Neon?style=flat&label=Star%20this%20mod!&color=yellow"></a>
</p>

> [!NOTE]
> 若有反馈，请发送邮件至 bugsoc@qq.com / If you have any feedback, please email bugsoc@qq.com.

- [中文](#中文)
- [English](#english)

## 中文

> 看得更清楚，操作更顺手，问题更容易查清。

### Neon 是什么

Neon 是 Mindustry 的客户端辅助工作台。

它把日常游玩、建造、联机、制图和逻辑制作中常用的工具整合进同一个模组，让重要信息更直观，让重复操作更顺手，让复杂问题更容易定位。

Neon 的价值不在于堆砌功能，而在于把原本分散的辅助工具收拢到同一个安装包、同一个设置入口和统一的悬浮窗口工作流中。你可以按自己的游玩习惯启用观察、建造、单位控制、地图编辑、逻辑排错、信息查询和交流辅助，无需逐个安装和维护一长串独立模组。

Neon 为纯客户端模组，无需服务器安装，适用于单机和多人游戏。它不新增方块、单位或资源，专注于减少界面切换、重复操作、误判与信息盲区。

### 为什么使用 Neon

- **看清局面**：将电网、物流、单位、投影和威胁等关键信息直接呈现在游戏画面中。
- **减少操作**：让建造、编队、单位控制、地图编辑和截图更贴合你的工作流。
- **查明原因**：快速定位逻辑引用、datapatch 变更、玩家记录和聊天内容中的问题。
- **一次安装，统一管理**：大多数功能可按需开关，设置、悬浮窗口和更新入口全部集中在 Neon 中。

### 快速开始

1. 从 [Releases](https://github.com/DeterMination-Wind/Neon/releases) 下载 `Neon.zip`，放入 Mindustry 的 `mods` 目录并在游戏内启用。
2. 在 `设置 → 控制` 中按需调整 Neon 提供的快捷键。
3. 在 `设置 → 模组 → Neon` 中按分组启用和配置功能。
4. 支持悬浮窗口的功能会统一出现在 Overlay 管理器中，可通过屏幕左侧的齿轮按钮或默认快捷键 `Z` 打开。

> [!WARNING]
> Neon 已包含相关独立子模组的功能。请勿同时启用对应的独立版本，否则可能导致功能重复或界面冲突。

### 兼容性

- 需要 Mindustry v160.1 或更高版本。
- Neon 为纯客户端模组，服务器无需安装。
- 原版客户端可正常使用核心功能；在 MindustryX 或提供兼容桥接的环境中，支持的功能会自动接入对应的 OverlayUI 与标记能力。
- Android 端请使用 Release 中包含 `classes.dex` 的 `Neon.jar`，切勿使用桌面构建的中间产物。

### 功能详情

完整功能说明、使用场景与术语解释见 [FEATURES.md](FEATURES.md)。

分类文档（架构、开发、测试、发布与用户指南）见 [docs/](docs/README.md)。

### 反馈

- [BEK 辅助 mod 反馈群](https://qm.qq.com/q/cZWzPa4cTu)

### 开发者构建

在项目根目录执行：

```powershell
.\gradlew.bat clean deploy
```

主要构建产物：

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

Neon does not require server installation and works in both singleplayer and multiplayer. It does not add new blocks, units, or resources; instead, it streamlines the information and workflows around the game.

### Why use Neon?

- **See the situation**: surface useful power, logistics, unit, projector, and threat information directly in-game.
- **Work with less friction**: streamline building, formations, unit control, map editing, and screenshots.
- **Find the cause**: trace logic references, datapatch changes, player records, and chat-related issues.
- **Install once, manage centrally**: enable most features on demand, with shared settings, overlays, and update entry points.

### Quick start

1. Download `Neon.zip` from [Releases](https://github.com/DeterMination-Wind/Neon/releases), place it in Mindustry's `mods` folder, and enable it in-game.
2. Rebind the Neon keybinds you need in `Settings -> Controls`.
3. Configure features in `Settings -> Mods -> Neon`.
4. Overlay-capable features appear in the shared Overlay manager. Open it with the gear button on the left side of the screen or the default `Z` keybind.

> [!WARNING]
> Neon already includes the functionality of the bundled standalone mods. Do not enable their standalone versions at the same time, or duplicate features and UI conflicts may occur.

### Compatibility

- Requires Mindustry v160.1 or later.
- Neon is fully client-side; servers do not need to install it.
- Core features work on vanilla clients. When MindustryX or a compatible bridge is present, supported features use the corresponding OverlayUI and marker integrations.
- On Android, use the release `Neon.jar` that includes `classes.dex`, not a desktop-only or intermediate build.

### Feature details

See [FEATURES.md](FEATURES.md) for the complete feature guide, use cases, and terminology. Categorized documentation (architecture, development, testing, release, and user guide) lives under [docs/](docs/README.md).

### Feedback

- [Discord feedback channel](https://discord.com/channels/391020510269669376/1467903894716940522)

### Development

From the project root directory:

```powershell
.\gradlew.bat clean deploy
```

Main outputs:

- `dist/Neon.zip`
- `dist/Neon.jar`
- `../构建/Neon/Neon-<version>.zip`
- `../构建/Neon/Neon-<version>.jar`

# Neon / 氖 (Mindustry Mod)

<p align="center">
  <a href="https://github.com/DeterMination-Wind/Neon/releases/latest"><img src="https://img.shields.io/github/v/release/DeterMination-Wind/Neon?display_name=release&label=Latest%20Release&color=green"></a>
  <a href="https://github.com/DeterMination-Wind/Neon/releases"><img src="https://img.shields.io/github/downloads/DeterMination-Wind/Neon/total?label=Downloads&color=blue"></a>
  <a href="LICENSE"><img src="https://img.shields.io/github/license/DeterMination-Wind/Neon?label=License"></a>
  <a href="https://github.com/DeterMination-Wind/Neon"><img src="https://img.shields.io/github/stars/DeterMination-Wind/Neon?style=flat&label=Star%20this%20mod!&color=yellow"></a>
</p>

> [!NOTE]
> 使用中遇到问题或有功能建议，欢迎发送邮件至 bugsoc@qq.com 反馈。
> Have feedback or found a bug? Please email bugsoc@qq.com.

- [中文](#中文)
- [English](#english)

---

## 中文

> **战场局势一目了然，日常操作行云流水，疑难问题一查到底。**

### Neon 是什么

**Neon** 是一款专为 Mindustry 打造的**纯客户端全能辅助工具箱**。

无论你是专注前线作战、热衷蓝图建造、喜欢联机开黑，还是钻研地图制作与逻辑编程，Neon 都能把原本分散在各处的实用辅助工具收拢到**同一个模组**中——一个安装包、一个统一设置入口、一套清爽的悬浮窗口工作流，无需再逐个安装和维护一长串独立子模组。

它不会改动游戏的任何原版内容（不新增方块、单位或资源），也**无需服务器安装**。你只需按需开启顺手的功能，就能在单机与多人联机中减少界面切换、重复操作、误判与信息盲区。

### 为什么选择 Neon

- **洞察全局，告别盲区**：将电网负载、物流走向、单位状态、建筑投影与敌方威胁范围直接呈现在画面上，关键信息一目了然。
- **简化操作，手感顺畅**：优化建筑施工、单位编队与微操、地图编辑以及游戏截图流程，减少重复点击，让操作更贴合你的习惯。
- **精准排错，有据可查**：快速定位逻辑引用、datapatch 变更、玩家历史记录与聊天信息中的异常，让复杂问题更易查明原因。
- **一次安装，统一管理**：大多数功能模块均可按需开关，设置选项、悬浮窗口与版本更新入口全部集中在 Neon 中统一管理。

### 快速开始

1. **安装模组**：前往 [Releases](https://github.com/DeterMination-Wind/Neon/releases) 下载 `Neon.zip`（**Android 端请下载 `Neon.jar`**），放入 Mindustry 的 `mods` 目录并在游戏内启用。
2. **自定义按键**：进入 `设置 → 控制`，按个人习惯调整 Neon 提供的快捷键。
3. **按需开启功能**：进入 `设置 → 模组 → Neon`，按分组启用和配置对应子模组。
4. **呼出悬浮窗口**：支持悬浮窗口的功能会统一出现在 **Overlay 管理器**中，点击屏幕左侧的**齿轮图标**或按下默认快捷键 `Z` 即可随时打开。

> [!WARNING]
> **防冲突提示**：Neon 已包含相关独立子模组的功能。启用 Neon 时，请勿同时启用对应的独立版本，否则可能导致功能重复或界面冲突。

### 兼容性说明

- **游戏版本**：需要 **Mindustry v160.1** 或更高版本。
- **联机支持**：纯客户端模组，加入任意原版或第三方服务器均可正常使用，无需服务端安装。
- **环境适配**：原版客户端即可使用全部核心功能；在 **MindustryX** 或提供兼容桥接的环境中，支持的功能会自动接入对应的 OverlayUI 与标记增强能力。
- **Android 端须知**：Android 设备请务必下载 Release 中包含 `classes.dex` 的 `Neon.jar`，切勿使用桌面构建的中间产物。

### 功能详情

- **完整功能手册**：全部子模组的详细用法、适用场景与术语说明见 [FEATURES.md](FEATURES.md)。
- **进阶与开发文档**：架构设计、开发指南、测试、发布与用户指南分类目录见 [docs/](docs/README.md)。

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

---

## English

> **See the battlefield clearly. Build effortlessly. Troubleshoot without guesswork.**

### What is Neon?

**Neon** is an all-in-one, **client-side companion toolkit** for Mindustry.

Whether you are fighting on the frontlines, designing blueprints, playing multiplayer, crafting maps, or wiring up logic processors, Neon brings your essential utility tools together into **a single mod**—one package, one unified settings entry, and one shared overlay workflow, without installing and maintaining a long chain of separate standalone mods.

Neon adds no new blocks, units, or resources and **requires zero server-side installation**. Enable only the tools that fit your playstyle to reduce menu switching, repetitive clicks, and blind spots across both singleplayer and multiplayer.

### Why use Neon?

- **See the whole picture**: Surface critical power grid stats, logistics flows, unit statuses, projector ranges, and incoming threats directly in-game.
- **Work with less friction**: Streamline building, unit formations, micro-control, map editing, and screenshots with workflows designed to save clicks.
- **Troubleshoot with confidence**: Quickly trace logic references, datapatch changes, player records, and chat-related issues instead of guessing what went wrong.
- **Install once, manage centrally**: Toggle most modules on demand, with shared settings, floating overlays, and update entry points managed in one place.

### Quick Start

1. **Install**: Download `Neon.zip` from [Releases](https://github.com/DeterMination-Wind/Neon/releases) (**Android users: download `Neon.jar`**), place it in Mindustry's `mods` folder, and enable it in-game.
2. **Set your hotkeys**: Open `Settings -> Controls` to customize Neon's keybinds as needed.
3. **Pick your tools**: Open `Settings -> Mods -> Neon` to enable and configure features by category.
4. **Open the Overlay**: Overlay-capable features appear in the shared **Overlay Manager**—click the **gear icon** on the left edge of the screen or press the default `Z` keybind anytime.

> [!WARNING]
> **Avoid Mod Conflicts**: Neon already includes the functionality of the bundled standalone mods. Do not enable their standalone versions alongside Neon, or duplicate features and UI conflicts may occur.

### Compatibility

- **Game Version**: Requires **Mindustry v160.1** or later.
- **Multiplayer Ready**: Fully client-side; servers do not need to install it.
- **Client Integrations**: Core features work out of the box on vanilla clients. When **MindustryX** or a compatible bridge is present, supported features automatically hook into the corresponding OverlayUI and marker capabilities.
- **Android Note**: On Android, use the official release `Neon.jar` containing `classes.dex`, not a desktop-only or intermediate build.

### Feature Details

- **Full Feature Guide**: See [FEATURES.md](FEATURES.md) for detailed breakdowns, use cases, and terminology.
- **Documentation**: Browse categorized guides (architecture, development, testing, release, and user guide) in [docs/](docs/README.md).

### Feedback

- [Discord feedback channel](https://discord.com/channels/391020510269669376/1467903894716940522)

### Development

From the project root directory:

```powershell
.\gradlew.bat clean deploy
```

Main build outputs:

- `dist/Neon.zip`
- `dist/Neon.jar`
- `../构建/Neon/Neon-<version>.zip`
- `../构建/Neon/Neon-<version>.jar`

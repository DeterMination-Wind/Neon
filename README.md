# Neon / 氖

> [!NOTE]
> 使用中遇到问题或有功能建议？欢迎发送邮件至 bugsoc@qq.com 反馈！
> Have feedback or found a bug? Feel free to reach out at bugsoc@qq.com!

* [中文](https://www.google.com/search?q=%23%E4%B8%AD%E6%96%87)
* [English](https://www.google.com/search?q=%23english)

---

## 中文

> **战场局势一目了然，日常操作行云流水，疑难问题一查到底。**

### Neon 是什么？

**Neon** 是一款专为 Mindustry 打造的**纯客户端全能辅助工具箱**。

无论你是专注前线作战、热衷蓝图建造、喜欢联机开黑，还是钻研地图制作与逻辑编程，Neon 都能把分散在各处的实用辅助功能整合进**同一个模组**中——告别翻找十几个独立 Mod 的烦恼，一个安装包、一套统一设置、一个清爽的悬浮窗即可全部搞定。

它不会改动游戏的任何原版内容（不新增方块、单位或资源），也**无需服务器安装**。你只需要按需开启自己顺手的功能，就能在单人与多人联机中获得更清晰的战场视野和更流畅的操作体验。

### 为什么选择 Neon？

* **洞察全局，告别盲区**：将电网负载、物流走向、单位状态、建筑投影及敌方威胁范围直接呈现在画面上，关键信息不用再靠猜。
* **简化操作，手感拉满**：优化建筑施工、单位编队与微操、地图编辑以及游戏截图流程，减少重复点击，让操作更贴合你的习惯。
* **精准排错，有据可查**：快速定位逻辑连接引用、数据补丁变动、玩家历史记录与聊天信息中的异常，不再对着复杂工程抓瞎。
* **化零为整，随用随开**：所有功能模块均可自由开关，设置选项、悬浮面板与版本更新入口全部集中管理，既清爽又省心。

### 快速上手

1. **安装模组**：前往 [Releases 页面](https://github.com/DeterMination-Wind/Neon/releases) 下载 `Neon.zip`（**安卓玩家请下载 `Neon.jar`**），放入 Mindustry 的 `mods` 文件夹并在游戏内启用。
2. **自定义按键**：进入游戏 `设置 → 控制`，按个人习惯调整 Neon 提供的快捷键。
3. **按需开启功能**：进入 `设置 → 模组 → Neon`，根据你的游玩偏好勾选开启对应模块。
4. **呼出悬浮面板**：支持悬浮窗的功能会集中在 **Overlay 管理器**中，点击屏幕左侧的**齿轮图标**或按下默认快捷键 **`Z`** 即可随时呼出。

> [!WARNING]
> **防冲突提示**：Neon 已整合了相关独立辅助模组的功能。启用 Neon 时，**请务必关闭或移除对应的独立版本 Mod**，以免出现功能重复或界面重叠冲突。

### 兼容性说明

* **游戏版本**：需要 **Mindustry v160.1** 或更高版本。
* **联机支持**：纯客户端运行，加入任意原版或第三方服务器均可正常使用，无需服务端安装。
* **环境适配**：原版客户端即可体验全部核心功能；若同时安装了 **MindustryX** 或提供兼容接口的客户端，支持的功能还将自动解锁对应的 OverlayUI 与地图标记增强效果。
* **安卓端须知**：安卓设备请务必下载 Release 中包含 `classes.dex` 的 `Neon.jar`，切勿使用桌面版构建的中间文件。

### 了解更多与反馈

*  **完整功能手册**：想了解每个模块的详细用法与术语说明？请查阅 [FEATURES.md](FEATURES.md)。
*  **进阶与开发文档**：架构设计、测试及使用指南分类目录见 [docs/](https://www.google.com/search?q=docs/README.md)。
*  **玩家交流群**：加入 [BEK 辅助 mod 反馈群](https://qm.qq.com/q/cZWzPa4cTu) 一起交流或反馈 Bug。

在项目根目录执行以下命令即可构建：

```powershell
.\gradlew.bat clean deploy

```

主要构建产物输出路径：

* `dist/Neon.zip`
* `dist/Neon.jar`
* `../构建/Neon/Neon-<version>.zip`
* `../构建/Neon/Neon-<version>.jar`

---

## English

> **See the battlefield clearly. Build effortlessly. Troubleshoot without guesswork.**

### What is Neon?

**Neon** is an all-in-one, **client-side companion toolkit** for Mindustry.

Whether you are fighting on the frontlines, designing blueprints, playing multiplayer, crafting maps, or wiring up logic processors, Neon brings your essential utility tools together into **a single mod**. Instead of juggling a long list of separate mods, you get one clean package, one settings menu, and one unified overlay workflow.

Neon adds no new blocks, units, or resources, and **requires zero server-side installation**. Simply toggle on the tools that match your playstyle to eliminate blind spots, cut down on repetitive clicks, and play smoother in both singleplayer and multiplayer.

### Why use Neon?

* **See the whole picture**: Surface critical power grid stats, logistics flows, unit statuses, projector ranges, and incoming threats directly on your screen.
* **Play with less friction**: Streamline building, unit formations, micro-control, map editing, and screenshots with workflows designed to save clicks.
* **Troubleshoot with confidence**: Quickly trace logic references, datapatch changes, player records, and chat logs instead of guessing what went wrong.
* **One install, total control**: Every module can be toggled on or off at will, with settings, floating overlays, and update checks managed in one place.

### Quick Start

1. **Install**: Download `Neon.zip` from [Releases](https://github.com/DeterMination-Wind/Neon/releases) (**Android users: download `Neon.jar`**), drop it into your Mindustry `mods` folder, and enable it in-game.
2. **Set your hotkeys**: Open `Settings -> Controls` to customize Neon's keybinds to your liking.
3. **Pick your tools**: Head to `Settings -> Mods -> Neon` to enable and configure features by category.
4. **Open the Overlay**: Overlay-enabled tools live in the shared **Overlay Manager**—click the **gear icon** on the left edge of your screen or press **`Z`** (default) to open it anytime.

> [!WARNING]
> **Avoid Mod Conflicts**: Neon already includes the features of its related standalone mods. **Do not run their standalone versions alongside Neon**, or you may encounter duplicate features and UI overlaps.

### Compatibility

* **Game Version**: Requires **Mindustry v160.1** or later.
* **Multiplayer Ready**: 100% client-side—works seamlessly on any server without requiring server-side installation.
* **Client Integrations**: Core features work right out of the box on vanilla clients. If you use **MindustryX** or a compatible bridge, supported features automatically hook into enhanced OverlayUI and map marker capabilities.
* **Android Note**: Please use the official release `Neon.jar` containing `classes.dex`—do not use desktop-only or intermediate build files.

### Learn More & Community

*  **Full Feature Guide**: Check out [FEATURES.md](FEATURES.md) for detailed breakdowns, use cases, and terminology.
*  **Documentation**: Browse categorized guides (user guides, architecture, testing, and releases) in [docs/](https://www.google.com/search?q=docs/README.md).
*  **Community & Support**: Join our [Discord feedback channel](https://discord.com/channels/391020510269669376/1467903894716940522) to share feedback or report issues.

Run the following command from the project root directory:

```powershell
.\gradlew.bat clean deploy

```

Main build outputs:

* `dist/Neon.zip`
* `dist/Neon.jar`
* `../构建/Neon/Neon-<version>.zip`
* `../构建/Neon/Neon-<version>.jar`

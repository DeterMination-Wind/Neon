# 用户指南

Neon 是 Mindustry 的纯客户端辅助工作台。本指南按使用场景介绍各项功能的用法与注意事项；如需逐条查阅全部功能的完整说明，也可参考 [FEATURES.md](../../FEATURES.md)。

## 场景分类

| 场景 | 页面 | 覆盖的功能 |
| --- | --- | --- |
| 战场观察 | [battlefield-awareness.md](battlefield-awareness.md) | 电网小地图、偷袭小道、增强小地图、投影叠加、物流速率增强、地理围栏报警、锁定攻击 |
| 建造与操控 | [building-and-control.md](building-and-control.md) | 圆盘快捷建造、更好的 RTS 编队、取消巡逻、Poly 建造辅助、智能放置、智能拆除、快捷键增强、建造透视 |
| 地图与逻辑 | [maps-and-logic.md](maps-and-logic.md) | 地图编辑增强、高级替换、自然地形生成、LogicSugar、谁在用这个建筑、补丁查看器、隐藏处理器显示 |
| 联机与信息 | [multiplayer-and-info.md](multiplayer-and-info.md) | 玩家数据库、自定义标记、外语服务器翻译、模组更新中心、拼音搜索支持 |
| 个性化与趣味 | [personalization.md](personalization.md) | Random 随机化、导管染色、更好的截图 |

## 通用约定

Neon 中的所有功能共用同一套入口。先了解以下约定，可以省去大部分上手困惑。

### 设置在哪里

- **总设置**：位于 `设置 → 模组 → Neon`，全部功能按分组排列，不需要的模块可随时关闭主开关（重启游戏后生效）。
- **快捷键**：位于 `设置 → 控制`，搜索对应功能名称即可改键；本指南中列出的均为默认值。

### Overlay 管理器

支持悬浮窗口的功能（如电网小地图、偷袭小道、玩家数据库等）统一收口在 Overlay 管理器中，可通过屏幕左侧的齿轮按钮或默认快捷键 `Z` 打开。悬浮窗口支持自由拖动与边缘吸附；它们仅存在于你的本地客户端，其他玩家看不到。

### 默认关闭的功能

以下功能因带有误操作风险、视觉偏好差异或存在已知缺陷，在聚合版中默认关闭，需要时可在 Neon 设置中手动开启：

| 功能 | 默认关闭的原因 |
| --- | --- |
| 偷袭小道（Stealth Path） | 当前版本仍有已知缺陷 |
| 智能拆除（AutoPruner） | 避免不了解规则时误删建筑 |
| 导管染色（Color-the-ducts） | 属于视觉偏好类功能，按个人喜好启用 |

首次手动开启后，设置会自动记住你的选择。

### 特殊入口：Random

Random 没有独立的设置分组。如果想体验随机化效果，在主菜单点击「千万别点」，下一次进入世界时即会生效。

### 安装与兼容

- 需要 Mindustry v160.1 或更高版本；从 [Releases](https://github.com/DeterMination-Wind/Neon/releases) 下载 `Neon.zip`，放入 `mods` 目录并在游戏内启用。
- 纯客户端模组：单机可用，联机时服务器无需安装。
- Android 端请使用 Release 中包含 `classes.dex` 的正式 `Neon.jar`。
- **请勿同时启用 Neon 与对应的独立版子模组**（例如单独安装一份 betterMiniMap），否则会出现界面重复或冲突。

### 反馈

- QQ 群：[BEK 辅助 mod 反馈群](https://qm.qq.com/q/cZWzPa4cTu)

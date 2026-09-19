# 用户指南

Neon 是纯客户端辅助模组。本指南按使用场景分类介绍功能的使用方式与注意点；每个功能的完整说明也可以查 [FEATURES.md](../../FEATURES.md)。

## 场景分类

| 场景 | 页面 | 覆盖的功能 |
| --- | --- | --- |
| 战场观察 | [battlefield-awareness.md](battlefield-awareness.md) | 电网小地图、偷袭小道、增强小地图、投影叠加、物流速率、地理围栏、锁定攻击 |
| 建造与操控 | [building-and-control.md](building-and-control.md) | 圆盘建造、RTS 编队、取消巡逻、Poly 辅助、智能放置/拆除、快捷键增强 |
| 地图与逻辑 | [maps-and-logic.md](maps-and-logic.md) | 地图编辑、高级替换、地形生成、LogicSugar、逻辑反查、补丁查看、隐藏处理器 |
| 联机与信息 | [multiplayer-and-info.md](multiplayer-and-info.md) | 玩家数据库、自定义标记、外语翻译、更新中心、拼音搜索 |
| 个性化与趣味 | [personalization.md](personalization.md) | Random 随机化、导管染色、高清截图 |

## 通用约定

所有功能共用同一套入口，先读这一节能省掉大部分困惑。

### 设置在哪里

- 总设置：`设置 → 模组 → Neon`，全部功能按分组排列，不需要的可以单独关闭。
- 快捷键：`设置 → 控制`，搜索对应功能名即可改键；本文档写的都是默认值。

### Overlay 管理器

支持悬浮窗的功能（电网、偷袭小道、玩家数据库等）把窗口收进统一的 Overlay 管理器：屏幕左侧的齿轮按钮，或默认快捷键 `Z` 打开。窗口可拖动、吸附；这些窗口只存在于你的客户端，别人看不到。

### 默认关闭的功能

有些功能有破坏风险或已知缺陷，聚合包里默认关闭，需要时在 Neon 设置中手动开启：

| 功能 | 默认关闭的原因 |
| --- | --- |
| 偷袭小道 | 当前版本仍有已知缺陷 |
| 智能拆除（AutoPruner） | 避免不了解规则时误删建筑 |
| 导管染色 | 视觉偏好类功能，尊重个人选择 |

首次开启后设置会记住你的选择。

### 特殊入口：Random

Random 没有设置组。想体验随机化效果，在主菜单点击"千万别点"，下次进入世界生效。

### 安装与兼容

- 需要 Mindustry v160.1+；从 [Releases](https://github.com/DeterMination-Wind/Neon/releases) 下载 `Neon.zip` 放入 `mods` 目录。
- 纯客户端模组：单机可用，联机时服务器无需安装。
- 安卓使用 Release 中含 `classes.dex` 的正式 `Neon.jar`。
- **不要同时启用 Neon 与对应的独立版模组**（如单独装一份 betterMiniMap），会出现重复界面与冲突。

### 反馈

- QQ: [BEK 辅助 mod 反馈群](https://qm.qq.com/q/cZWzPa4cTu)

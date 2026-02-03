# BEK-Tools

## 反馈 / Feedback

【BEK辅助mod反馈群】：https://qm.qq.com/q/cZWzPa4cTu

![BEK辅助mod反馈群二维码](docs/bek-feedback-group.png)

## 中文说明

BEK-Tools 是一个 Mindustry 客户端工具集（JavaMod），将以下 3 个模组合并为一个（无需分别安装）：

- Power Grid Minimap（电网小地图叠加）
- Stealth Path（偷袭小道 / 安全路径）
- Radial Build Menu（圆盘快捷建造）

### 功能一览（融合自 3 个模组的 README）

#### 1) 电网小地图 / Power Grid Minimap

在小地图/大地图上为每个电网分别着色，并在每个电网中心显示电力盈亏；支持电网断开告警与建议连接点。  
Color each disconnected power network on the minimap/full map and show its power balance at the grid center; includes split alerts and reconnect hints.

#### 2) 偷袭小道 / Stealth Path

根据敌方炮塔/单位射程计算并绘制最安全/最少受伤路线。  
Compute and draw safest/lowest-damage paths using enemy turret/unit ranges.

当前版本 / Current version: `3.0.2`

热键 / Hotkeys（可在“设置 → 控制”中查看/改绑）：

- `X` (`sp_path_turrets`)：仅敌方炮塔（长按实时预览） / turrets only (hold for live preview)
- `Y` (`sp_path_all`)：敌方炮塔 + 单位（长按实时预览） / turrets + units (hold for live preview)
- `N` (`sp_auto_mouse`)：自动模式（单位群→鼠标） / auto mode (unit cluster → mouse)
- `M` (`sp_auto_attack`)：自动模式（单位群→`<Attack>(x,y)`） / auto mode (unit cluster → `<Attack>(x,y)`)
- `K` (`sp_mode_cycle`)：点按轮流切换显示模式 / tap to cycle display mode
- `L` (`sp_threat_cycle`)：切换威胁计算（陆军/空军/一起） / cycle threat filter (ground/air/both)

自动模式要点 / Auto modes notes：

- 自动模式以“单位群中心”为起点（优先框选单位，否则玩家当前单位），自动识别空军/陆军/混合，并按 Y 模式（炮塔+单位威胁）计算最少受伤路径。  
  Auto mode starts from the unit cluster center (selected units if any, otherwise your current unit) and computes a Y-mode lowest-damage path.
- 自动模式刷新频率：**设置 → 偷袭小道 → 预览刷新间隔**。  
  Refresh rate: **Settings → Stealth Path → Preview refresh interval**.
- 攻击聊天目标格式：在聊天发送 `"<Attack>(x,y)"`（x,y 为格子坐标）；仅客户端解析你自己发出的消息。  
  Chat target: send `"<Attack>(x,y)"` (tile coords); only your own message is parsed client-side.

设置入口 / Settings: `Settings → Stealth Path`（`设置 → 偷袭小道`）

多人游戏 / Multiplayer：这是纯客户端叠加显示 mod（`mod.json` 使用 `"hidden": true` 以避免服务器/客户端 mod 不匹配检查）。

#### 3) 圆盘快捷建造 / Radial Build Menu

按住热键在鼠标周围打开圆盘 HUD，松开即可快速切换当前选中的建造建筑。  
Hold the hotkey to open a radial HUD around your cursor. Release to quickly switch the selected build block.

功能 / Features：

- 最多 16 个可配置槽位（内圈 8 + 外圈 8；外圈默认空） / Up to 16 configurable slots (8 inner + 8 outer; outer defaults empty)
- 热键可在“设置 → 控制”中绑定 / Hotkey is configurable in Settings → Controls
- HUD 可调：缩放、透明度、内圈半径、外圈半径、颜色 / Adjustable HUD: scale, opacity, inner/outer radius, color
- 仅使用 8 个槽位时支持“按方向快捷选择”（不必精确悬停图标） / With only 8 slots, supports direction-based selection
- 规则切换：按地图时长/星球切换到另一套槽位 / Rule switching by map time or planet
- 槽位配置可导出/导入（JSON） / Export/import slots config (JSON)

### 安装 / Install

将 `BEK-Tools.zip` 放入 Mindustry 的 `mods` 目录并在游戏内启用。  
Put `BEK-Tools.zip` into Mindustry's `mods` folder and enable it in-game.

### 构建

```bash
./gradlew jar
```

输出文件：`build/libs/BEK-Tools.zip`

### 同步上游更新（维护脚本）

仓库内提供 `tools/update_submods.py` 用于自动检测并合并 3 个本地子模组仓库（PGMM / StealthPath / RBM）的更新到本仓库（不访问 GitHub，直接读取本地 git HEAD）：

```bash
python tools/update_submods.py --check
python tools/update_submods.py
```

它会：

- 更新 3 个主类源码并自动重新注入 BEK-Tools 的整合钩子
- 合并 `bundles/*.properties` 并生成 `tools/submods.lock.json`（记录上游 commit）

### Release（自动构建）

推送形如 `v2.0.0` 的 tag，会触发 GitHub Actions 自动构建并发布 Release。

---

## English

BEK-Tools is a Mindustry client-side toolkit (Java mod) that bundles:

- Power Grid Minimap
- Stealth Path
- Radial Build Menu

### Feature overview (merged from the 3 mods' READMEs)

- Power Grid Minimap: color each disconnected power network on the minimap/full map, show its power balance at the grid center; includes split alerts and reconnect hints.
- Stealth Path: compute/draw safest or lowest-damage paths based on enemy turret/unit ranges (hotkeys: X/Y/N/M/K/L; settings under Settings → Stealth Path).
- Radial Build Menu: hold a hotkey to open a radial HUD to quickly switch the selected build block (up to 16 slots; configurable HUD; export/import JSON).

### Install

Put `BEK-Tools.zip` into Mindustry's `mods` folder and enable it in-game.

### Build

```bash
./gradlew jar
```

Output: `build/libs/BEK-Tools.zip`

### Sync upstream updates (maintainer script)

This repo includes `tools/update_submods.py` to auto-detect and merge updates from 3 local git checkouts (PGMM / StealthPath / RBM). It does NOT access GitHub; it reads local git HEADs:

```bash
python tools/update_submods.py --check
python tools/update_submods.py
```

It will:

- Refresh the 3 main Java sources and re-inject BEK-Tools integration hooks
- Merge `bundles/*.properties` and write `tools/submods.lock.json` (upstream commit lock)

### Release (CI)

Push a tag like `v2.0.0` to trigger GitHub Actions to build and publish a Release automatically.

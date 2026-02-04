# 🧰 BEK-Tools 更新日志 / Release Notes

## 🆕 v2.0.11
- 🧩 Radial Build Menu：合并 RBM v5.1.2（槽位组切换/显示空槽位；无需“专业模式”也能配置槽位组；修复中文设置项不显示）

## 🆕 本次版本（持续维护）
- ✅ 这个文件会作为每次 GitHub Release 的说明文本。
- 📝 需要写“本次新增/修复”时，直接在这里追加即可（保留下面的“功能总览”）。

## ✨ 功能总览（历史累计）
BEK-Tools 是一个整合包（Java Mod），将以下 3 个模块合并为一个安装包：

### ⚡ Power Grid Minimap（电网小地图）
- 🗺️ 在小地图/大地图上为每个独立电网着色，并显示电力盈亏信息。
- 🚨 电网断开提示与重连引导（减少“找不到断点”的时间）。
- ⚙️ 相关显示与刷新参数可在设置中调整。

### 🥷 StealthPath（偷袭小道）
- 🗺️ 路径叠加显示：按键计算并绘制路线（线宽/透明度/时长可调）。
- ⚔️ 威胁计算：炮塔/单位射程 + 估算 DPS → 威胁地图 → 预计受伤。
- 🧠 寻路策略：0 受伤优先、最小受伤、最近路径；可选 A* / DFS。
- 🎯 多目标核心预览：核心模式支持最近 K 个核心同时规划。
- 🤖 自动模式（单位集群）+ RTS 自动移动：支持分拆计算到 Tick，路点上限/更新间隔/指令间隔等参数。
- 🪟 OverlayUI 窗口：与 MindustryX OverlayUI 兼容（或回退 HUD）。
- 🛡️ 绕开无敌盾范围：将 `shield-projector` / `large-shield-projector` 盾域视作不可通行。

### 🧭 Radial Build Menu（圆盘快捷建造）
- 🎛️ 长按热键在鼠标周围弹出圆盘 HUD，松开即切换建造方块。
- 🧩 支持多个槽位、导入导出、HUD 尺寸/透明度/颜色等可调。
- 🧠 MindustryX 风格设置菜单体验。

## 🔔 启动检测更新（整合包）
- 🌐 开启游戏后检查 GitHub Releases 最新版本，发现更新会弹窗提示并支持一键跳转/忽略该版本。

## 🔗 链接
- GitHub Releases：https://github.com/DeterMination-Wind/BEK-Tools/releases

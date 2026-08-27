# 术语表

按主题归类的 Neon 术语。每条说明它在 Neon 语境下的准确含义。

## 项目形态

### 聚合模组（aggregate mod）
Neon 由多个原本独立的子模组合并而成。聚合后所有功能共用一个安装包、一个设置入口和一个 Overlay 管理器；子模组的存量功能与行为保持不变。

### bekBundled
子模组主类上的静态布尔标记，表示"当前运行在 Neon 聚合环境中"。为 `true` 时模块必须跳过自建设置分类，改由聚合入口接管；同步脚本会断言该约定成立。

### main / mainX
`mod.json` 中的两个入口声明。`main`（`bektools.BekToolsMod`）是原版入口；`mainX`（`bektools.BekToolsModX`）由 MindustryX 类加载器优先加载，用于替换兼容桥和模块的 X 变体。两者共享全部业务代码。

### 纯客户端（client-side）
Neon 全部功能运行在玩家自己的客户端，不要求服务器安装。涉及单位命令的功能也只走 Mindustry 原有的客户端命令路径，服务器视角不感知模组存在。

## 兼容机制

### OverlayUI
管理游戏内悬浮窗口的界面层。原版客户端使用 Neon 内嵌实现（`neoncompat.overlay`）；MindustryX 环境复用 MDtX 的 OverlayUI。悬浮窗都是本地 UI，其他玩家看不到。

### 桥接口（bridge）
`mdtxcompat` 包中隔离外部能力的接口族：`OverlayUiBridge`、`MarkerBridge`、`SchematicShareBridge` 等。模块只面向接口编程，由启动参数决定接入 MindustryX 实现、内嵌实现或 Noop 空实现。

### LegacyMindustryXGuard
MDtX 版本守卫。设定最低可用版本（`2026.04.03.B439`），检测过旧的 MindustryX 并阻止桥接，回退到内嵌 Overlay 或 Noop，避免半兼容环境崩溃。

### X 变体（ModX）
同一功能在 MindustryX 环境下的替换实现类（如 `PowerGridMinimapModX`）。仅做环境适配，业务逻辑留在基类。

## 游戏概念

### datapatch
修改游戏数据库对象/属性的补丁内容。PatchViewer 展示其前后差异。

### `getblock(x,y)` 引用
逻辑处理器不经显式链接、而在运行时按坐标读取建筑的方式。WhoUsesThisBuilding 会识别这种间接引用。

### 超速投影 / 高布 / 穹顶
Overdrive Projector 及其覆盖关系。Better Projector Overlay 用于放置前预判电力影响并标记被高布/穹顶覆盖而浪费的普通投影。

### 巡逻姿态
指挥单位的 patrol 状态。处于巡逻姿态时普通移动/攻击命令不会立即生效——Patrol Cancel 通过右键时自动清除该状态解决此问题。

## 数学与算法

### CIEDE2000（ΔE00）
感知均匀的颜色差公式，比直接比较 RGB 更接近人眼判断。AdvancedReplace 用它决定"容忍度"范围内的染色地形是否算同色：容忍度低匹配严格，高则覆盖更多近似色。它只影响匹配范围，不改最终颜色。

### 移动平均（moving average）
在最近一个时间窗口上取均值以抑制短期波动。betterLogisticsSpeed 以它计算吞吐量：窗口越长越稳但反应慢，越短越灵敏但噪声大。

## 构建与发布

### D8 / classes.dex
D8 是把 Java 字节码转成安卓 `classes.dex` 的工具。安卓端加载 Java 模组必须包含 dex；桌面中间 jar 不含它，不能分发。构建时必须带 `--lib arc-core`，否则会出设备端 `AbstractMethodError`。

### 版本码（version code）
`mod.json` 等描述文件里的数字版本：稳定版 `N12 → 120000`，预发行 `B12.5 → 120005`。详见[版本与发布](release.md)。

### bundle
Mindustry 的 i18n 文案文件（`bundle.properties` / `bundle_zh_CN.properties`）。Neon 的用户可见文案统一写在 `tools/bektools-bundles/` 下再合并进资源目录，不允许硬编码。

### 镜像（mirror）
更新中心的下载备选源。可用性取决于网络环境；下载前应自行确认来源版本。

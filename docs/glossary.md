# 术语表

本页按主题归类 Neon 的常用术语，说明每个词在 Neon 语境下的准确含义。

## 项目形态

### 聚合模组（aggregate mod）

Neon 由多个原本独立的子模组合并而成。聚合后，所有功能共用一个安装包、一个设置入口和一个 Overlay 管理器；各子模组的存量功能与行为保持不变。

### bekBundled

子模组主类上的静态布尔标记，表示“当前运行在 Neon 聚合环境中”。当其为 `true` 时，模块必须跳过自建设置分类，改由聚合入口接管；同步脚本会自动断言该约定成立。

### main / mainX

`mod.json` 中的两个入口声明。`main`（`bektools.BekToolsMod`）为原版入口；`mainX`（`bektools.BekToolsModX`）由 MindustryX 类加载器优先加载，用于替换兼容桥和模块的 X 变体。两者共享全部业务代码。

### 纯客户端（client-side）

Neon 的全部功能均运行在玩家自己的客户端上，不要求服务器安装。涉及单位命令的功能也只走 Mindustry 原有的客户端命令路径，服务器视角不会感知模组存在。

## 兼容机制

### OverlayUI

管理游戏内悬浮窗口的界面层。原版客户端默认使用 ocb 子模组提供的同 FQCN `mindustryX.features.ui.OverlayUI`；仅当 ocb 构造失败时，才回退到冻结副本 `neoncompat.overlay`。MindustryX 环境直接复用 MDtX 自带的 OverlayUI。悬浮窗口均为本地 UI，其他玩家看不到。

### 桥接口（bridge）

`mdtxcompat` 包中用于隔离外部能力的接口族，包括 `OverlayUiBridge`、`MarkerBridge`、`SchematicShareBridge` 等。功能模块只面向接口编程，由入口与运行时探测决定接入 MindustryX 实现、ocb 的 OverlayUI 副本或 Noop 空实现。

### LegacyMindustryXGuard

MindustryX 运行时守卫。`MINIMUM_VERSION`（`2026.04.03.B439`）是声明的最低版本及拒绝信息中的升级目标；它通过核心标记类探测真 MindustryX 运行时，当真 X 误入原版 `main` 入口时直接拒绝并给出升级/回退指引。该守卫**不做**版本字符串比较，也不会静默回退到内嵌 Overlay 或 Noop。

### X 变体（ModX）

同一功能在 MindustryX 环境下的替换实现类（如 `PowerGridMinimapModX`）。X 变体只负责环境适配，业务逻辑统一保留在基类中。

## 游戏概念

### datapatch

用于修改游戏数据库对象或属性的补丁内容。PatchViewer 可直观展示补丁生效前后的差异。

### `getblock(x,y)` 引用

逻辑处理器不通过显式链接，而是在运行时按坐标读取建筑的方式。WhoUsesThisBuilding 会识别此类间接引用。

### 超速投影 / 高布 / 穹顶

Overdrive Projector 及其覆盖关系。Better Projector Overlay 用于在放置前预判电力影响，并标记被高布或穹顶覆盖而浪费的普通超速投影。

### 巡逻姿态

指挥单位的 patrol 状态。处于巡逻姿态时，普通移动或攻击命令不会立即生效；Patrol Cancel 会在右键下令时自动清除该状态，解决这一问题。

## 数学与算法

### CIEDE2000（ΔE00）

感知均匀的色差计算公式，比直接比较 RGB 数值更贴近人眼判断。AdvancedReplace 用它判定“容忍度”范围内的染色地形是否属于同色：容忍度越低，匹配越严格；容忍度越高，覆盖的近似色范围越广。它只影响匹配范围，不改变最终颜色。

### 移动平均（moving average）

在最近一个连续时间窗口内取平均值以抑制短期波动。betterLogisticsSpeed 用它计算物流吞吐量：窗口越长数据越稳但反应较慢，窗口越短反馈越灵敏但噪声更大。

## 构建与发布

### D8 / classes.dex

D8 是将 Java 字节码转换为 Android `classes.dex` 的工具。Android 端加载 Java 模组必须包含 `classes.dex`，桌面中间 JAR 不含该文件，不能直接分发。构建时必须传入 `--lib arc-core`，否则会在设备端触发 `AbstractMethodError`。

### 版本码（version code）

`mod.json` 等描述文件中的数字版本号：稳定版 `N12 → 120000`，预发行版 `B12.5 → 120005`。详见 [版本与发布](release.md)。

### bundle

Mindustry 的国际化（i18n）文案文件（`bundle.properties` / `bundle_zh_CN.properties`）。Neon 的用户可见文案统一维护在 `tools/bektools-bundles/` 下，再合并到资源目录，不允许硬编码。

### 镜像（mirror）

模组更新中心的备选下载源。可用性取决于网络环境，下载前仍应自行确认来源与版本。

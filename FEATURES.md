# Neon Features / 功能详情

Neon 将多种客户端辅助工具整合在同一个模组中。本文按使用场景说明每项功能解决什么问题、适合何时使用，以及容易被忽略的技术细节。

- [中文](#中文)
- [English](#english)

## 中文

### 功能地图

| 使用场景 | 主要帮助 |
| --- | --- |
| 战场观察 | 看清电网、物流、单位、投影与潜在威胁 |
| 建造与操控 | 减少选方块、编队、控制单位与拆除建筑时的重复操作 |
| 地图与逻辑 | 改进制图流程、定位逻辑引用并审查 datapatch |
| 联机与信息 | 记录本地玩家信息，辅助翻译、标记与模组更新 |
| 个性化与趣味 | 调整界面显示、输入方式与部分视觉内容 |

所有功能均通过 Neon 的统一设置入口管理，部分功能还会出现在 Overlay 管理器中；不需要的功能可随时单独关闭。模块主开关在重启游戏后生效：关闭后的模块完全不再加载，重新开启同样需要重启游戏。

### 1. 电网小地图（Power Grid Minimap）

电网小地图将电力网络的结构与运行状态直接叠加在小地图或全屏地图上。

- 每张独立电网以不同颜色区分，便于发现断网与跨网误接。
- 在电网中心显示净电力盈亏，字号、颜色与透明度均可调节。
- 当大电网分裂并出现负电时，自动标出可能的重新连接位置。
- 缺电救援建议可提示正电岛隔离方案以及可能需要暂时停用的冲击反应堆（该功能目前为 Beta）。
- 「电力表」悬浮窗口以列表汇总各大电网的当前盈亏与近期最低值，便于快速找出最危险的电网。

它的重点不是替代原版电力界面，而是让“哪张网出了问题”和“该去哪里排查”一目了然。

### 2. 偷袭小道（Stealth Path）

偷袭小道根据地图上的威胁分布估算单位路线，并将路线绘制在世界中。

- 支持预览更安全、预计受损更少的路线。
- 支持按陆军、空军或全部威胁过滤，并可切换不同路线模式。
- 自动模式可将单位集群规划至鼠标位置或聊天坐标，并通过快捷键指挥单位沿路线移动。
- 在 MindustryX 或兼容的 OverlayUI 环境中，可打开悬浮窗口查看模式、预计伤害与控制信息。
- 线宽、透明度与显示时长均可调整。

> [!NOTE]
> 由于当前版本仍有已知缺陷，该功能在 Neon 聚合版中**默认关闭**，需要时请在 Neon 设置中手动开启。

### 3. 自定义标记（customMarker）

自定义标记用于将聊天坐标和玩家想记录的位置转化为直观易用的地图标记。

- 采用“按钮 → 全屏点位 → 面板确认”的流程，减少在地图与聊天界面间来回切换。
- 支持编辑 5 组标记模板，模板消息格式为 `<内容><内容>(x,y)`。
- 内置聊天坐标捕获窗口，可直接从聊天记录中点选坐标并快速聚焦。
- 在支持标记桥接的环境中，可接入 MindustryX 的标记能力。

### 4. 更好的截图（BetterScreenShot）

BetterScreenShot 用于生成高分辨率世界截图，适合保存地图全景、展示基地或制作攻略素材。

- 可通过默认快捷键 `F8`（可在 `设置 → 控制` 修改）或 Overlay 按钮启动。
- 对大地图采用分块渲染，避免一次性创建超出单张纹理尺寸上限的超大图片。
- 开始前可预估输出尺寸，并在渲染过程中实时显示进度状态。
- Neon 中的 BSS 核心代码源自 Miner，Neon 负责将其接入统一设置与界面。

### 5. 圆盘快捷建造（Radial Build Menu）

圆盘快捷建造将常用方块放进径向 HUD，省去在建造栏中翻找方块的时间。

- 长按热键打开圆盘，松开即选中对应方块。
- 最多支持 16 个槽位，分为内圈 8 槽与外圈 8 槽。
- 支持创建多套槽位配置，并按时间、星球或自定义条件自动切换。
- 支持 A/B 槽位组热键切换，以及缩放、透明度、半径、图标大小与展开方向调整。
- 支持通过 JSON 导入/导出配置，便于备份或分享。

### 6. 增强小地图（betterMiniMap）

betterMiniMap 用于提升小地图的战场信息密度。

- 在小地图上叠加单位与建筑图标，支持调整朝向、透明度、缩放与聚合间距。
- 敌我双方的单位与建筑可分别显示或隐藏。
- 单位与建筑均配有可搜索的筛选对话框，支持全选、清空与反选。

### 7. 玩家数据库（ServerPlayerDataBase）

玩家数据库在本地记录玩家与聊天上下文，便于长期联机或服务器管理时快速查询。

- 可记录玩家名称、UID、服务器信息以及可用的 IP 追踪结果。
- 支持可选聊天日志、导入/导出、完整性校验与异常提示。
- 在具备 OverlayUI 的环境中提供查询与调试悬浮窗口，无 OverlayUI 时自动回退为普通对话框。

> [!WARNING]
> 这些数据仅保存在客户端本地。使用时请遵守服务器规则与当地隐私法规，切勿将本地记录当作公开资料随意传播。

### 8. 地图编辑增强（betterMapEditor）

地图编辑增强用于改进地图生成器中的镜像操作。

- 在生成器预览中直接拖动镜像轴，快速调整对称位置。
- 用可拖动镜像滤镜替代固定操作，不改变原有地图生成流程。

### 9. 投影叠加（Better Projector Overlay）

投影叠加在放置超速投影前提前展示电力影响，避免放下方块后才发现电网供电不足。

- 显示放置后的电网正负预判圈与实时数值提示。
- 扫描并标记被高布或穹顶覆盖的普通超速投影。
- 可按需开启聊天提醒，及时提示发现的问题。

### 10. 物流速率增强（betterLogisticsSpeed）

原版物流瞬时数据容易受短期波动影响。物流速率增强提供更长时间窗口的吞吐量统计。

- 为建筑物流面板补充移动平均吞吐量。
- 可显示总吞吐量行，并自定义统计窗口、小数位数与显示方式。
- 较长的统计窗口能过滤单次运输或短时堵塞带来的误差，更适合判断长期产能。

### 11. 快捷键增强（betterHotKey）

快捷键增强用于扩展原版输入配置，适合有复杂建造与操作习惯的玩家。

- 支持双键组合热键与分组配置。
- 可处理地形编号冲突，并选择是否保留被禁用建筑对应的槽位。
- 提供可视化配置界面，减少直接编辑配置文件的需要。

### 12. 模组更新中心（modUpdater）

模组更新中心将多个模组的更新检查收拢到同一个界面。

- 启动时检查 GitHub Release，并在列表中区分「可更新」「已最新」「黑名单」「无仓库」四种状态。
- 支持单个更新与批量更新。
- 支持仓库覆盖、黑名单管理与镜像下载切换。
- Neon 聚合版统一收口了更新入口，避免各个子模组重复弹出更新提示。

下载镜像与第三方服务的可用性取决于网络环境，更新前仍应自行确认来源与版本。

### 13. 谁在用这个建筑（WhoUsesThisBuilding）

谁在用这个建筑用于反向排查逻辑处理器与建筑之间的依赖关系。

- 按住快捷键并悬停在建筑上时，反向高亮所有引用该建筑的逻辑处理器。
- 标签格式为 `Lxx(opcode)`，直接定位逻辑行号与引用指令类型。
- 同时支持显式处理器链接与 `getblock(x,y)` 形式的间接引用。

在大型逻辑地图中，这比逐个打开处理器搜索建筑名称高效得多。

### 14. 补丁查看器（PatchViewer）

补丁查看器用于审查 datapatch 对游戏数据库造成的实际变更。

- 在数据库界面中内联展示补丁生效前后的内容对比。
- 对新增、删除与修改的属性进行高亮区分。
- 提供配色设置与预览项，并已整合进 Neon 统一设置页。

它聚焦“数据最终变成了什么”，适合审查内容包或补丁，而非编辑地图本身。

### 15. 拼音搜索支持（PinyinSearchSupport）

拼音搜索支持让中文玩家可以用拼音快速查找游戏内的中文内容。

- 在搜索框中输入完整拼音、模糊拼音或首字母即可匹配中文条目。
- 支持多音字读音与中英数字混合输入。
- 支持调节搜索延迟，在即时反馈与大型列表性能之间自由平衡。

### 16. 外语服务器翻译（ForeignServerTranslator）

外语服务器翻译为跨语言联机提供辅助，不会永久改写服务器内容。

- 可标记外语服务器，辅助翻译收发聊天与服务器文本。
- 支持 Microsoft Translator 与 OpenAI 兼容接口。
- 可开启 Mindustry bundle 术语提示，让方块、单位与物品名称更贴合游戏原文。
- 所有配置统一存放在 Neon 设置中。

> [!WARNING]
> 翻译功能需要配置对应服务的地址或凭据。待翻译文本会发送至所配置的第三方服务，使用前请先确认服务商的隐私政策。

### 17. 地理围栏报警（Tripwire）

Tripwire 通过在地图上划定围栏，监测指定类型的单位是否穿越目标区域。

- 支持创建地理围栏，并按单位类型检测穿越事件。
- 支持 Toast、聊天消息与 marker 三种提醒方式。
- 可在世界与小地图上显示围栏，并自定义检测间隔、聊天合并窗口与颜色覆盖。

适合用于基地入口预警、资源区监视与战场边界提醒。

### 18. Poly 建造辅助（BetterPolyAi）

BetterPolyAi 为 Poly 提供建造辅助。

- 可通过快捷键随时启停。
- 只执行玩家自己的建造与拆除规划，不接管其他玩家的计划。
- 支持调节建造目标之间的间隔。

该功能源自 MindustryX 中的 Poly 建造辅助，在 Neon 中以客户端功能形式提供。

### 19. 高级替换（AdvancedReplace）

高级替换扩展了地图编辑器中的染色地形工具。

- 支持同色染色墙填充、染色地板填充与染色墙画笔三种模式。
- 使用 CIEDE2000 色差容忍度判断两种颜色是否足够接近。
- 容忍度较低时匹配更严格；容忍度较高时可覆盖更多视觉近似色。

适合处理由多种近似色组成的染色地图，大幅减少手动逐块替换的工作量。CIEDE2000 的原理见下文「术语与实现说明」。

### 20. 更好的 RTS 编队（BetterRTSFormation）

BetterRTSFormation 改进了原版 RTS 编队的创建、选择与识别体验。

- 保留原有快捷键习惯，同时提供更清晰的编队角标。
- 支持严格编队选择，减少误选其他单位。
- 支持在非指挥模式下进行编队相关操作。

### 21. 自然地形生成（BetterTerrainGen-V2）

该功能为地图编辑器的地形生成器新增 Natural Water（自然水体）滤镜。

它没有单独的复杂设置页，Neon 仅在统一设置中保留模块说明。需要生成自然水体时，直接在地图生成器中选择对应滤镜即可。

### 22. 智能拆除（AutoPruner）

智能拆除用于批量清理临时或冗余建筑。

- 可通过独立快捷键拆除多余电力节点：按电网连通性（相邻供电与连线）保留连通骨架，塞尔普洛电力节点与埃瑞克光束节点通用。
- 可按建筑放置时间窗口批量拆除建筑。

> [!NOTE]
> 为避免不熟悉规则时误删建筑，该功能在 Neon 聚合版首次使用时**默认关闭**，手动开启后会保留你的配置。

### 23. 导管染色（Color-the-ducts）

导管染色通过液体颜色帮助玩家一眼辨认液体导管中的内容。

- 在液体导管中心绘制对应液体颜色的标记。
- 支持悬停模式、整条连接显示，以及缩放与透明度调节。
- 属于纯视觉叠加，不改变液体导管的运输逻辑。

> [!NOTE]
> 该功能在 Neon 聚合版首次使用时**默认关闭**，手动开启后可在统一设置页中调整参数。

### 24. LogicSugar

LogicSugar 为 Mindustry 逻辑编辑提供更接近结构化代码的编写体验。

- 提供结构化逻辑语句、编辑器替换与编译辅助。
- 保留跳转线着色与积木颜色设置。
- 当 `call` 未填写实参时，显示被调用函数的参数列表占位提示，并随函数名与参数变化实时更新。
- 数据结构卡：将内存块区间登记为 `array`、`matrix`、`record`、`stack`、`queue`、`deque`、`bitset`、`map`、`uset`、`list`、`heap`、`chain`，每种结构均有独立操作卡（采用 `vector_push_back`、`map_set`、`stack_top` 等 STL 风格命名，旧短名仍兼容解析），并支持 `buf[i]`、`m.get(k)` 等表达式语法糖。声明与操作都会降级为普通原版指令，未装模组的客户端照常运行。
- 编辑器辅助：支持撤销/重做（桌面端 `Ctrl+Z` / `Ctrl+Y`，移动端底部按钮）、底栏按钮按窗口宽度自动换行，以及编译后指令数与 1000 上限的实时对照。
- 单位 flag 显示：可选在单位正上方显示其逻辑 flag，并为不同 flag 值分配鲜明颜色；纯展示效果，不影响存档与联机。
- 断言语句（「断言」分类共八张卡片）：数组下标越界、数据类型不符、值与期望不符、打印输出比对不符都会使程序停在出错行并在处理器上方显示原因；断点可冻结整个游戏供从容排查；日志卡写入日志而不打断运行。默认情况下断言仅存在于编辑器中，保存的代码不含断言；仅在开启「调试断言构建」（限单机）后才真正执行，联机保存的程序始终与原版客户端兼容。设置中还支持禁用断点、将断言失败视为断点，以及暂停期间保持视角分离。
- 处理器状态指示：停机的处理器头顶显示停在哪一行，长等待的处理器绘制进度圆环，运行出错时原地显示消息；等待阈值、检查频率与提醒特效均可在设置中调节。
- 复制变量 / 复制打印缓冲：将当前处理器的全部变量按名称整理为保留完整精度的表格复制到剪贴板（可直接粘贴进电子表格），或复制程序当前的打印内容。

适合需要频繁编写或维护大型逻辑程序的玩家，旨在降低阅读与编辑成本，而不改变逻辑处理器本身的运行规则。

### 25. Random（随机化）

Random 为下一次进入世界提供可选的视觉与文本随机化体验。

- 随机打乱数据库文本、战役与液体描述，以及游戏内说明提示。
- 随机打乱方块、物品、单位与战役区块图标。
- 不改变任何游戏逻辑行为。

Neon 聚合版没有为其设置独立分组。想体验时，在主菜单点击「千万别点」，下一次进入世界即可生效。

### 26. 锁定攻击（LockAttack）

锁定攻击帮助玩家持续盯紧同一个敌方目标。

- 按锁定键（默认 `L`）锁定鼠标指向的敌方单位或建筑。
- 直接控制的单位会持续瞄准并攻击该目标。
- 被选中的指挥单位会通过原版客户端命令路径收到一次性集火指令，单机与多人游戏均可用。
- 在另一目标上再按锁定键即可切换；点击空地或己方目标解锁；目标死亡后自动解锁。
- 支持锁定框、目标连线与可选目标血条，快捷键可在 `设置 → 控制` 中修改。

### 27. 智能放置（Smart Placement）

智能放置专门解决混合科技地图中交叉物品运输线的常见误操作。

- 拖放交叉物品线时，符合条件会自动选用未配置的反向分拣器。
- 单科技星球、平行线路、端点与非物品障碍物一律保持原版放置行为。
- 设置页可分别关闭「传送带 × 传送带」「传送带 × 管道」「管道 × 传送带」「管道 × 管道」四类交叉，关闭后该类恢复原版行为。
- 管道线遇到 3 格及以上的交叉阻挡带时整段改用物品管道桥，1–2 格仍放反向分拣器；该规则也可单独关闭。
- 默认仅在行星规则为「任意」（混合科技）时介入，关闭限制后所有星球均套用。

它只针对明确的交叉运输线场景，不会全面替换原版的方块选择逻辑。

### 28. 隐藏处理器显示（Hide What Processors Show）

该功能用于临时减轻处理器特效与动态 Marker 对画面的干扰。

- 通过两个独立快捷键，分别隐藏世界处理器特效与动态 Marker。
- 仅改变客户端显示，不修改地图逻辑或处理器行为。

### 29. 取消巡逻（Patrol Cancel）

取消巡逻解决指挥单位仍处于巡逻姿态、导致普通移动或攻击命令无法立即生效的问题。

- 右键指挥选中单位时自动清除巡逻状态。
- 中键排队与设置巡逻路线不受影响。

### 30. 建造透视（Build X-ray）

建造透视将建造预览遮挡的区域变为半透明，便于看清要放置或拆除的位置。

- 正在建造、拆除、框选蓝图或拖拽重建选择时，被预览遮住的单位、子弹与特效会渐隐，停止操作后渐显恢复。
- 鼠标周围保留一块始终透明的圆形区域，半径支持 8–64 格调节。
- 「透视遮罩的透明度」支持 0%–100% 调节，默认为 80% 透明度、32 格半径。
- 仅影响客户端渲染，不改变建造合法性，不发送任何网络请求。

来源说明：玩法逻辑源自 Miner 的脚本模组 build-xray（原始 JS 实现随 Neon 子仓库保存），Neon 负责 Java 移植、设置接入与聚合。

### 术语与实现说明

#### CIEDE2000 是什么？

CIEDE2000 是一种衡量颜色差异的标准公式，通常写作 ΔE00。它在感知上比直接比较 RGB 数值更贴近人眼对色差的判断，会综合考虑明度、色相与饱和度之间的关系。

在 AdvancedReplace 中，它用于判断两块染色地形的颜色是否足够接近：

- 容忍度低：只匹配非常接近的颜色，误替换更少。
- 容忍度高：可匹配更大范围的近似色，适合存在渐变或压缩误差的地图。

它不是新的染色方式，也不会改变最终颜色，只决定替换工具的匹配范围。

#### 移动平均是什么？

移动平均是在最近一个连续时间窗口内取平均值。物流速率增强不会只盯最近一次运输，而是综合最近一段时间的数据计算，因此短暂堵塞或单次集中运输不会立刻带偏长期吞吐判断。

窗口越长，数据越稳定但反应越慢；窗口越短，反馈越及时但波动越明显。

#### datapatch 和 PatchViewer

datapatch 可以修改游戏数据库中的对象或属性。PatchViewer 将修改前后的结果内联展示，并高亮标出新增、删除与修改的内容，便于确认补丁实际影响了哪些数据。

#### `getblock(x,y)` 引用

逻辑处理器不一定通过显式链接引用建筑，也可能在运行时按坐标读取建筑。WhoUsesThisBuilding 会识别这类间接引用，从而覆盖普通链接搜索找不到的依赖关系。

#### OverlayUI

OverlayUI 是管理游戏内悬浮窗口的界面层。Neon 在原版客户端提供兼容的窗口入口；在 MindustryX 或兼容桥接环境中，支持的功能会自动接入外部 OverlayUI。悬浮窗口属于本地客户端界面，其他玩家不会因为你打开窗口而看到相同内容。该层在设置中拥有独立主开关（重启生效）：关闭后原版客户端不再显示悬浮窗口（无齿轮按钮、无 `Z` 键，窗口注册退化为空操作），内置兜底实现仅在兼容层初始化失败时接管；在真 MindustryX 环境下始终优先使用 X 自带的 OverlayUI。

#### 纯客户端是什么意思？

Neon 的全部功能均运行在玩家自己的客户端上，不要求服务器安装同一个模组。电网显示、搜索、截图、地图编辑及大多数操作辅助只影响本地界面与输入；涉及单位命令的功能仍走 Mindustry 原有的客户端命令路径。

#### `classes.dex` 和 Android 包

Android 端加载 Java 模组必须包含 `classes.dex`。Release 中的 `Neon.jar` 是面向桌面与 Android 的最终合并包；桌面专用 JAR 或构建中间产物无法在 Android 端加载。

## English

### Overview

Neon brings a collection of client-side tools together for the full Mindustry workflow. The sections below explain the practical purpose of each feature and clarify terms that are easy to misunderstand.

### Feature map

| Area | What it helps with |
| --- | --- |
| Battlefield awareness | Power, logistics, units, projectors, and threats |
| Building and control | Faster selection, formations, commands, and cleanup |
| Maps and logic | Map editing, logic tracing, and datapatch review |
| Multiplayer and information | Local player records, translation, markers, and updates |
| Personalization | Input, visual overlays, and optional randomization |

### 1. Power Grid Minimap

Power Grid Minimap overlays power-network structure and status on the minimap or full map.

- Independent grids use distinct colors.
- Net power balance can be shown at each grid center with configurable styling.
- Split grids with a deficit can display reconnect hints.
- The power table summarizes current balance and recent lows for large grids.
- Experimental rescue hints can suggest positive-island isolation and temporary reactor shutdowns.

### 2. Stealth Path

Stealth Path estimates threats and draws lower-risk routes for units.

- Supports multiple route modes and land/air/all threat filters.
- Can route unit groups to the cursor or chat coordinates.
- An auto-move keybind can send units along the planned route.
- Overlay information windows are available through MindustryX or a compatible OverlayUI environment.

> [!NOTE]
> Disabled by default in the aggregate build because the current implementation still has known defects. Enable it manually in Neon settings if needed.

### 3. customMarker

customMarker turns manually selected positions and chat coordinates into reusable map markers.

- Uses a button, fullscreen position picker, and confirmation-panel workflow.
- Provides five editable marker templates in `<text><text>(x,y)` format.
- Includes a chat-coordinate capture window.

### 4. BetterScreenShot

BetterScreenShot captures high-resolution world screenshots.

- Supports a keybind (default `F8`) and an Overlay button.
- Renders large maps in chunks to avoid texture-size limits.
- Provides output-size estimation and optional progress feedback.
- The BSS core originates from Miner and is integrated into Neon settings and UI.

### 5. Radial Build Menu

Radial Build Menu reduces time spent searching the build bar.

- Hold a key to open a radial HUD and release to select.
- Provides up to 16 configurable slots (8 inner and 8 outer).
- Supports profiles selected by time, planet, or custom conditions.
- Supports A/B slot groups and JSON import/export.
- Radius, scale, opacity, icon size, and direction are configurable.

### 6. betterMiniMap

betterMiniMap adds unit and building icons to the minimap.

- Supports direction, scale, opacity, spacing, and separate friendly/enemy filters.
- Unit and building selection dialogs support search, select all, clear, and invert.

### 7. ServerPlayerDataBase

ServerPlayerDataBase keeps optional player and chat records locally on the client.

- Can collect names, UIDs, server information, and available IP-tracking results.
- Supports chat logs, import/export, integrity checks, and warnings.
- Provides OverlayUI query/debug windows when available and falls back to regular dialogs otherwise.

> [!WARNING]
> Local records are stored on your client only. Handle them according to server rules and applicable privacy requirements.

### 8. betterMapEditor

betterMapEditor simplifies symmetric map editing by letting you drag the mirror axis directly in the generator preview.

### 9. Better Projector Overlay

Better Projector Overlay previews the power impact of overdrive projector placement and marks redundant or overlapping projector coverage. Optional chat alerts are available.

### 10. betterLogisticsSpeed

betterLogisticsSpeed adds longer-window moving-average throughput to building logistics panels. Window length, decimal precision, and the total-throughput row are configurable to reduce short-term noise.

### 11. betterHotKey

betterHotKey adds combo keybinds and grouped configuration. It also provides controls for terrain-number conflicts and banned-building slots.

### 12. modUpdater

modUpdater centralizes GitHub release checks and update actions.

- Distinguishes update-available, up-to-date, blacklisted, and missing-repository states.
- Supports single and batch updates.
- Provides repository overrides, blacklist management, and mirror switching.

### 13. WhoUsesThisBuilding

WhoUsesThisBuilding reverse-highlights processors that reference the building under the cursor.

- Labels show `Lxx(opcode)` for line-level tracing.
- Handles both explicit links and indirect `getblock(x,y)` references.

### 14. PatchViewer

PatchViewer displays before/after datapatch changes inline inside database content panels and highlights added, removed, and modified properties.

### 15. PinyinSearchSupport

PinyinSearchSupport lets search fields match Chinese content via full pinyin, fuzzy pinyin, initials, heteronyms, and mixed Chinese/English/numeric input. Search delay is configurable.

### 16. ForeignServerTranslator

ForeignServerTranslator assists with foreign-language servers and chat.

- Supports incoming, outgoing, and server text assistance.
- Supports Microsoft Translator and OpenAI-compatible endpoints.
- Can use Mindustry bundle terminology hints.

> [!WARNING]
> External translation services receive the text being translated. Review your provider's privacy policy before configuring credentials.

### 17. Tripwire

Tripwire creates in-world geofences and triggers alerts when selected unit types cross them. Toast, chat, marker, world display, minimap display, detection interval, merge windows, and color overrides are configurable.

### 18. BetterPolyAi

BetterPolyAi provides toggleable Poly build assistance. It executes only your own build/deconstruct plans and supports a configurable target gap.

### 19. AdvancedReplace

AdvancedReplace adds colored-wall fill, colored-floor fill, and colored-wall brush modes to the map editor. Its CIEDE2000 tolerance matches nearby colors by perceptual difference rather than raw RGB distance.

### 20. BetterRTSFormation

BetterRTSFormation improves RTS group creation, selection, and visibility while preserving vanilla keybind habits. It supports formation badges, strict group selection, and operations outside command mode.

### 21. BetterTerrainGen-V2

BetterTerrainGen-V2 adds the Natural Water filter to the map editor terrain generator. It has no standalone settings panel.

### 22. AutoPruner

AutoPruner removes redundant power nodes or buildings using separate keybinds and placement-time windows.

> [!NOTE]
> Disabled by default on first use in the Neon aggregate to prevent accidental deletion.

### 23. Color-the-ducts

Color-the-ducts draws liquid-colored center marks on liquid ducts. It supports hover mode, connected-line display, scale, and opacity. The overlay does not change duct transport behavior.

> [!NOTE]
> Disabled by default on first use in the Neon aggregate.

### 24. LogicSugar

LogicSugar provides structured logic statements, editor replacement, and compilation helpers. It preserves jump-line and block-color assistance and displays function-argument placeholders when a `call` has no arguments.

- Assertion statements (eight cards in the "Assertions" category): out-of-range array indexes, mismatched data types, unexpected values, and print-output mismatches stop the program on the offending line with a message above the processor; a breakpoint freezes the game for inspection; a log statement writes to the game log. Assertions exist only in the editor by default and never enter saved code; the "Debug Assert Build" toggle (singleplayer only) runs them for real, while multiplayer saves always remain vanilla-compatible. Settings can also disable breakpoints, treat assertion failures as breakpoints, and keep the camera detached while paused.
- Processor status overlay: stopped processors display which line they halted on, long waits draw a progress ring, and failures display their message in place; threshold, scan rate, and warning effects are configurable.
- Copy Variables / Copy Print Buffer: copy all variables of the current processor as a name-sorted, full-precision table for spreadsheets, or copy the program's current print output.
- Data-structure cards: register a memory-block range as `array`, `matrix`, `record`, `stack`, `queue`, `deque`, `bitset`, `map`, `uset`, `list`, `heap`, or `chain`; each structure provides dedicated operation cards (`vector_push_back`, `map_set`, `stack_top`, and other STL-style names, with legacy short names still parsed) plus expression sugar such as `buf[i]` and `m.get(k)`. Declarations and operations lower to plain vanilla instructions so saves run on unmodded clients.
- Editor helpers: undo/redo (`Ctrl+Z` / `Ctrl+Y` on desktop, bottom-bar buttons on mobile), a bottom bar that wraps to the available width, and a live compiled-instruction counter against the limit.
- Unit flag display: optionally draw each unit's logic flag above it with a distinct vivid color per flag value; display-only, with no effect on saves or multiplayer.

### 25. Random

Random alters presentation for the next world by shuffling database text, campaign and liquid descriptions, instructional text, and selected block, item, unit, and sector icons without changing gameplay logic. In the aggregate build, click `Do Not Click` in the main menu to enable it.

### 26. LockAttack

LockAttack locks onto the enemy unit or building under the cursor (default key `L`), keeps your directly controlled unit focused on it, and issues a one-shot focus-fire order to selected command units through the vanilla command path.

Press the key on another target to switch, click empty ground or a friendly target to unlock, and let the target's death unlock automatically. Lock boxes, target lines, and an optional target health bar are configurable.

### 27. Smart Placement

On mixed-tech planets, Smart Placement selects an unconfigured inverted sorter when an item line is dragged across another item line. Single-tech planets, parallel lines, endpoints, and non-item obstacles retain vanilla placement behavior.

### 28. Hide What Processors Show

Two independent keybinds hide world processor effects and dynamic markers. This affects local rendering only and does not modify map logic.

### 29. Patrol Cancel

Patrol Cancel clears patrol stance when a selected command unit receives a right-click order, allowing movement or attack orders to take effect immediately. Middle-click queued commands remain available for patrol routes.

### 30. Build X-ray

Build X-ray fades out elements covered by the build preview so the target area stays readable while placing or breaking blocks.

- Units, bullets, and effects behind the preview fade out while placing, breaking, box-selecting a schematic, or drag-selecting for rebuild, and fade back in afterward.
- A circular area around the cursor remains transparent, with a configurable radius from 8 to 64 tiles.
- Mask transparency is configurable from 0% to 100%; defaults are 80% transparency and a 32-tile radius.
- Rendering only: placement validity is unchanged and nothing is sent over the network.

Credits: the gameplay logic comes from Miner's script mod build-xray (the original JavaScript is preserved in the sub-repository); Neon provides the Java port, settings integration, and bundling.

### Terminology

#### CIEDE2000

CIEDE2000, commonly written as ΔE00, is a perceptual color-difference formula. It compares lightness, hue, and chroma in a way that matches human vision much more closely than raw RGB distance.

In AdvancedReplace, lower tolerance enforces stricter matching, while higher tolerance accepts a wider range of visually similar colors. It controls the match range only, not the final rendered color.

#### Moving average

A moving average averages measurements over a recent time window. A longer window is more stable but reacts more slowly; a shorter window reacts faster but shows more noise.

#### datapatch and PatchViewer

A datapatch modifies objects or properties in the game database. PatchViewer displays the before/after state inline and highlights added, removed, and modified entries.

#### `getblock(x,y)` references

Logic can read a building by coordinates at runtime instead of using an explicit processor link. WhoUsesThisBuilding recognizes this indirect reference form as well.

#### OverlayUI

OverlayUI is the in-game layer used to manage floating windows. Neon provides a compatible entry on vanilla clients and connects supported features to MindustryX or a compatible bridge when available. These windows are local client UI. The layer has its own master switch in settings (restart to apply): with it off, vanilla clients show no floating windows at all (no gear button, no `Z` key, and window registration becomes a no-op), and the built-in fallback takes over only when the compatibility layer fails to initialize; a real MindustryX client always uses its own OverlayUI.

#### Client-side

Client-side means features run exclusively in your own game client without requiring the server to install Neon. Visual overlays, search, screenshots, map editing, and most workflow tools are purely local; unit commands still use Mindustry's existing client command path.

#### `classes.dex`

Android requires a `classes.dex` payload inside Java mod packages. The release `Neon.jar` is the final desktop-and-Android package; desktop-only and intermediate JARs will not load on Android.

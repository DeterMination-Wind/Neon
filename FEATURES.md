# Neon Features / 功能详情

Neon 把多种客户端辅助工具放在同一个模组中。本文按使用场景说明每项功能解决什么问题、适合什么时候使用，以及其中容易被忽略的技术细节。

- [中文](#中文)
- [English](#english)

## 中文

### 功能地图

| 使用场景 | 主要帮助 |
| --- | --- |
| 战场观察 | 看清电网、物流、单位、投影和潜在威胁 |
| 建造与操作 | 减少选方块、编队、控制单位和拆除建筑时的重复操作 |
| 地图与逻辑 | 改进制图流程，定位逻辑引用，并审查数据补丁 |
| 联机与信息 | 记录本地玩家信息，辅助翻译、标记和模组更新 |
| 个性化与趣味 | 调整界面显示、输入方式和部分视觉内容 |

所有功能都通过 Neon 的统一设置入口管理。部分功能还会出现在 Overlay 管理器中；不需要的功能可以单独关闭。

### 1. 电网小地图（Power Grid Minimap）

电网小地图把电力网络的结构和状态直接叠加到小地图或全屏地图上。

- 每个独立电网使用不同颜色显示，方便发现断开的网络和跨网误接。
- 在电网中心显示净电力盈亏，可调整字号、颜色和透明度。
- 当大电网分裂并出现负电时，标出可能的重新连接位置。
- 缺电救援建议可以提示正电岛隔离方案，以及可能需要暂时停用的冲击反应堆。该部分仍属于 Beta。
- 电力表以列表汇总各个大电网的当前盈亏和近期最低值，适合快速找出最危险的电网。

它的重点不是替代原版电力界面，而是把“哪张网出了问题”和“应该去哪里检查”变得更直观。

### 2. 偷袭小道（Stealth Path）

偷袭小道根据地图上的威胁估算单位路线，并把路线绘制到世界中。

- 支持更安全、预计受伤更少的路线预览。
- 可以按陆军、空军或全部威胁过滤，也可以切换不同路线模式。
- 自动模式可以规划单位集群前往鼠标位置或聊天坐标，并通过快捷键让单位沿路线移动。
- 在 MindustryX 或兼容的 OverlayUI 环境中，可以打开显示模式、伤害和控制信息的窗口。
- 线宽、透明度和显示时间都可以调整。

由于当前版本仍有已知缺陷，该功能在 Neon 聚合版中默认关闭。需要时可在 Neon 设置中手动开启。

### 3. 自定义标记（customMarker）

自定义标记用于把聊天中的坐标和玩家想记录的位置转成更容易操作的地图标记。

- 采用“按钮、全屏点位、面板确认”的流程，减少在地图和聊天之间来回切换。
- 支持编辑 5 组标记模板，模板消息格式为 `<内容><内容>(x,y)`。
- 集成聊天坐标捕获窗口，可以从聊天记录中选择坐标并快速聚焦。
- 在支持标记桥接的环境中，可以使用对应的 MindustryX 标记能力。

### 4. BetterScreenShot

BetterScreenShot 用于生成高分辨率世界截图，适合保存地图、展示基地或制作攻略素材。

- 可以通过快捷键或 Overlay 按钮启动，默认截图快捷键为 `F8`。
- 大地图会分块渲染，避免一次性创建超出纹理尺寸限制的巨大图片。
- 开始前可以估算输出尺寸，并在渲染过程中显示进度状态。
- Neon 中的 BSS 核心代码来自 Miner，Neon 负责将其与统一设置和界面接入。

### 5. 圆盘快捷建造（Radial Build Menu）

圆盘快捷建造把常用方块放到一个径向 HUD 中，减少在建造栏中寻找方块的时间。

- 长按热键打开圆盘，松开后选择对应方块。
- 最多支持 16 个槽位，分为内圈 8 槽和外圈 8 槽。
- 可以创建多套槽位配置，并按时间、星球或自定义条件切换。
- 支持 A/B 槽位组热键切换、缩放、透明度、半径、图标大小和方向调整。
- 配置可以使用 JSON 导入和导出，方便备份或分享。

### 6. betterMiniMap

betterMiniMap 增强小地图的战场信息密度。

- 在小地图上显示单位和建筑图标，并支持朝向、透明度、缩放和聚合间距调整。
- 敌我单位和建筑可以分别显示或隐藏。
- 单位和建筑都有可搜索的筛选对话框，支持全选、清空和反选。

### 7. 玩家数据库（ServerPlayerDataBase）

玩家数据库在本地记录玩家和聊天相关信息，方便在长期联机或管理服务器时查询上下文。

- 可记录玩家名称、UID、服务器和可用的 IP 追踪结果。
- 可选记录聊天日志，并支持导入、导出、完整性校验和异常提示。
- 在支持 MindustryX OverlayUI 的环境中提供查询和调试窗口；没有 OverlayUI 时回退到普通对话框。

这些数据保存在客户端本地。使用时应遵守服务器规则和当地隐私法规，不要把本地记录当作公开资料随意传播。

### 8. 地图编辑增强（betterMapEditor）

地图编辑增强改进地图生成器中的镜像操作。

- 在生成器预览中直接拖动镜像轴，快速调整对称位置。
- 用可拖动的镜像滤镜替代固定操作，不改变原有地图生成流程。

### 9. 投影叠加（Better Projector Overlay）

投影叠加在放置超速投影时提前展示电力影响，帮助避免放下建筑后才发现电网不够。

- 显示放置后的电网正负预判圈和实时数值提示。
- 扫描并标记被高布或穹顶覆盖的普通超速投影。
- 可以选择是否通过聊天提醒发现的问题。

### 10. 物流速率增强（betterLogisticsSpeed）

原版物流数据容易受到短时间波动影响。物流速率增强提供更长时间窗口的吞吐统计。

- 为建筑物流信息增加移动平均吞吐量。
- 可以显示总吞吐行，并调整统计窗口、小数位和显示方式。
- 较长的统计窗口能减少单次运输或短时堵塞造成的误判，更适合判断长期产能。

### 11. 快捷键增强（betterHotKey）

快捷键增强扩展原版输入配置，适合拥有复杂建造和操作习惯的玩家。

- 支持双键组合热键和分组配置。
- 可以处理地形编号冲突，并选择是否保留被禁用建筑对应的槽位。
- 提供可视化配置界面，减少直接编辑配置文件的需要。

### 12. 模组更新中心（modUpdater）

模组更新中心把多个模组的更新检查集中到一个界面。

- 启动时检查 GitHub Release，并区分可更新、已最新、黑名单和没有仓库的模组。
- 支持单个更新和批量更新。
- 支持仓库覆盖、黑名单和镜像下载切换。
- Neon 聚合版将更新入口集中起来，避免每个子模组重复弹出更新提示。

下载镜像和第三方服务的可用性取决于网络环境，更新前仍应确认来源和版本。

### 13. 谁在用这个建筑（WhoUsesThisBuilding）

谁在用这个建筑用于反向检查逻辑处理器和建筑之间的依赖关系。

- 按住快捷键并悬停建筑时，反向高亮引用它的逻辑处理器。
- 标签显示 `Lxx(opcode)`，可以直接定位逻辑行号和引用类型。
- 同时支持显式处理器链接和 `getblock(x,y)` 形式的间接引用。

对于大型逻辑图，这比逐个打开处理器搜索建筑名称更快。

### 14. 补丁查看器（PatchViewer）

补丁查看器帮助检查 datapatch 对游戏数据库产生了什么变化。

- 在数据库界面内联显示补丁前后的内容。
- 对新增、删除和修改的属性进行可视化高亮。
- 提供颜色设置和预览项目，并纳入 Neon 的统一设置页。

它关注的是“数据最终变成了什么”，适合审查内容包或补丁，而不是编辑地图本身。

### 15. 拼音搜索支持（PinyinSearchSupport）

拼音搜索支持让中文玩家可以用拼音查找游戏中的中文内容。

- 在搜索框中输入完整拼音、模糊拼音或首字母来匹配中文条目。
- 支持多音字读音和中英数字混合输入。
- 可以调整搜索延迟，平衡即时反馈和大型列表中的性能。

### 16. 外语服务器翻译（ForeignServerTranslator）

外语服务器翻译为跨语言联机提供辅助，不会把服务器内容永久改写。

- 可以标记外语服务器，并辅助翻译收发聊天和服务器文本。
- 支持 Microsoft Translator 和 OpenAI 兼容接口。
- 可使用 Mindustry bundle 术语提示，让方块、单位和物品名称更接近游戏原文。
- 设置统一放在 Neon 中。

翻译功能需要配置对应服务的地址或凭据。请求会经过所配置的第三方服务，使用前应确认服务商的隐私政策。

### 17. 地理围栏报警（Tripwire）

Tripwire 用地图上的围栏监测指定单位是否穿过某个区域。

- 可以创建地理围栏，并按单位类型检测穿越事件。
- 支持 Toast、聊天和 marker 提醒。
- 可以在世界和小地图上显示围栏，并调整检测间隔、聊天合并窗口和颜色覆盖。

它适合做基地入口预警、资源区监视和战场边界提醒。

### 18. BetterPolyAi

BetterPolyAi 为 Poly 提供建造辅助。

- 可以通过快捷键启停。
- 只执行玩家自己的建造和拆除规划，不接管其他玩家的计划。
- 支持调整建造目标之间的间隔。

该功能源自 MindustryX 中的 Poly 建造辅助，并作为 Neon 中的客户端功能提供。

### 19. 高级替换（AdvancedReplace）

高级替换扩展地图编辑器中的染色地形工具。

- 支持同色染色墙填充、染色地板填充和染色墙画笔。
- 使用 CIEDE2000 色差容忍度判断两个颜色是否足够接近。
- 容忍度较低时匹配更严格，容忍度较高时可以覆盖更多视觉上接近的颜色。

它适合处理由多个近似颜色组成的染色地图，减少手动逐块替换的工作量。CIEDE2000 的含义见本文末尾的术语说明。

### 20. 更好的 RTS 编队（BetterRTSFormation）

BetterRTSFormation 改进原版 RTS 编队的创建、选择和识别。

- 保留原有快捷键习惯，同时提供更清晰的编队角标。
- 支持严格编队选择，减少误选其他单位。
- 支持在非指挥模式下进行编队相关操作。

### 21. 更自然的地形生成（BetterTerrainGen-V2）

该功能为地图编辑器地形生成器增加 Natural Water 自然水体滤镜。

它没有单独的复杂设置页，Neon 只在统一设置中保留模块说明。需要生成自然水体时，可以直接在地图生成器中选择对应滤镜。

### 22. 智能拆除（AutoPruner）

智能拆除用于清理临时或冗余建筑。

- 可以通过独立快捷键拆除多余电力节点。
- 可以根据建筑放置时间窗口批量拆除建筑。
- Neon 聚合版首次使用时默认关闭，手动开启后保留原有设置。

默认关闭是为了避免玩家在不了解规则时误删建筑。

### 23. 导管染色（Color-the-ducts）

导管染色通过液体颜色帮助玩家辨认液体导管中的内容。

- 在液体导管中心绘制对应颜色的标记。
- 支持悬停模式、整条连接显示、缩放和透明度调整。
- Neon 聚合版首次使用时默认关闭，手动开启后可以在统一设置页调整。

这是视觉叠加，不会改变导管运输逻辑。

### 24. LogicSugar

LogicSugar 为 Mindustry 逻辑编辑提供更接近结构化代码的辅助。

- 提供结构化逻辑语句、编辑器替换和编译辅助。
- 保留跳转线着色和积木颜色设置。
- 当 `call` 没有填写实参时，可以显示被调用函数的参数列表占位提示。
- 参数提示会随着函数名和参数变化实时更新。

它适合需要频繁编写或维护大型逻辑程序的玩家，目标是降低阅读和编辑成本，而不是改变逻辑处理器本身的运行规则。

### 25. Random

Random 为下一次进入世界提供可选的视觉和文本随机化。

- 随机打乱数据库文本、战役和液体描述，以及游戏内说明提示。
- 随机打乱方块、物品、单位和战役区块图标。
- 不改变逻辑行为。

Neon 聚合版没有单独设置组。想体验时，在主菜单点击“千万别点”，下一次进入世界即可启用。

### 26. 锁定攻击（LockAttack）

锁定攻击让玩家可以持续关注一个敌方目标。

- 按锁定键（默认 `L`）锁定鼠标指向的敌方单位或建筑。
- 直接控制的单位会持续瞄准并攻击该目标。
- 被选中的指挥单位会通过原版网络命令路径收到一次性集火命令，单机和多人都可以使用。
- 在另一个目标上按锁定键可以切换；点击空地或己方目标可以解锁；目标死亡后自动解锁。
- 支持锁定框、目标连线和可选目标血条，键位可以在 `设置 → 控制` 中修改。

### 27. 智能放置（Smart Placement）

智能放置处理混合科技地图中交叉物品运输线的一个常见误操作。

- 拖放交叉物品线时，如果条件符合，会自动选择未配置的反向分拣器。
- 单科技星球、平行线路、端点和非物品障碍物仍保持原版放置行为。

它只针对明确的交叉运输线场景，不会全面替换原版的建筑选择逻辑。

### 28. 隐藏处理器显示（Hide What Processors Show）

该功能用于暂时减少处理器和 Marker 对画面的干扰。

- 使用两个独立快捷键，分别隐藏世界处理器特效和动态 Marker。
- 只改变客户端显示，不修改地图逻辑或处理器行为。

### 29. 取消巡逻（Patrol Cancel）

取消巡逻解决指挥单位仍处于巡逻姿态、导致普通移动或攻击命令不立即生效的问题。

- 右键指挥选中单位时自动清除巡逻状态。
- 中键排队和设置巡逻路线不受影响。

## 术语与实现说明

### CIEDE2000 是什么？

CIEDE2000 是一种衡量颜色差异的标准公式，通常写作 ΔE00。它在感知上比直接比较 RGB 数值更接近人眼对颜色差异的判断，会考虑明度、色相和饱和度之间的关系。

在 AdvancedReplace 中，它用于判断两个染色地形的颜色是否足够接近：

- 容忍度低：只匹配非常接近的颜色，误替换更少。
- 容忍度高：可以匹配更大范围的近似颜色，适合颜色有渐变或压缩误差的地图。

它不是一种新的染色方式，也不会改变最终颜色；它只决定替换工具的匹配范围。

### 移动平均是什么？

移动平均是在一个连续的时间窗口中取平均值。物流速率增强不会只看最近一次运输，而是把最近一段时间的数据一起计算，因此短暂堵塞或单次大量运输不会立刻把长期吞吐量判断带偏。

窗口越长，数据越稳定但反应越慢；窗口越短，反馈越及时但波动越明显。

### datapatch 和 PatchViewer

datapatch 可以修改游戏数据库中的对象或属性。PatchViewer 把修改前后的结果并排或内联展示，并标出新增、删除和修改的内容，便于确认补丁实际影响了哪些数据。

### `getblock(x,y)` 引用

逻辑处理器不一定通过显式链接引用建筑，也可能在运行时用坐标读取建筑。WhoUsesThisBuilding 会尝试识别这种间接引用，因此能覆盖一部分普通链接搜索找不到的关系。

### OverlayUI

OverlayUI 是用于管理游戏内悬浮窗口的界面层。Neon 在原版客户端提供兼容的窗口入口；在 MindustryX 或兼容桥接环境中，支持的模块可以接入外部 OverlayUI。Overlay 窗口不是服务器界面，其他玩家不会因为你打开本地窗口而看到相同内容。

### 纯客户端是什么意思？

Neon 的功能在玩家自己的客户端运行，不要求服务器安装同一个模组。电网显示、搜索、截图、地图编辑和大多数操作辅助只影响本地界面和输入；涉及单位命令的功能仍通过 Mindustry 原有的客户端命令路径工作。

### `classes.dex` 和安卓包

安卓端需要从 Java 模组包中加载 `classes.dex`。Release 中的 `Neon.jar` 是面向桌面和安卓的最终包；桌面专用 JAR 或构建过程中的中间 JAR 可能无法在安卓端加载。

## English

### Overview

Neon is a collection of client-side tools for the full Mindustry workflow. The sections below explain the practical purpose of each feature and clarify the terms that are easy to misunderstand.

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

- Independent grids use different colors.
- Net power balance can be shown at each grid center with configurable styling.
- Split grids with a deficit can produce reconnect hints.
- The power table summarizes current and recent low points for large grids.
- Experimental rescue hints can suggest positive-island isolation and temporary reactor shutdowns.

### 2. Stealth Path

Stealth Path estimates threats and draws lower-risk routes for units.

- Supports route modes and land/air/all threat filters.
- Can route unit groups to the cursor or chat coordinates.
- An auto-move keybind can send units along the planned route.
- Overlay information windows are available through MindustryX or a compatible OverlayUI environment.

The feature is disabled by default in the aggregate build because the current implementation still has known defects.

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
- Provides up to 16 configurable slots.
- Supports profiles selected by time, planet, or conditions.
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

Local records should be handled according to server rules and applicable privacy requirements.

### 8. betterMapEditor

betterMapEditor makes symmetric map editing easier by allowing the mirror axis to be dragged directly in the generator preview.

### 9. Better Projector Overlay

Better Projector Overlay previews the power impact of overdrive projector placement and marks risky projector coverage. Optional chat alerts are available.

### 10. betterLogisticsSpeed

betterLogisticsSpeed adds longer-window moving-average throughput to building logistics information. The window length, decimal precision, and total-throughput row can be configured to reduce short-term noise.

### 11. betterHotKey

betterHotKey adds combo keybinds and grouped configuration. It also provides controls for terrain-number conflicts and banned-building slots.

### 12. modUpdater

modUpdater centralizes GitHub release checks and update actions.

- Shows update, current, blacklisted, and repository-missing states.
- Supports single and batch updates.
- Provides repository overrides, blacklist management, and mirror switching.

### 13. WhoUsesThisBuilding

WhoUsesThisBuilding reverse-highlights processors that reference the building under the cursor.

- Labels show `Lxx(opcode)` for line-level tracing.
- It handles explicit links and indirect `getblock(x,y)` references.

### 14. PatchViewer

PatchViewer shows before/after datapatch changes inside database content panels and highlights added, removed, and modified properties.

### 15. PinyinSearchSupport

PinyinSearchSupport lets search fields match Chinese content through full pinyin, fuzzy pinyin, initials, heteronyms, and mixed Chinese/English/numeric input. Search delay is configurable.

### 16. ForeignServerTranslator

ForeignServerTranslator assists with foreign-language servers and chat.

- Supports incoming, outgoing, and server text assistance.
- Supports Microsoft Translator and OpenAI-compatible endpoints.
- Can use Mindustry bundle terminology hints.

External translation services may receive the text being translated. Review the provider's privacy policy before configuring credentials.

### 17. Tripwire

Tripwire creates in-world geofences and alerts when selected unit types cross them. Toast, chat, marker, world display, minimap display, detection timing, merge windows, and color overrides are configurable.

### 18. BetterPolyAi

BetterPolyAi provides toggleable Poly build assistance. It executes only the player's own build/deconstruct plans and supports a configurable target gap.

### 19. AdvancedReplace

AdvancedReplace adds colored-wall fill, colored-floor fill, and colored-wall brush modes to the map editor. Its CIEDE2000 tolerance lets near-colors be matched by perceptual difference instead of raw RGB distance.

### 20. BetterRTSFormation

BetterRTSFormation makes RTS groups easier to create, select, and identify while preserving the original keybind behavior. It supports formation badges, strict group selection, and operations outside command mode.

### 21. BetterTerrainGen-V2

BetterTerrainGen-V2 adds the Natural Water filter to the map editor terrain generator. It has no complex standalone settings panel.

### 22. AutoPruner

AutoPruner removes redundant power nodes or buildings using separate keybinds and placement-time windows. It is disabled by default on first use in the Neon aggregate to avoid accidental deletion.

### 23. Color-the-ducts

Color-the-ducts draws liquid-colored center marks on liquid ducts. It supports hover mode, connected-line display, scale, and opacity. The overlay does not change duct transport behavior.

### 24. LogicSugar

LogicSugar provides structured logic statements, editor replacement, and compilation helpers. It keeps jump-line and block-color assistance and can show function-argument placeholders when a `call` has no arguments.

### 25. Random

Random changes presentation for the next world by shuffling database text, campaign and liquid descriptions, instructional text, and selected block, item, unit, and sector icons. It does not change logic behavior. In the aggregate build, click `Do Not Click` in the main menu to enable it.

### 26. LockAttack

LockAttack locks the enemy unit or building under the cursor (default key `L`), keeps the directly controlled unit focused on it, and can issue a one-shot focus-fire order to selected command units through the vanilla command path.

Tap another target to switch, tap empty ground or a friendly target to unlock, and let the target's death unlock automatically. Lock boxes, target lines, and an optional target health bar are configurable.

### 27. Smart Placement

On mixed-tech planets, Smart Placement selects an unconfigured inverted sorter when an item line is dragged across another item line. Single-tech planets, parallel lines, endpoints, and non-item obstacles retain vanilla placement behavior.

### 28. Hide What Processors Show

Two independent keybinds hide world processor effects and dynamic markers. This changes only local rendering and does not modify map logic.

### 29. Patrol Cancel

Patrol Cancel clears patrol stance when a selected command unit receives a right-click command, allowing movement or attack orders to take effect immediately. Middle-click queued commands remain available for patrol routes.

### Terminology

#### CIEDE2000

CIEDE2000, commonly written as ΔE00, is a perceptual color-difference formula. It compares lightness, hue, and chroma in a way that is closer to human color perception than direct RGB distance.

In AdvancedReplace, lower tolerance means stricter matching; higher tolerance accepts a wider range of visually similar colors. It controls the replacement match range, not the final rendered color.

#### Moving average

A moving average averages measurements over a recent time window. A longer window is more stable but reacts more slowly; a shorter window reacts faster but shows more noise.

#### datapatch and PatchViewer

A datapatch changes objects or properties in the game database. PatchViewer shows the resulting before/after state and marks added, removed, and modified data.

#### `getblock(x,y)` references

Logic can read a building by coordinates at runtime instead of using an explicit processor link. WhoUsesThisBuilding attempts to recognize this indirect form as well.

#### OverlayUI

OverlayUI is the in-game layer used to manage floating windows. Neon supplies a compatible entry on vanilla clients and can connect supported features to MindustryX or a compatible bridge. These windows are local client UI.

#### Client-side

Client-side means the feature runs in the player's own game client and does not require the server to install Neon. Visual overlays, search, screenshots, map editing, and most workflow assistance are local; unit commands still use Mindustry's existing client command path.

#### `classes.dex`

Android needs a `classes.dex` payload in the Java mod package. The release `Neon.jar` is the final desktop-and-Android package; desktop-only and intermediate JARs may not load on Android.

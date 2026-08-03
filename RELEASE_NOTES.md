# Neon v11.0.0

## 中文

- Neon 聚合包新增 BetterRTSFormation、BetterTerrainGen-V2、AutoPruner、Color-the-ducts、LogicSugar 和 Random，扩展至 25 个客户端工具模块。
- 集成 BetterRTSFormation，改进 RTS 编队的创建、选择和识别，支持编队角标、严格选择以及非指挥模式下的编队操作。
- 集成 BetterTerrainGen-V2 和 AutoPruner：地图编辑器新增 Natural Water 自然水体滤镜；AutoPruner 可通过独立快捷键按规则清理多余电力节点或建筑，并在聚合版首次使用时默认关闭。
- 集成 Color-the-ducts，按液体颜色显示导管内容，并支持悬停模式、整条连接导管、缩放和透明度调整。
- 集成 LogicSugar，提供结构化逻辑语句、编辑器替换、编译辅助、跳转线着色和积木颜色设置，并保留旧版 LCanvas 兼容性。
- 集成 Random，在下一次进入世界时随机化数据库、战役和液体描述及游戏提示，并随机化方块、物品、单位和战役区块图标，不改变逻辑行为。
- 将 PatchViewer 更新至 2.3.0，改进原生内容窗口中的文本、属性和资源堆叠差异显示，同时保留原有组件布局与交互。
- 改进 ForeignServerTranslator 对不同 Mindustry `JoinDialog` 字段布局及本地、远程服务器列表的兼容性。
- 完善聚合模块的统一设置接入、初始化隔离和模块状态统计，降低单个模块初始化失败对其他功能的影响。

## English

- Adds BetterRTSFormation, BetterTerrainGen-V2, AutoPruner, Color-the-ducts, LogicSugar, and Random to the Neon aggregate, expanding it to 25 client-side utility modules.
- Integrates BetterRTSFormation with improved RTS control-group creation, selection, and recognition, including formation badges, strict selection, and controls outside command mode.
- Integrates BetterTerrainGen-V2 and AutoPruner: the map editor gains the Natural Water filter, while AutoPruner can remove redundant power nodes or buildings by rule through separate hotkeys and starts disabled on first use in the aggregate.
- Integrates Color-the-ducts to display liquid colors on ducts, with hover mode, connected-line coloring, scale, and opacity controls.
- Integrates LogicSugar with structured logic statements, editor replacement, compilation helpers, jump-line coloring, block colors, and legacy LCanvas compatibility.
- Integrates Random to randomize database, campaign, and liquid descriptions plus instructional tooltips for the next world, as well as block, item, unit, and campaign-sector icons without changing logic behavior.
- Updates PatchViewer to 2.3.0, improving text, stat, and resource-stack diffs in native content dialogs while preserving their original layout and interaction.
- Improves ForeignServerTranslator compatibility with different Mindustry `JoinDialog` field layouts and local or remote server lists.
- Improves unified settings integration, isolated module initialization, and bundled-module state reporting so one initialization failure is less likely to affect other features.

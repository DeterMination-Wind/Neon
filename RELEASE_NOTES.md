# Neon v11.0.0

本说明汇总 `v10.3.1` 之后至 `v11.0.0` 的全部提交。

## 中文

- Neon 聚合包从 19 个客户端工具扩展到 25 个，加入 BetterRTSFormation、BetterTerrainGen-V2、AutoPruner、Color-the-ducts、LogicSugar 和 Random，并统一接入聚合设置、初始化隔离、模块状态统计与子模组版本锁定。
- 集成 BetterRTSFormation：改进 RTS 编队的创建、选择和识别，支持编队角标、严格选择以及非指挥模式下的编队操作。
- 集成 BetterTerrainGen-V2：为地图编辑器地形生成器增加 Natural Water 自然水体滤镜。
- 集成 AutoPruner：通过独立快捷键清理多余电力节点或按放置时间窗口清理建筑；聚合版首次使用时默认关闭，并保留已有设置行为。
- 集成 Color-the-ducts：按液体颜色显示导管内容，支持悬停模式、整条连接导管、缩放和透明度调整；聚合版首次使用时默认关闭。
- 集成 LogicSugar：提供结构化逻辑语句、编辑器替换和编译辅助，并保留跳转线着色、积木颜色设置以及旧版 LCanvas 兼容性。
- 集成 Random v1.1.0：在下一次进入世界时随机化数据库、战役和液体描述及游戏提示，并随机化方块、物品、单位和战役区块图标，不改变逻辑行为；同时补充聚合版使用说明。
- 将 PatchViewer 更新至 2.3.0，改进原生内容窗口中的文本、属性和资源堆叠差异显示，同时保留原有布局与交互。
- 改进 ForeignServerTranslator 对不同 Mindustry `JoinDialog` 字段布局及本地、远程服务器列表的兼容性。
- 完善聚合模块的统一设置接入、初始化隔离、使用状态统计和中英文资源，并同步模块清单、README 文档和子模组锁定信息。
- 修复 PinyinSearchSupport 在星球、地图和蓝图等原生结果列表被重建后未重新应用过滤的问题；蓝图搜索保留数字查询，并增加重开窗口与列表替换回归测试。
- 完善 ServerPlayerDataBase 的中英文界面文案，覆盖设置、命令、查询/调试、语义搜索、完整性校验和导入导出流程。
- 为 ServerPlayerDataBase 文件导入导出增加对 v159 `FileChooser` 与旧版平台选择器的兼容，并将语义索引状态改为可本地化文案。
- 将 Tripwire 创建/删除快捷键默认设为未设置，避免安装后占用意外按键。

## English

This release summarizes every commit after `v10.3.1` through `v11.0.0`.

- Expands the Neon aggregate from 19 to 25 client-side tools by adding BetterRTSFormation, BetterTerrainGen-V2, AutoPruner, Color-the-ducts, LogicSugar, and Random, with unified settings integration, isolated initialization, module status reporting, and locked bundled revisions.
- Integrates BetterRTSFormation with improved RTS control-group creation, selection, and recognition, including formation badges, strict selection, and controls outside command mode.
- Integrates BetterTerrainGen-V2 and adds the Natural Water filter to the map editor terrain generator.
- Integrates AutoPruner with separate hotkeys for redundant power nodes and placement-time-based building cleanup; it starts disabled on first use in the aggregate while preserving existing settings.
- Integrates Color-the-ducts to display liquid colors on ducts, with hover mode, connected-line coloring, scale, and opacity controls; it starts disabled on first use in the aggregate.
- Integrates LogicSugar with structured logic statements, editor replacement, compilation helpers, jump-line coloring, block colors, and legacy LCanvas compatibility.
- Updates the bundled Random module to v1.1.0. It randomizes database, campaign, and liquid descriptions plus instructional tooltips for the next world, and randomizes block, item, unit, and campaign-sector icons without changing logic behavior; bundled usage documentation is included.
- Updates PatchViewer to 2.3.0, improving text, stat, and resource-stack diffs in native content dialogs while preserving the original layout and interaction.
- Improves ForeignServerTranslator compatibility with different Mindustry `JoinDialog` field layouts and local or remote server lists.
- Completes unified settings integration, isolated module initialization, usage-state reporting, bilingual resources, module-list documentation, README updates, and bundled submodule lock metadata.
- Fixes PinyinSearchSupport losing its filter when native planet, map, or schematic result lists are rebuilt. Numeric schematic queries remain eligible, with regression coverage for reopening dialogs and replacing result cards.
- Completes bilingual UI text for ServerPlayerDataBase across settings, commands, query/debug panels, semantic search, integrity checks, and import/export flows.
- Adds ServerPlayerDataBase file import/export compatibility for both the v159 `FileChooser` API and the legacy platform chooser, and makes semantic-index status messages localizable.
- Sets Tripwire create/delete keybinds to unset by default to avoid claiming unintended keys after installation.

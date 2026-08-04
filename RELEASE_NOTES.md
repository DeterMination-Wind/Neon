# Neon v11.0.0

## 中文

- 修复 PinyinSearchSupport 在原生结果列表被重建后未重新应用过滤的问题，覆盖星球、地图和蓝图等场景；蓝图搜索保留数字查询，并新增重开窗口与列表替换回归测试。
- 完善 ServerPlayerDataBase 的中英文界面文案，覆盖设置、命令、查询/调试、语义搜索、完整性校验和导入导出流程。
- 为 ServerPlayerDataBase 文件导入导出增加对 v159 `FileChooser` 与旧版平台选择器的兼容，并将语义索引状态改为可本地化文案。
- 将 Tripwire 创建/删除快捷键默认设为未设置，避免安装后占用意外按键。

## English

- Fixes PinyinSearchSupport losing its filter when native result lists are rebuilt. Planet, map, and schematic views are covered; numeric schematic queries remain eligible, with regression coverage for reopening dialogs and replacing result cards.
- Completes bilingual UI text for ServerPlayerDataBase across settings, commands, query/debug panels, semantic search, integrity checks, and import/export flows.
- Adds file import/export compatibility for both the v159 `FileChooser` API and the legacy platform chooser, and makes semantic-index status messages localizable.
- Sets Tripwire create/delete keybinds to unset by default to avoid claiming unintended keys after installation.

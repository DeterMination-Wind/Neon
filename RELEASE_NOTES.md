# Neon v10.1.3

## 中文

- 合并 PinyinSearchSupport v2.3.1：修复游戏内编辑器 HUD 方块搜索无法使用拼音的问题；局部搜索面板可安全过滤，同时不会跨越 HUD、菜单或不相关结果列表。
- 新增安全 `DataImagePacker` 兼容层，处理 v159 数据补丁 atlas 的转移、卸载和重复安装；覆盖地图/状态重置以及多类加载器场景，降低退出编辑器或切换状态时的资源残留与崩溃风险。
- 增加 atlas 生命周期与拼音搜索场景树回归测试。

## English

- Bundles PinyinSearchSupport v2.3.1, fixing pinyin search in the in-game editor HUD block picker. Local panels filter safely without crossing HUD, menu, or unrelated result-list boundaries.
- Added a safe `DataImagePacker` compatibility layer for v159 data-patch atlases. It handles transfer, unload, and repeated installation across map/state resets and multiple class loaders, reducing resource leaks and exit-time crashes.
- Added regression coverage for atlas lifecycle handling and pinyin-search scene scopes.

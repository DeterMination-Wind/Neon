# B12.3

## 中文

- 修复仅启用 Neon 时 Stealth Path 窗口未注册为 OverlayUI 窗口的问题；现在会通过兼容桥接注册模式、伤害、控制和悬停 DPS 四个窗口，不支持 OverlayUI 时仍保留 HUD 回退。
- 合并 LogicSugar v2.1.6：新增在 MindustryX 变量浏览器中隐藏 `__ls_*` 内部变量和 `_0`、`_1` 等表达式临时变量的设置（仅影响显示，不影响逻辑执行、存档或网络同步）。
- 优化 Logic Sugar `for` 语句编辑器的字段布局与文案，并增加 `SugarCompiler.isSugarProgram(String)` 程序识别辅助 API。
- 补全菲律宾语、印尼语、日语、巴西葡萄牙语、欧洲葡萄牙语和俄语资源，并同步新增 Logic Sugar 文案的中英文及简繁中文翻译。

## English

- Fixed Stealth Path windows not being registered as OverlayUI windows when only Neon is enabled. The mode, damage, controls, and hover-DPS windows now use the compatibility bridge, while the HUD fallback remains available when OverlayUI is unsupported.
- Bundled LogicSugar v2.1.6, adding a setting to hide `__ls_*` internal variables and `_0`, `_1`, and other expression temporaries from MindustryX's variable browser. This only changes display and does not affect execution, saves, or network sync.
- Improved the Logic Sugar `for` statement editor layout and labels, and added the `SugarCompiler.isSugarProgram(String)` helper for identifying Logic Sugar programs.
- Completed Filipino, Indonesian, Japanese, Brazilian Portuguese, European Portuguese, and Russian resources, including the new Logic Sugar strings in English and Simplified/Traditional Chinese.

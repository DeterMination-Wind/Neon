# B12.6

## 中文

- 同步 LogicSugar v2.2.0（新功能）：错误的 `return` 语句（如位于函数外）标红提示；鼠标悬停语句显示简短用途说明；语句搜索框输入时高亮匹配；设置页排版对齐优化。
- 同步 LogicSugar v2.2.0（修复与改进）：打开编辑器前校验存档代码，损坏内容给出明确提示，消除极端情况下程序被意外清空的隐患；修复函数调用参数 / 返回值含引号等特殊字符时保存后损坏；修复函数内打印文本被自动改写；修复函数库中 `memory1` 等存储设备罕见被误改名；编辑器遇到损坏代码不再崩溃；编辑大型程序与读取函数库的性能优化；修复按键重复触发、数据残留累积等稳定性问题；与 Neon 捆绑时设置项不再重复出现；内部回归测试全面恢复并新增针对性用例。
- 重做 ForeignServerTranslator 菜单翻译：改由反射调用本机菜单系统（兼容新版 Mindustry 将菜单 API 从 `UI` 移至 `mindustry.ui.Menus` 的变更，保留旧版回退），不再用克隆对话框模拟；菜单选项文本也纳入翻译判定；信息弹窗隐藏时正确清理。
- PatchViewer 紧凑对比排版优化：宽度足够时「旧值 -> 新值」保持同一行，放不下才换行，箭头始终留在旧值一侧；build cost 堆叠与原子统计流同样处理。
- 版本号 120005 → 120006。

## English

- Bundled LogicSugar v2.2.0 (new features): out-of-place `return` statements (such as outside a function) are highlighted in red; hovering a statement shows a short hint; the statement search box highlights matches while you type; settings page alignment was cleaned up.
- Bundled LogicSugar v2.2.0 (fixes and improvements): the editor validates saved code before opening and shows a clear message for corrupted content, eliminating a rare case where a program could be silently wiped; arguments / return values containing quotes or special characters no longer get corrupted on save; printed text inside functions is no longer rewritten; a rare mis-rename of `memory1`-style storage devices in the function library is fixed; corrupted code no longer crashes the editor; editing large programs and loading the function library are faster; duplicated key handling and stale data accumulation are fixed; settings no longer appear twice when bundled with Neon; internal regression tests are fully restored with new targeted cases.
- Reworked ForeignServerTranslator menu translation: menus now delegate to the native menu system via reflection (handling the newer Mindustry move of the menu API from `UI` to `mindustry.ui.Menus`, with a legacy fallback) instead of a cloned fake dialog; menu option text is also checked for translatability; hidden info popups are cleaned up properly.
- PatchViewer compact diff layout: "old -> new" stays on one line when it fits and only wraps when it does not, with the arrow always on the old-value side; build-cost stacks and atomic stat flows follow the same rule.
- Version code 120005 → 120006.

# B12.5

## 中文

- 合并 LogicSugar 输入框聚焦修复与 Ctrl 复制开关（对应独立版 v2.1.8）：语句输入框 / 表达式编辑器点击后可正常聚焦；新增 "Ctrl+点击 = 复制积木" 与 "Ctrl+拖动 = 复制积木" 两个设置开关，默认开启，可单独关闭。
- 同步 PatrolCancel v1.0.1：显式实现全部 InputProcessor 方法，修复 Android 上 touchDragged 等回调触发 AbstractMethodError 的崩溃。
- Android 打包：dexAndroid 增加 d8 --lib arc-core，确保 InputProcessor 接口默认方法在设备上可解析。
- 版本号 120004 → 120005。

## English

- Bundled the LogicSugar input-focus fix and Ctrl copy switches (standalone v2.1.8): clicking statement text fields / the expression editor now takes focus correctly; two new settings "Ctrl+Click = Copy Statement" and "Ctrl+Drag = Copy Statements" are on by default and individually toggleable.
- Synced PatrolCancel v1.0.1: all eight InputProcessor methods are now declared explicitly, fixing an Android AbstractMethodError on callbacks such as touchDragged.
- Android packaging now passes d8 --lib arc-core so InputProcessor default methods resolve on device.
- Version code 120004 → 120005.
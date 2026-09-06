# N13

## 中文

首个稳定版 N 系列版本。本次合并 LogicSugar 两个版本（v3.1.0、v4.0.0 大版本）、新增内置 OverlayCompatBridge 子模组，并同步 PatchViewer 修复。

- LogicSugar v4.0.0（大版本）：
  - 新增断言语句（「断言」分类七张卡片）：断言边界（检查数组下标是否越界、是否为整数/倍数）、断言相等（变量值与期望不符即停机提示）、记录打印位置 + 断言打印（成对验证某段代码的打印输出，通过后自动清理）、运行错误（立即停机并显示带变量值的消息）、写日志（写入游戏日志文件）、断点（条件满足时冻结整个游戏供检查现场，继续后照常运行）。默认只在编辑器中存在，保存的代码不含断言。
  - 新增「调试断言构建」开关（仅单机/地图编辑器生效）：打开后断言才作为真实指令运行；联机时自动关闭，保存的程序永远与原版客户端兼容。
  - 处理器状态指示：停机的处理器头顶显示"已停在第 N 条"，长等待显示进度圆环，运行出错原地显示消息；等待阈值、检查频率、提醒特效可在设置中调节。
  - 编辑器新增「复制变量」（全部变量按名称排序整理成表格，保留完整精度，可直接粘贴进电子表格）与「复制打印缓冲」两个按钮。
- 同步 LogicSugar v3.1.0：语句卡片改用确定性布局，消除布局跳动；For 固定两行排版；深缩进下条件行不再被顶出可视区；修复输入框下划线变白。
- 新增内置 OverlayCompatBridge 子模组：为使用 MindustryX OverlayUI 界面的 Java 模组提供原版界面回退，未安装 MindustryX 时相关界面仍可正常显示。
- 同步 PatchViewer v2.4.1：修复数值对比文本重叠。
- 版本号 120006 → 130000。

## English

The first stable N-series release. This update bundles two LogicSugar versions (v3.1.0 and the v4.0.0 major release), adds the new built-in OverlayCompatBridge sub-mod, and picks up a PatchViewer fix.

- LogicSugar v4.0.0 (major):
  - New assertion statements (seven cards in the new "Assertions" category): Assert Bounds (catches out-of-range array indexes, non-integers, wrong multiples), Assert Equals (stops with a message when a value differs from what you expect), Assert Flush + Assert Prints (verify a section's printed output as a pair, cleaning it up afterwards), Error (halts with a message embedding variable values), Log (writes to the game log file), Breakpoint (freezes the whole game for inspection, resumes cleanly). Assertions live only in the editor by default and never enter saved code.
  - New "Debug Assert Build" toggle (single-player / map editor only): assertions run for real only with it enabled; it switches itself off in multiplayer, so everything you save stays vanilla-compatible.
  - Processor status on the map: stopped processors show "Stopped at #N", long waits draw a progress ring, failures show their message in place; threshold, scan rate and warning effects are adjustable in settings.
  - Two new editor buttons: "Copy Variables" (all variables sorted by name as a full-precision table for spreadsheets) and "Copy Print Buffer".
- Bundled LogicSugar v3.1.0: statement cards switched to deterministic layouts, eliminating layout jitter; fixed two-row For form; condition rows stay visible under deep nesting; white text-field underlines fixed.
- New built-in OverlayCompatBridge sub-mod: Java mods using the MindustryX OverlayUI fall back to vanilla-style interfaces when MindustryX is not installed.
- Bundled PatchViewer v2.4.1: fixed overlapping stat diff text.
- Version code 120006 → 130000.

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
# Neon B11.7

本说明汇总 `B11.6` 之后至 `B11.7` 的全部提交。

## 中文

- **LogicSugar 更新至 v2.1.2（内置逻辑辅助模块）**：
  - `call foo(...)` 实参为空时，灰色占位提示现在显示**被调函数的参数列表**（如 `data1, count2, block3`），不再是一成不变的 `a, b+1`；
  - 提示实时跟随：改名或修改函数定义后立即更新，重开编辑器后依然正确；
  - 解析顺序与编译器一致：本地 `func` 定义优先，全局函数库兜底；函数不存在或无参数时退回通用提示；
  - 附带 v2.1.1 兼容性修复：表达式编译器改用 Java 8 兼容集合写法（`Set.of` → 不可变 `HashSet`），修复 Android 11 以下设备上逻辑表达式相关崩溃。

## English

This release summarizes every commit after `B11.6` through `B11.7`.

- **LogicSugar updated to v2.1.2** (bundled logic-assist module):
  - when a `call foo(...)` statement has empty arguments, the gray placeholder now shows the **parameter list of the called function** (e.g. `data1, count2, block3`) instead of the static `a, b+1`;
  - the hint follows live: rename the function or edit its definition and the placeholder updates immediately; it stays correct after reopening the editor;
  - resolution order matches the compiler: local `func` definitions win, the global function library is the fallback; unknown functions and functions without parameters fall back to the generic hint;
  - includes the v2.1.1 compatibility fix: the expression compiler now uses Java 8-compatible set construction (`Set.of` → unmodifiable `HashSet`), fixing crashes related to logic expressions on Android below 11.

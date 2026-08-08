# Neon B11.3

本说明汇总 `B11.2` 之后至 `B11.3` 的全部提交。

## 中文

- **合并 LogicSugar 2.0（函数支持）**：逻辑编辑器新增函数积木（`Func Def` / `Func Call` / `Return`），支持参数（完整表达式）、可选返回值、void 函数、提前 `return` 与函数间互相调用（递归编译报错）；新增全局函数库，定义一次、任意处理器可调用，库函数不会修改调用方变量（写入变量自动混淆）；可在设置或编辑器工具栏打开函数库，直接在处理器编辑器中编辑，关闭时自动校验保存，保存失败自动重开且修改不丢失；函数模式（`normal` 子程序 / `inline` 内联）与函数库入口已并入 Neon 总设置。
- 地理围栏报警（Tripwire）新增位移保护；Better RTS Formation 新增删除编队的拖拽手势；betterHotKey 修复数字键重映射冲突。

## English

This release summarizes every commit after `B11.2` through `B11.3`.

- **Bundles LogicSugar 2.0 (function support)**: the logic editor gains function blocks (`Func Def` / `Func Call` / `Return`) with parameters (full expressions), optional return values, void functions, early `return`, and mutual calls (recursion is a compile error); a global function library lets you define functions once and call them from any processor without touching caller variables (written names are auto-mangled); the library is edited inside the processor editor (from Settings or the editor toolbar) and is validated and saved on close — failed saves reopen the editor with your work kept; function mode (`normal` subroutine / `inline`) and the library entry are merged into the Neon settings page.
- Tripwire gains a geofence shift guard, Better RTS Formation adds a delete-formation drag gesture, and betterHotKey fixes a number-key remap conflict.

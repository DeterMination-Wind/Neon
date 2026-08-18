# B12.2

## 中文

- 合并 LogicSugar v2.1.5。
- 修复逻辑编译器错误优化涉及 `@counter` 的操作：读取或写入处理器指令指针时，优化器现在会保留原始操作，避免改变控制流。
- 增加 `@counter` 优化屏障回归测试。

## English

- Bundled LogicSugar v2.1.5.
- Fixed incorrect optimization of operations that touch `@counter`: reads and writes of the processor instruction pointer now preserve the original operation, preventing control-flow changes.
- Added a regression test for the `@counter` optimization barrier.

# Neon B11.5

本说明汇总 `B11.4` 之后至 `B11.5` 的全部提交。

## 中文

- **修复 Better RTS Formation 的 Android 按键崩溃**（更新至 1.0.3）：
  - 输入处理器此前只实现了 3 个接口方法，其余依赖接口默认实现；Android 打包后默认方法不可用，任意按键即抛 `AbstractMethodError: InputProcessor.keyDown` 崩溃；
  - 本版补全全部 8 个接口方法且行为完全不变（均返回 `false`，事件传播与桌面端一致），桌面端与 Android 均不再依赖接口默认实现。

## English

This release summarizes every commit after `B11.4` through `B11.5`.

- **Fix Better RTS Formation Android key-press crash** (updated to 1.0.3):
  - the input processor previously implemented only 3 of the 8 interface methods and relied on the interface default implementations, which are unavailable in the Android dex — any key press crashed with `AbstractMethodError: InputProcessor.keyDown`;
  - this release implements all 8 methods with unchanged behavior (all return `false`, so event propagation matches desktop); neither desktop nor Android relies on interface default methods anymore.

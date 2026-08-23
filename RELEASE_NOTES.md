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
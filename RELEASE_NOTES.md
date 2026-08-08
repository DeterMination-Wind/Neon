# Neon B11.4

本说明汇总 `B11.3` 之后至 `B11.4` 的全部提交。

## 中文

- **LogicSugar 更新至 2.1（sugar 源码存活修复 + 多人协作）**：
  - sugar 源码持久化：编译产物末尾新增 `set __ls_sugar "<base64 源码>"` 载体指令，经原版 parse→save 往返后依然存活——无模组玩家打开再关闭逻辑编辑器，sugar 源码不再丢失；
  - 库函数嵌入：用到库函数时嵌入 `set __ls_lib "<base64(函数子集)>"`，嵌入式库优先、本地库补缺，任何机器（含缺库账号）重编译结果一致；
  - 外部修改检测：打开处理器时用两种函数模式重新编译恢复的 sugar 并与存储代码归一化比对，不一致时显示编译代码并提示；
  - 并发协作保护：画布未改动而方块代码在打开期间被他人修改时，关闭不再静默覆盖（最后写入者赢，与原版一致）；
  - 16KB 存储上限兜底：保存前预压缩检查，超限自动剥注释标记重试，仍超限则明确报错并保留本地草稿；
  - 完全兼容 v2.0.0 已存方块与旧版模组。

## English

This release summarizes every commit after `B11.3` through `B11.4`.

- **LogicSugar updated to 2.1 (sugar source survival + multiplayer collaboration)**:
  - persistent sugar source: compiled output now carries the source in a real `set __ls_sugar "<base64>"` statement that survives the vanilla parse→save round trip, so a no-mod player opening and closing the logic editor no longer wipes the sugar source;
  - embedded library subset (`set __ls_lib "..."`): embedded functions take priority and local functions fill the gaps, so recompilation is identical on any machine (even without the local library file);
  - external-edit detection: on open the restored sugar is recompiled in both function modes and compared (normalized) against the stored code; mismatches show the compiled code with a hint;
  - concurrent editing protection: closing an untouched canvas no longer clobbers saves made by others while the editor was open (last writer wins, same as vanilla);
  - 16KB storage limit fallback: a pre-save compression check strips the comment marker and retries; if still over the limit it fails loudly and keeps the local draft;
  - fully compatible with v2.0.0 stored blocks and older mod versions.

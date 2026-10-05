> [!NOTE]
> 需要 **Mindustry v160.1+**（桌面 / Android）或对应的 MindustryX；服务器不需要安装。
> Requires **Mindustry v160.1+** (desktop / Android) or a matching MindustryX. Servers do not need it.

## 中文

### 本次新增

- **内置 LogicSugar 升级到 v5.8.0**，逻辑调试能力新增下面几项。
- **性能分析器**：统计每条逻辑指令的执行次数或消耗的指令预算，并给出分支比例与代码覆盖率；从变量界面的 📊 按钮或 `profile` 指令打开，数据只留在本机客户端，不影响游戏运行。
- **快照支持逐指令录制**：录制快照会保存初始状态与之后 N 条指令，每条指令一份子快照，并默认只显示该指令用到的变量；连通快照会一并收录处理器正在控制的单位。
- **变量 / 内存 / 属性界面可以把数值导出到文件，或从文件导入**。

### 本次修复

- **StealthPath 悬浮窗关闭后不再自己回来**（在 MindustryX 里关掉的窗口，重启后保持关闭）
- **变量界面里含 `[` 的字符串按原文显示，超长字符串不再拖慢界面**（过长的值截断显示）

## English

### Added

- **Bundled LogicSugar is now v5.8.0**, adding the logic-debugging capabilities below.
- **Profiler**: counts each logic instruction's executions or consumed instruction budget, plus branch ratio and code coverage; open it from the 📊 button in the vars dialog or the `profile` instruction — the data stays on your own client and never affects the game.
- **Snapshots can now record instruction by instruction**: a recording snapshot stores the initial state and the next N instructions, one sub-snapshot each, showing only the variables that instruction uses by default; connected snapshots also capture the units the processor is controlling.
- **The vars / memory / sensor dialogs can export values to a file and import them back**.

### Fixed

- **StealthPath overlay windows no longer come back after you close them** (windows closed in MindustryX stay closed across restarts)
- **Strings containing `[` show literally in the vars dialog, and over-long values no longer slow the UI down** (long values are truncated for display)

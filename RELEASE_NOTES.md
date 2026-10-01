**欢度国庆！
Happy National Day!**

> [!NOTE]
> 需要 **Mindustry v160.1+**（桌面 / Android）或对应的 MindustryX；服务器不需要安装。
> Requires **Mindustry v160.1+** (desktop / Android) or a matching MindustryX. Servers do not need it.

## 中文

### 本次新增

- **内置 LogicSugar 升级到 v5.6.0**，逻辑编辑器新增下面几项能力。
- **新增「断言」积木**：直接写条件，条件不成立时程序停在失败处并显示消息，消息里可用 `{1}` / `{变量名}` 引用实际值
- **新增「变量 / 内存 / 属性」界面**：连点方块三次、或点内存块与处理器的配置面板即可打开，实时查看处理器变量、内存槽位与任意方块的可感知属性
- **新增「快照」**：给处理器、内存块或整个逻辑网络留档，随时回看并按需恢复；失败断言与断点可自动留档
- **内存块可在界面上编辑**：一键清空，或把内容复制到剪贴板 / 从剪贴板导入
- **新增内置子模组「建造透视（作者 Miner）」**：建造、拆除或框选蓝图时，被预览遮住的单位、子弹与特效渐隐，鼠标周围保留一块透明区
- **内置 LogicSugar 同时带来 v5.5.0 的能力**：用表达式把多个内存块拼成一段连续地址、单位控制积木（认领 / 循环控制 / 解控）、文本预览支持 `:name:` 图标

### 本次修复

- **MindustryX 上不再多出一个齿轮按钮**：OverlayCompatBridge 不会与 Overlay 按钮重复绘制
- **内置 LogicSugar 的几处逻辑修正**：`||` 按「不是 0 就算真」判断；`if` / `elif` / `switch` 的严格相等与分支选择不再混淆；循环很多时保存不再超出原版能识别的跳转标签数

## English

### Added

- **Bundled LogicSugar is now v5.6.0**, adding the following capabilities to the logic editor.
- **New `assert` block**: write a condition directly; on failure the program stops there with a message, where `{1}` / `{variable}` expand to the actual values
- **New Vars / Memory / Properties screens**: triple-tap a block or use the config panel of a memory block or processor to inspect processor variables, memory slots and any block's senseable properties live
- **New snapshots**: capture a processor, a memory block or the whole logic network, review them at any time and restore on demand; failed assertions and breakpoints can capture automatically
- **Memory blocks are editable in the UI**: clear them, copy their contents to the clipboard, or import values back from it
- **New bundled sub-mod "Build X-ray (by Miner)"**: units, bullets and effects hidden by a build preview fade out, with a transparent circle kept around the cursor
- **Bundled LogicSugar also brings the v5.5.0 capabilities**: combine several memory blocks into one continuous address range with an expression, unit control blocks (claim / loop / release) and `:name:` icons in the text preview

### Fixed

- **No duplicate gear button on MindustryX**: the OverlayCompatBridge no longer draws on top of the Overlay button
- **Several logic fixes in the bundled LogicSugar**: `||` now treats any non-zero value as true; strict equality and branch selection in `if` / `elif` / `switch` no longer mix up values; saving a program with many loops no longer exceeds the jump labels vanilla can recognize

> [!IMPORTANT]
> Neon 是纯客户端模组，服务器不需要安装；支持 Mindustry v159+（桌面 / Android）与 MindustryX。
> 本版内置的 LogicSugar v5.2.0 以 Mindustry v160.1 为基线：它的数据结构卡等依赖 v160 内存语义的功能在 v159 上不保证正确。
>
> Neon is client-side only; servers do not need it. It supports Mindustry v159+ (desktop / Android) and MindustryX.
> The bundled LogicSugar v5.2.0 targets Mindustry v160.1: its data-structure cards and other features that rely on v160 memory semantics are not guaranteed on v159.

> [!NOTE]
> 本版把内置的 LogicSugar 从 v4.0.0 升级到 v5.2.0：新增数据结构卡与表达式读写、68 个运算改名成 C++ STL 风格、修复表达式卡的三处缺陷，并带上单位 flag 显示、断点行为开关、撤销重做与底栏自动换行；另外同步 betterLogisticsSpeed 的空指针修复，并校正描述符版本号。
>
> This release moves the bundled LogicSugar from v4.0.0 up to v5.2.0: new data-structure cards with expression read/write sugar, 68 operations renamed to C++ STL style, three Expr card fixes, plus the unit flag overlay, breakpoint switches, undo/redo and bottom-bar row packing. It also picks up a betterLogisticsSpeed null-pointer fix and a descriptor version correction.

## 中文

* LogicSugar v5.2.0：数据结构卡（`array`、`matrix`、`record`、`stack`、`queue`、`deque`、`bitset`、`map`、`uset`、`list`、`heap`、`chain`）把内存块区间登记成结构化数据，每种结构有独立操作卡；表达式里可直接写 `buf[i]`、`m.get(k)` 这样的读写。声明与操作全部降级为普通原版指令，没装模组的客户端照常运行，已保存的逻辑程序也能恢复成卡片。
* LogicSugar v5.2.0：68 个数据结构运算改用 C++ STL 风格命名（`spush` → `stack_push`、`mapset` → `map_set`、`sum` → `array_sum` 等），旧短名继续可解析，改名不改变降级产物。
* LogicSugar v5.2.0：表达式卡（Expr）修复三处缺陷——加号菜单插入后积木凭空消失、保存一次后掉回普通积木、显示多一个 `]`；单行表达式现在随载体多带一行注释标记，重开与撤销重做都能还原成表达式卡。
* LogicSugar：新增「数据类型」断言卡，断言分类共八张卡片；处理器状态指示与单位 flag 显示可在设置里调节，包括给不同 flag 值着不同颜色。
* LogicSugar：编辑器新增撤销 / 重做（电脑 `Ctrl+Z` / `Ctrl+Y`，手机底部按钮）、底栏按钮按窗口宽度自动换行、编译后指令条数与 1000 上限实时对照；函数库上限提升到 10000 条语句。
* **破坏性变更：** 13 个可失败的数据结构操作失败时返回 `-1`（旧提示写的是保持原长度、越界写入 0、不存在返回 0），照旧写的分支判断需要改成 `== -1`；`array_sort` / `array_sort_desc` / `array_find` / `array_copy` 的内置函数体在 v5.0 / v5.1 有改动，更早版本保存过、且用过它们的处理器重开时会回落到原版视图（可执行 mlog 不变，重新拖一次卡片即可恢复）。
* betterLogisticsSpeed：修复 160.2 上物流窗口缓存重置可能抛空指针的问题。
* 文案与描述符：并入 LogicSugar 新增的约 660 条界面文案；修正 `mod.json` 版本号仍停在 120006 的问题，与 `mod.hjson` / `build.gradle`（130000）保持一致；`FEATURES.md` 与 `docs/user` 的 LogicSugar 章节同步更新。

## English

* LogicSugar v5.2.0: data-structure cards (`array`, `matrix`, `record`, `stack`, `queue`, `deque`, `bitset`, `map`, `uset`, `list`, `heap`, `chain`) register memory ranges as structured data, each with its own operation cards; expressions can read and write them directly as `buf[i]` or `m.get(k)`. Declarations and operations lower to plain vanilla instructions, so unmodded clients keep working and saved programs reopen as cards.
* LogicSugar v5.2.0: all 68 data-structure operations were renamed to C++ STL style (`spush` → `stack_push`, `mapset` → `map_set`, `sum` → `array_sum`, and so on); old short names still parse and the rename does not change lowered output.
* LogicSugar v5.2.0: three Expr card fixes - the card vanishing after a palette insert, degrading to a plain block after one save, and one bracket too many in the display; single-line expressions now carry one comment marker so reopening and undo restore the card.
* LogicSugar: new Assert Type card, eight cards in the Assertions category; the processor status overlay and the unit flag overlay are configurable in settings, including a distinct color per flag value.
* LogicSugar: undo/redo in the editor (`Ctrl+Z` / `Ctrl+Y` on desktop, bottom-bar buttons on mobile), a bottom bar that wraps to the available width, a live compiled-instruction count against the 1000 limit, and a function library limit of 10000 statements.
* **Breaking:** the 13 fallible data-structure operations report `-1` on failure (the old tooltips said keeps its length, writes 0, or 0 when missing), so branches written against the old wording must compare `== -1`; the builtin bodies of `array_sort` / `array_sort_desc` / `array_find` / `array_copy` changed in v5.0 / v5.1, so processors saved by older builds that used them reopen in the vanilla view (the executable mlog is unchanged; re-place the card to restore the structured view).
* betterLogisticsSpeed: fixed a possible null pointer when the logistics window cache is reset on 160.2.
* Text and descriptor: pulled in about 660 new LogicSugar interface strings; fixed `mod.json` still reporting version 120006 instead of the 130000 used by `mod.hjson` / `build.gradle`; the LogicSugar sections of `FEATURES.md` and `docs/user` were updated.

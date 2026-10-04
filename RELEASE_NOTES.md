> [!NOTE]
> 需要 **Mindustry v160.1+**（桌面 / Android）或对应的 MindustryX；服务器不需要安装。
> Requires **Mindustry v160.1+** (desktop / Android) or a matching MindustryX. Servers do not need it.

## 中文

### 本次新增

- **内置 LogicSugar 升级到 v5.7.1**（下面的修复与改动都来自它）
- **OverlayUI 兼容层有了独立总开关**（和其它子模块一样，重启生效；关掉后本次会话由内置兜底实现接管）

### 本次修复

- **表达式卡里的数据 getter 与跨格内存下标保存、重开后不再退化成裸指令**（`stack.top()`、`queue.front()`、`deque.back()`、`x = buf[i]` 这类写法现在原样回来）

### 本次改动

- **移除 `@counter` 指示线**（写 `@counter` 的积木不再显示跳转徽标与候选目标幻影线）

## English

### Added

- **Bundled LogicSugar is now v5.7.1** (the fix and the change below come from it)
- **The OverlayUI compat layer has its own master switch** (like every other sub-module; restart to apply, and while it is off the built-in fallback serves the overlay for that session)

### Fixed

- **Data-structure getters and multi-cell span subscripts in expression cards no longer degrade into raw instructions after saving and reopening** (`stack.top()`, `queue.front()`, `deque.back()`, `x = buf[i]` now come back as written)

### Changed

- **Removed the `@counter` indicator line** (cards that write `@counter` no longer show a jump badge or candidate-target phantom lines)

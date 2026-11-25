> [!NOTE]
> 需要 **Mindustry v160.1+**（桌面 / Android，或对应的 MindustryX）。服务器无需安装。
> Requires **Mindustry v160.1+** (desktop / Android) or a matching MindustryX. Servers do not need it.

## 中文

### 新增功能

- **智能放置新增 4 项交叉开关**：传送带×传送带、传送带×管道、管道×传送带、管道×管道可以分别关闭，关掉的那类交叉保持原版行为。
- **智能放置可处理宽阻挡带**：管道线遇到 3 格及以上的交叉阻挡时整段改用物品管道桥，1~2 格仍放未配置的反向分类器。
- **智能放置可限制只在「任意」星球生效**：关闭后所有星球都套用智能放置规则。

### 问题修复

- **智能放置此前在 Neon 整包里静默不工作，现已修复**：混合科技星球上交叉线路会正常改放反向分类器。
- **智能拆除的「拆多余电力节点」改为按电网连通性判定**：网状电网会被削到只剩连通骨架，不再出现按键后一个都拆不掉的情况；并联备用线路也会被一并拆掉。

## English

### Added

- **Smart Placement gains four crossing switches**: conveyor×conveyor, conveyor×duct, duct×conveyor and duct×duct can each be turned off, and the disabled crossing keeps vanilla behavior.
- **Smart Placement handles wide blocked bands**: a duct line crossing three or more blocked tiles now uses a single item duct bridge, while one to two tiles still use an unconfigured inverted sorter.
- **Smart Placement can be limited to "Any" planets**: turn the restriction off to apply it on every planet.

### Fixed

- **Smart Placement no longer silently does nothing in the Neon bundle**: crossings on mixed-tech planets now correctly place an inverted sorter.
- **Auto Pruner's redundant power node key now works by grid connectivity**: dense power meshes are pruned down to a connectivity-preserving backbone instead of removing nothing, and parallel backup lines are removed too.

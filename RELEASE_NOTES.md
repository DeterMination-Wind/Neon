> [!NOTE]
> 需要 **Mindustry v160.1+**（桌面 / Android，或对应的 MindustryX）。服务器无需安装。
> Requires **Mindustry v160.1+** (desktop / Android) or a matching MindustryX. Servers do not need it.

## 中文

### 问题修复

- **电力节点改为按世界瓦片扫描收集**：上一版日志显示 `带电建筑 8、连线 0`，而图里明显有几十个节点，说明 `Groups.build` 没有给全（Mindustry 自己的编辑器代码也注明过 `Groups.build can be empty`）。现在顶点直接扫 `world.tiles` 的中心格，不再依赖这个分组。
- **诊断日志补上队伍分布**：每次按 `J` 会输出 `世界中心建筑 / Groups.build / 本队（带电）/ 图顶点 / 连线 / 队伍分布 / 非节点 / 受保护 / 不可拆 / 可拆节点`。两处建筑数不一致就说明 `Groups.build` 不完整；`队伍分布` 能直接看出那张电网是本队还是 `derelict`。

## English

### Fixed

- **Power nodes are now collected by scanning world tiles**: the previous log showed `powered buildings 8, links 0` while dozens of nodes were visible, meaning `Groups.build` was incomplete (Mindustry's own editor code notes that `Groups.build can be empty`). Vertices now come from the centre tiles of `world.tiles` instead of that group.
- **The diagnostic line now includes a team histogram**: every `J` press logs `world centres / Groups.build / own team (powered) / vertices / links / team histogram / non-nodes / protected / not breakable / prunable nodes`. A mismatch between the two building counts proves `Groups.build` is incomplete, and the histogram shows immediately whether the grid belongs to your team or to `derelict`.

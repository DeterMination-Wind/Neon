> [!NOTE]
> 需要 **Mindustry v160.1+**（桌面 / Android，或对应的 MindustryX）。服务器无需安装。
> Requires **Mindustry v160.1+** (desktop / Android) or a matching MindustryX. Servers do not need it.

## 中文

### 问题修复

- **智能拆除的电力网络不再只认自己队伍的建筑**：日志显示 `本队带电 8、图顶点 8、连线 0`，说明整张电网被队伍过滤挡在图外。现在顶点收集不再按队伍过滤，能否拆除改由 `validBreak` 决定——本队与 `derelict` 中立电网都能削，敌对建筑只会计入"不可拆"，不会被排队拆除。
- **连线判定补上显式 `power.links` 兜底**：除了 `Building.getPowerConnections`（相邻通电建筑 + 连线），再并一遍节点的显式连线，避免对方因模块或队伍原因没进顶点集时把节点误判成割点。
- **诊断日志更细**：`last_log.txt` 里每次按 `J` 会输出 `[AutoPruner] 诊断：世界建筑 …，本队带电 …，图顶点 …，连线 …；非节点 …，受保护 …，不可拆 …，可拆节点 …。`，第二行在本队没有可拆节点时给出本队带电方块样本，一次就能定位是版本、模块、队伍还是权限问题。

## English

### Fixed

- **Auto Pruner no longer limits the power graph to your own team**: the log showed `own powered 8, vertices 8, links 0`, i.e. the whole grid was filtered out by team. Vertices are now collected regardless of team, and whether a node may be removed is decided by `validBreak` — your own grid and neutral `derelict` grids can both be pruned, while hostile buildings are only counted as "not breakable" and never queued.
- **Explicit `power.links` are now unioned in**: besides `Building.getPowerConnections` (adjacent powered buildings plus links), each node's explicit links are added again, so a neighbour that is missing from the vertex set for module or team reasons no longer turns a node into a false cut vertex.
- **Richer diagnostics**: every `J` press now logs `[AutoPruner] diag: world buildings …, own powered …, vertices …, links …; non-nodes …, protected …, not breakable …, prunable nodes ….` to `last_log.txt`, and a second line samples your own powered blocks when nothing is prunable, so build/module/team/permission problems are distinguishable in one press.

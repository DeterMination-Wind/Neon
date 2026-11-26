> [!NOTE]
> 需要 **Mindustry v160.1+**（桌面 / Android，或对应的 MindustryX）。服务器无需安装。
> Requires **Mindustry v160.1+** (desktop / Android) or a matching MindustryX. Servers do not need it.

## 中文

### 问题修复

- **智能拆除的「拆多余电力节点」现在按游戏自己的电网关系判定**：边取自 `Building.getPowerConnections`（相邻通电建筑加上电力连线，和游戏建电力网的判定完全一致），不再漏掉"贴着电池/发电机"这类只靠相邻导电的节点，网状电网能稳定削到连通骨架。
- **埃瑞克的光束节点也会被处理**：`beam-node` 与 `beam-tower` 的网络同样可以按连通性削掉多余节点，此前只认塞尔普洛的 `power-node`。
- **每次按键会在日志里留下判定统计**：`last_log.txt` 中新增 `[AutoPruner] 电力网络：带电建筑 X，连线 Y，可拆节点 Z，判定多余 W。`，提示"没有找到可拆的多余电力节点"时能直接区分是版本没更新、模块没开，还是图上确实没有多余节点。

## English

### Fixed

- **Auto Pruner's redundant-node key now uses the game's own power connections**: edges come from `Building.getPowerConnections` (adjacent powered buildings plus explicit power links, the same relation Mindustry uses to build power graphs), so nodes that only conduct through a neighbouring battery or generator are no longer mistaken for required ones, and a dense mesh is reliably pruned down to a connected backbone.
- **Erekir beam nodes are handled too**: `beam-node` and `beam-tower` networks can now be pruned by connectivity; previously only Serpulo `power-node` blocks were considered.
- **Every press logs its graph statistics**: `last_log.txt` now gets `[AutoPruner] power network: buildings X, links Y, prunable nodes Z, redundant W.` When the mod reports that nothing was found, the line tells you whether the build is outdated, the module is off, or the map simply has no redundant nodes.

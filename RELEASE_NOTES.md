> [!NOTE]
> 需要 **Mindustry v160.1+**（桌面 / Android，或对应的 MindustryX）。服务器无需安装。
> Requires **Mindustry v160.1+** (desktop / Android) or a matching MindustryX. Servers do not need it.

## 中文

### 问题修复

- **智能拆除不再误删发电建筑**：判定加入发电豁免，只发电不耗电的建筑（沙盒 `power-source`、各类发电机）与当前发电量大于 0 的节点一律不参与拆除。沙盒电力源本身就是 `PowerNode` 的子类，之前会被当成多余节点删掉。
- **Erekir beam nodes are still prunable**: `beam-node` / `beam-tower` set both `outputsPower` and `consumesPower` (they buffer power), so the exemption keys on "producer only" and they keep taking part in the connectivity pruning instead of being mistaken for generators.
- **去掉按键时的调试日志**：按 `J` 不再往 `last_log.txt` 写电力网络诊断，只保留游戏内的拆除汇总提示。

## English

### Fixed

- **Auto Pruner no longer deletes power sources**: the requirement now exempts pure producers — blocks with `outputsPower && !consumesPower` (sandbox `power-source`, every generator), plus any node whose current power production is above zero. The sandbox power source is a `PowerNode` subclass, so it used to be treated as a redundant node.
- **Erekir beam nodes are still prunable**: `beam-node` / `beam-tower` set both `outputsPower` and `consumesPower` (they buffer power), so the exemption keys on "producer only" and they keep taking part in the connectivity pruning instead of being mistaken for generators.
- **The per-press debug logging is gone**: pressing `J` no longer writes power-network diagnostics into `last_log.txt`; only the in-game summary toast remains.

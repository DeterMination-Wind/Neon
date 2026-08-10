# Neon B11.6

本说明汇总 `B11.5` 之后至 `B11.6` 的全部提交。

## 中文

- **新增内置模块 LockAttack（锁定攻击）**（独立仓库 [DeterMination-Wind/LockAttack](https://github.com/DeterMination-Wind/LockAttack)，v1.0.0）：
  - 按锁定键（默认 `L`）锁定鼠标指向的敌方单位或建筑，直接控制的单位每帧强制瞄准并集火目标；
  - 选中的指挥单位通过原版 `Call.commandUnits` 网络命令路径收到一次性攻击命令（单机与多人均有效），目标死亡后游戏自动清除命令；
  - 再次按键切换目标，点空地或己方目标解锁，目标死亡/进迷雾/变友方自动解锁；
  - 视觉反馈：旋转锁定框、目标连线、可选目标血条（含名称与血量，设置中可开关）；
  - 键位为标准游戏键位，可在 `设置 → 控制` 中修改；聚合设置页新增“锁定攻击”分组；
  - 修复血条与文字的居中对齐问题（`Fill.rect` 为中心锚定，血条背景改为以目标为中心，血量填充左锚定）。

## English

This release summarizes every commit after `B11.5` through `B11.6`.

- **New bundled module: LockAttack** (standalone repo [DeterMination-Wind/LockAttack](https://github.com/DeterMination-Wind/LockAttack), v1.0.0):
  - tap the lock key (default `L`) to lock on to the enemy unit or building under the cursor; the directly controlled unit is forced to aim at and fire on the target every frame;
  - selected command units receive a one-shot attack order through the vanilla `Call.commandUnits` network path (singleplayer and multiplayer); the game clears the order automatically when the target dies;
  - tap again on another target to switch, tap empty ground or a friendly target to unlock, auto-unlock when the target dies, leaves fog, or turns friendly;
  - visual feedback: rotating lock box, target line, optional target HP bar (name + health, toggleable in settings);
  - the key is a standard game keybind, rebindable in `Settings -> Controls`; a new “Lock Attack” group appears in the Neon aggregate settings page;
  - fixed the HP bar/text centering (the bar background is now centered on the target while the health fill stays left-anchored).

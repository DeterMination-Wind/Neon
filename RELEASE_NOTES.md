> [!NOTE]
> 需要 **Mindustry v160.1+**（桌面 / Android）或对应的 MindustryX；服务器不需要安装。
> Requires **Mindustry v160.1+** (desktop / Android) or a matching MindustryX. Servers do not need it.

## 中文

### 本次修复

- **重启后 Overlay 面板尺寸错乱**：Neon 注册的悬浮窗口在未手动缩放过时会以零尺寸开始，面板塌陷、内容重叠，必须按 `Z` 才恢复；现在窗口显示后按内容补一次尺寸，重启即可直接正常显示。
- **内置 betterLogisticsSpeed 更新至 v2.0.2**：标记节点列表面板按内容定尺寸，重启后不再塌成零宽，内容变窄时也能收回。
- **内置 Radial Build Menu 更新至 v5.7.2**：移动端 Overlay 开关窗口重启后不再变成零尺寸。

### 本次改动

- **BetterScreenShot 面板改为按内容定尺寸**，不再保留手动缩放尺寸。

## English

### Fixed

- **Overlay panels keep their size after a restart**: Neon-registered windows that were never manually resized started at zero size, collapsing their content until `Z` was pressed; they are now packed from their content when shown, so a restart needs no workaround.
- **Bundled betterLogisticsSpeed is now v2.0.2**: the marked-node list sizes itself from its content and no longer collapses to zero width after a restart.
- **Bundled Radial Build Menu is now v5.7.2**: the mobile overlay toggle no longer becomes zero-sized after a restart.

### Changed

- **The BetterScreenShot panel now sizes itself from its content**; a previously saved manual size is dropped.

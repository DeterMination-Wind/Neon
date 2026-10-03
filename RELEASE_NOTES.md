> [!NOTE]
> 需要 **Mindustry v160.1+**（桌面 / Android）或对应的 MindustryX；服务器不需要安装。
> Requires **Mindustry v160.1+** (desktop / Android) or a matching MindustryX. Servers do not need it.

## 中文

### 本次新增

- **内置 LogicSugar 升级到 v5.7.0**，逻辑编辑器新增下面这项能力。
- **支持从原版 Mlog 重建「控制多个单位」「控制一个单位」等单位控制积木**
- **每个子模块都能在设置里单独开关**（关掉的模块在下次启动前完全不加载）

### 本次修复

- **表达式卡在编辑、复制与重开时不再闪烁、丢焦点或退化成普通积木**
- **FuncDef 的「返回」框填错值后逻辑编辑器不再打不开**（现在会标红）
- **移动端拖动不再需要先长按**（快速滑动即可拖动，小位移仍用长按微调）
- **在逻辑文本里写 `@counter = 0` 不再落成无效积木**

## English

### Added

- **Bundled LogicSugar is now v5.7.0**, adding the capability below to the logic editor.
- **Rebuild LogicSugar's unit-control blocks ("Control Units", "Control One Unit") from vanilla mlog**
- **Every sub-module has its own master switch in the settings** (a module switched off is not loaded at all until the next restart)

### Fixed

- **Expression cards no longer flicker, lose focus, or degrade into plain blocks while editing, copying, or reopening**
- **The logic editor no longer becomes unopenable after a wrong value in a FuncDef's return field** (it is marked red now)
- **Mobile drag no longer requires a long press first** (a quick swipe starts the drag; small movements still use the long-press fine adjustment)
- **`@counter = 0` written in the logic text view no longer ends up as an invalid block**

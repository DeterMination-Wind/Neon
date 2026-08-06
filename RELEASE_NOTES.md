# Neon B11.1

本说明汇总 `v11.0.0` 之后至 `B11.1` 的全部提交。

## 中文

- 更新内置 LogicSugar 至 v1.1.3：修复与 MindustryX 的兼容性——加载时替换逻辑编辑对话框后，旧对话框上的浮层面板（逻辑辅助器X）会一并消失；现在会将这些面板转移到新的 LogicSugar 对话框，使其在 LogicSugar 存在时恢复显示。同时修复删除 `jump` 跳转目标后残留引用导致逻辑编辑器崩溃的问题，并同步清理 `dest` 与 `destIndex`。
- 重构模组更新中心：移除旧的 `GithubUpdateCheck` 与 `RepoResolver`，改用新的 `GithubReleaseClient` 直接对接 GitHub Release，重写 `ModUpdateCenter` 的版本检测、更新列表与安装流程，并新增版本号回归测试。
- 采用新的发布版本命名（`N11` / `B11.1` 格式）与数字版本码；更新器同时兼容新格式、数字版本码与历史 `vX.Y.Z` 版本。

## English

This release summarizes every commit after `v11.0.0` through `B11.1`.

- Updates the bundled LogicSugar to v1.1.3: fixes MindustryX compatibility — after replacing the logic editor dialog at load time, floating overlay panels attached to the old dialog (Logic Support X) were discarded and disappeared; they are now transferred onto the new LogicSugar dialog so they reappear when LogicSugar is installed. Also fixes the logic editor crash caused by stale `jump` destination references after deleting a target statement, clearing both `dest` and `destIndex` during deletion.
- Refactors the mod update center: removes the old `GithubUpdateCheck` and `RepoResolver`, switches to a new `GithubReleaseClient` that talks to GitHub Releases directly, rewrites `ModUpdateCenter`'s version detection, release list, and install flow, and adds a version-code regression test.
- Adopts the new release naming scheme (`N11` / `B11.1`) with numeric version codes; the updater supports the new format, numeric version codes, and legacy `vX.Y.Z` versions.

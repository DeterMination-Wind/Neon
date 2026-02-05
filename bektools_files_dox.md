# Neon 文件说明（files dox）

本文件按“仓库根目录相对路径”列出 Neon 的主要文件/目录作用。

## 根目录

- `README.md`：玩家向说明（整合包内容、安装方式、构建方式、维护脚本）
- `build.gradle`：Gradle 构建脚本（zip/jar 输出与复制）
- `src/main/resources/mod.json`：Mindustry Mod 元数据（`name`/`version`/`main` 等）
- `.github/workflows/release.yml`：Release 工作流（tag 触发构建并上传 `zip`/`jar`）
- `bektools_overview_dox.md`：概览
- `bektools_files_dox.md`：文件说明
- `bektools_api_dox.md`：接口说明（整合包对外接口/子模组聚合方式）
- `bektools_sync_release_dox.md`：同步与发布流程

## Java 源码（`src/main/java/`）

### `bektools/`

- `bektools/BekToolsMod.java`
  - 主入口：创建 PGMM/SP/RBM 实例；合并设置页面；转发 client commands
- `bektools/GithubUpdateCheck.java`
  - Neon 自身更新检查（若存在）
- `bektools/ui/RbmStyle.java`
  - 用于设置页的统一样式与组件（Header/Spacer 等）

### `powergridminimap/`、`stealthpath/`、`radialbuildmenu/`

这三部分代码由 `tools/update_submods.py` 从本地子仓库同步合并而来：

- `powergridminimap/*`：Power Grid Minimap（PGMM）
- `stealthpath/*`：StealthPath（SP）
- `radialbuildmenu/*`：Radial Build Menu（RBM）

## 资源（`src/main/resources/`）

- `mod.json`：Mod 元信息
- `bundles/`：多语言文本（由三个子模组合并）

## 维护脚本（`tools/`）

- `tools/submods.json`：子模组同步配置（本地仓库路径、包目录、主类文件、bundles 目录等）
- `tools/submods.lock.json`：锁文件（记录每个子模组同步时的 git HEAD 与时间）
- `tools/update_submods.py`：同步脚本（不访问 GitHub，只读本地仓库）
- `tools/bektools-bundles/*`：Neon 自己的 bundles（不会来自子模组）


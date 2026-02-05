# Neon 文档（维护者/开发者）

本目录下的 `*_dox.md` 为 Neon 仓库级说明文档，统一放在仓库根目录，便于维护与发布。

## 1. 这个 Mod 做什么

Neon 是一个 **客户端工具整合包**（JavaMod），把以下 3 个子模组的源码合并在一个包里发布：

- Power Grid Minimap（PGMM）
- StealthPath（SP）
- Radial Build Menu（RBM）

## 2. 关键入口

- 主类：`src/main/java/bektools/BekToolsMod.java`
  - 将三个子模组的 `bekBundled` 置为 `true`
  - 实例化三个子模组
  - 将三个子模组的设置页面合并到 Neon 的一级分类下
- 子模组合并脚本：`tools/update_submods.py`
  - 读取 `tools/submods.json` 中配置的本地仓库路径
  - 以本地 git HEAD 为准同步源码与 bundles
  - 输出锁文件：`tools/submods.lock.json`

## 3. 构建与发布

- 本地构建：`./gradlew.bat --no-daemon clean jar jarAndroid`
  - 输出：`build/libs/Neon.zip`（桌面/通用）、`build/libs/Neon.jar`（Android）
- GitHub Actions 发布：`.github/workflows/release.yml`
  - 推送 tag（如 `v2.0.0`）触发自动构建并创建 Release

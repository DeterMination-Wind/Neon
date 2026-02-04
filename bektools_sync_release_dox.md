# BEK-Tools 同步与发布流程（sync/release dox）

## 1. 同步子模组（本地仓库 → BEK-Tools）

配置文件：`tools/submods.json`

同步脚本：`tools/update_submods.py`

典型流程：

```powershell
cd BEK-Tools
python tools/update_submods.py --check
python tools/update_submods.py
```

脚本行为要点：

- 以本地 git checkout 为唯一数据源（不访问 GitHub/远程）
- 同步 3 个子模组：
  - Java 包目录（如 `src/main/java/stealthpath`）
  - bundles（合并到 `src/main/resources/bundles`）
- 写入 `tools/submods.lock.json` 记录每个子模组的 git HEAD
- 注入 BEK-Tools 需要的聚合钩子（`bekBundled`、`bekBuildSettings`）

## 2. 版本号更新

每次发布需要至少更新：

- `build.gradle` 的 `version`
- `src/main/resources/mod.json` 的 `"version"`

如 README 中含有子模组版本展示，也建议同步更新。

## 3. 构建验证

```powershell
./gradlew.bat --no-daemon clean jar jarAndroid
```

## 4. 提交与发布（GitHub Actions Release）

```powershell
git add -A
git commit -m "vX.Y.Z: sync submods"
git push origin main

git tag vX.Y.Z
git push origin vX.Y.Z
```

`.github/workflows/release.yml` 会在 tag push 后自动：

- 构建 `zip` 与 Android `jar`
- 上传 artifact
- 创建 GitHub Release 并附带构建产物


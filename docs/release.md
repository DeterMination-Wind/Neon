# 版本与发布

Neon 的发版规则、本地必做步骤和 CI 的分工。原则：**版本号与构建都在本地完成，CI 只负责在 tag 推送后生成 GitHub Release**，不得依赖 CI 改版本或出包。

## 版本号体系

| 形态 | 标签 | 版本码（写进 `mod.json` / `mod.hjson` / `build.gradle`） |
| --- | --- | --- |
| 稳定版 | `N<v>`，如 `N12` | `<v>0000`，即 `120000` |
| 预发行 | `B<v>.<b>`，如 `B12.5` | `<v>0000 + <b>`，即 `120005` |

- tag 只使用 `N*` 或 `B*`。
- 更新器必须同时兼容三种历史形态：新数字版本码、预发行码、老的 `vX.Y.Z` 字符串。改动 [modupdater](subsystems/modupdater.md) 时用 `versionUtilTest` 回归。

## 发布前本地必做步骤

1. **更新本地版本号**
   ```powershell
   python tools/neon_version.py --set-files 120005   # 例：B12.5
   ```
   会同步修改 `mod.json` / `mod.hjson` / `build.gradle` 三处。
2. **本地完整构建**
   ```powershell
   .\gradlew.bat clean deploy
   ```
   环境要求：`ANDROID_SDK_ROOT` 指向完整 SDK，且不再走工作区 `commandlinetools-win-*` 兜底路径；先跑一遍 `.\gradlew.bat test` 确认回归任务全绿。
3. **拷贝并核实产物**
   - `dist/Neon.jar` → `../构建/Neon/Neon-v<标签>.jar`
   - `dist/Neon.zip` → `../构建/Neon/Neon-v<标签>.zip`
   
   核实两个文件都存在、大小合理；桌面 jar（不含 dex）不允许进入发布流程。

## 打包管线细节

`deploy` 背后的任务链：

```text
classes ──► d8InputJar ──► dexAndroid ──► jarMerged ──► zipMerged ──► dist/ + 构建/
             (classes+      (d8 --min-api    (合并 jar：       (同内容 zip)
              pinyin4j+      14 --release     classes+依赖解包+
              kotlin-stdlib, --lib arc-core)  classes.dex+描述)
              无副作用)
```

要点：

- `d8InputJar` 只打包 class 与 `pinyin4j` / `kotlin-stdlib`，是 D8 专用输入，不留 dist 副作用。
- `dexAndroid` 用 `--min-api 14 --release` 出 `classes.dex`；必须带 `--lib <arc-core>`——缺了它 Arc 的接口会被当外部类处理，设备上触发 `AbstractMethodError`（曾踩过的坑，别移除）。
- `jarMerged` / `zipMerged` 是唯一可分发形态；`jar` 任务的 `Neon-desktop.zip` 是中间产物，仅存在于 build 目录。
- SPDB 语义搜索的嵌入模型由 `downloadEmbeddingModel` 按需下载（hf-mirror 源），首次离线打包请提前跑一次该任务。

## CI（`.github/workflows/release.yml`）

触发：推送 `N*` / `B*` tag 或手动 dispatch。流程：

1. checkout + Temurin 17 + Gradle + Android SDK。
2. `tools/neon_version.py --code/--release-name/--set-files/--check-files` 从 tag 反算版本码并校验描述文件一致性；`B*` 标记为 prerelease。
3. 构建合并产物并创建 GitHub Release。

## Release 资产安全规则

Mindustry 游戏内安装器会取 Release API 返回的**第一个 `.jar`**，且不按操作系统挑资产。因此：

- 一个 Release 必须有且只有一个 `.jar` 资产，即最终可安装包（含 `mod.json` + 桌面主类 + `classes.dex`）。
- 严禁把 `*-android.jar`、`*-desktop.jar`、D8 输入 jar 等中间产物传上 Release。
- 发布后用 API 复查：
  ```bash
  gh release view <tag> --json assets
  ```
  发现多余 `.jar` 立即删除。

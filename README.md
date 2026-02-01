# BEK-Tools

## 中文说明

BEK-Tools 是一个 Mindustry 客户端工具集（JavaMod），将以下 3 个模组合并为一个：

- Power Grid Minimap（电网小地图叠加）
- Stealth Path（偷袭小道 / 安全路径）
- Radial Build Menu（圆盘快捷建造）

### 构建

```bash
./gradlew jar
```

输出文件：`build/libs/BEK-Tools.zip`

### Release（自动构建）

推送形如 `v1.0.0` 的 tag，会触发 GitHub Actions 自动构建并发布 Release。

---

## English

BEK-Tools is a Mindustry client-side toolkit (Java mod) that bundles:

- Power Grid Minimap
- Stealth Path
- Radial Build Menu

### Build

```bash
./gradlew jar
```

Output: `build/libs/BEK-Tools.zip`

### Release (CI)

Push a tag like `v1.0.0` to trigger GitHub Actions to build and publish a Release automatically.

# Neon 文档

本目录存放 Neon 的分类文档。文档以中文为主，功能名保留英文原名，便于与源码和游戏内设置界面对照。

## 从哪里开始

| 你的角色或目标 | 推荐入口 |
| --- | --- |
| 玩家 | [用户指南](user/index.md) |
| 了解 Neon 的整体组织方式 | [架构总览](architecture.md) |
| 修改 Neon 代码或并入新子模组 | [开发指南](development.md) 与 [架构总览](architecture.md) |
| 准备版本发布 | [版本与发布](release.md) |
| 查询项目专有术语 | [术语表](glossary.md) |

## 文档地图

```text
docs/
|-- README.md                  本页：文档导航
|-- architecture.md            架构总览：入口、生命周期、设置系统、兼容层
|-- development.md             开发指南：环境、构建命令、接入新子模组
|-- release.md                 版本与发布：N/B 版本码、本地构建步骤、CI
|-- testing.md                 测试指南：回归任务、手测清单
|-- glossary.md                术语表
|-- conventions.md             全局写作与格式约定：禁止软换行、提交信息规范
|-- subsystems/                子系统详解（面向开发者）
|   |-- bektools-core.md       聚合核心：模块注册、故障隔离、指令注册
|   |-- build-xray.md          建造透视：原始脚本来源、透明合成三阶段、聚合契约
|   |-- overlay-compat.md      Overlay 兼容层：桥接口、原生回退、旧版守卫
|   `-- modupdater.md          更新中心：状态机、镜像、历史版本兼容
`-- user/                      用户指南（面向玩家）
    |-- index.md               通用约定：设置入口、Overlay 管理器、默认关闭项
    |-- battlefield-awareness.md  战场观察类功能
    |-- building-and-control.md   建造与操控类功能
    |-- maps-and-logic.md         地图与逻辑类功能
    |-- multiplayer-and-info.md   联机与信息类功能
    `-- personalization.md        个性化与趣味类功能
```

## 相关文件

- [FEATURES.md](../FEATURES.md)：按功能逐条编写的完整说明（30 项功能详解与术语解释）。
- [AGENTS.md](../AGENTS.md)：代理行为与工作流约束（根目录仅保留全局规则与索引指针）。
- 模块级代理说明：[tools/AGENTS.md](../tools/AGENTS.md)、[bektools/AGENTS.md](../src/main/java/bektools/AGENTS.md)、[mdtxcompat/AGENTS.md](../src/main/java/mdtxcompat/AGENTS.md)。
- [README.md](../README.md)：项目主页。

## 维护约定

- 全局排版规则见 [conventions.md](conventions.md)：**一段一行，禁止软换行**，同样适用于提交信息、Issue 模板与 PR 描述。
- 用户指南说明“怎么用”，子系统文档说明“怎么实现”。同一主题在两侧都有涉及时，用户指南从玩家操作视角展开，子系统文档从代码结构与契约展开。
- 快捷键可由玩家自定义，因此文档只记录默认值，并注明可在 `设置 → 控制` 中修改。
- 默认关闭的功能必须在用户指南中明确标注，避免玩家误以为功能缺失。
- 实现与命令事实以源码、`build.gradle` 和各专题文档为准；`AGENTS.md` 只约束代理行为与流程，不复制容易漂移的实现细节。发现文档或指针过时，应在同一次改动中修正，不维护“以谁为准”的双真源。

# Neon 文档

Neon 的分类文档。文档以中文为主，功能名保留英文本名，便于与代码和设置界面对照。

## 从哪里开始

| 你是 | 从这里开始 |
| --- | --- |
| 玩家 | [用户指南](user/index.md) |
| 想了解 Neon 怎么组织的人 | [架构总览](architecture.md) |
| 想改 Neon 代码 / 并入新子模组 | [开发指南](development.md) 与[架构总览](architecture.md) |
| 准备发版 | [版本与发布](release.md) |
| 遇到不认识的词 | [术语表](glossary.md) |

## 文档地图

```text
docs/
|-- README.md                  本页：文档导航
|-- architecture.md            架构总览：入口、生命周期、设置系统、兼容层
|-- development.md             开发指南：环境、构建命令、接入新子模组
|-- release.md                 版本与发布：N/B 版本码、本地构建步骤、CI
|-- testing.md                 测试指南：回归任务、手测清单
|-- glossary.md                术语表
|-- subsystems/                子系统详解（写给开发者）
|   |-- bektools-core.md       聚合核心：模块注册、故障隔离、指令注册
|   |-- overlay-compat.md      Overlay 兼容层：桥接口、原生回退、旧版守卫
|   `-- modupdater.md          更新中心：状态机、镜像、历史版本兼容
`-- user/                      用户指南（写给玩家）
    |-- index.md               通用约定：设置入口、Overlay 管理器、默认关闭项
    |-- battlefield-awareness.md  战场观察类功能
    |-- building-and-control.md   建造与操控类功能
    |-- maps-and-logic.md         地图与逻辑类功能
    |-- multiplayer-and-info.md   联机与信息类功能
    `-- personalization.md        个性化与趣味类功能
```

## 相关文件

- [FEATURES.md](../FEATURES.md)：按功能的完整说明（29 个功能逐条介绍 + 术语解释）。
- [AGENTS.md](../AGENTS.md)：仓库维护约束（版本号规则、设置接入规范、子模组同步）。
- [README.md](../README.md)：项目主页。

## 维护约定

- 用户指南描述"用"，子系统文档描述"怎么实现"。同一主题两边都会出现时，用户指南从操作视角写，子系统文档从代码结构写。
- 快捷键可能被玩家自定义；文档只写默认值，并注明在 `设置 → 控制` 可改。
- 默认关闭的功能必须在用户指南中标注，避免玩家误以为功能缺失。

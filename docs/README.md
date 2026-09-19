---
sidebar_position: 1
title: BlockOffensive Wiki
slug: /
---

# BlockOffensive Wiki

BlockOffensive 是面向 Minecraft 1.20.1 Forge 的 CS2 风格战术竞技模组，基于 FPSMatch 比赛框架运行。本站按玩家、服主/地图作者和开发者三条路线组织内容，覆盖 `cs` 爆破、`csdm` 死斗、经济商店、C4 目标、HUD 与旁观系统。

## 从这里开始

| 你的目标 | 推荐入口 | 你会先看到 |
| --- | --- | --- |
| 参加一场爆破或死斗 | [玩家手册](./player) | 加入地图、选队、准备、购买、C4 与旁观 |
| 开设服务器或制作地图 | [安装与环境要求](installation.md) → [管理员与服主手册](admin.md) → [地图制作者手册](mapper.md) | 依赖安装、部署、地图房间、出生点、队伍和商店 |
| 编写附属模组或脚本 | [模组开发者指南](developer-guide.md) | 架构、生命周期、网络、持久化和兼容层 |

## 先确认环境

- Minecraft `1.20.1`、Forge `47.4.10`、Java `17`。
- 服务端需要 FPSMatch `1.26.9+`；客户端界面需要 Modern UI `1.20.1-3.12.0.1`。
- BlockOffensive 提供 `cs` 回合制爆破和 `csdm` 死斗；枪械、投掷物与物理布娃娃通过兼容层接入，TaCZ 等第三方模组按服务器玩法选择安装。
- 管理命令默认要求 OP 2。游戏内执行 `/fpsm help` 可查看当前版本实际注册的命令树。

## 两种核心模式

**CS 爆破**围绕购买阶段、回合经济和 C4 目标展开：T 阵营安放并保护炸弹，CT 阵营拆除或阻止安放；回合结束后进入结算、换边和下一回合。

**CSDM 死斗**支持自由混战或团队语义，按击杀排名并在重生时提供短暂保护。它使用独立的排名、TAB 和 HUD，不会改变爆破模式的回合经济。

## 比赛内反馈

HUD 会呈现比分、队伍状态、购买阶段、击杀信息、投票和炸弹引信；死亡后可以进入击杀回放，或切换到队友旁观。Ping、队伍聊天、无线电环形菜单、MVP 播报和本地 MVP 音乐用于补充战术沟通与回合节奏。

## 版本边界

本站内容对应当前源码工作区的 `1.26.9-SNAPSHOT`。构建号按 `主版本.两位年份.月份` 动态生成；具体模式、武器兼容和命令可能由整合包或附属模组追加，遇到差异时以游戏内 `/fpsm help`、服务器日志和实际依赖版本为准。

## 按任务查找

| 任务 | 页面 |
| --- | --- |
| 第一次安装客户端或专用服 | [安装与环境要求](installation.md) |
| 创建边界、出生点和爆破区 | [地图制作者手册](mapper.md) |
| 配置服务器、备份和升级 | [管理员与服主手册](admin.md) |
| 查询命令参数和权限 | [命令参考](reference/commands.md) |
| 查询全局配置键 | [配置参考](reference/configuration.md) |
| 编写附属模组 | [开发者指南](developer-guide.md) · [API 参考](api.md) |
| 遇到版本或兼容问题 | [兼容性](compatibility.md) · [故障排查](troubleshooting.md) |

## 相关链接

- [BlockOffensive 源码](https://github.com/SSOrangeCATY/BlockOffensive)
- [FPSMatch Wiki](https://github.com/SSOrangeCATY/BlockOffensive/tree/master/FPSMatch/docs)
- [GitHub Releases](https://github.com/SSOrangeCATY/BlockOffensive/releases)
- [Modrinth](https://modrinth.com/mod/blockoffensive)
- [CurseForge](https://www.curseforge.com/minecraft/mc-mods/blockoffensive)


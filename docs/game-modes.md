---
sidebar_position: 4
title: 模式与运维
description: 服务器管理员配置 BlockOffensive 模式、依赖和常见排错路径。
---

# 模式与运维

BlockOffensive 以 FPSMatch 为底座，当前提供两个游戏类型：`cs` 回合制爆破和 `csdm` 死斗。FPSMatch 负责地图房间、队伍、回合基础设施、商店能力和网络同步，BlockOffensive 负责 C4、爆破结算、CS 风格 HUD、死斗规则和兼容层。

## 安装边界

服务端至少需要 Minecraft `1.20.1`、Forge `47.4.10`、Java `17`、FPSMatch 和 BlockOffensive。客户端还需要 Modern UI；TaCZ、CounterStrikeGrenade、LR Tactical、KubeJS、Physics Mod 等只在启用对应功能时安装。

## 常用命令

```text
/fpsm help
/fpsm mapselect
/fpsm map ...
```

不要猜测完整命令路径。`/fpsm help` 会根据当前加载的模式、能力和扩展生成补全；地图创建和编辑默认需要 OP 2。

## 模式选择

| 模式 | 目标 | 关键配置 |
| --- | --- | --- |
| `cs` | T 安装并引爆 C4，或 CT 拆除/守住目标 | 回合数、目标区、C4、购买阶段、队伍出生点 |
| `csdm` | 在时间或积分结束前取得击杀优势 | 自由/团队死斗、重生、保护时间、积分和排名 |

## 排错路径

1. 先确认客户端和服务端的 Minecraft、Forge、FPSMatch、BlockOffensive 版本。
2. 执行 `/fpsm help`，确认模式和地图能力真的被注册。
3. 在地图详情页检查边界、队伍、出生点、目标区和商店是否初始化。
4. 查看服务器日志中的第一条异常，尤其是依赖加载、资源位置和网络包注册错误。
5. 若只在客户端出现问题，再检查 Modern UI、资源包和可选枪械模组。

## 兼容性边界

兼容类会在检测到可选模组后才启用。TaCZ 负责枪械表现，CounterStrikeGrenade 负责额外投掷物，Physics Mod 与 Hit Indication 只影响客户端反馈；它们不会替代 FPSMatch 的回合或队伍数据。

下一步：[模组开发者手册](/docs/developer/) · [玩家手册](/docs/player/)。


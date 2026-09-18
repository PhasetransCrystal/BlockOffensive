---
title: 地图设置与房间
description: 配置 BlockOffensive 地图的回合、经济、自动开始和展示字段。
---

# 地图设置与房间

`CSGameMap.settings()` 暴露 `winnerRound`、`roundTimeLimit`、`startEconomy`、`c4InstantKillRadius` 等 `cs` 字段；`CSDeathMatchMap.settings()` 暴露 `matchTimeLimit` 和 `spawnProtectionTime` 等 `csdm` 字段。

| 模式 | 源码默认值 | 作用 |
| --- | --- | --- |
| `cs` | `winnerRound=13` | 先达到目标回合数的一方获胜 |
| `cs` | `roundTimeLimit=2300` tick | 回合战斗时限 |
| `cs` | `startEconomy=800` | 初始资金 |
| `cs` | `c4InstantKillRadius=20` | C4 爆炸致命半径 |
| `csdm` | `matchTimeLimit=12000` tick | 死斗比赛时长 |
| `csdm` | `spawnProtectionTime=6` 秒 | 出生保护时长 |

通用字段如 `displayName`、`iconTexture`、`backgroundTexture`、`allowJoinInProgress`、`autoStart` 和 `readyStartEnabled` 由 FPSMatch 提供。使用地图设置界面或 `/fpsm map ... settings list/get/set` 查询当前版本。

完成设置后，先在等待房间确认人数和准备条件，再验证自动开始、回合结束、换边和房间清理。

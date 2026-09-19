---
title: 地图设置与房间
description: 配置 BlockOffensive 地图的回合、经济、自动开始和展示字段。
---

# 地图设置与房间

`CSGameMap.settings()` 和 `CSDeathMatchMap.settings()` 会把 BlockOffensive 自己的字段与 FPSMatch 通用字段一起提供给地图设置界面和命令。下表列出当前源码注册的 BlockOffensive 字段；时间单位标为 tick 时，`20 tick` 约等于 1 秒。

## `cs` 回合与时间

| 设置键 | 默认值 | 作用 |
| --- | ---: | --- |
| `winnerRound` | `13` | 先达到该胜场数的一方获胜 |
| `overtimeRound` | `3` | 单个加时阶段的回合参数 |
| `pauseTime` | `1200` tick | 暂停时长（约 60 秒） |
| `winnerWaitingTime` | `160` tick | 回合胜负结算等待（约 8 秒） |
| `warmUpTime` | `1200` tick | 热身时长（约 60 秒） |
| `waitingTime` | `300` tick | 回合准备等待（约 15 秒） |
| `roundTimeLimit` | `2300` tick | 回合战斗时限（约 115 秒） |
| `knifeSelection` | `false` | 是否启用刀战选边流程 |

## `cs` 经济与 C4

| 设置键 | 默认值 | 作用 |
| --- | ---: | --- |
| `startEconomy` | `800` | 比赛初始资金 |
| `defaultLoserEconomy` | `1400` | 默认败方经济基数 |
| `defuseEconomy` | `600` | 拆包相关奖励 |
| `compensationBase` | `500` | 连败补偿递增基数 |
| `tDeathRewardPer` | `50` | T 阵亡相关奖励参数 |
| `closeShopTime` | `200` tick | 商店关闭时间参数（约 10 秒） |
| `timeoutEconomy` | `3250` | 超时结算奖励 |
| `aceEconomy` | `3250` | 全灭结算奖励 |
| `defuseBombEconomy` | `3500` | 拆除 C4 的回合奖励 |
| `detonateBombEconomy` | `3500` | C4 引爆的回合奖励 |
| `c4InstantKillRadius` | `20` 格 | C4 爆炸的即时致命半径 |

## `csdm`

| 设置键 | 默认值 | 作用 |
| --- | ---: | --- |
| `isTDM` | `false` | `false` 为自由死斗，`true` 为团队死斗 |
| `matchTimeLimit` | `12000` tick | 比赛时长（约 10 分钟） |
| `spawnProtectionTime` | `6` 秒 | 出生保护时长 |

通用字段如 `displayName`、`iconTexture`、`backgroundTexture`、`allowJoinInProgress`、`autoStart` 和 `readyStartEnabled` 由 FPSMatch 提供。使用地图设置界面或 `/fpsm map ... settings list/get/set` 查询当前版本实际暴露的分组、键名与类型，不要只按表格猜命令参数。

半场转场使用独立 JSON，不会出现在这组地图设置中；配置方法见[半场转场动画](halftime-transition.md)。

完成设置后，先在等待房间确认人数和准备条件，再验证自动开始、回合结束、换边和房间清理。

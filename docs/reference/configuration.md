---
sidebar_position: 2
title: 配置参考
description: BlockOffensive 客户端与通用 Forge 配置键、默认值和取值范围。
---

# 配置参考

适用读者：服主、整合包维护者和需要调整 HUD/比赛规则的玩家。配置由 `BOConfig` 注册为 Forge `CLIENT` 与 `COMMON` 两类。

## 客户端配置

| 分组/键 | 类型 | 默认值 | 范围 | 作用 |
| --- | --- | ---: | --- | --- |
| `kill message.hudEnabled` | boolean | `true` | — | 显示击杀消息 |
| `kill message.hudPosition` | int | `2` | `1..4` | 消息位置 |
| `kill message.messageShowTime` | int | `5` | `1..60` | 单条显示时间 |
| `kill message.maxShowCount` | int | `5` | `1..10` | 同时显示数 |
| `kill icon.killIconEnabled` | boolean | `true` | — | 显示击杀图标 |
| `spectator.spectatorBombHudEnabled` | boolean | `true` | — | 旁观 C4 引信 HUD |
| `spectator.spectatorRosterEnabled` | boolean | `true` | — | 旁观者名单 |

## 通用配置

| 分组/键 | 类型 | 默认值 | 范围/枚举 | 作用 |
| --- | --- | ---: | --- | --- |
| `step sound.teammateMuffledStepVolume` | double | `0.05` | `0..10` | 队友遮挡脚步音量 |
| `step sound.teammateStepVolume` | double | `0.15` | `0..10` | 队友脚步音量 |
| `step sound.enemyMuffledStepVolume` | double | `0.4` | `0..10` | 敌方遮挡脚步音量 |
| `step sound.enemyStepVolume` | double | `1.2` | `0..10` | 敌方脚步音量 |
| `c4.Fuse Time` | int | `800` | `1..3200` tick | C4 引信时长 |
| `ping.pingTtlSeconds` | int | `6` | `3..30` 秒 | Ping 存活时间 |
| `ping.pingMaxDistance` | double | `256` | `16..1024` 格 | Ping 最大距离 |
| `inGameRules.keepInventory` | boolean | `true` | — | 死亡保留物品 |
| `inGameRules.immediateRespawn` | boolean | `true` | — | 立即重生 |
| `inGameRules.daylightCycle` | boolean | `false` | — | 日夜循环 |
| `inGameRules.weatherCycle` | boolean | `false` | — | 天气循环 |
| `inGameRules.mobSpawning` | boolean | `false` | — | 自然生物生成 |
| `inGameRules.naturalRegeneration` | boolean | `false` | — | 自然回血 |
| `inGameRules.hardDifficulty` | boolean | `true` | — | 困难难度 |
| `web server.webServerEnabled` | boolean | `false` | — | Web 服务开关 |
| `web server.webServerPort` | int | `8080` | `1..65535` | Web 端口 |
| `overtime.overtimeMode` | enum | `VOTE` | `VOTE/AUTO/DISABLED` | 加时策略 |
| `overtime.overtimeStartMoney` | int | `10000` | `0..100000` | 加时起始资金 |
| `overtime.overtimeVoteThreshold` | double | `0.6` | `0..1` | 加时投票门槛 |
| `overtime.overtimeVoteSeconds` | int | `20` | `5..120` 秒 | 投票时长 |
| `overtime.overtimeMaxSegments` | int | `0` | `0..10` | 最大加时段，0 无限 |
| `vote.voteTimeoutPolicy` | enum | `FAIL` | `FAIL/PASS_IF_MAJORITY` | 超时策略 |
| `vote.voteAbstentionPolicy` | enum | `COUNT_AS_NO` | `COUNT_AS_NO/IGNORE` | 弃权计票 |
| `vote.unpauseVoteThreshold` | double | `1.0` | `0.5..1` | 取消暂停门槛 |

> 地图自身的 `winnerRound`、`roundTimeLimit`、`startEconomy`、`c4InstantKillRadius` 等不是全局 Forge 键，而是地图设置；使用地图设置界面或 `/fpsm map ... settings` 修改。

修改后重启对应端，进入测试地图验证 Ping、C4、旁观 HUD、投票和回合结束流程。

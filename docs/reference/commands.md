---
sidebar_position: 1
title: 命令参考
description: BlockOffensive 注册的命令、参数、权限和失败条件。
---

# 命令参考

适用读者：管理员、地图作者和查询命令树的玩家。尖括号表示必填参数，方括号表示可选参数。FPSMatch 的地图、队伍、能力和设置命令会随版本动态注册，完整补全以当前服务器 `/fpsm help` 为准。

## BlockOffensive 命令

| 路径 | 参数 | 权限/执行端 | 用途 |
| --- | --- | --- | --- |
| `/fpsm p`、`/fpsm pause` | 无 | 地图内玩家 | 请求暂停 |
| `/fpsm up`、`/fpsm unpause` | 无 | 地图内玩家 | 发起取消暂停投票 |
| `/fpsm a`、`/fpsm agree` | 无 | 地图内玩家 | 同意当前投票 |
| `/fpsm da`、`/fpsm disagree` | 无 | 地图内玩家 | 反对当前投票 |
| `/fpsm d`、`/fpsm drop` | 无 | 地图内玩家 | 放弃刀战选择 |
| `/pause` | 无 | 地图内玩家 | 独立暂停入口 |
| `/cs2 <action>` | 动作字符串 | 玩家 | 转发到当前 `cs` 地图 |
| `/fpsm mvp <targets> <sound> [name]` | 玩家、声音 ID、显示名 | 管理员 | 写入 MVP 音乐 |
| `/fpsm clonedata cs <source> shopdata <target>` | 两个地图 ID | OP 2 | 克隆商店数据 |
| `/fpsm clonedata cs <source> gamedata <target>` | 两个地图 ID | OP 2 | 克隆比赛数据 |

## 半场过场

这些命令需要 OP 2，配置保存于世界 `serverconfig/blockoffensive-halftime-intro.json`，`<side>` 为 `ct` 或 `t`：

```text
/fpsm blockoffensive halftime reload
/fpsm blockoffensive halftime select <map> <side> <from> <to>
/fpsm blockoffensive halftime facing <map> <side> <yaw> [pitch]
/fpsm blockoffensive halftime duration <map> <side> <ticks>
/fpsm blockoffensive halftime enable <map> switch <true|false>
/fpsm blockoffensive halftime preview <map> <side>
/fpsm blockoffensive halftime debug_halftime_switch <map> [<ct_score> <t_score>]
/fpsm blockoffensive halftime clear <map> <side>
```

`duration` 范围为 `60..140` tick；`select` 两坐标不能相同；`enable` 当前只接受 `switch` 并同时修改两队；调试切换分数之和必须等于 `winnerRound - 1`。完整的区域规划、朝向、预览、JSON 字段与验收流程见[配置半场转场动画](../mapper/halftime-transition.md)。

非 production 环境才会注册 TaCZ、物理布娃娃和死亡图标调试命令。

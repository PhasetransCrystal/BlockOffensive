---
title: 配置半场转场动画
description: 为 cs 地图配置 T/CT 入场区域、朝向、时长，并预览和验收换边转场。
---

# 配置半场转场动画

半场转场会在 `cs` 比赛进入换边节点时，为 T 和 CT 分别生成最多 5 人的队伍展示、镜头移动、遮罩与阵营音效。它是地图级配置，不属于全局 Forge 配置，也不适用于 `csdm`。

配置需要 OP 2 权限。开始前先完成地图边界、T/CT 队伍和两队出生点；转场结束后，玩家会回到本回合分配的出生点。

## 1. 规划展示区域

为两队各准备一块平整、已加载且位于安全场景内的长方体区域。区域负责限制队列行走和镜头取景，不会替代队伍出生点。

- 建议至少留出约 `8 x 4 x 8` 格空间，地面应连续，头顶不能有低矮方块。
- T 与 CT 区域应互不重叠，并避开比赛中的玩家、实体和高对比遮挡物。
- 两个角点不能是同一个方块；命令会自动取两点的最小/最大边界。
- 一支队伍最多展示 5 名在线玩家；人数不足时按实际在线人数生成队列。

以下示例使用地图 ID `dust2`，坐标请替换为你的场景：

```text
/fpsm blockoffensive halftime select dust2 ct 120 65 -40 130 69 -30
/fpsm blockoffensive halftime select dust2 t 150 65 -40 160 69 -30
```

`select` 会立即写入当前世界的 `serverconfig/blockoffensive-halftime-intro.json`。

## 2. 设置队列朝向

`yaw` 决定队伍向前行进的方向，`pitch` 决定角色上下俯仰。Minecraft 常用朝向为：`0` 朝 +Z、`90` 朝 -X、`-90` 朝 +X、`180` 朝 -Z。

```text
/fpsm blockoffensive halftime facing dust2 ct 90 0
/fpsm blockoffensive halftime facing dust2 t -90 0
```

命令接受 `yaw -360..360` 和 `pitch -90..90`；当前队列动作会把实际角色俯仰限制在 `-8..8` 度，通常保持 `0` 最稳定。若人物背对镜头或走向墙体，先调整 `yaw`，再重新预览。

## 3. 设置时长并启用

时长范围是 `60..140` tick，即约 3–7 秒；默认值是 `90` tick（约 4.5 秒）。T 与 CT 可以使用不同的时长。

```text
/fpsm blockoffensive halftime duration dust2 ct 90
/fpsm blockoffensive halftime duration dust2 t 90
/fpsm blockoffensive halftime enable dust2 switch true
```

`enable` 当前只接受 `switch`，并同时修改两队的换边转场开关。新建配置的 `switchEnabled` 默认为 `true`，仍建议显式启用一次，便于确认地图 ID 和配置状态。

## 4. 分队预览

预览使用真实在线队员和已配置的出生点。让至少一名测试玩家加入目标队伍，再分别执行：

```text
/fpsm blockoffensive halftime preview dust2 ct
/fpsm blockoffensive halftime preview dust2 t
```

预览时检查：

1. 队员没有卡入地面、墙体或彼此重叠。
2. 队列行进方向正确，主体始终处于镜头内。
3. T/CT 图标、遮罩和对应音效能正常出现。
4. 动画结束后玩家回到正确的队伍出生点，游戏模式和飞行等能力被恢复。

命令返回 `No valid sequence` 时，依次检查地图是否为 `cs`、该侧是否已设置有效区域、队伍中是否有在线玩家，以及该侧是否存在当前维度的出生点。

## 5. 验证真实换边

单队预览通过后，用调试入口推进到半场换边。默认会构造一组总分为 `winnerRound - 1` 的比分；也可以显式指定 CT/T 分数，但两者之和必须满足同一条件。

```text
/fpsm blockoffensive halftime debug_halftime_switch dust2
/fpsm blockoffensive halftime debug_halftime_switch dust2 6 6
```

第二条只适用于 `winnerRound=13`。调试命令会走 BlockOffensive 自己的下一回合与换边流程，应在测试房间中使用，不要在正式比赛中执行。

最终用两队真实玩家完成一次换边，确认两侧转场同时触发、比分与队伍交换正确、下一回合可以正常购买和移动。

## 配置文件与手工调整

配置文件位于当前世界：

```text
<世界目录>/serverconfig/blockoffensive-halftime-intro.json
```

一个完整条目如下。地图键由游戏类型和地图 ID 组成；当前命令固定写入 `cs:<map>`。

```json
{
  "maps": {
    "cs:dust2": {
      "ct": {
        "area": {"x1": 120, "y1": 65, "z1": -40, "x2": 130, "y2": 69, "z2": -30},
        "yaw": 90.0,
        "pitch": 0.0,
        "durationTicks": 90,
        "startEnabled": false,
        "switchEnabled": true
      },
      "t": {
        "area": {"x1": 150, "y1": 65, "z1": -40, "x2": 160, "y2": 69, "z2": -30},
        "yaw": -90.0,
        "pitch": 0.0,
        "durationTicks": 90,
        "startEnabled": false,
        "switchEnabled": true
      }
    }
  }
}
```

命令修改会自动保存。手工编辑前先备份文件，编辑后执行：

```text
/fpsm blockoffensive halftime reload
```

`startEnabled` 控制比赛开始时是否也播放同一套入场序列，默认关闭，当前没有对应的 `enable start` 命令；需要时只能在停服或确保没有序列运行时手工修改，再重新加载。`durationTicks` 即使被手工写到范围外，运行时仍会限制到 `60..140`。

## 关闭、清理与排错

临时关闭换边转场但保留区域：

```text
/fpsm blockoffensive halftime enable dust2 switch false
```

删除某一侧的完整配置：

```text
/fpsm blockoffensive halftime clear dust2 ct
/fpsm blockoffensive halftime clear dust2 t
```

排错时在 `latest.log` 搜索 `[BlockOffensive Halftime]`。若只有人物动作缺失，检查客户端是否加载了随模组提供的玩家动画资源；若整个序列不触发，优先检查地图 ID、队伍在线状态、出生点维度、区域配置和 `switchEnabled`。修改前后都应保留配置文件备份。

下一步：[房间管理与验收](room-management.md) · [命令参考](../reference/commands.md)。

---
title: 创建地图与边界
description: 创建 BlockOffensive 的 cs 或 csdm 地图并设置三维边界。
---

# 创建地图与边界

本文创建 `cs` 地图 `training`。创建命令由 FPSMatch 提供，参数和补全以 `/fpsm help` 为准。

## 命令方式

```text
/fpsm map create cs training <from> <to>
```

例如：

```text
/fpsm map create cs training 0 64 0 100 90 100
```

也可以把 `cs` 换成 `csdm` 创建死斗地图。`from` 和 `to` 是两个三维方块坐标。

## 创建后的检查

1. 确认地图 ID 没有重复。
2. 确认执行者所在维度就是比赛维度。
3. 在地图设置中填写展示名、图标和详情背景。
4. 重新打开地图详情页，确认边界包住建筑和所有路线。

缩小边界前先检查爆破区、出生点和商店区域是否仍在边界内；比赛进行中编辑通常会被拒绝。

下一步：[队伍与出生点](teams-and-spawns.md)。

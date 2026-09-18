---
title: 起始装备
description: 为 BlockOffensive 队伍设置进入比赛或回合时使用的装备。
---

# 起始装备

起始装备由 FPSMatch 的 `StartKitsCapability` 保存。BlockOffensive 的 `cs` 和 `csdm` 队伍都包含该能力，但实际枪械可来自 TaCZ 或其他整合包内容。

1. 选中目标地图和队伍。
2. 将完整物品栈放在主手，或在命令中指定物品与数量。
3. 添加装备并查看列表。
4. 开始一局并验证死亡、重生和新回合后的装备。

```text
/fpsm map modify <game_type> <map> team teams <team> capability kits add [item] [amount]
/fpsm map modify <game_type> <map> team teams <team> capability kits list
/fpsm map modify <game_type> <map> team teams <team> capability kits clear
```

不要把可选枪械模组的物品 ID 写成 BlockOffensive 内置资源；以服务器物品注册表和命令补全为准。

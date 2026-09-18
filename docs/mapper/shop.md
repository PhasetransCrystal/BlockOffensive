---
title: 队伍商店与经济
description: 初始化 cs 或 csdm 队伍商店并验证购买区、槽位和经济。
---

# 队伍商店与经济

BlockOffensive 在 `cs` 和 `csdm` 注册 `cs` 商店类型；每个队伍保存自己的商店能力。商品槽位、价格、分组和购买区域由 FPSMatch 商店能力管理。

## 最小流程

1. 初始化目标队伍商店类型和起始资金。
2. 设置购买区域，确保玩家能在区域内打开商店。
3. 为槽位设置物品、价格、弹药或互斥 `group_id`。
4. 同步商店数据，使用两名测试玩家验证购买、余额变化、购买结束和重连恢复。

```text
/fpsm map modify <game_type> <map> team teams <team> capability shop initialize <type> [startMoney]
/fpsm map modify <game_type> <map> team teams <team> capability shop areas add <pos1> <pos2>
/fpsm map modify <game_type> <map> team teams <team> capability shop info
/fpsm map modify <game_type> <map> team teams <team> capability shop sync
```

以上路径来自 FPSMatch 参考实现；若补全不同，以服务器帮助树为准。`cs` 的回合经济设置在地图设置中，客户端按 `B` 打开商店只是意图入口。

下一步：[地图设置](settings.md)。

---
title: 复用与导入地图配置
description: 在地图间克隆商店或游戏数据，并说明不能自动迁移的内容。
---

# 复用与导入地图配置

BlockOffensive 提供 OP 2 数据克隆命令，用于同类型 `cs` 地图间复用商店或游戏数据。

```text
/fpsm clonedata cs <source_map> shopdata <target_map>
/fpsm clonedata cs <source_map> gamedata <target_map>
```

克隆前确认源/目标地图存在且类型兼容，并备份目标数据。执行后重新打开设置与商店页面，再用玩家流程验证。世界建筑、维度、边界以及资源包文件不会因为克隆命令自动复制。

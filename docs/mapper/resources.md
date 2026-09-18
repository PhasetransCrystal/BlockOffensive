---
title: 房间展示资源
description: 配置地图名称、图标和详情背景并验证客户端资源。
---

# 房间展示资源

地图设置 `displayName`、`iconTexture` 和 `backgroundTexture` 控制房间展示。资源位置遵循 Minecraft `namespace:path` 形式，图片必须由客户端资源包或模组资源提供。

1. 把 PNG 放入资源包的 `assets/<namespace>/textures/`。
2. 在地图设置中填写与源码资源一致的位置。
3. 重新加载资源包，再打开地图列表和详情页。
4. 在没有资源包的客户端验证兜底显示不会影响加入比赛。

资源缺失通常只影响展示。若整页无法打开，优先检查 Modern UI 和客户端日志，而不是地图边界。

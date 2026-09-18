---
title: 保存、备份与恢复
description: 保存 BlockOffensive 地图数据并在升级前建立可恢复备份。
---

# 保存、备份与恢复

`MapRegister` 把 `CSGameMap` 和 `CSDeathMatchMap` 注册到 FPSMatch 数据保存器。地图设置、能力和队伍数据由对应 Codec 持久化；半场过场另存为世界 `serverconfig/blockoffensive-halftime-intro.json`。

升级或批量编辑前停止服务器，并备份世界目录、FPSMatch 数据、`serverconfig/`、`config/` 和模组清单。恢复时使用同一组依赖版本，先在副本世界加载并查看日志。

不要根据文档猜测底层 JSON 字段并直接编辑；优先使用地图界面、命令和已有数据修复器。

---
sidebar_position: 6
title: API 与模块参考
description: BlockOffensive 可供附属模组查阅的模块、网络包和调用边界。
---

# API 与模块参考

适用读者：需要集成 BlockOffensive 的模组开发者。本文是源码导航和端侧约束，不承诺未标注为公共 API 的内部类稳定。

| 模块 | 代表类型 | 调用时机/端侧 | 用途 |
| --- | --- | --- | --- |
| 地图 | `CSGameMap`、`CSDeathMatchMap` | 服务端地图生命周期 | 读取模式、设置、队伍和回合 |
| 规则 | `CSGameEvents`、`CSRoundContext` | 服务端事件/回合 | 接入目标、死亡和结算 |
| 网络 | `BOPacketRegistration`、`net.*Packet` | C2S/S2C，按包定义端侧 | 注册和同步状态 |
| 客户端 HUD | `CSGameHud`、`CSDMOverlay`、`CSVoteHud` | 客户端渲染线程 | 显示比分、死斗、投票 |
| 旁观 | `BOSpecManager`、`KillCamManager` | 客户端/服务端协作 | 击杀镜头和队友附着 |
| 数据 | `CSGameMap.CODEC`、`CSDeathMatchMap.CODEC` | 服务端保存/加载 | 地图和能力持久化 |
| 集成 | `PhysicsModCompat`、`CSGrenadeCompat` | 检测到可选模组时 | 外部表现适配 |

扩展前先检查目标类型是否为公开注册入口；如果只能通过反射访问可选模组，请复制 `compat/` 的存在检测和客户端隔离模式。网络处理器应把工作排入正确线程，并在服务端再次验证权限与状态。

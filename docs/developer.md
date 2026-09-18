---
sidebar_position: 5
title: 模组开发者手册
description: 为 BlockOffensive 与 FPSMatch 编写模式规则、网络同步和客户端表现。
---

# 模组开发者手册

BlockOffensive 的源码把 FPSMatch 的通用竞赛框架和 CS 玩法拆开：地图、队伍、回合、房间和基础商店来自 FPSMatch；`CSGameMap`、C4 目标、死斗计分、比分同步、击杀镜头与客户端 HUD 由 BlockOffensive 扩展。

## 先认识模块边界

| 目标 | 入口 |
| --- | --- |
| 注册或修改回合模式 | `map/CSGameMap`、`map/CSMapRoundContext`、`map/CSEliminationRule` |
| 添加爆破规则 | `CSBombExplodedRule`、`CSBombDefusedRule`、`CompositionC4` |
| 扩展死斗 | `CSDeathMatchMap`、`CSDMScoring`、`CSDMTeamSemantics` |
| 同步比分和设置 | `net/CSScoreboardS2CPacket`、`CSGameSettingsS2CPacket` |
| 绘制 HUD 与旁观 | `client/screen`、`client/spec`、`client/BOClientEvent` |
| 接入可选模组 | `compat/` 下的独立兼容类 |

## 回合与地图生命周期

不要在客户端推断胜负。服务端地图持有回合上下文、队伍、目标状态和结算原因，客户端只消费同步快照。新规则应接入 FPSMatch 的地图/回合事件，在 `start`、`tick`、死亡、目标完成和 `reset` 时清理自己的状态。

## 网络与客户端

为 HUD、商店、旁观和 C4 进度定义明确的 S2C/C2S 包；服务端校验玩家、地图和回合状态，客户端只负责展示和发起意图。断线重连后重新发送完整快照，不要依赖客户端保留旧状态。包含 `Minecraft`、渲染器或第三方模组类型的代码放在客户端或独立兼容类，避免专用服务端加载失败。

## 资源与集成

HUD 资源使用 `assets/blockoffensive` 命名空间；地图图标、消息和武器数据通过数据包或资源包提供。TaCZ、KubeJS、Physics Mod 等集成只在目标模组存在时实例化，公共规则不要直接引用可选依赖类型。

## 开发验证

- 用一个空地图验证加载、开始、重置和清理。
- 用两队和最小出生点验证加入、重连、死亡、旁观和回合结算。
- 在服务端无 Modern UI、无可选枪械模组的情况下启动，确认类加载边界安全。
- 检查 C4、比分、商店和 HUD 在重新进入地图后都收到新快照。

源码中的测试与 `README.md` 版本矩阵是 API 兼容性的最终参考。


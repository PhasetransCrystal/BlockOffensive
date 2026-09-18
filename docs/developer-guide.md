---
sidebar_position: 5
title: 模组开发者指南
description: 理解 BlockOffensive 的模式、事件、网络、持久化和兼容层边界。
---

# 模组开发者指南

适用读者：熟悉 Java、Forge 1.20.1 和 Minecraft 事件系统的开发者。前置条件是能够构建本项目并阅读 FPSMatch 源码；本文目标是找到正确扩展点。

## 架构地图

| 责任 | 源码入口 | 说明 |
| --- | --- | --- |
| 模组启动与配置 | `BlockOffensive`、`BOConfig` | 注册物品、实体、声音、网络包和 Forge 配置 |
| 游戏类型 | `MapRegister`、`CSGameMap`、`CSDeathMatchMap` | 注册 `cs`/`csdm`、设置和生命周期 |
| 爆破规则 | `CSBombExplodedRule`、`CSBombDefusedRule`、`CompositionC4` | C4 目标、引信和回合结果 |
| 死斗规则 | `CSDMScoring`、`CSDMTeamSemantics` | 击杀分数、队伍语义和重生 |
| 网络同步 | `BOPacketRegistration`、`net/` | C2S 意图与 S2C 快照/反馈 |
| 客户端表现 | `client/screen`、`client/spec`、`BOClientBootstrap` | HUD、商店、旁观和击杀镜头 |
| 可选集成 | `compat/` | 仅在目标模组存在时初始化 |

## 生命周期与端侧

服务端地图对象是回合、队伍、目标和持久化状态的权威。扩展规则应在地图/回合开始、tick、死亡、目标完成和 reset 路径清理状态；客户端只消费同步数据，不推断胜负。包含 `Minecraft`、渲染器或第三方模组类型的代码必须保持客户端隔离，避免专用服务端加载失败。

## 网络约定

C2S 包只表达玩家意图，例如炸弹操作、Ping、商店掉落拾取或旁观切换；服务端重新校验地图、玩家、距离和阶段。S2C 包同步比分、游戏设置、C4 进度、商店状态、击杀镜头和投票。断线重连后发送完整快照，不依赖客户端旧缓存。

## 最小验证矩阵

1. 空地图：加载、开始、reset 和保存。
2. 两队地图：加入、准备、死亡、旁观、重连和回合结算。
3. 无 Modern UI、无可选枪械模组的专用服务器启动。
4. C4、比分、商店和 HUD 在重新进入地图后重新收到快照。

API 细节以当前源码和 FPSMatch 对应版本为准；项目目前没有独立生成的 JavaDoc 站点，不要把未在源码中出现的类、方法或事件写成稳定 API。

下一步：[API 与模块参考](api.md) · [兼容性](compatibility.md)。

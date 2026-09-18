---
sidebar_position: 3
title: 管理员与服主手册
description: 部署服务器、维护地图数据、查看日志并安全升级。
---

# 管理员与服主手册

适用读者：专用服务器管理员、整合包维护者和地图发布者。前置条件是完成[安装](installation.md)并拥有 OP 2 或等效权限。

## 推荐流程

1. 安装并确认 `/fpsm help` 可用。
2. 在目标维度建造场景，按[地图制作者手册](mapper.md)创建地图边界、队伍和出生点。
3. 配置 `cs` 或 `csdm` 的地图设置、爆破区、商店和展示资源。
4. 用两名测试玩家验证加入、准备、购买、C4、死亡、旁观和重连。

## 运行时检查

| 检查项 | 成功信号 | 失败时查看 |
| --- | --- | --- |
| 模组加载 | 日志无 `Missing`/`ModLoadingException` | `latest.log` 第一条异常 |
| 玩法注册 | `/fpsm help` 出现地图能力 | `MapRegister` 和依赖日志 |
| 地图数据 | 地图列表出现 ID | 世界存档与 FPSMatch 数据 |
| 房间流程 | 玩家可选队、准备并开始 | `autoStart`、`readyStartEnabled` |
| 客户端同步 | HUD、商店、旁观在重连后恢复 | 网络包和 Modern UI 日志 |

## 维护命令

```text
/fpsm help
/fpsm map ...
/fpsm p
/fpsm up
/fpsm mvp <targets> <sound> [name]
/fpsm clonedata cs <source_map> shopdata <target_map>
/fpsm clonedata cs <source_map> gamedata <target_map>
```

独立入口还包括 `/pause` 和 `/cs2 <action>`；它们最终调用当前玩家所在 `cs` 地图的动作处理。地图编辑、克隆和调试通常要求 OP 2；玩家动作命令必须由地图内玩家执行。

## 备份与升级

升级前停止服务器并备份世界存档（包括 `serverconfig/`）、FPSMatch 地图数据、`config/` 下的 BlockOffensive 配置、`mods/` 清单和 `latest.log`。先在副本服务器执行 `/fpsm help`、打开测试地图并完成一轮，再替换生产实例。

`CSGameMap` 与 `CSDeathMatchMap` 通过 FPSMatch 数据保存器持久化；旧数据格式变化时由 `CSGameMapFixer` 参与迁移。不要在没有备份时手工删除字段。

## 日志排错顺序

1. 记录 Minecraft、Forge、FPSMatch、BlockOffensive 和 Modern UI 的实际版本。
2. 从 `latest.log` 找第一条异常，而不是最后一条连锁错误。
3. 区分加载、地图编辑、比赛开始、网络同步和客户端渲染阶段。
4. 用最小地图复现；暂时移除 TaCZ、物理模组或投掷物等可选集成。
5. 提交地图 ID、模式、命令全文、权限等级和复现步骤。

`web server.webServerEnabled` 默认关闭，端口默认 `8080`；开启前确认端口未占用并限制访问范围。

下一步：[命令参考](reference/commands.md) · [配置参考](reference/configuration.md) · [故障排查](troubleshooting.md)。

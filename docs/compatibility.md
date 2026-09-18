---
sidebar_position: 7
title: 兼容性与版本边界
description: 区分必需依赖、可选集成和随版本变化的事实。
---

# 兼容性与版本边界

`mods.toml` 中声明的必需依赖只有 Minecraft、Forge、FPSMatch 和客户端 Modern UI；TaCZ、LR Tactical、CounterStrikeGrenade、KubeJS、Physics Mod、Hit Indication、GD656 Kill Icon、TaCZ Tweaks 等由兼容层按存在性启用。

| 组件 | 角色 | 失败影响 |
| --- | --- | --- |
| FPSMatch | 必需框架 | 地图、队伍、回合和商店基础设施不可用 |
| Modern UI | 客户端必需 | 地图浏览、商店和部分界面不可用 |
| TaCZ | 可选枪械 | 仅影响枪械来源与表现 |
| CounterStrikeGrenade | 可选投掷物 | 相关投掷物兼容逻辑不启用 |
| Physics Mod/Hit Indication | 可选客户端表现 | 仅影响布娃娃或命中反馈 |

版本不一致时，以当前 `gradle.properties`、`mods.toml`、服务器命令补全和 `latest.log` 为准；README 中历史发布矩阵不能覆盖当前源码行为。

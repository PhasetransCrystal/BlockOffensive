---
sidebar_position: 2
title: 安装与环境要求
description: 安装 BlockOffensive 客户端和专用服务器，并验证依赖是否正确加载。
---

# 安装与环境要求

适用读者：玩家、服主和整合包维护者。前置条件是能够安装 Forge 模组；适用当前源码工作区。本文目标是加载 BlockOffensive 并完成第一次连接验证。

## 版本与依赖

| 组件 | 要求 | 端侧 | 依据 |
| --- | --- | --- | --- |
| Minecraft | `1.20.1`，范围 `[1.20.1,1.21)` | 双端 | `gradle.properties`、`mods.toml` |
| Forge | `47.4.10` 或满足 `[47.4.10,)` | 双端 | Gradle 属性与模组元数据 |
| Java | `17` | 服务端/开发环境 | Gradle toolchain |
| FPSMatch | `1.26.9` 及以上 | 双端 | 必需依赖 |
| Modern UI | Forge `3.12.0.1` 及以上 | 客户端 | 客户端必需依赖 |

TaCZ、LR Tactical、CounterStrikeGrenade、KubeJS、Physics Mod、Hit Indication、GD656 Kill Icon 和 TaCZ Tweaks 等在构建脚本中有版本变量，但不是 `mods.toml` 的必需依赖；只有使用对应兼容功能时才安装。

> 当前源码版本由构建日期动态生成，非发布构建形如 `1.26.9-SNAPSHOT`（`主版本.两位年份.月份`）。以客户端、服务端日志中的实际版本为准。

## 玩家客户端

1. 安装 Minecraft `1.20.1` 和 Forge `47.4.10`。
2. 将 BlockOffensive、FPSMatch、Modern UI 及服务器要求的可选模组放入客户端 `mods` 目录。
3. 启动客户端，确认 `latest.log` 中没有 `blockoffensive` 或 `fpsmatch` 加载异常。
4. 加入服务器后执行 `/fpsm help`；能看到帮助树并显示 `cs` 或 `csdm` 地图，说明注册成功。

## 专用服务器

专用服务器不需要 Modern UI。安装 Forge 服务端、Java 17、FPSMatch 和 BlockOffensive。

```bash
java -version
java -Xms2G -Xmx4G -jar forge-1.20.1-47.4.10-installer.jar --nogui
```

安装器文件名仅作示例；应使用 Forge 生成的服务端启动脚本。首次启动后接受 `eula.txt`，再把模组放入 `mods/`。

```text
/fpsm help
```

## 常见安装失败

| 症状 | 原因 | 处理 |
| --- | --- | --- |
| `Missing mandatory dependency fpsmatch` | FPSMatch 缺失或版本过低 | 安装满足 `[1.26.9,)` 的 FPSMatch |
| 客户端界面崩溃 | Modern UI 缺失或不匹配 | 客户端安装 `3.12.0.1+` |
| 专用服加载客户端类失败 | 客户端专属模组或错误端侧引用 | 检查第一条异常并移除仅客户端模组 |
| 没有 `cs`/`csdm` | BlockOffensive 未加载或注册失败 | 检查日志，再执行 `/fpsm help` |

下一步：[管理员与服主手册](admin.md) · [玩家手册](player.md)。

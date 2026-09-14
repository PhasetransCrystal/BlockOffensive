# 方块攻势

[![Ask DeepWiki](https://deepwiki.com/badge.svg)](https://deepwiki.com/SSOrangeCATY/BlockOffensive)
[English Documentation](README.md) · [FPSMatch 本地 Wiki](FPSMatch/WIKI.md) · [GitHub Wiki](https://github.com/SSOrangeCATY/BlockOffensive/wiki)

方块攻势是面向 Minecraft 1.20.1 Forge 的战术竞技模组，基于 [FPSMatch](https://github.com/SSOrangeCATY/FPSMatch) 比赛框架构建。当前工作区直接提供 Counter-Strike 风格的回合制爆破 `cs` 和死斗 `csdm`，包含队伍经济、C4 目标、击杀反馈与客户端比赛展示能力，适合整合包与服务器玩法使用。

## 功能概览

| 模块 | 说明 |
| --- | --- |
| 对局流程 | 基于 FPSMatch 的 `cs` 回合生命周期、比分、加时、投票、旁观、死亡回放与 MVP 流程 |
| 死斗玩法 | `csdm` 支持自由混战或团队死斗、排名/与第一名差距反馈与复活保护（默认 6 秒） |
| 队伍系统 | 队伍选择、队伍状态、比分流程与队伍商店支持 |
| 经济系统 | 可编辑队伍商店，并接入购买阶段玩法 |
| 目标玩法 | C4 放置、爆炸、拆弹工具与目标回合胜负判定 |
| 战斗反馈 | 击杀反馈、死亡信息、爆头反馈、HUD、Overlay、TAB、队伍标记与旁观提示 |
| 地图房间 | 浏览地图、加入房间、选择队伍、准备，以及可选的地图缩略图/详情背景 |
| 兼容集成 | 必需 FPSMatch、Modern UI、LDLib2；兼容 TaCZ 及相关 Forge 玩法模组 |
| 指令帮助 | 游戏内可通过 `/fpsm help` 查看指令帮助 |

服主和地图作者可查阅随项目提供的 [FPSMatch Wiki](FPSMatch/WIKI.md)，其中包括地图创建、安全出生点、比赛中编辑锁、数据保存和[缩略图/地图图标教程](FPSMatch/WIKI.md#地图缩略图与图标填写教程)。地图 ID 长度为 1-48，仅允许小写 `a-z`、数字 `0-9`、`_`、`-`；地图和出生点工具需要 OP 2 级权限。

## 版本兼容矩阵

带 `*` 的列为必须依赖，未标注的模组列为兼容集成项。`1.3.0` 行表示当前源码工作区快照，并不代表已确认存在公开发布产物。

| BlockOffensive | 分发来源 | Minecraft* | Forge* | FPSMatch* | Modern UI* | LDLib2* | TaCZ | LR Tactical | CounterStrikeGrenade | KubeJS | Physics Mod | Hit Indication | GD656 Kill Icon | TaCZ Tweaks |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1.3.0 | GitHub 工作区快照 | 1.20.1 | 47.4.10 | 1.3.0+ | 3.12.0.1 | 2.2.27+1.20.1 | 1.1.7-hotfix | 0.4.3 | 1.20.1-1.5.2 | 2001.6.5-build.14 | 3.0.14 | 1.20.1-1.4 | 1.0.8-1.20.1-forge | 2.11.2 |
| 1.2.5.1 | Modrinth / CurseForge | 1.20.1 | 47.4.6 | 1.2.5 | 3.11.1.6 | - | 1.1.6-hotfix | 0.3.0 | 1.2.8 | - | 3.0.14 | 1.20.1-1.4 | 0.4.2-1.20.1 | - |
| 1.2.5 | Modrinth | 1.20.1 | 47.4.6 | 1.2.5 | 3.11.1.6 | - | 1.1.6-hotfix | 0.3.0 | 1.2.8 | - | 3.0.14 | 1.20.1-1.4 | 0.4.2-1.20.1 | - |

TaCZ、CounterStrikeGrenade、LR Tactical、KubeJS 及其他未标注的条目属于可选集成，并非 BlockOffensive 元数据声明的硬依赖；按服务器整合包实际使用的功能安装即可。

## 下载

| 平台 | 链接 |
| --- | --- |
| GitHub Releases | [Releases](https://github.com/SSOrangeCATY/BlockOffensive/releases) |
| Modrinth | [Modrinth 上的 BlockOffensive](https://modrinth.com/mod/blockoffensive) |
| CurseForge | [CurseForge 上的 BlockOffensive](https://www.curseforge.com/minecraft/mc-mods/blockoffensive) |

## 如何依赖方块攻势

方块攻势可以从公开的模组分发 Maven 仓库中拉取。根据你希望使用的分发平台，在 Gradle 中选择对应仓库和依赖坐标即可。下列公开坐标是已确认的历史发布版本，不代表存在公开的 `1.3.0` 快照产物。

### CurseForge Maven

CurseForge Maven 通过 CurseForge 项目 ID 与文件 ID 解析产物。当前已确认的 CurseForge 项目 ID 为 `1332812`，`1.2.5.1` 对应的公开文件 ID 为 `7110162`。

```gradle
repositories {
    maven {
        name = "CurseMaven"
        url = "https://www.cursemaven.com"
    }
}

dependencies {
    modImplementation "curse.maven:blockoffensive-1332812:7110162"
}
```

### Modrinth Maven

Modrinth Maven 通过项目 slug 与 Modrinth 版本号解析产物。当前已确认的项目 slug 为 `blockoffensive`。

```gradle
repositories {
    maven {
        name = "Modrinth"
        url = "https://api.modrinth.com/maven"
    }
}

dependencies {
    modImplementation "maven.modrinth:blockoffensive:1.2.5.1"
}
```

如果使用项目自身 Maven 发布配置生成的源码构建产物，依赖坐标为 `com.ptcrys:blockoffensive:<方块攻势版本>`。

## 社区与链接

| 资源 | 链接 |
| --- | --- |
| GitHub | [SSOrangeCATY/BlockOffensive](https://github.com/SSOrangeCATY/BlockOffensive) |
| FPSMatch | [SSOrangeCATY/FPSMatch](https://github.com/SSOrangeCATY/FPSMatch) · [本地 Wiki](FPSMatch/WIKI.md) |
| Bilibili | [作者主页](https://space.bilibili.com/21254202) |
| QQ 群 | 771884981 |

## 许可证

使用方块攻势即表示你接受 GPL v3 条款。完整许可证见 [LICENSE](LICENSE)。

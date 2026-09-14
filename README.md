# BlockOffensive

[![Ask DeepWiki](https://deepwiki.com/badge.svg)](https://deepwiki.com/SSOrangeCATY/BlockOffensive)
[中文文档](README_ZH-CN.md) · [FPSMatch local Wiki](FPSMatch/WIKI.md) · [GitHub Wiki](https://github.com/SSOrangeCATY/BlockOffensive/wiki)

BlockOffensive is a Forge mod for Minecraft 1.20.1 that builds Counter-Strike-inspired tactical modes on top of the [FPSMatch](https://github.com/SSOrangeCATY/FPSMatch) competition framework. This workspace supplies round-based demolition as `cs` and deathmatch as `csdm`, with team economy, C4 objectives, kill feedback, and client-side match presentation for modded Minecraft servers.

## Features

| Area | Description |
| --- | --- |
| Match flow | Counter-Strike-style `cs` round lifecycle, score, overtime, voting, spectator, kill-cam, and MVP flow based on FPSMatch |
| Deathmatch | `csdm` supports free-for-all or team deathmatch, rank/leader-gap feedback, and spawn protection (default: 6 seconds) |
| Teams | Team selection, team state, score flow, and team shop support |
| Economy | Editable team shops and buy-phase gameplay integration |
| Objectives | C4 placement, explosion, defuse tools, and objective round results |
| Combat feedback | Kill feedback, death messages, headshot feedback, HUD, overlay, TAB display, team pings, and spectator controls |
| Map rooms | Browse maps, join rooms, choose teams, ready up, and use optional map icons/detail backgrounds |
| Compatibility | Requires FPSMatch, Modern UI, and LDLib2; supports TaCZ and related Forge gameplay integrations |
| Commands | In-game command help is available with `/fpsm help` |

For server operators and map makers, the bundled [FPSMatch Wiki](FPSMatch/WIKI.md) covers map creation, safe spawn points, active-match edit locks, data saving, and the [thumbnail/map-icon tutorial](FPSMatch/WIKI.md#地图缩略图与图标填写教程). Map IDs must be 1-48 lowercase `a-z`, `0-9`, `_`, or `-` characters; map and spawn tools require OP level 2.

## Version Compatibility Matrix

Columns marked with `*` are required dependencies. Unmarked mod columns are compatibility integrations. The `1.3.0` row is the current source-workspace snapshot, not a confirmed public release artifact.

| BlockOffensive | Distribution | Minecraft* | Forge* | FPSMatch* | Modern UI* | LDLib2* | TaCZ | LR Tactical | CounterStrikeGrenade | KubeJS | Physics Mod | Hit Indication | GD656 Kill Icon | TaCZ Tweaks |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1.3.0 | GitHub workspace snapshot | 1.20.1 | 47.4.10 | 1.3.0+ | 3.12.0.1 | 2.2.27+1.20.1 | 1.1.7-hotfix | 0.4.3 | 1.20.1-1.5.2 | 2001.6.5-build.14 | 3.0.14 | 1.20.1-1.4 | 1.0.8-1.20.1-forge | 2.11.2 |
| 1.2.5.1 | Modrinth / CurseForge | 1.20.1 | 47.4.6 | 1.2.5 | 3.11.1.6 | - | 1.1.6-hotfix | 0.3.0 | 1.2.8 | - | 3.0.14 | 1.20.1-1.4 | 0.4.2-1.20.1 | - |
| 1.2.5 | Modrinth | 1.20.1 | 47.4.6 | 1.2.5 | 3.11.1.6 | - | 1.1.6-hotfix | 0.3.0 | 1.2.8 | - | 3.0.14 | 1.20.1-1.4 | 0.4.2-1.20.1 | - |

TaCZ, CounterStrikeGrenade, LR Tactical, KubeJS, and the other unmarked entries are optional integrations rather than mandatory dependencies declared by BlockOffensive metadata. Install the ones required by the intended server pack.

## Download

| Platform | Link |
| --- | --- |
| GitHub Releases | [Releases](https://github.com/SSOrangeCATY/BlockOffensive/releases) |
| Modrinth | [BlockOffensive on Modrinth](https://modrinth.com/mod/blockoffensive) |
| CurseForge | [BlockOffensive on CurseForge](https://www.curseforge.com/minecraft/mc-mods/blockoffensive) |

## How to Depend on BlockOffensive

BlockOffensive can be consumed from the public mod distribution Maven repositories. Pick one repository and one dependency coordinate that matches the platform you want to resolve from. The listed public coordinates are confirmed historical releases; they do not identify a public `1.3.0` snapshot artifact.

### CurseForge Maven

CurseForge Maven resolves artifacts by CurseForge project ID and file ID. The current confirmed CurseForge project ID is `1332812`, and the confirmed public file ID for `1.2.5.1` is `7110162`.

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

Modrinth Maven resolves artifacts by project slug and Modrinth version number. The confirmed project slug is `blockoffensive`.

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

For source builds published through the project's own Maven publishing configuration, the artifact coordinate is `com.ptcrys:blockoffensive:<BlockOffensive version>`.

## Community and Links

| Resource | Link |
| --- | --- |
| GitHub | [SSOrangeCATY/BlockOffensive](https://github.com/SSOrangeCATY/BlockOffensive) |
| FPSMatch | [SSOrangeCATY/FPSMatch](https://github.com/SSOrangeCATY/FPSMatch) · [local Wiki](FPSMatch/WIKI.md) |
| Bilibili | [Author page](https://space.bilibili.com/21254202) |
| QQ group | 771884981 |

## License

By using BlockOffensive, you agree to the terms of GPL v3. The complete license is available in [LICENSE](LICENSE).

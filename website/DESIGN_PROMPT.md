# BlockOffensive Wiki 文档站完整提示词

请在现有 `website/` Docusaurus 项目中设计并实现 BlockOffensive Wiki 文档站。严格参考同仓库 `FPSMatch/website/` 与 `FPSMatch/docs/` 的产品结构、信息架构、导航逻辑和阅读身份选择机制，并将视觉主题转换为贴合 Counter-Strike 2 的战术竞技风格。最终结果必须是可构建、可导航、可阅读的真实文档站，而不是静态效果图或只有首页的概念稿。

## 1. 参考与边界

- 以 `FPSMatch/website/src/pages/index.tsx`、`index.module.css`、`docusaurus.config.ts`、`sidebars.ts` 和 `FPSMatch/docs/` 为结构基准。
- 沿用 FPSMatch 的三类读者入口：玩家、地图制作者、服主/开发者；首次访问提供身份选择，选择结果保存到 `localStorage`，之后可以重新打开选择层。
- 保留 Docusaurus、React、TypeScript 和现有路径，不引入新的 UI 框架，不破坏 GitHub Pages 的 `baseUrl`。
- 使用仓库已有的 BlockOffensive Logo。不要下载或伪造 Valve、CS2 官方角色、武器图、地图图或商标素材。
- 技术事实必须以当前源码、`gradle.properties`、`dependencies.gradle`、FPSMatch 文档和游戏内 `/fpsm help` 为依据；不确定的命令、默认值或按键不要臆造。

## 2. 产品目标

让玩家在数秒内知道如何进入比赛，让地图作者找到完整的建图路径，让服主和开发者理解依赖、模式、运维与扩展边界。首页负责选择阅读路线，Wiki 正文负责深度说明，侧栏负责持续定位。

首页应明确展示：

- BlockOffensive 是 Minecraft 1.20.1 Forge 上、基于 FPSMatch 的 CS2 风格战术竞技模组。
- 两个核心模式：`cs` 回合制爆破与 `csdm` 死斗。
- 环境信息：Forge 47.4.10、Java 17、FPSMatch、客户端 Modern UI 3.12.0.1。
- 三条阅读路线：玩家 PLAY、地图制作者 BUILD、服主/开发者 EXTEND。

## 3. 信息架构

文档总览采用 FPSMatch 的“从这里开始”结构，并至少包含：

1. `README.md`：项目定位、读者入口、环境要求、模式概览、版本边界与相关链接。
2. `player.md`：加入房间、选队、准备、`cs` 爆破、`csdm` 死斗、购买经济、HUD、旁观和问题报告。
3. `mapper.md`：世界建筑与 FPSMatch 地图的区别、建图顺序、边界、T/CT 出生点、A/B 爆破区、商店、展示资源和测试清单。
4. `game-modes.md`：安装边界、模式职责、常用管理入口、服务器排错与可选兼容模组。
5. `developer.md`：`CSGameMap`、`CSDeathMatchMap`、C4 规则、网络同步、客户端 HUD、可选模组类加载边界与验证清单。

侧栏必须按任务而不是按文件名组织：

- 战术手册：玩家、模式与运维。
- 地图与开发：地图制作者、模组开发者。
- 若继续扩写子页，沿用 FPSMatch 的层级方式拆分“第一张地图”“经济与商店”“目标区域”“客户端表现”“兼容与 API”。

## 4. CS2 视觉方向

视觉主张：把首页设计成一份正在同步的比赛作战简报，把文档页设计成清晰、克制的竞技规则档案。

- 基底使用深墨蓝黑、灰白和低彩中性色；暖橙只用于主动作、模式 ID、状态和重点链接。
- 可以用一处冷蓝或青色表达 CT/系统同步状态，但避免霓虹赛博朋克。
- 使用编辑式大标题、窄体/高密度无衬线、等宽数据标签和明确的横向基线。
- 使用真实语义编号，例如 `READING INDEX / 00`、`LIVE MATCH INDEX / 01`、`BO / 1.2026.09-SNAPSHOT`。
- 背景可以包含低对比工程网格、圆形雷达构图或比分板结构线，但必须避开正文和操作区域。
- 控件以直角或轻微缺口为主，避免统一大圆角卡片、玻璃拟态、发光描边、警告条堆叠和随机军武 HUD。
- 首页只保留一个强视觉焦点；后续章节依靠信息层级而非持续装饰制造节奏。

## 5. 首页交互

- 首次访问显示阅读身份选择层，包含玩家、地图制作者、服主/开发者三项。
- 选择身份后保存到 `localStorage`，进入对应文档；再次回到首页显示“继续：当前身份”。
- 选择层支持 Esc 关闭、键盘焦点、清晰的 hover/focus 状态和移动端重排。
- 首页还需展示 `CS` 与 `CSDM` 的模式索引、版本环境、FPSMatch 关系以及进入 Wiki 的主动作。
- 所有内部链接使用 Docusaurus `Link`，兼容站点 `baseUrl` 和 `trailingSlash`。

## 6. 响应式与可访问性

- 检查 2048×1152、普通桌面、平板和 390px 移动端。窄屏必须重排，不得整体缩小。
- 保证正文、导航、按钮和状态文字的对比度；微型等宽字只承担辅助信息。
- 使用语义化标题、`aria-label`、`role=dialog`、`aria-modal` 和可见焦点。
- 响应 `prefers-reduced-motion`，移除非必要位移和延迟。
- 深浅色主题均需可读；不要让深色模式中的标题继续使用深色前景变量。

## 7. 内容写作规则

- 中文为默认语言，英文只用于真实模式 ID、代码符号和短技术标签。
- 先写用户要完成的任务，再解释内部实现。
- 命令、类名、版本和依赖使用代码格式；长命令单独放代码块。
- 明确区分 FPSMatch 的通用职责与 BlockOffensive 的玩法职责。
- 明确标注当前动态版本 `1.2026.09-SNAPSHOT` 和 FPSMatch `1.26.9+` 的版本边界，提醒以 `/fpsm help`、服务器日志和实际依赖版本为准。

## 8. 验收标准

- `tsc --noEmit` 通过。
- `docusaurus build` 通过，`onBrokenLinks: 'throw'` 下无断链。
- 所有文档路由出现在 sitemap，侧栏可达。
- 扫描不存在 Unicode 替换字符或乱码。
- 首次访问、已保存身份、Esc 关闭、三条路线跳转、深色模式和移动端均可用。
- 结果应明显继承 FPSMatch 的产品框架，同时通过橙黑竞技配色、模式索引、比分/雷达结构和 BlockOffensive 内容形成独立的 CS2 主题。

完成后说明改动的文件、信息架构、视觉主张、已执行的验证和仍需人工确认的视觉细节。

# 中国象棋 / Chinese Chess

一个用 Java Swing 写的单机中国象棋：人人对战、三档人机、悔棋重做、棋谱存取、复盘回看、三套主题。
内置引擎是完整的 alpha-beta 搜索（PVS + 置换表 + 空着裁剪 + 静态搜索），也可接 Pikafish。

A desktop Chinese Chess game built with Java Swing — local PvP, three AI levels, undo/redo,
game record save & load, move-by-move replay, and three board themes.

![截图](docs/screenshot.png)

<sub>木纹主题。另有青瓷、暗夜两套：`docs/screenshot-celadon.png`、`docs/screenshot-night.png`</sub>

---

## 功能

### 对局
- **人人对战 / 人机对战**，人机三档：简单、中等、困难
- 执红或执黑；执黑时**棋盘自动翻转**，视角永远是自己在下
- **点选两步走** 与 **按住拖拽** 两种走子方式并存
- 合法落点提示、吃子提示、最后一步高亮、将军红圈脉动
- **悔棋 / 重做**：人机模式下一次退/进双方各一步；认输、议和不能靠悔棋撤销
- **认输 / 求和**：人机模式下 AI 只有在自己不占优时才接受和棋
- **双方用时**：轮到谁，谁的时钟就实时走动（500ms 刷新），落子后计入累计
- **大师级提示**：独立引擎（优先 Pikafish，4 线程）给出推荐着法，蓝色箭头标注，
  同时显示引擎主变着的后续两三手、评估分与搜索深度（约 2.5 秒返回）

### 棋谱
- 保存 / 载入 `.xq` 棋谱（PGN 风格头 + 中文记谱 + 大括号内 UCI 坐标，人能读、程序能回放）
- 棋谱头记录结束原因，载入一盘认输/议和的棋谱不会「复活」成未结束的对局
- 导出纯中文记谱 `.txt`，方便复制分享
- **点击右侧着法表任意一手即可回看当时局面**，方向键逐手翻看，`Esc` 返回对局

### 界面
- 窗口可自由缩放，棋盘与棋子等比自适应，支持高分屏
- 三套主题：木纹、青瓷、暗夜
- 传统棋盘细节：外框贯通、九宫斜线、炮兵位标记、楚河汉界、双方纵线编号
- 音效由代码合成并缓存，落子即响，不滞后

### 规则
- 将死、困毙判负；将帅照面非法；送将非法
- **长将作负**、**长捉作负**（含一将一捉），其余三次重复局面判和
- **自然限着**：连续 60 回合无吃子判和；**子力不足**（只剩将士象）判和

---

## 快速开始

需要 **JDK 17 或更高**。

```bash
git clone https://github.com/neoleegm/ChineseChess.git
cd ChineseChess

./build.sh     # 编译并打包到 build/ChineseChess.jar
./run.sh       # 启动游戏
./test.sh      # 运行全部测试
```

也可以直接用 jar：

```bash
java -jar build/ChineseChess.jar
```

> 想要更难的对手可以装外部引擎：`./scripts/fetch-pikafish.sh`，
> 程序会自动发现 `engines/pikafish`。

---

## 操作

| 操作 | 方式 |
|---|---|
| 走子 | 点棋子再点目标格，或直接拖拽 |
| 取消选择 | 再点一次选中的棋子 |
| 复盘 | 点击右侧着法表任意一行 |
| 复盘翻页 | `←` / `→` 上一手 / 下一手，`Home` / `End` 跳到开局 / 最新 |
| 返回对局 | `Esc`（或点最新一手） |

| 快捷键 | 功能 |
|---|---|
| `⌘/Ctrl + Z` | 悔棋 |
| `⌘/Ctrl + Shift + Z` | 重做 |
| `⌘/Ctrl + N` | 重新开始 |
| `H` | 提示（人机对战） |
| `F` | 翻转棋盘 |
| `⌘/Ctrl + S` | 保存棋谱 |
| `⌘/Ctrl + O` | 载入棋谱 |
| `F1` | 游戏规则 |
| `⌘/Ctrl + Q` | 退出 |

> 快捷键都带修饰键，误触单个字母不会重开或退出对局。

---

## 项目结构

```
ChineseChess/
├── src/main/java/xiangqi/
│   ├── app/      Main.java                程序入口
│   ├── core/     Board Piece Move         规则层：走法生成、合法性、终局裁决、Zobrist
│   │             RepetitionRules         重复局面裁决（长将/长捉/判和）
│   │             Notation Fen Uci GameResult
│   ├── ai/       SearchEngine Evaluation  引擎层：PVS 搜索、评估、UCI 适配、难度门面
│   │             UciEngine AiPlayer
│   │             Difficulty SearchLimits SearchResult Engine
│   ├── game/     GameSession GameRecord   对局层：状态迁移、悔棋重做、复盘、棋谱读写
│   │             DrawPolicy               AI 是否接受和棋（纯逻辑，可测试）
│   ├── ui/       MainFrame BoardView      视图层：绘制、交互、主题、AI 调度
│   │             SidebarPanel RecordPanel
│   │             Theme SegmentedControl Ui Dialogs AiWorker
│   └── audio/    SoundBank.java           音效合成与缓存
├── src/test/java/xiangqi/test/            50 个自动化用例（规则/引擎/会话/渲染）
├── scripts/fetch-pikafish.sh              下载外部引擎到 engines/
├── docs/                                  截图与审视报告
├── build.sh  run.sh  test.sh              零依赖构建脚本
└── .github/workflows/ci.yml               GitHub Actions
```

依赖方向严格单向：`ui → game → ai → core`。`core` 与 `ai` 不依赖 Swing，
所以整局棋和整个引擎都能在无头环境里跑测试。

---

## 技术要点

### 规则层

- **走法生成**：先产生伪合法着法（`int` 打包），再逐个试走过滤自将，规则只校验一次。
- **被将判定**：从将帅格**反向探测**攻击源——四方向射线找车/炮/将（顺带覆盖将帅照面）、
  八个马位反查蹩腿点、三个兵位反查。单次判定约 20 次查表，不再「遍历所有敌子逐个试」。
- **将帅位置增量维护**，判定被将时不必全盘扫描。
- **重复局面**：维护历史局面键序列，三次重复时只回溯最近一个循环，O(n) 完成裁决；
  裁决逻辑抽在 `RepetitionRules`，规则层和搜索层共用同一口径。
- **捉子判定**：走完一步后，这枚子新攻击到一个无根（或换子不亏）的敌子即为「捉」，
  长捉与一将一捉按竞赛规则判负。
- **线程模型**：`Board` 不加锁。界面线程持有对局棋盘，AI 在 `copy()` 出的副本上工作。

### 引擎

迭代加深 + PVS + 置换表（1M 项）+ 空着裁剪 + 后期着法削减（LMR）+
杀手/历史启发 + 被将延伸 + 静态搜索（被将时搜索全部应将着法，不用 stand-pat）+
搜索路径上的长将感知重复评分，搜索结束后从置换表抽取主变着（PV）。

细节上有两处容易踩的坑，都已处理：

- 将军着、应将着、杀手着**不做 LMR 削减**，否则好棋会被推迟到后面才被看到；
- 子树里出现过重复局面时，该节点的分值依赖当前搜索路径，**只存着法不存分数**，
  避免不同路径复用同一置换表条目得出错误结论。

| 难度 | 策略 | 时间预算 |
|---|---|---|
| 简单 | 两层搜索 + 静态搜索，在最优着法 0.6 兵以内随机挑一手（会吃子、不白送，看不到三步以上的战术） | 0.6 s |
| 中等 | 完整 alpha-beta + 静态搜索，深度上限 6 | 1.2 s |
| 困难 | 迭代加深，深度上限 64；配置了 Pikafish 则交给它 | 8 s |

### 性能

走法生成从初始局面穷举合法着法（perft），与公开基准值完全一致：

| 深度 | 节点数 | 旧实现 | 当前实现 | 提升 |
|---|---|---|---|---|
| 1 | 44 | 1 ms | <1 ms | — |
| 2 | 1,920 | 7 ms | 2 ms | 3.5× |
| 3 | 79,666 | 83 ms | 15 ms | 5.5× |
| 4 | 3,290,240 | 1,481 ms | 240 ms | **6.2×** |

细节见 [docs/PRODUCT_REVIEW.md](docs/PRODUCT_REVIEW.md)。

---

## 测试

```bash
./test.sh
```

```
[规则与走法]  FEN 序列化与解析、UCI、perft、七种棋子走法、牵制、照面、将死、困毙、
              三次重复、长将、长捉、双方互捉、自然限着、子力不足、悔棋还原、克隆隔离、
              记谱、攻击判定
[AI 引擎]     吃白送车、一步杀、三档合法性、时间上限、长将自保、简单档不白送子、
              UCI 局面命令、UCI 杀棋换算、内置引擎主变着、自对弈
[对局会话]    悔棋成对、重做对称、复盘、认输、棋谱往返、计时、实时时钟、求和策略、
              认输后禁悔棋、载入终局棋谱、AI 不打断复盘、从终局继续、AI 回合
[界面渲染]    三套主题绘制、翻转生效、历史局面绘制
```

`perft` 是走法生成的黄金标准：深度 1–4 必须等于 **44 / 1920 / 79666 / 3290240**，
任何蹩马腿、塞象眼、将帅照面、送将判定的疏漏都会立刻让数字对不上。

生成 README 截图（离屏渲染，无需显示器）：

```bash
java -cp build/classes:build/test-classes xiangqi.test.Snapshot docs/screenshot.png 1080 760 木纹
```

---

## 外部引擎（可选）

内置引擎已经能下完整的棋；想要职业级强度，装一个 Pikafish：

```bash
./scripts/fetch-pikafish.sh              # 下载最新版到 engines/
./scripts/fetch-pikafish.sh 2026-01-02   # 指定版本
```

脚本会按平台挑合适的二进制（macOS Apple Silicon / Linux 按指令集挑 avx2 等 / Windows），
并把 `pikafish.nnue` 一起放到 `engines/`。游戏启动时按这个顺序查找引擎：

1. 上次在侧栏里选过的路径（保存在 Java Preferences）
2. jar 所在目录下的 `pikafish`
3. 当前工作目录下的 `pikafish`
4. `engines/pikafish`

也可以手动配置：侧边栏点「选择 Pikafish…」，选中可执行文件。

引擎以 4 线程 / 128 MB 置换表启动；侧栏会显示「Pikafish 已就绪」或具体失败原因
（例如缺少 `pikafish.nnue`、二进制与平台不匹配）。困难档优先用它，失败时自动回退内置引擎。
提示会带上引擎给出的主变着（PV）与评估：`提示：炮二平五 → 马8进7 马二进三 · d24 +0.35`。

> 引擎二进制与 `pikafish.nnue`（约 53 MB）都按平台/版本区分，因此不入库，
> 由 `scripts/fetch-pikafish.sh` 获取。

---

## Changelog

### v4.1（按 review 提纲修正）
- **规则补齐**：长捉作负、一将一捉；自然限着（60 回合无吃子）与子力不足判和。
  裁决逻辑抽到 `RepetitionRules`，规则层与搜索层共用
- **修 bug**
  - 人机求和判定写反（原先玩家落后时 AI 反而同意和棋）
  - 复盘提示「按 U 返回」实际会悔棋 → 改为 `←/→/Home/End/Esc` 导航，复盘时悔棋不再动棋
  - 取消搜索对 Pikafish 无效 → 发 UCI `stop`，实测 6 秒搜索 3ms 内返回
  - UCI `score mate N` 按回合换算，三步杀不再显示成「杀棋 +2」
  - 时钟只在落子时跳变 → 改为 500ms 实时走秒
  - 认输/议和后悔棋会让对局复活 → 禁止
  - 载入终局棋谱会复活并叫 AI 续走 → 记录 `[Reason]`，默认进复盘
  - 平局播放「将军」音效 → 新增 `DRAW`
  - `AiPlayer` 跨线程字段竞争 → volatile + 换引擎前先停搜索
- **引擎质量**：将军着/杀手着不再被 LMR 削减；受重复局面影响的分值不再写入置换表；
  静态搜索响应中止信号；内置引擎也能给出主变着
- **体验**：快捷键改为 `⌘/Ctrl + 字母`（误触 Q 不再直接退出），退出前确认；
  两个 AI 共用一个引擎进程；`scripts/fetch-pikafish.sh` 按平台下载引擎；
  侧栏显示引擎健康状态；载入终局棋谱可选择「仅复盘 / 从终局继续」
- **测试**：37 → 50 项，覆盖新增规则与上述每个 bug

### v4.0
- **架构重构**：拆分为 `core / ai / game / ui / audio` 五个包，`ChessPanel`（1095 行）拆成
  `BoardView` + `GameSession` + `MainFrame` + `SidebarPanel` + `RecordPanel`
- **性能**：重写走法生成与被将判定，去掉热路径锁，perft 提速 6.2×
- **正确性**：修正黑方将/士/象 PST 恒为 0 的评估偏差；静态搜索被将时改为搜索全部应将
- **新功能**：翻转棋盘、拖拽走子、窗口自适应、重做、认输、求和、双方用时、
  棋谱保存/载入/导出、点击复盘、三套主题、引擎深度与评分显示
- **工程化**：`build.sh / run.sh / test.sh`、GitHub Actions CI、37 个自动化用例（含 perft）
- 音效改为预渲染 Clip，消除落子音效延迟
- 简单档从「一步静态评估」升级为「两层搜索 + 根节点随机」，不再白送子

### v3.2 及更早
见 `git log`。

---

## English

A desktop Chinese Chess (Xiangqi) game in Java Swing. Local PvP and three-level PvE,
undo/redo, expert hints, game-record save/load, move-by-move replay, three board themes,
and a built-in alpha-beta engine that can also delegate to Pikafish via UCI.

```bash
./build.sh && ./run.sh    # requires JDK 17+
./test.sh                 # 50 automated checks, including perft
```

Move by clicking or dragging. Click any row in the move list to replay that position
(`←`/`→` step, `Esc` returns to the live game).
Shortcuts: `⌘/Ctrl+Z` undo, `⌘/Ctrl+Shift+Z` redo, `⌘/Ctrl+N` restart, `H` hint,
`F` flip board, `F1` rules. Rules include perpetual check/chase losses, the 60-move
no-capture draw, and insufficient-material draws.

---

## 许可 / License

本项目为开源学习项目，可自由使用和修改。
This is an open-source learning project. Feel free to use and modify.

## 致谢

- 规则与棋理参考：中国象棋竞赛规则
- 引擎思路参考：象棋巫师、Pikafish 等开源项目
- GUI：Java Swing

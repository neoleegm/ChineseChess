# ChineseChess Review 提纲（v4.0 → v4.1）

基线：`main @ dfcf768`。写提纲时 37 个测试全部通过，落地后为 50 个。
每条按「问题 → 位置 → 怎么改 → 验收」写。优先级：**P0 = 明确的 bug，必须修；P1 = 规则缺口或体验缺陷；P2 = 优化建议**。

> 本文是 v4.1 的施工单。实际落地情况见 `PRODUCT_REVIEW.md` 第七节。

## 实施约束（DeepSeek 必须遵守）

- 依赖方向保持 `ui → game → ai → core`，core/ai 不得 import Swing。零第三方依赖，Java 17。
- 每修一条，都在 `src/test/java/xiangqi/test/` 对应的 *Test 里补用例（用自制 `Checks`，不引入 JUnit）。
- 每批改完跑 `./test.sh` 必须全绿；perft(1..4) = 44 / 1920 / 79666 / 3290240 不能变。
- 不要提交 `pikafish.nnue`；commit 按 P0 / P1 / P2 分批。

---

## P0 明确 bug

### 1. 人机求和的判定逻辑写反了

- 位置：`ui/MainFrame.offerDraw()` 第 431–438 行。当前 `playerBalance <= -150`，即**玩家落后**时 AI 反而接受和棋。
- 怎么改：把判定抽到 game 层（新建 `game/DrawPolicy.java`，纯逻辑、可测试）。AI 只在「自己不占优」时接受：
- 优先用 `aiPlayer.lastResult().score()`（AI 视角，行棋方为正）；
- 接受条件：`aiScore <= +30`，且 `plyCount >= 20`；
- 没有搜索结果时退回子力差：`aiMaterial - playerMaterial <= 0`；
- 被接受前先调 `cancelThinking()`。
- 验收：`SessionTest` 新增 3 个用例——AI 大优时拒绝、均势时接受、AI 落后时接受。

### 2. 复盘提示「按 U 返回对局」实际会悔掉棋

- 位置：`MainFrame.updateStatus()` 第 239 行。U 绑定的是 `undo()`，它先 `exitReview()` 再悔棋。
- 怎么改：
- 新增复盘快捷键，用 `boardView`/根面板的 InputMap 实现，不走菜单加速键：Esc = 返回对局，←/→ = 上一手/下一手，Home/End = 开局/最新；
- 文案改为「复盘：第 N 手后（←/→ 翻看，Esc 返回对局）」；
- 复盘期间 `undo()` 直接 return，不再悔棋。
- 验收：手动验证复盘时按 U 不改变 `plyCount`；方向键能逐手翻看。

### 3. 取消搜索对 Pikafish 无效，悔棋/重开后 AI 可能卡 8–16 秒

- 位置：`ui/AiWorker.cancel()/request()` 只调用 `ai.abort()`；`ai/AiPlayer.abort()`（第 79 行）只中止内置引擎；`ai/UciEngine.search()` 是 `synchronized` 的，会一直阻塞到 `bestmove`。单线程 executor 里，新请求要排在旧搜索后面。
- 怎么改：
- `UciEngine` 新增 `public void stop()`：**不能**用 `synchronized(this)`，要单独用一把 `writerLock` 保护 `send`，只发送 `stop`；
- `search()` 收到 `bestmove` 后照常返回，调用方靠代际号丢弃这个过期结果；
- `AiPlayer.abort()` 同时调用 `searchEngine.abort()` 和 `uciEngine.stop()`；
- 防御性处理：`search()` 发送 `position` 之前先 `output.clear()`，防止残留的 `bestmove` 被误读。
- 验收：困难档 + Pikafish 下，AI 思考中按悔棋，再走一步，AI 应在约 `timeMs + 300ms` 内应着。可写成带 `Assume`（跳过）的集成测试：没有 pikafish 时自动跳过。

### 4. UCI 杀棋步数显示错误

- 位置：`UciEngine.parseInfo()` 第 200–204 行。UCI 的 `score mate N` 单位是**回合**，代码却当成**半回合**：`MATE - N`。结果 `SearchResult.scoreText()` 换算出的步数偏小（mate 3 显示成「杀棋 +2」）。
- 怎么改：先换算成半回合 `plies = N > 0 ? 2N-1 : 2|N|`，再算 `score = N>0 ? MATE - plies : -MATE + plies`。
- 验收：`EngineTest` 直接喂 `info depth 10 score mate 3 pv ...`，期望得到「杀棋 +3」；`mate -2` 期望「被杀 -2」。需要把 `parseInfo` 改成包级可见的静态解析方法，测试才能调用。

### 5. 用时时钟不会实时走动

- 位置：`ui/RecordPanel.update()` 第 165–166 行。只显示已完成着法的累计用时，所以当前方一直是 00:00，直到落子才跳变。
- 怎么改：
- `GameSession` 新增 `liveRedMillis()/liveBlackMillis()`：已完成用时 + 当前方的 `elapsedSinceTurnStart()`（终局或复盘时不累加）；
- `MainFrame` 加一个 `javax.swing.Timer(500ms)`，只刷新 `recordPanel` 的两个时钟标签，不触发整体 `refresh()`；
- 高亮该谁走时，用 `session.board().isRedTurn()`（实时局面），不要用 `displayBoard()`（复盘局面）。
- 验收：手动验证轮到谁，谁的时钟就每秒递增；`SessionTest` 用可注入的时钟源（`LongSupplier nanoClock`）断言 live 用时会增长。

### 6. 认输/议和之后还能悔棋「撤销」结果

- 位置：`GameSession.canUndo()` 第 199–205 行。只检查 `entries` 非空，所以认输后悔一步，`result` 会被清空、对局复活。
- 怎么改：`result.reason()` 为 `RESIGNATION` 或 `AGREEMENT` 时，`canUndo()` 返回 false（将死、长将、重复仍允许悔棋）。
- 验收：`SessionTest`：认输后 `canUndo()==false`。

### 7. 载入已结束的棋谱后，对局会被「复活」，AI 还可能立刻续走

- 位置：`GameSession.loadMoves()` 第 337–364 行、`MainFrame.loadRecord()` 第 694 行。认输/议和这类结果只存在 `ResultText` 里，载入时丢失了；随后 `maybeStartAiTurn()` 直接让 AI 落子，改动了原谱。
- 怎么改：
- `GameRecord` 写入 `[Result]` 和 `[Reason]` 枚举名，载入时还原 `GameResult`；
- 载入后**默认进入复盘**（`setReviewPly(0)`），并弹窗询问「从最后局面继续对弈」还是「仅复盘」；
- 只有选「继续」时才调 `maybeStartAiTurn()`。
- 验收：保存一盘认输的棋谱，再载入：结果仍在，AI 不动。

### 8. 平局用的是「将军」音效

- 位置：`MainFrame.maybeAnnounceResult()` 第 332 行。
- 怎么改：在 `audio/SoundBank.Effect` 里新增 `DRAW`（例如两声中性低音），平局时播放它。

### 9. AiPlayer 被两个线程同时读写（数据竞争）

- 位置：`ai/AiPlayer`。EDT 调用 `setDifficulty/setEnginePath/setTimeOverride`，worker 线程同时在 `chooseMove` 里读这些字段；`setEnginePath` 还可能在搜索途中 `shutdown()` 正在用的进程。
- 怎么改：
- 字段 `difficulty`、`uciEngine`、`timeOverrideMs`、`uciFailures` 加 `volatile`；
- `MainFrame.applyEnginePath()/difficultyChanged()` 修改配置前先 `cancelThinking()`；
- `chooseMove` 开头先把 `uciEngine` 读进局部变量再使用。
- 验收：代码审查通过；现有测试全绿。

---

## P1 规则缺口与体验缺陷

### 10. 规则：补「长捉」判负与「一将一捉」（中国象棋竞赛规则的核心条款）

- 现状：`Board.adjudicateRepetition()` 只判长将，其余重复一律判和。同样的逻辑在 `SearchEngine.repetitionScore()` 里重复写了一遍。
- 怎么改：

1. 新建 `core/RepetitionRules.java`，供 `Board` 和 `SearchEngine` 共用，消除重复代码；
2. `MoveRecord` 新增 `boolean chases`。判定：走完这步后，行棋方新攻击到一个对方棋子（不含将帅和未过河兵），这个子走前没被攻击，并且满足「无根」或「攻击方子力价值低于被捉子」（车捉马/炮即使有根也算捉）。实现上可以用 `isSquareAttacked` 加一个保护判断；
3. 裁决：循环中一方每步都是「将或捉」（长打），另一方不是 → 长打方负；双方都长打，或双方都不是 → 判和；
4. `GameResult.Reason` 新增 `PERPETUAL_CHASE("长捉作负")`，并同步更新 `Dialogs.showRules` 的规则说明。

- 验收：`RulesTest` 新增：车长捉无根马判负；一将一捉判负；双方互捉判和；兵捉子不算捉。`EngineTest`：AI 会避开立即的长捉作负（`AiPlayer.losesByPerpetualCheck` 改名为 `losesByRepetition`，并覆盖长捉）。

### 11. 规则：自然限着与子力不足判和

- 怎么改：
- `Board` 维护 `pliesSinceCapture`，计数器放进 `MoveRecord`，悔棋时可还原；连续 120 个半回合（60 回合）无吃子 → `Reason.MOVE_LIMIT` 判和；
- 双方都没有车、马、炮和过河兵（只剩将士象）→ `Reason.INSUFFICIENT_MATERIAL` 判和；
- `Fen.toFen` 的半回合计数写入真实的 `pliesSinceCapture`。
- 验收：`RulesTest` 两个用例；perft 不受影响。

### 12. 快捷键设计不安全

- 位置：`MainFrame.createMenuBar()` 第 154–163 行。R/U/Y/H/F/Q 都是无修饰键：按一下 Q 就直接退出，没有确认；R 会重开。
- 怎么改：
- 改为 `shortcut`（Cmd/Ctrl）+ 字母：Cmd+N 新局、Cmd+Z 悔棋、Cmd+Shift+Z 重做、Cmd+Q 退出；H/F 可以保留单键；
- 侧栏按钮文案同步，比如「提示（H）」「重新开始（⌘N）」；
- `shutdown()` 和 `windowClosing` 在对局进行中先确认（或配合第 17 条的自动保存后直接退出）；
- macOS 上用 `Desktop.getDesktop().setQuitHandler(...)` 让 Cmd+Q 也走 `shutdown()`；再加 `Runtime.addShutdownHook` 兜底关闭 Pikafish 进程。
- 验收：手动验证误触单个字母不会退出或重开。

### 13. Pikafish 分发与可用性提示

- 现状：仓库提交了 **Apple Silicon 专用**的 `pikafish` 二进制，但 nnue 被 gitignore。全新克隆后困难档会静默失败两次，然后回退内置引擎；界面仍显示「已配置：pikafish」。README 里「开箱即用」和「需自行下载 nnue」互相矛盾。另外引擎是按当前工作目录 `new File("pikafish")` 查找的（`MainFrame` 第 747 行），双击 jar 启动时找不到。
- 怎么改：
- 新增 `scripts/fetch-pikafish.sh`：按 `uname -sm` 从官方 Release 下载对应平台的二进制和 nnue，放到 `engines/`；
- 从 git 中移除平台二进制（`git rm --cached pikafish`），一并加入 `.gitignore`；
- 引擎查找顺序：Preferences → jar 所在目录 → 当前工作目录 → `engines/`；
- `UciEngine` 新增 `healthCheck()`：握手成功，且 stdout 里没有 `ERROR`/`network`；侧栏显示「Pikafish 就绪 / 缺少 nnue / 平台不兼容」三种状态；
- README 统一说明。
- 验收：在没有 nnue 的目录启动，侧栏能显示明确的原因。

### 14. 两个 Pikafish 进程各占 4 线程 + 128MB，且无论什么难度都预热

- 位置：`MainFrame` 第 139–144 行的预热会同时启动 `aiPlayer` 和 `hintPlayer` 的进程，即使当前难度是简单。
- 怎么改：两个 `AiPlayer` 共享同一个 `UciEngine` 实例（由 `MainFrame` 注入，`search` 本来就串行），且只预热一次；AI 档位不是困难时不占用引擎进程。
- 验收：用 `ps` 确认只有一个 pikafish 进程。

### 15. 复盘被 AI 落子强行打断

- 位置：`GameSession.play()` 第 129–131 行，一落子就 `exitReview()`。玩家在 AI 思考时点开复盘，AI 一落子就把画面拉回实时局面。
- 怎么改：AI 落子时保持复盘状态，只更新着法表，状态栏提示「AI 已应着，Esc 返回」。`play()` 新增参数 `keepReview`，或者把退出复盘的决定移到 UI 层。

### 16. 内置引擎提示没有主变着

- 位置：`SearchEngine.iterativeDeepening()` 第 136 行返回的 `pv` 为空。没有 Pikafish 时，提示只有一手棋。
- 怎么改：搜索结束后从 TT 抽取 PV：从根开始，`ttProbeMove` 一步步 apply，直到命中缺失、着法不合法或重复，上限 8 手；再用 `SearchResult` 的 5 参构造返回。
- 验收：`EngineTest` 断言内置引擎 `pv().size() >= 2`，且 `pv[0] == move`。

---

## P2 优化建议

### 引擎质量（`ai/SearchEngine.java`）

- **TT 存了受重复局面影响的分数**（graph-history interaction）：`pvsBody` 第 349 行。当子树内命中 `repetitionScore` 时，不要把这个分数存为 EXACT。做法：用一个 `boolean` 栈标记「子树依赖路径」，被标记的结果不存 TT，或只存着法不存分数。
- **LMR 会削减将军着**：第 312 行只判断了「自己是否被将」。要补上「这步棋是否将军对方」（apply 之后 `board.inCheck(!side)`）；杀手着也不削减。
- `quiescence()` 第 363 行只检查截止时间，没检查 `aborted`；补上 `aborted ||`。
- `iterativeDeepening` 被中止时，丢掉了当前深度已搜完的首着结果。可以接受「当前深度首着已完整搜完且分数更好」的结果。
- `Evaluation.structure()` 每个车、将都要扫描整列。可以在 `Board` 里增量维护每列的兵数 `pawnsOnFile[2][9]`。
- `Board.legalMoves()` 每次都会分配 List，UI/AiPlayer 兜底路径上可以接受，但搜索里不要用。

### 体验 / 产品

- **自动保存与恢复**：退出时把当前对局写到 `~/.chinesechess/autosave.xq`，启动时询问是否继续。
- **分析模式**：复盘时后台逐手用 Pikafish 打分，画一条评估曲线（正分红色、负分绿色），并把评分骤降 ≥1.5 兵的着法标为「失误」或「漏着」。这是同类 App 里最受欢迎的功能。
- **摆局 / FEN**：「编辑局面」模式，支持粘贴或复制 FEN。`GameRecord` 支持 `[FEN]` 头；`GameSession.replay()` 改为从初始 FEN 重演，现在硬编码的是 `new Board()`。`Fen.parse` 要校验将帅数量、九宫、象眼和兵的位置合法性。
- **残局闯关与开局库**：内置 20–50 个经典残局（七星聚会、野马操田等），用 FEN 列表实现；开局阶段用小型开局库增加变化。
- 存谱的默认文件名改为 `对局-yyyyMMdd-HHmm.xq`，现在是 `currentTimeMillis % 100000`（`MainFrame` 第 633 行）。`[Date]` 记录开局时间，而不是保存时间（`GameRecord` 第 66 行）。
- 人人对弈时认输，应询问是哪一方认输，现在默认是轮到走棋的一方（`GameSession.resign` 第 215 行）。
- 记谱：同一纵线有 4–5 个兵时，黑方序号应统一（`Notation` 第 60–66 行）；两条纵线上各有多兵的特殊记法还没覆盖。建议补测试，对照《中国象棋竞赛规则》附录的记谱示例。

### 工程

- `refresh()` 会被调用两次：`session` 监听器一次，各操作末尾再手动调一次。可以统一只由监听器驱动，或者合并到下一帧（`invokeLater` + 脏标记）。
- `MainFrame`（780 行）职责过重。把「AI 回合调度、提示、求和策略」抽成 `game/GameController`（不依赖 Swing），UI 只负责转发，这样才能对第 1、6、7、15 条写单元测试。
- CI：再加一个 macOS runner，自动下载 Pikafish，跑 UCI 集成测试（第 3、4 条）；`Snapshot` 状态栏里写死的文字改为参数。
- 修完后同步更新 `docs/PRODUCT_REVIEW.md`、`README.md` 以及根目录 `AGENTS.md` 的 ChineseChess 章节。

---

## 建议执行顺序

1. P0 #1、#2、#4、#6、#8（改动小、收益明确）→ 提交一次
2. P0 #3、#5、#7、#9（并发与计时）→ 提交一次
3. P1 #10、#11（规则，最难，先抽出 `RepetitionRules` 再加长捉）→ 提交一次
4. P1 #12–#16 → 提交一次
5. P2 按需挑选
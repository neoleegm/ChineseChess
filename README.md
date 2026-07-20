# 中国象棋 / Chinese Chess

一个使用 Java Swing 开发的图形化中国象棋游戏，支持人人对战和人机对战，包含简单、中等、困难三种 AI 难度。

A graphical Chinese Chess game developed with Java Swing, supporting both player vs player and player vs AI modes, with three difficulty levels: Easy, Medium, and Hard.

---

## 📸 截图 / Screenshot

```
┌─────────────────────────────────────┐
│           中国象棋                   │
│    車馬象士將士象馬車               │
│    · · · · · · · · ·                │
│      砲           砲                │
│  卒 · 卒 · 卒 · 卒 · 卒             │
│  ═══════ 楚河  汉界 ═══════         │
│  兵 · 兵 · 兵 · 兵 · 兵             │
│      炮           炮                │
│    · · · · · · · · ·                │
│    俥傌相仕帥仕相傌俥               │
│           [红方走棋]                 │
└─────────────────────────────────────┘
```

---

## ✨ 功能特点 / Features

### 中文
- 🎮 **双人对战**：支持两位玩家在同一电脑上对战
- 🤖 **人机对战**：与 AI 对战，三种难度可选
  - **简单**：随机走法，适合初学者
  - **中等**：基于局面评估的智能走法
  - **困难**：优先支持 Pikafish 外部引擎，未配置时使用增强版内置搜索
- ♟️ **自由选择**：可选择执红（先手）或执黑（后手）
- 🎨 **精美界面**：木质棋盘、双层边框棋子、位置标记、走子滑动动画
- 🔊 **音效提示**：选择、移动、吃子、将军、胜利音效
- ↩️ **悔棋功能**：支持悔棋，人机模式下悔双方各一步
- 📜 **对局记录栏**：右侧独立栏，中文记谱着法列表 + 双方被吃子，几十回合一目了然
- 💡 **高手提示**：人机对战中按"提示"按钮或 H 键，高手级引擎推荐着法并以蓝色箭头标出
- 💀 **被吃子展示**：实时显示双方被吃棋子
- ⚖️ **完整胜负判定**：将死、困毙判负，三次重复局面判和，长将作负
- 💾 **设置记忆**：模式、难度、执棋方、音效设置自动保存
- 📖 **规则说明**：内置游戏规则帮助

### English
- 🎮 **PvP Mode**: Two players can play on the same computer
- 🤖 **PvE Mode**: Play against AI with three difficulty levels
  - **Easy**: Random moves, suitable for beginners
  - **Medium**: Position-based evaluation for smarter moves
  - **Hard**: Can use the Pikafish external engine, with an enhanced built-in fallback
- ♟️ **Side Selection**: Choose to play as Red (first) or Black (second)
- 🎨 **Beautiful UI**: Wooden board style, double-border pieces, position markers, smooth move animation
- 🔊 **Sound Effects**: Selection, move, capture, check, and win sounds
- ↩️ **Undo**: Support undo moves, undoes both sides in PvE mode
- 📜 **Game Record Panel**: Dedicated right panel with Chinese-notation move list and captured pieces, readable for dozens of rounds
- 💡 **Expert Hint**: In PvE, press the Hint button or H key — the expert engine suggests a move and marks it with a blue arrow
- 💀 **Captured Pieces**: Real-time display of captured pieces for both sides
- ⚖️ **Full Adjudication**: Checkmate and stalemate losses, threefold-repetition draw, perpetual check loss
- 💾 **Persistent Settings**: Mode, difficulty, side, and sound settings are saved
- 📖 **Game Rules**: Built-in rule explanation

---

## 🚀 快速开始 / Quick Start

### 系统要求 / Requirements
- Java 17 或更高版本 / Java 17 or higher
- 支持图形界面的操作系统 / OS with GUI support

### 运行方法 / How to Run

#### 方法一：编译后运行 / Method 1: Compile and Run
```bash
# 克隆仓库 / Clone repository
git clone https://github.com/neoleegm/ChineseChess.git
cd ChineseChess

# 编译源代码 / Compile source code
javac -encoding UTF-8 -d bin src/*.java

# 运行游戏 / Run game
java -cp bin ChineseChessGame

# 运行轻量测试 / Run lightweight tests
java -cp bin ChineseChessTests
```

#### 方法二：使用 VS Code / Method 2: Using VS Code
- 使用 VS Code 打开项目文件夹
- 安装 Java 扩展包
- 按 F5 运行

---

## 🎮 游戏操作 / How to Play

### 中文说明

1. **选择游戏模式**
   - 在左侧设置面板选择"人机对战"或"人人对战"

2. **设置 AI 难度**（人机模式下）
   - 简单 / 中等 / 困难

   困难模式可以在侧边栏选择本机 Pikafish 可执行文件；未配置或启动失败时会自动回退到内置 AI。

3. **选择执棋方**（人机模式下）
   - 红方：先行（棋盘下方）
   - 黑方：后行（棋盘上方）

4. **走棋方法**
   - 点击要移动的棋子 → 出现绿色选中框
   - 点击目标位置 → 棋子移动
   - 再次点击已选中的棋子可取消选择

5. **胜负判定**
   - 将死对方或使对方无棋可走（困毙）即可获胜
   - 同一局面第三次出现：若一方步步将军则判长将作负，否则判和棋
   - 终局后可选择"再来一局"

6. **快捷键**
   - `U` - 悔棋
   - `R` - 重新开始
   - `H` - 提示（人机对战）
   - `Q` - 退出游戏

### English Instructions

1. **Select Game Mode**
   - Choose "Player vs AI" or "Player vs Player" in the left settings panel

2. **Set AI Difficulty** (PvE mode only)
   - Easy / Medium / Hard

3. **Choose Your Side** (PvE mode only)
   - Red: Moves first (bottom side)
   - Black: Moves second (top side)

4. **How to Move**
   - Click the piece you want to move → Green selection box appears
   - Click the target position → Piece moves
   - Click the selected piece again to cancel selection

5. **Winning Condition**
   - Checkmate the opponent, or leave them with no legal moves (stalemate counts as a loss)
   - On the third occurrence of the same position: perpetual check loses; otherwise it's a draw
   - Choose "Play again" on the game-over dialog to start a new game

6. **Keyboard Shortcuts**
   - `U` - Undo move
   - `R` - Restart game
   - `H` - Expert hint (PvE only)
   - `Q` - Quit game

---

## 📋 游戏规则 / Game Rules

### 棋子走法 / Piece Movements

| 棋子 / Piece | 走法 / Movement |
|-------------|----------------|
| 帅/将 King | 在九宫内一格一格移动 / Move one step within the palace |
| 仕/士 Advisor | 在九宫内斜线走一格 / Move diagonally one step within the palace |
| 相/象 Elephant | 走"田"字，不能过河，象眼不能被塞 / Move in "田" pattern, cannot cross river, blocked if eye is occupied |
| 傌/马 Horse | 走"日"字，马腿不能被蹩 / Move in "日" pattern, blocked if leg is occupied |
| 俥/车 Rook | 直线走，不能越子 / Move horizontally or vertically, cannot jump over pieces |
| 炮/砲 Cannon | 直线走，吃子需隔一个棋子 / Move like Rook, capture by jumping over exactly one piece |
| 兵/卒 Pawn | 向前走，过河后可横走 / Move forward, can move horizontally after crossing river |

### 特殊规则 / Special Rules

#### 中文
- **楚河汉界**：棋盘中间的界限，相/象、兵/卒不能越过
- **九宫**：将/帅、仕/士的活动范围，位于棋盘两端（3×3区域）
- **将帅对脸**：双方将/帅不能在同一直线上无遮挡相对
- **将军**：将/帅被攻击时必须解将，无法解将则为将死
- **送将**：不能主动走导致自己被将军的棋

#### English
- **River**: The middle boundary of the board. Elephants and Pawns cannot cross it
- **Palace**: The 3×3 area at each end where King and Advisors must stay
- **King Face-off**: The two Kings cannot face each other on the same file without any pieces between them
- **Check**: When King is under attack, must resolve it; if cannot, it's checkmate
- **Suicide Move**: Cannot make a move that leaves your King in check

---

## 🏗️ 项目结构 / Project Structure

```
ChineseChess/
├── src/
│   ├── ChineseChessGame.java    # 主程序 / Main class
│   ├── ChessBoard.java          # 棋盘逻辑 / Board logic
│   ├── ChessPanel.java          # 图形界面 / GUI panel
│   ├── ChessPiece.java          # 棋子类 / Piece class
│   ├── ChessAI.java             # AI 算法 / AI algorithm
│   ├── InternalChessEngine.java # 内置 AI 引擎 / Built-in AI engine
│   ├── PikafishEngine.java      # Pikafish UCI 适配 / Pikafish UCI adapter
│   ├── FenCodec.java            # FEN 编码 / FEN codec
│   ├── Move.java                # 走法对象 / Move value object
│   ├── MoveCodec.java           # UCI 走法编码 / UCI move codec
│   ├── MoveNotation.java        # 中文记谱生成 / Chinese move notation
│   ├── Engine.java              # 引擎接口 / Engine interface
│   ├── ChineseChessTests.java   # 轻量测试 / Lightweight tests
│   └── SoundManager.java        # 音效管理 / Sound manager
├── bin/                         # 编译输出目录 (gitignore)
├── .gitignore                   # Git 忽略配置
└── README.md                    # 本文件 / This file
```

---

## 🤖 AI 算法说明 / AI Algorithm

### 简单 / Easy
- 随机选择合法走法
- 80% 概率优先吃子
- 前进走法有额外权重

### 中等 / Medium
- 基于局面评估选择最优走法
- 两层搜索 + 递归吃子静态搜索
- 评估因素：
  - 棋子基础价值
  - 兵卒位置加成
  - 将军奖励
  - 被将军惩罚

### 困难 / Hard
- **Pikafish 外部引擎**：侧边栏选择可执行文件后，困难模式优先通过 UCI 协议请求最佳走法
- **自动回退**：Pikafish 未配置、超时、崩溃或返回非法走法时，自动使用内置 AI
- **内置搜索**：迭代加深 + Negamax + Alpha-Beta 剪枝 + 吃子静态延伸
- **完整评估函数**：
  - 棋子价值与车/马/炮/兵位置价值
  - 机动性、子力安全、将军/将死检测
  - 只生成真正合法走法，避免送将和将帅照面

### Pikafish 配置 / Pikafish Setup

1. 从 [Pikafish Releases](https://github.com/official-pikafish/Pikafish/releases) 下载适合系统的版本。
2. 解压后在游戏侧边栏点击“选择 Pikafish 引擎”，选择 `pikafish` 可执行文件。
3. 选择会保存在本机 Java Preferences 中；困难模式会优先使用 Pikafish，失败时回退内置 AI。

---

## 📝 更新日志 / Changelog

### v3.1
- ✅ 三栏布局：左侧游戏设置、中间棋盘、右侧对局记录（着法列表占满栏高，不再被压缩）
- ✅ 高手提示：人机对战中按 H 或"提示"按钮，独立困难档引擎（配置了 Pikafish 则用 Pikafish）推荐着法并以蓝色箭头标出
- ✅ AI 避免长将：搜索按长将规则判负评分，三档 AI 均不主动长将送负

### v3.0
- ✅ 规则完善：三次重复局面判和、长将作负，终局区分将死/困毙/和棋文案
- ✅ 修复竞态：重置/切换模式不再与 AI 后台计算冲突，AI 全程在克隆棋盘上计算
- ✅ 修复执黑悔棋锁死、AI 走法描述丢失、Pikafish 超时后协议错位等问题
- ✅ Pikafish 同步完整对局历史，引擎进程生命周期妥善管理
- ✅ 引擎修正：置换表杀棋分数归一化、中等难度递归静态搜索
- ✅ 走子滑动动画、中文记谱着法列表、被吃子展示、将军音效与提示
- ✅ 终局"再来一局"对话框、对局中重置确认、设置持久化
- ✅ 扩充轻量测试至 17 组：全部棋子规则、送将保护、将死/困毙、悔棋一致性、重复裁决、中文记谱、AI 战术

### v2.1
- ✅ 补齐将帅照面规则，AI 与 UI 统一使用真正合法走法
- ✅ 重构 AI，引入共享 Move 对象、FEN/UCI 走法编码和 Engine 接口
- ✅ 困难模式支持 Pikafish 外部引擎，并保留增强版内置搜索回退
- ✅ 新增轻量命令行测试覆盖规则、FEN、走法编码和 AI 可执行性

### v2.0 (2025-03-28)
- ✅ 完全重写项目，修复所有反编译错误
- ✅ 修正棋盘布局方向（红方在下，黑方在上）
- ✅ 完善所有棋子走法规则
  - 正确的卒子过河逻辑
  - 完整的将军/将死检测
  - 送将保护（不能主动送将）
- ✅ 增强 AI 算法
  - 三种难度级别
  - Minimax + Alpha-Beta 剪枝
  - 位置价值评估
- ✅ 优化用户界面
  - 木质风格棋盘
  - 最后一步高亮显示
  - AI 思考提示
- ✅ 添加音效系统
  - 程序生成音效，无需外部文件
- ✅ 完善项目结构
  - 清理编译文件
  - 更新 .gitignore

### v1.0
- ✅ 基础象棋功能
- ✅ 图形化界面
- ✅ 人机对战（三档难度）
- ✅ 人人对战

---

## 📄 许可 / License

本项目为开源学习项目，可自由使用和修改。

This is an open-source learning project. Feel free to use and modify.

---

## 🙏 致谢 / Acknowledgments

- 棋子字体：系统默认中文字体 / Font: System default CJK font
- 开发语言：Java / Language: Java
- GUI 框架：Swing / GUI Framework: Swing

---

**祝您游戏愉快！ / Enjoy the game!** 🎉

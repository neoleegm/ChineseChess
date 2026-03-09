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
  - **困难**：使用 Minimax + Alpha-Beta 剪枝算法，棋力较强
- ♟️ **自由选择**：可选择执红（先手）或执黑（后手）
- 🎨 **精美界面**：木质棋盘、双层边框棋子、位置标记
- 📖 **规则说明**：内置游戏规则帮助

### English
- 🎮 **PvP Mode**: Two players can play on the same computer
- 🤖 **PvE Mode**: Play against AI with three difficulty levels
  - **Easy**: Random moves, suitable for beginners
  - **Medium**: Position-based evaluation for smarter moves
  - **Hard**: Uses Minimax + Alpha-Beta pruning algorithm
- ♟️ **Side Selection**: Choose to play as Red (first) or Black (second)
- 🎨 **Beautiful UI**: Wooden board style, double-border pieces, position markers
- 📖 **Game Rules**: Built-in rule explanation

---

## 🚀 快速开始 / Quick Start

### 系统要求 / Requirements
- Java 8 或更高版本 / Java 8 or higher
- 支持图形界面的操作系统 / OS with GUI support

### 运行方法 / How to Run

#### 方法一：直接运行 / Method 1: Direct Run
```bash
# 进入游戏目录 / Enter game directory
cd chinese-chess

# 运行游戏 / Run game
java ChineseChessGame
```

#### 方法二：编译后运行 / Method 2: Compile and Run
```bash
# 进入游戏目录 / Enter game directory
cd chinese-chess

# 编译源代码 / Compile source code
javac -encoding UTF-8 *.java

# 运行游戏 / Run game
java ChineseChessGame
```

---

## 🎮 游戏操作 / How to Play

### 中文说明

1. **选择游戏模式**
   - 在左侧设置面板选择"人机对战"或"人人对战"

2. **设置 AI 难度**（人机模式下）
   - 简单 / 中等 / 困难

3. **选择执棋方**（人机模式下）
   - 红方：先行（棋盘下方）
   - 黑方：后行（棋盘上方）

4. **走棋方法**
   - 点击要移动的棋子 → 出现绿色选中框
   - 点击目标位置 → 棋子移动
   - 再次点击已选中的棋子可取消选择

5. **胜负判定**
   - 吃掉对方的将/帅即可获胜

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
   - Capture the opponent's King (将/帅) to win

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

#### English
- **River**: The middle boundary of the board. Elephants and Pawns cannot cross it
- **Palace**: The 3×3 area at each end where King and Advisors must stay
- **King Face-off**: The two Kings cannot face each other on the same file without any pieces between them

---

## 🏗️ 项目结构 / Project Structure

```
chinese-chess/
├── ChineseChessGame.java    # 主程序 / Main class
├── ChessBoard.java          # 棋盘逻辑 / Board logic
├── ChessPanel.java          # 图形界面 / GUI panel
├── ChessPiece.java          # 棋子类 / Piece class
├── ChessAI.java             # AI 算法 / AI algorithm
└── README.md                # 本文件 / This file
```

---

## 🤖 AI 算法说明 / AI Algorithm

### 简单 / Easy
- 随机选择合法走法
- 有 70% 概率优先吃子

### 中等 / Medium
- 评估函数考虑：
  - 棋子基础价值
  - 兵卒位置加成
  - 车的机动性
  - 将帅安全度
- 预判一层对手反击

### 困难 / Hard
- **Minimax 算法**：搜索深度 3 层
- **Alpha-Beta 剪枝**：优化搜索效率
- 完整的局面评估函数

---

## 📝 更新日志 / Changelog

### v1.0
- ✅ 完整的象棋规则实现
- ✅ 图形化界面
- ✅ 人机对战（三档难度）
- ✅ 人人对战
- ✅ 棋盘美化

---

## 📄 许可 / License

本项目为开源学习项目，可自由使用和修改。

This is an open-source learning project. Feel free to use and modify.

---

## 🙏 致谢 / Acknowledgments

- 棋子字体：宋体 / Font: SimSun
- 开发语言：Java / Language: Java
- GUI 框架：Swing / GUI Framework: Swing

---

**祝您游戏愉快！ / Enjoy the game!** 🎉

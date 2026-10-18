package xiangqi.ai;

import xiangqi.core.Board;

/**
 * 引擎抽象。内置搜索与外部 UCI 引擎都实现此接口，界面只依赖它。
 */
public interface Engine {

    /**
     * 在 {@code board} 的行棋方视角下搜索最佳着法。
     * 实现方必须自己复制棋盘，不得修改传入的棋盘。
     */
    SearchResult search(Board board, SearchLimits limits);

    /** 引擎显示名，用于状态栏。 */
    String name();
}

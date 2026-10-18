package xiangqi.test;

/**
 * 测试入口。全部用例跑完后按失败数返回退出码，可直接用于 CI。
 *
 * <pre>
 *   ./test.sh
 *   java -cp build/classes:build/test-classes xiangqi.test.TestRunner
 * </pre>
 */
public final class TestRunner {

    private TestRunner() {
    }

    public static void main(String[] args) {
        long start = System.nanoTime();
        System.out.println("中国象棋 测试套件");
        System.out.println("=".repeat(48));

        RulesTest.run();
        EngineTest.run();
        SessionTest.run();
        UiTest.run();

        System.out.println("=".repeat(48));
        long elapsed = (System.nanoTime() - start) / 1_000_000;
        boolean ok = Checks.summary("合计（" + elapsed + " ms）");
        System.exit(ok ? 0 : 1);
    }
}

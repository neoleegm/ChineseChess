package xiangqi.test;

import java.util.ArrayList;
import java.util.List;

/**
 * 极简断言与用例注册器。项目刻意不引入第三方测试框架：
 * 零依赖意味着 {@code ./test.sh} 在任何装了 JDK 的机器上都能直接跑。
 */
final class Checks {

    private static final List<String> FAILURES = new ArrayList<>();
    private static int passed;
    private static int failed;

    private Checks() {
    }

    static void test(String name, Runnable body) {
        try {
            body.run();
            passed++;
            System.out.println("  ok    " + name);
        } catch (Throwable t) {
            failed++;
            String message = t.getMessage() == null ? t.toString() : t.getMessage();
            FAILURES.add(name + " -> " + message);
            System.out.println("  FAIL  " + name + " -> " + message);
        }
    }

    static void isTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    static void isFalse(boolean condition, String message) {
        isTrue(!condition, message);
    }

    static void equal(Object expected, Object actual, String message) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(message + "：期望 <" + expected + ">，实际 <" + actual + ">");
        }
    }

    static void notNull(Object value, String message) {
        if (value == null) {
            throw new AssertionError(message);
        }
    }

    /** 打印汇总，返回是否全部通过。 */
    static boolean summary(String suite) {
        System.out.println();
        System.out.println(suite + "：" + passed + " 通过，" + failed + " 失败");
        if (failed > 0) {
            System.out.println("失败用例：");
            for (String failure : FAILURES) {
                System.out.println("  - " + failure);
            }
        }
        return failed == 0;
    }
}

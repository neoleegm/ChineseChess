#!/usr/bin/env bash
# 编译并运行全部测试（规则 / 引擎 / 会话 / 渲染），失败时以非零码退出。
set -euo pipefail
cd "$(dirname "$0")"

MAIN="build/classes"
TEST="build/test-classes"

mkdir -p "$MAIN" "$TEST"
find src/main/java -name '*.java' > build/sources.txt
javac --release 17 -encoding UTF-8 -d "$MAIN" @build/sources.txt

find src/test/java -name '*.java' > build/test-sources.txt
javac --release 17 -encoding UTF-8 -cp "$MAIN" -d "$TEST" @build/test-sources.txt

exec java -Djava.awt.headless=true -cp "$MAIN:$TEST" xiangqi.test.TestRunner "$@"

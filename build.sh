#!/usr/bin/env bash
# 编译主程序并打包成可执行 jar。
set -euo pipefail
cd "$(dirname "$0")"

OUT="build/classes"
JAR="build/ChineseChess.jar"

rm -rf "$OUT"
mkdir -p "$OUT"

find src/main/java -name '*.java' > build/sources.txt
javac --release 17 -encoding UTF-8 -d "$OUT" @build/sources.txt

jar --create --file "$JAR" --main-class xiangqi.app.Main -C "$OUT" .

echo "构建完成：$JAR"
echo "运行：./run.sh   或   java -jar $JAR"

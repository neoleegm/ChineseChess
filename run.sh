#!/usr/bin/env bash
# 启动游戏。首次运行会自动构建。
set -euo pipefail
cd "$(dirname "$0")"

if [ ! -f build/ChineseChess.jar ]; then
    ./build.sh
fi

# 在项目根目录启动，便于自动发现同目录下的 pikafish 引擎
exec java -jar build/ChineseChess.jar "$@"

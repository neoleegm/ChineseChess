#!/usr/bin/env bash
# 下载并安装 Pikafish 外部引擎到 engines/。
#
# 用法：
#   ./scripts/fetch-pikafish.sh                 # 取最新版本
#   ./scripts/fetch-pikafish.sh 2026-01-02      # 指定版本
#   ./scripts/fetch-pikafish.sh --force         # 已存在也重新下载
#
# 官方只发布 .7z 包（内含各平台二进制 + pikafish.nnue），因此需要 7z 或 bsdtar。
# macOS 自带的 bsdtar 一般可直接解压；Linux 请安装 p7zip。
set -euo pipefail

cd "$(dirname "$0")/.."
DEST="engines"
VERSION=""
FORCE=0

for arg in "$@"; do
    case "$arg" in
        --force) FORCE=1 ;;
        -h|--help) sed -n '2,12p' "$0"; exit 0 ;;
        *) VERSION="$arg" ;;
    esac
done

if [ "$FORCE" -eq 0 ] && [ -f "$DEST/pikafish.nnue" ] \
        && { [ -x "$DEST/pikafish" ] || [ -x "$DEST/pikafish.exe" ]; }; then
    echo "已存在 $DEST/pikafish 与 $DEST/pikafish.nnue，跳过下载（--force 可强制重下）"
    exit 0
fi
if [ -z "$VERSION" ]; then
    VERSION=$(curl -fsSL https://api.github.com/repos/official-pikafish/Pikafish/releases/latest \
        | sed -n 's/.*"tag_name": *"Pikafish-\([^"]*\)".*/\1/p' | head -1)
fi
if [ -z "$VERSION" ]; then
    echo "无法确定 Pikafish 版本，请手动指定，例如：./scripts/fetch-pikafish.sh 2026-01-02" >&2
    exit 1
fi

URL="https://github.com/official-pikafish/Pikafish/releases/download/Pikafish-$VERSION/Pikafish.$VERSION.7z"
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT

echo "下载 Pikafish $VERSION …"
echo "  $URL"
curl -fL --progress-bar "$URL" -o "$TMP/pikafish.7z"

echo "解压 …"
mkdir -p "$TMP/out"
EXTRACTED=0
# 依次尝试 bsdtar / 7zz / 7z：macOS 自带 bsdtar 就能读 7z，Linux 一般需要 p7zip
if command -v bsdtar >/dev/null 2>&1 && bsdtar -xf "$TMP/pikafish.7z" -C "$TMP/out" 2>/dev/null; then
    EXTRACTED=1
elif command -v 7zz >/dev/null 2>&1 && 7zz x -y -o"$TMP/out" "$TMP/pikafish.7z" >/dev/null 2>&1; then
    EXTRACTED=1
elif command -v 7z >/dev/null 2>&1 && 7z x -y -o"$TMP/out" "$TMP/pikafish.7z" >/dev/null 2>&1; then
    EXTRACTED=1
fi
if [ "$EXTRACTED" -eq 0 ]; then
    echo "解压失败：请安装 p7zip（brew install p7zip / apt install p7zip-full），或手动解压。" >&2
    exit 1
fi

# 官方包按平台分目录，Linux/Windows 还按 CPU 指令集给了多个变体
case "$(uname -s)" in
    Darwin)              PLATFORM_DIR="MacOS" ;;
    Linux)               PLATFORM_DIR="Linux" ;;
    MINGW*|MSYS*|CYGWIN*) PLATFORM_DIR="Windows" ;;
    *) echo "暂不支持的系统：$(uname -s)，请手动安装 Pikafish。" >&2; exit 1 ;;
esac

pick() {
    # 优先按推荐顺序挑，找不到就退回该目录下第一个候选
    for want in "$@"; do
        local hit
        hit=$(find "$TMP/out/$PLATFORM_DIR" -type f -name "$want" 2>/dev/null | head -1)
        if [ -n "$hit" ]; then
            echo "$hit"
            return 0
        fi
    done
    find "$TMP/out/$PLATFORM_DIR" -type f \( -name 'pikafish*' -o -name 'pikafish*.exe' \) 2>/dev/null | head -1
}

case "$PLATFORM_DIR" in
    Windows) BINARY=$(pick pikafish-avx2.exe pikafish-bmi2.exe pikafish-sse41-popcnt.exe) ;;
    Linux)   BINARY=$(pick pikafish-avx2 pikafish-bmi2 pikafish-sse41-popcnt pikafish-avx512icl) ;;
    *)       BINARY=$(pick pikafish-apple-silicon pikafish) ;;
esac
NNUE=$(find "$TMP/out" -type f -name '*.nnue' | head -1 || true)

if [ -z "${BINARY:-}" ]; then
    echo "压缩包里没有找到 $PLATFORM_DIR 平台的 pikafish 可执行文件，请手动安装。" >&2
    exit 1
fi
if [ "$PLATFORM_DIR" = "MacOS" ] && [ "$(uname -m)" != "arm64" ]; then
    echo "提示：官方只提供 Apple Silicon 版本，Intel Mac 需要自行编译或使用 Rosetta。" >&2
fi

mkdir -p "$DEST"
DEST_NAME="pikafish"
[ "$PLATFORM_DIR" = "Windows" ] && DEST_NAME="pikafish.exe"
cp "$BINARY" "$DEST/$DEST_NAME"
chmod +x "$DEST/$DEST_NAME"
if [ -n "$NNUE" ]; then
    cp "$NNUE" "$DEST/pikafish.nnue"
else
    echo "警告：压缩包里没有 .nnue 文件，引擎会在第一次搜索时退出。" >&2
fi

echo "安装完成："
ls -lh "$DEST/pikafish" "$DEST/pikafish.nnue" 2>/dev/null || true
echo
echo "启动游戏后会自动发现 engines/pikafish；也可以在侧栏「选择 Pikafish…」手动指定。"

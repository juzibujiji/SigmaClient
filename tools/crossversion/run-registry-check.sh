#!/usr/bin/env bash
# 跨版本注册表回归检查。
#
# 验证跨版本扩展（1.17-1.21.11 的新物品与方块）没有让任何原版 1.16.4 的 ID 移位。
# 一旦移位，连接 1.8 / 1.12 / 1.16.4 服务器时所有物品都会错位，因此这项检查必须
# 在每次改动 Items/Blocks/ModernItems/ModernBlocks 之后运行。
#
# 用法：bash tools/crossversion/run-registry-check.sh
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$REPO_ROOT"

MVN="${MVN:-mvn}"
if ! command -v "$MVN" >/dev/null 2>&1; then
    for candidate in /f/HCMLNew/tools/apache-maven-*/bin/mvn ../tools/apache-maven-*/bin/mvn; do
        [ -x "$candidate" ] && MVN="$candidate" && break
    done
fi

BUILD_DIR="target/crossversion-check"
CP_FILE="$BUILD_DIR/classpath.txt"
mkdir -p "$BUILD_DIR"

# classpath 变化不频繁，缓存下来避免每次都跑 Maven（需要联网解析插件）。
if [ ! -s "$CP_FILE" ] || [ pom.xml -nt "$CP_FILE" ]; then
    echo "==> 生成依赖 classpath"
    "$MVN" -q dependency:build-classpath -Dmdep.outputFile="$CP_FILE"
fi

echo "==> 编译主工程"
"$MVN" -o -q compile

CP="target/classes;$BUILD_DIR;$(cat "$CP_FILE")"

echo "==> 编译检查程序"
# -proc:none：依赖里含 lombok / sisu 的注解处理器，会被自动发现并在 JDK 17 下报错。
javac -proc:none -encoding UTF-8 -cp "$CP" -d "$BUILD_DIR" \
    tools/crossversion/RegistryCheck.java tools/crossversion/DataPackCheck.java

echo "==> 运行注册表检查"
java -Dfile.encoding=UTF-8 -cp "$CP" verify.RegistryCheck \
    docs/registry-diff/baseline-1.16.4-items.txt \
    docs/registry-diff/baseline-1.16.4-blocks.txt

# 数据包检查需要「已注册对象」清单。tag 引用缺失会让 1.16.4 丢弃整张 tag，
# 进而导致「数据包出现错误，世界无法加载」；战利品表引用未注册物品会在挖方块时抛异常。
# 这两类问题都不会在编译期暴露，必须在这里挡住。
REG_BLOCKS="$BUILD_DIR/reg-blocks.txt"
REG_ITEMS="$BUILD_DIR/registered-items.txt"
if [ -s "$REG_BLOCKS" ] && [ -s "$REG_ITEMS" ]; then
    echo "==> 运行数据包检查"
    java -Dfile.encoding=UTF-8 -cp "$CP" verify.DataPackCheck \
        src/main/resources/data/minecraft "$REG_BLOCKS" "$REG_ITEMS"
else
    echo "==> 跳过数据包检查（缺少已注册对象清单，先跑一次生成器）"
fi

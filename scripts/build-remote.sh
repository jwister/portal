#!/usr/bin/env bash
# 在远程 Docker daemon 上构建 Portal 镜像。
#
# 用法：
#   ./scripts/build-remote.sh
#   ./scripts/build-remote.sh v1.0.0
#   PUSH=1 ./scripts/build-remote.sh v1.0.0
#   REMOTE=tcp://host:2375 IMAGE=wenyou7/ztoken-portal ./scripts/build-remote.sh

set -euo pipefail

REMOTE="${REMOTE:-tcp://192.168.100.153:2375}"
IMAGE="${IMAGE:-wenyou7/ztoken-portal}"
PLATFORM="${PLATFORM:-linux/amd64}"
EXTRA_TAG="${1:-}"
PUSH="${PUSH:-0}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
BACKEND_ROOT="$REPO_ROOT/backend"
TARGET_ROOT="$BACKEND_ROOT/target"

log() { printf '\033[1;34m[build-remote]\033[0m %s\n' "$*"; }
die() { printf '\033[1;31m[build-remote]\033[0m %s\n' "$*" >&2; exit 1; }
run_step() {
  local failure_message="$1"
  shift
  if ! "$@"; then
    die "$failure_message"
  fi
}

command -v docker >/dev/null 2>&1 || die 'PATH 中未找到 docker CLI'
command -v mvn >/dev/null 2>&1 || die 'PATH 中未找到 Maven 命令 mvn'

TAGS=("$IMAGE:latest")
if [ -n "$EXTRA_TAG" ]; then
  TAGS+=("$IMAGE:$EXTRA_TAG")
fi

log "远程 Docker daemon：$REMOTE"
log "镜像标签：${TAGS[*]}"
log "目标平台：$PLATFORM"
log "构建上下文：$REPO_ROOT"

case "$REMOTE" in
  tcp://*:2375|tcp://*:2375/*) log '警告：正在使用未加密的 tcp:2375，请确认该 Docker daemon 位于可信网络。' ;;
esac

log '步骤 1/2：本地打包前后端单体 JAR...'
cd "$BACKEND_ROOT"
run_step 'Portal 本地打包失败' mvn clean package -DskipTests -B

shopt -s nullglob
JARS=("$TARGET_ROOT"/ztoken-portal-*.jar)
shopt -u nullglob
EXECUTABLE_JARS=()
for jar in "${JARS[@]}"; do
  [[ "$jar" == *.jar.original ]] || EXECUTABLE_JARS+=("$jar")
done
[ "${#EXECUTABLE_JARS[@]}" -eq 1 ] || die "期望在 $TARGET_ROOT 中找到一个可执行 JAR，实际找到 ${#EXECUTABLE_JARS[@]} 个"
log "打包产物：$(basename "${EXECUTABLE_JARS[0]}")"

log '步骤 2/2：在远程 Docker daemon 构建镜像...'
cd "$REPO_ROOT"
run_step "无法连接远程 Docker daemon：$REMOTE" docker -H "$REMOTE" version --format '{{.Server.Version}}'

BUILD_ARGS=(-H "$REMOTE" build --platform "$PLATFORM")
for tag in "${TAGS[@]}"; do
  BUILD_ARGS+=(-t "$tag")
done
BUILD_ARGS+=(-f Dockerfile .)
run_step '远程 Docker 镜像构建失败' docker "${BUILD_ARGS[@]}"

log '镜像构建完成。'
run_step '查询远程镜像列表失败' docker -H "$REMOTE" image ls --filter "reference=$IMAGE" --format 'table {{.Repository}}:{{.Tag}}\t{{.ID}}\t{{.Size}}\t{{.CreatedSince}}'

if [ "$PUSH" = '1' ]; then
  for tag in "${TAGS[@]}"; do
    log "推送镜像：$tag"
    run_step "推送镜像失败：$tag" docker -H "$REMOTE" push "$tag"
  done
fi

log '完成。'

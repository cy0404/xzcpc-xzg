#!/bin/bash
#===============================================================================
# 盘点工具 1.0 — 总部端服务管理脚本
# 用法: ./server.sh {start|stop|restart|status}
#===============================================================================

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
JAR_DIR="$SCRIPT_DIR"
JAR_NAME="store_inventory-1.0.0.jar"
JAR_PATH="$JAR_DIR/$JAR_NAME"
PID_FILE="$SCRIPT_DIR/server.pid"
LOG_FILE="$SCRIPT_DIR/app.log"

# JVM 参数
JAVA_OPTS="-Xms256m -Xmx512m -Xss512k -Dfile.encoding=UTF-8"

#------------------------------------------------------------------------------
# 颜色输出
#------------------------------------------------------------------------------
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

log_info()  { echo -e "${GREEN}[INFO]${NC}  $(date '+%Y-%m-%d %H:%M:%S') $*"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC}  $(date '+%Y-%m-%d %H:%M:%S') $*"; }
log_error() { echo -e "${RED}[ERROR]${NC} $(date '+%Y-%m-%d %H:%M:%S') $*"; }

#------------------------------------------------------------------------------
# 获取进程 PID（优先 PID 文件，其次通过 jar 名匹配）
#------------------------------------------------------------------------------
get_pid() {
    if [ -f "$PID_FILE" ]; then
        local pid
        pid=$(cat "$PID_FILE" 2>/dev/null)
        if [ -n "$pid" ] && kill -0 "$pid" 2>/dev/null; then
            echo "$pid"
            return 0
        fi
    fi
    # 降级：通过 jar 名查找
    local pid
    pid=$(ps -ef | grep "$JAR_NAME" | grep -v grep | awk '{print $2}' | head -1)
    if [ -n "$pid" ]; then
        echo "$pid"
        return 0
    fi
    return 1
}

#------------------------------------------------------------------------------
# 启动服务
#------------------------------------------------------------------------------
start() {
    if pid=$(get_pid); then
        log_warn "服务已在运行中 (PID: $pid)"
        return 0
    fi

    if [ ! -f "$JAR_PATH" ]; then
        log_error "找不到 JAR 包: $JAR_PATH"
        log_error "请先执行: mvn clean package -DskipTests"
        exit 1
    fi

    log_info "正在启动 $JAR_NAME ..."
    nohup java $JAVA_OPTS -jar "$JAR_PATH" >> "$LOG_FILE" 2>&1 &
    local new_pid=$!
    echo "$new_pid" > "$PID_FILE"

    # 等待启动，最多 30 秒
    local waited=0
    while [ $waited -lt 30 ]; do
        sleep 1
        waited=$((waited + 1))
        if ! kill -0 "$new_pid" 2>/dev/null; then
            log_error "服务启动失败，请查看日志: $LOG_FILE"
            rm -f "$PID_FILE"
            exit 1
        fi
        # 检查端口是否已监听
        if ss -tlnp 2>/dev/null | grep -q ":4026 " || netstat -tlnp 2>/dev/null | grep -q ":4026 "; then
            log_info "服务启动成功 (PID: $new_pid, 端口: 4026)"
            return 0
        fi
    done

    # 30 秒后端口未就绪但仍存活，也算启动成功
    if kill -0 "$new_pid" 2>/dev/null; then
        log_info "服务已启动 (PID: $new_pid)，端口可能仍在初始化中"
    else
        log_error "服务启动超时"
        rm -f "$PID_FILE"
        exit 1
    fi
}

#------------------------------------------------------------------------------
# 停止服务
#------------------------------------------------------------------------------
stop() {
    local pid
    if ! pid=$(get_pid); then
        log_warn "服务未在运行"
        rm -f "$PID_FILE"
        return 0
    fi

    log_info "正在停止服务 (PID: $pid) ..."

    # 优雅关闭 (SIGTERM)，最多等 15 秒
    kill "$pid" 2>/dev/null || true
    local waited=0
    while [ $waited -lt 15 ]; do
        if ! kill -0 "$pid" 2>/dev/null; then
            log_info "服务已停止"
            rm -f "$PID_FILE"
            return 0
        fi
        sleep 1
        waited=$((waited + 1))
    done

    # 强制关闭 (SIGKILL)
    log_warn "优雅关闭超时，执行强制关闭..."
    kill -9 "$pid" 2>/dev/null || true
    sleep 1
    if kill -0 "$pid" 2>/dev/null; then
        log_error "无法停止服务 (PID: $pid)"
        exit 1
    else
        log_info "服务已强制停止"
    fi
    rm -f "$PID_FILE"
}

#------------------------------------------------------------------------------
# 重启服务
#------------------------------------------------------------------------------
restart() {
    log_info "正在重启服务..."
    stop
    sleep 2
    start
}

#------------------------------------------------------------------------------
# 查看状态
#------------------------------------------------------------------------------
status() {
    local pid
    if pid=$(get_pid); then
        log_info "服务运行中 (PID: $pid)"

        # 显示端口信息
        if ss -tlnp 2>/dev/null | grep -q ":4026 " || netstat -tlnp 2>/dev/null | grep -q ":4026 "; then
            log_info "端口 4026 已监听"
        fi

        # 显示运行时间
        local elapsed
        elapsed=$(ps -o etime= -p "$pid" 2>/dev/null | tr -d ' ')
        if [ -n "$elapsed" ]; then
            log_info "运行时间: $elapsed"
        fi
    else
        log_warn "服务未运行"
        rm -f "$PID_FILE"
    fi
}

#------------------------------------------------------------------------------
# 入口
#------------------------------------------------------------------------------
case "${1:-}" in
    start)
        start
        ;;
    stop)
        stop
        ;;
    restart)
        restart
        ;;
    status)
        status
        ;;
    *)
        echo "用法: $0 {start|stop|restart|status}"
        echo ""
        echo "  start   - 启动 $JAR_NAME"
        echo "  stop    - 停止服务"
        echo "  restart - 重启服务"
        echo "  status  - 查看服务状态"
        exit 1
        ;;
esac

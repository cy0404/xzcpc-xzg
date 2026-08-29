#!/bin/bash
#===============================================================================
# 盘点工具 1.0 — 小程序端测试服务管理脚本
# 用法: ./server-test.sh {start|stop|restart|status}
#===============================================================================

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
JAR_DIR="$SCRIPT_DIR"
JAR_NAME="mp_store_inventory-test.jar"
JAR_PATH="$JAR_DIR/$JAR_NAME"
PID_FILE="$SCRIPT_DIR/mp-server-test.pid"
LOG_FILE="$SCRIPT_DIR/mp-test-app.log"

# JVM 参数（测试端口 30262）
JAVA_OPTS="-Xms512m -Xmx1024m -Xss512k -Dfile.encoding=UTF-8 -Dspring.profiles.active=prod -Dserver.port=30262"

# ---------- 环境变量（密钥，不写入 git） ----------
export DB_PASSWORD="Xzcpc@2026"
export MP_JWT_SECRET="xzcpc-mp-jwt-secret-key-2026-production-change-this"
export WX_APP_SECRET="f9651f46d1b892c6e36e20eac6f932b3"
export STORE_API_KEY="aSwnTbaCRhKPARFtARvaBHtyZ6fUMZJM"
export XINFO_API_KEY="xk-axK3mP9vL2nQ7wR4jF6tH1yC5bN8"
export FEISHU_ALERT_WEBHOOK_URL="https://open.feishu.cn/open-apis/bot/v2/hook/f69d5ede-5be0-4d31-a285-4d64f4757bf8"

#------------------------------------------------------------------------------
# 颜色输出
#------------------------------------------------------------------------------
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log_info()  { echo -e "${GREEN}[INFO]${NC}  $(date '+%Y-%m-%d %H:%M:%S') $*"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC}  $(date '+%Y-%m-%d %H:%M:%S') $*"; }
log_error() { echo -e "${RED}[ERROR]${NC} $(date '+%Y-%m-%d %H:%M:%S') $*"; }

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
    local pid
    pid=$(ps -ef | grep "$JAR_NAME" | grep -v grep | awk '{print $2}' | head -1)
    if [ -n "$pid" ]; then
        echo "$pid"
        return 0
    fi
    return 1
}

#------------------------------------------------------------------------------
start() {
    if pid=$(get_pid); then
        log_warn "测试服务已在运行中 (PID: $pid)"
        return 0
    fi

    if [ ! -f "$JAR_PATH" ]; then
        log_error "找不到 JAR 包: $JAR_PATH"
        log_error "请先执行: cp mp_store_inventory-1.0.0.jar mp_store_inventory-test.jar"
        exit 1
    fi

    log_info "正在启动测试服务 $JAR_NAME (端口 30262)..."
    nohup java $JAVA_OPTS -jar "$JAR_PATH" </dev/null >> "$LOG_FILE" 2>&1 &
    local new_pid=$!
    echo "$new_pid" > "$PID_FILE"
    disown "$new_pid" 2>/dev/null || true

    local waited=0
    while [ $waited -lt 30 ]; do
        sleep 1
        waited=$((waited + 1))
        if ! kill -0 "$new_pid" 2>/dev/null; then
            log_error "测试服务启动失败，请查看日志: $LOG_FILE"
            rm -f "$PID_FILE"
            exit 1
        fi
        if ss -tlnp 2>/dev/null | grep -q ":30262 " || netstat -tlnp 2>/dev/null | grep -q ":30262 "; then
            log_info "测试服务启动成功 (PID: $new_pid, 端口: 30262)"
            return 0
        fi
    done

    if kill -0 "$new_pid" 2>/dev/null; then
        log_info "测试服务已启动 (PID: $new_pid)，端口可能仍在初始化中"
    else
        log_error "测试服务启动超时"
        rm -f "$PID_FILE"
        exit 1
    fi
}

#------------------------------------------------------------------------------
stop() {
    local pid
    if ! pid=$(get_pid); then
        log_warn "测试服务未在运行"
        rm -f "$PID_FILE"
        return 0
    fi

    log_info "正在停止测试服务 (PID: $pid) ..."
    kill "$pid" 2>/dev/null || true
    local waited=0
    while [ $waited -lt 15 ]; do
        if ! kill -0 "$pid" 2>/dev/null; then
            log_info "测试服务已停止"
            rm -f "$PID_FILE"
            return 0
        fi
        sleep 1
        waited=$((waited + 1))
    done

    log_warn "优雅关闭超时，执行强制关闭..."
    kill -9 "$pid" 2>/dev/null || true
    sleep 1
    if kill -0 "$pid" 2>/dev/null; then
        log_error "无法停止测试服务 (PID: $pid)"
        exit 1
    else
        log_info "测试服务已强制停止"
    fi
    rm -f "$PID_FILE"
}

#------------------------------------------------------------------------------
restart() {
    log_info "正在重启测试服务..."
    stop
    sleep 2
    start
}

#------------------------------------------------------------------------------
status() {
    local pid
    if pid=$(get_pid); then
        log_info "测试服务运行中 (PID: $pid)"
        if ss -tlnp 2>/dev/null | grep -q ":30262 " || netstat -tlnp 2>/dev/null | grep -q ":30262 "; then
            log_info "端口 30262 已监听"
        fi
        local elapsed
        elapsed=$(ps -o etime= -p "$pid" 2>/dev/null | tr -d ' ')
        if [ -n "$elapsed" ]; then
            log_info "运行时间: $elapsed"
        fi
    else
        log_warn "测试服务未运行"
        rm -f "$PID_FILE"
    fi
}

#------------------------------------------------------------------------------
case "${1:-}" in
    start)   start ;;
    stop)    stop ;;
    restart) restart ;;
    status)  status ;;
    *)
        echo "用法: $0 {start|stop|restart|status}"
        exit 1
        ;;
esac

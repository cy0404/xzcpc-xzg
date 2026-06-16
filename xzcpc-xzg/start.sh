#!/bin/bash
# 同时启动前后端（需先启动 MySQL）
DIR="$(dirname "$0")"

echo "=== 启动后端 ==="
"$DIR/start-backend.sh" &
BACKEND_PID=$!

echo "=== 启动前端 ==="
"$DIR/start-frontend.sh" &
FRONTEND_PID=$!

echo ""
echo "后端 PID: $BACKEND_PID  (http://localhost:8080)"
echo "前端 PID: $FRONTEND_PID  (http://localhost:5173)"
echo "Ctrl+C 停止所有服务"

trap "kill $BACKEND_PID $FRONTEND_PID 2>/dev/null; exit" INT TERM
wait

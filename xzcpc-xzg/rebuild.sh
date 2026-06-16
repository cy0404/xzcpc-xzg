#!/bin/bash
# 重新打包后端（改了 Java 代码后执行）

# 0. 停掉占用 8080 端口和 jar 文件的旧进程
echo ">>> 检查并停止旧后端进程..."
PID=$(netstat -ano 2>/dev/null | grep ":8080" | grep LISTENING | awk '{print $5}' | head -1)
if [ -n "$PID" ]; then
  taskkill //PID "$PID" //F 2>/dev/null
  echo "已停止旧后端进程 (PID: $PID)"
  sleep 1
else
  echo "没有运行中的后端进程"
fi

# 1. 设置 Maven 路径
if ! command -v mvn &>/dev/null; then
  MAVEN_DIR="/c/Users/xiemg/AppData/Local/Temp/apache-maven-3.9.6"
  if [ -f "$MAVEN_DIR/bin/mvn" ]; then
    export PATH="$MAVEN_DIR/bin:$PATH"
  fi
fi

# 2. 打包
cd "$(dirname "$0")"
mvn clean package -DskipTests

if [ $? -eq 0 ]; then
  echo ""
  echo "打包完成，执行 ./start-backend.sh 启动后端"
else
  echo ""
  echo "打包失败，请检查上方错误信息"
  exit 1
fi

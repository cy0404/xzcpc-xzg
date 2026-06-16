#!/bin/bash
# 手动启动 MySQL（前台运行，Ctrl+C 停止）
MYSQLD="/c/Program Files/MySQL/MySQL Server 8.0/bin/mysqld"
DATADIR="C:/ProgramData/MySQL/MySQL Server 8.0/Data"

"$MYSQLD" --standalone --console --datadir="$DATADIR"

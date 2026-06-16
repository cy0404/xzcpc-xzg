#!/bin/bash
# 快速启动后端（不经过 Maven 编译）
cd "$(dirname "$0")/server"
java -jar target/store_inventory-1.0.0.jar

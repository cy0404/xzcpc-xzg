@echo off
REM ============================================================
REM 从服务器拉取最新数据库备份到本机
REM 用法: 双击运行，或 Windows 任务计划程序定时执行
REM ============================================================

set SERVER=162.14.122.80
set SERVER_USER=你的服务器用户名
set BACKUP_DIR=%USERPROFILE%\Documents\db-backups
set REMOTE_DIR=/你的路径/inventory-tool/xzcpc-xzg/db-backups

mkdir "%BACKUP_DIR%" 2>nul

echo [%date% %time%] 正在从服务器拉取备份...
scp %SERVER_USER%@%SERVER%:%REMOTE_DIR%/store_inventory_*.sql.gz "%BACKUP_DIR%"

echo [%date% %time%] 清理本机 7 天前的旧备份...
forfiles /p "%BACKUP_DIR%" /m *.sql.gz /d -7 /c "cmd /c del @file" 2>nul

echo [%date% %time%] 完成。当前备份:
dir "%BACKUP_DIR%" /b

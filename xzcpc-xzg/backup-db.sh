#!/bin/bash
# ============================================================
# 盘点工具 1.0 — 数据库备份脚本
# 用法: ./backup-db.sh
# 建议: crontab 每日凌晨 3 点执行 0 3 * * * /path/to/backup-db.sh
# ============================================================

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
BACKUP_DIR="$SCRIPT_DIR/db-backups"
DB_HOST="${DB_HOST:-162.14.122.80}"
DB_PORT="${DB_PORT:-3306}"
DB_USER="${DB_USER:-store_inventory}"
DB_PASS="${DB_PASS:-Xzcpc@2026}"
DB_NAME="store_inventory"
RETENTION_DAYS=7

mkdir -p "$BACKUP_DIR"

TIMESTAMP=$(date +%Y%m%d_%H%M%S)
BACKUP_FILE="$BACKUP_DIR/${DB_NAME}_${TIMESTAMP}.sql.gz"

echo "[$(date '+%Y-%m-%d %H:%M:%S')] 开始备份 $DB_NAME ..."

mysqldump \
  -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER" -p"$DB_PASS" \
  --single-transaction \
  --no-create-info \
  --set-gtid-purged=OFF \
  "$DB_NAME" | gzip > "$BACKUP_FILE"

echo "[$(date '+%Y-%m-%d %H:%M:%S')] 备份完成: $BACKUP_FILE ($(du -h "$BACKUP_FILE" | cut -f1))"

# 清理 7 天前的旧备份
DELETED=$(find "$BACKUP_DIR" -name "${DB_NAME}_*.sql.gz" -mtime +$RETENTION_DAYS -delete -print | wc -l)
if [ "$DELETED" -gt 0 ]; then
  echo "[$(date '+%Y-%m-%d %H:%M:%S')] 清理了 $DELETED 个旧备份"
fi

echo "[$(date '+%Y-%m-%d %H:%M:%S')] 当前备份数量: $(ls "$BACKUP_DIR"/*.sql.gz 2>/dev/null | wc -l)"

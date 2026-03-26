#!/bin/bash
# Description: This script safely disables the temporary Google News fallback sources 
# using the MariaDB Docker container.

DB_USER="reader"
DB_PASS="readerpass"
DB_NAME="news_reader"

echo "[Cleanup] Disabling Google News sources using Docker..."

# 新版 mariadb 镜像的内置命令行客户端改名为 mariadb 了，而不是 mysql
docker run --rm --network host mariadb:latest mariadb -h 127.0.0.1 -u $DB_USER -p$DB_PASS $DB_NAME -e "
    UPDATE feed_item 
    SET enabled = 0 
    WHERE url LIKE '%news.google.com/rss/search%';
"

if [ $? -eq 0 ]; then
    echo "✅ Successfully disabled temporary Google News fallback sources!"
else
    echo "❌ Failed to update the database via Docker."
fi

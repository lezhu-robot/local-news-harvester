# Local News Harvester

## 项目简介

本地新闻聚合与阅读服务：后端自动抓取多种来源的新闻内容，前端提供移动端 UI 展示与浏览。

## 功能概览

- **新闻源管理**：支持 RSS / Web / Twitter(X) / Threads 四种类型
- **内容聚合**：按分类查看、定时刷新（每小时）、高级搜索
- **社交媒体抓取**：通过 [Scrape Creators API](https://scrapecreators.com) 获取 Twitter 和 Threads 的帖子
- **图片代理**：为来源站点图片提供统一代理接口
- **前端展示**：Flutter 应用展示来源列表与文章详情

## 技术栈

| 组件 | 技术 |
|------|------|
| 后端 | Java 17 / Spring Boot 3 |
| 数据库 | MariaDB |
| 社交媒体 API | [Scrape Creators](https://scrapecreators.com) (Twitter + Threads) |
| 前端 | Flutter |
| 部署 | Docker Compose |

---

## 快速开始

### 1. 启动数据库（MariaDB）
```bash
docker compose up -d
```

### 2. 启动后端（Spring Boot）
```bash
SPRING_DOCKER_COMPOSE_ENABLED=false ./mvnw -DskipTests spring-boot:run
```

后端默认地址：`http://localhost:8080`

### 3. 启动 Flutter 前端
```bash
cd flutter_news_application
flutter pub get
flutter run
```

Flutter 默认会请求 `http://localhost:8080` 的 API（见 `flutter_news_application/lib/config/app_config.dart`）。

---

## 配置说明

### 环境变量 (`.env`)

| 变量名 | 说明 | 必填 |
|--------|------|------|
| `MYSQL_DATABASE` | 数据库名称 | ✅ |
| `MYSQL_USER` / `MYSQL_PASSWORD` | 数据库账号密码 | ✅ |
| `APP_THREADS_SCRAPECREATORS_KEY` | Scrape Creators API Key (Threads) | ✅ |
| `APP_TWITTER_SCRAPECREATORS_KEY` | Scrape Creators API Key (Twitter)，留空则复用 Threads 的 key | ⬜ |

### Feature Flags (`application.properties`)

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `app.feature.web-ingest.enabled` | `false` | 启用 Web 页面抓取 |
| `app.feature.twitter-ingest.enabled` | `false` | 启用 Twitter/X 抓取 |
| `app.feature.threads-ingest.enabled` | `false` | 启用 Threads 抓取 |
| `app.feature.thumbnail-task.enabled` | `false` | 启用后台补图任务 |

### 社交媒体 API 配置

Twitter 和 Threads 均使用 [Scrape Creators API](https://scrapecreators.com)：

```properties
# Twitter
app.twitter.scrapecreators.base-url=https://api.scrapecreators.com
app.twitter.scrapecreators.api-key=${APP_TWITTER_SCRAPECREATORS_KEY:${APP_THREADS_SCRAPECREATORS_KEY:}}

# Threads
app.threads.scrapecreators.base-url=https://api.scrapecreators.com
app.threads.scrapecreators.api-key=${APP_THREADS_SCRAPECREATORS_KEY:}
```

> **注意**：Twitter 的 API key 默认回退到 Threads 的 key，所以如果使用同一个 Scrape Creators 账号，只需配置 `APP_THREADS_SCRAPECREATORS_KEY` 即可。

### 数据库配置

如果不使用 Docker，请自行准备 MariaDB：
- DB 名称：`news_reader`
- 用户名：`reader`
- 密码：`readerpass`

---

## 数据接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/categories` | 获取全部分类 |
| GET | `/api/categories/{category}/newsarticles` | 按分类获取文章 |
| GET | `/api/newsarticles` | 获取全部文章 |
| GET | `/api/newsarticles/{id}` | 获取单条文章 |
| GET | `/api/newsarticles/refresh` | 刷新文章（RSS / Web / Twitter / Threads） |
| POST | `/api/newsarticles/search` | 高级搜索 |
| POST | `/api/newsarticles/seed` | 插入示例文章 |
| DELETE | `/api/newsarticles/seed` | 删除示例文章 |
| GET | `/api/feeditems` | 获取全部新闻源 |
| POST | `/feeds/new` | 新建新闻源 |
| POST | `/feeds/preview` | 预览 Web 类型新闻源 |
| POST | `/admin/clear` | 清空业务表 |
| POST | `/admin/seed-rss` | 批量导入内置 RSS 源 |
| GET | `/api/image?url=...` | 图片代理 |

---

## 社交媒体源管理

### 添加 Twitter 源

```bash
curl -X POST http://localhost:9090/feeds/new \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "name=X @testingcatalog" \
  --data-urlencode "url=https://x.com/testingcatalog" \
  --data-urlencode "sourceType=TWITTER" \
  --data-urlencode "enabled=true" \
  --data-urlencode "category=AI"
```

- `url` 格式：`https://x.com/{username}`
- 仅抓取原创 tweet，过滤 reply / retweet / quote / pinned tweet
- API 端点：`GET /v1/twitter/user-tweets?handle={username}`

### 添加 Threads 源

```bash
curl -X POST http://localhost:9090/feeds/new \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "name=Threads @mattnavarra" \
  --data-urlencode "url=https://www.threads.com/@mattnavarra" \
  --data-urlencode "sourceType=THREADS" \
  --data-urlencode "enabled=true" \
  --data-urlencode "category=COMPETITORS"
```

- `url` 格式：`https://www.threads.com/@{username}`
- 直接用 username 查询，无需解析 user_id
- API 端点：`GET /v1/threads/user/posts?handle={username}`
- Threads 账号配置文件：`scripts/threads_accounts.json`
- 批量注册脚本：`scripts/add_threads_sources.py`

### 工具脚本

| 脚本 | 说明 |
|------|------|
| `scripts/fetch_threads_accounts.py` | 批量抓取 Threads 帖子并输出到 JSON 文件 |
| `scripts/add_threads_sources.py` | 批量注册 Threads 源到后端 |
| `scripts/add_testingcatalog_twitter_source.py` | 添加 @testingcatalog Twitter 源 |
| `scripts/test_threads_api.py` | 测试 Scrape Creators API 连通性（Threads + Twitter） |

---

## 云服务器部署 (Docker)

### 1. 准备工作
```bash
git clone <your-repo-url>
cd local-news-harvester
cp .env.example .env
# 编辑 .env 填入 Scrape Creators API Key
```

### 2. 启动服务
```bash
docker compose up -d --build
```

自动构建并启动：MariaDB + PhpMyAdmin + Spring Boot 后端

### 3. 访问服务
- API 地址：`http://<server-ip>:9090/api/newsarticles`
- PhpMyAdmin：`http://<server-ip>:8081`

### 4. 测试示例

```bash
# 刷新新闻
curl http://<server-ip>:9090/api/newsarticles/refresh

# 高级搜索（时间参数均为 UTC）
curl -X POST http://<server-ip>:9090/api/newsarticles/search \
  -H "Content-Type: application/json" \
  -d '{
    "keyword": "AI",
    "category": "AI",
    "sortOrder": "latest",
    "includeContent": false
  }'
```

> **时间筛选**：`publishedAt >= startDateTime` 且 `publishedAt < endDateTime`（右开区间），格式必须是 ISO 8601 UTC（带 `Z`）

### 5. (可选) 配置公网 HTTPS 访问 (Cpolar)

```bash
cpolar authtoken <your-token>
pkill cpolar
nohup cpolar http 9090 > cpolar.log 2>&1 &
```

查看 `cpolar.log` 获取生成的公网 HTTPS 地址。
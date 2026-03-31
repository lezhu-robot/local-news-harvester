# Local News Harvester

多源新闻聚合服务，支持 RSS、Twitter(X)、Threads 自动抓取，配合 Flutter 客户端提供移动端阅读体验。

## 架构概览

```
┌─────────────────────────────────────────────────────────────┐
│                    Docker Compose                           │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────┐   │
│  │  Spring Boot  │  │   MariaDB    │  │   PhpMyAdmin     │   │
│  │  :9090→:8080  │──│    :3306     │──│     :8081        │   │
│  └──────┬───────┘  └──────────────┘  └──────────────────┘   │
└─────────┼───────────────────────────────────────────────────┘
          │
    ┌─────┼──────────────────────────┐
    │     │     数据源               │
    │  ┌──┴──┐ ┌────────┐ ┌───────┐ │
    │  │ RSS │ │Twitter │ │Threads│ │
    │  │ 45个│ │ 1个    │ │ 6个   │ │
    │  └─────┘ └────────┘ └───────┘ │
    │   免费    Scrape Creators API  │
    └────────────────────────────────┘
```

## 功能特性

- **四种源类型**：RSS / Web / Twitter(X) / Threads
- **智能去重**：URL 精确匹配 + 标题 Jaccard 相似度检测 (≥0.9)
- **条件请求**：RSS 源支持 `ETag` / `If-Modified-Since`，减少无效流量
- **定时刷新**：每 30 分钟自动抓取所有已启用的源（并行执行）
- **高级搜索**：关键词组 AND/OR 模式、CJK 全文匹配、UTC 时间范围过滤
- **图片代理**：解决跨域图片加载问题
- **分类系统**：AI / Music / Games / Competitors / Uncategorized

## 技术栈

| 组件 | 技术 |
|------|------|
| 后端 | Java 17 / Spring Boot 3 / JPA + Hibernate |
| 数据库 | MariaDB |
| RSS 解析 | Rome (SyndFeed) |
| HTML 解析 | Jsoup |
| 社交媒体 API | [Scrape Creators](https://scrapecreators.com) (Twitter + Threads) |
| 前端 | Flutter |
| 部署 | Docker Compose (多阶段构建) |

---

## 项目结构

```
local-news-harvester/
├── src/main/java/.../
│   ├── models/
│   │   ├── FeedItem.java          # 新闻源实体（名称、URL、类型、分类、缓存头）
│   │   ├── NewsArticle.java       # 文章实体（标题、摘要、正文、缩略图、标签）
│   │   ├── NewsCategory.java      # 分类枚举（AI/MUSIC/GAMES/COMPETITORS/UNCATEGORIZED）
│   │   ├── ThumbnailTask.java     # 异步补图任务
│   │   └── dto/                   # 搜索请求 DTO
│   ├── controllers/
│   │   ├── NewsArticleController  # /api/newsarticles — 文章 CRUD、刷新、搜索
│   │   ├── FeedItemController     # /api/feeditems — 新闻源查询
│   │   ├── CategoryController     # /api/categories — 分类与按分类查文章
│   │   ├── FormController         # /feeds/new — 新建源、/admin/* — 管理操作
│   │   ├── ImageProxyController   # /api/image — 图片代理
│   │   └── HomeController         # / — 首页模板
│   ├── services/
│   │   ├── ScheduledTasks         # 定时任务（每30分钟刷新）
│   │   ├── IngestPipelineService  # 统一调度：按 sourceType 分发到对应 ingest 服务
│   │   ├── RssIngestService       # RSS 抓取（Rome 解析 + 条件请求）
│   │   ├── TwitterRapidApiClient  # Twitter API 客户端 (Scrape Creators)
│   │   ├── TwitterIngestService   # Twitter 推文解析与过滤
│   │   ├── ThreadsRapidApiClient  # Threads API 客户端 (Scrape Creators)
│   │   ├── ThreadsIngestService   # Threads 帖子解析
│   │   ├── WebIngestService       # Web 页面抓取（已禁用）
│   │   ├── NewsArticleDedupeService # 去重（URL + 标题相似度）
│   │   ├── NewsArticleService     # 核心业务（刷新、搜索）
│   │   ├── ThumbnailTaskService   # 异步 OG 图片补全（已禁用）
│   │   └── webadapters/           # 9 个站点专用 HTML 解析器
│   └── repositories/              # JPA Repositories × 3
├── scripts/
│   ├── fetch_threads_accounts.py  # 批量抓取 Threads 帖子 → JSON
│   ├── add_threads_sources.py     # 批量注册 Threads 源
│   ├── add_music_rss_sources.py   # 添加音乐类 RSS 源
│   ├── add_game_rss_sources.py    # 添加游戏类 RSS 源
│   ├── test_threads_api.py        # API 连通性诊断（Threads + Twitter）
│   └── threads_accounts.json      # Threads 账号列表
├── flutter_news_application/      # Flutter 移动端
├── compose.yaml                   # Docker 编排
├── Dockerfile                     # 多阶段构建 (JDK build → JRE run)
└── .env                           # 环境变量
```

---

## 快速开始

### 方式一：Docker Compose（推荐）

```bash
git clone <your-repo-url>
cd local-news-harvester
cp .env.example .env
# 编辑 .env，填入 APP_THREADS_SCRAPECREATORS_KEY

docker compose up -d --build
```

服务启动后：
- API：`http://localhost:9090`
- PhpMyAdmin：`http://localhost:8081`

```bash
# 导入内置 RSS 源（首次部署）
curl -X POST http://localhost:9090/admin/seed-rss

# 手动触发刷新
curl http://localhost:9090/api/newsarticles/refresh
```

### 方式二：本地开发

```bash
# 1. 先启动数据库
docker compose up -d database

# 2. 启动后端（会自动读取仓库根目录 .env）
SPRING_DOCKER_COMPOSE_ENABLED=false ./mvnw -DskipTests spring-boot:run

# 3. (可选) 启动 Flutter 前端
cd flutter_news_application && flutter pub get && flutter run
```

---

## 配置说明

### 环境变量 (`.env`)

| 变量名 | 说明 | 必填 |
|--------|------|------|
| `MYSQL_DATABASE` | 数据库名称 | ✅ |
| `MYSQL_USER` / `MYSQL_PASSWORD` | 数据库账号密码 | ✅ |
| `APP_THREADS_SCRAPECREATORS_KEY` | Scrape Creators API Key（Threads + Twitter 共用） | ✅ |
| `APP_TWITTER_SCRAPECREATORS_KEY` | Twitter 独立 key（留空则自动用上面的 key） | ⬜ |

### Feature Flags

| 配置项 | 默认 | 说明 |
|--------|------|------|
| `app.feature.twitter-ingest.enabled` | `false` | Twitter/X 抓取（已禁用） |
| `app.feature.threads-ingest.enabled` | `true` | Threads 抓取 |
| `app.feature.web-ingest.enabled` | `false` | Web 页面抓取（需站点适配器） |
| `app.feature.thumbnail-task.enabled` | `false` | 异步 OG 图片补全 |

---

## API 参考

### 文章

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/newsarticles` | 全部文章 |
| GET | `/api/newsarticles/{id}` | 单条文章 |
| GET | `/api/newsarticles/refresh` | 触发全量刷新 |
| POST | `/api/newsarticles/search` | 高级搜索 |

### 高级搜索 (`POST /api/newsarticles/search`)

```json
{
  "keyword": "AI",
  "keywordGroups": [["ChatGPT", "Claude"], ["Google", "Gemini"]],
  "groupMode": "AND",
  "category": "AI",
  "sources": ["TechCrunch", "TestingCatalog"],
  "tags": ["deep learning"],
  "startDateTime": "2026-03-01T00:00:00Z",
  "endDateTime": "2026-03-25T00:00:00Z",
  "sortOrder": "latest",
  "includeContent": false
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `keyword` | string | 单关键词搜索（标题 + 摘要） |
| `keywordGroups` | string[][] | 关键词组，组内 OR、组间由 `groupMode` 决定 |
| `groupMode` | string | `AND`（所有组都匹配）或 `OR`（任一组匹配） |
| `category` | string | 按分类过滤 |
| `sources` | string[] | 按来源名称过滤 |
| `tags` | string[] | 按标签过滤 |
| `startDateTime` / `endDateTime` | string | ISO 8601 UTC（必须带 `Z`），左闭右开区间 |
| `sortOrder` | string | `latest`（默认）或 `oldest` |
| `includeContent` | boolean | 是否返回 `rawContent` 正文（默认 `false`） |

> **CJK 支持**：中日韩关键词使用子串匹配，英文关键词使用完整单词匹配（word boundary）

### 新闻源

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/feeditems` | 全部新闻源 |
| GET | `/api/feeditems/{id}` | 单条新闻源 |
| POST | `/feeds/new` | 新建新闻源（表单提交） |
| POST | `/admin/seed-rss` | 批量导入内置 RSS 源 |
| POST | `/admin/clear` | 清空所有业务表 |

### 其他

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/categories` | 分类列表 |
| GET | `/api/categories/{cat}/newsarticles` | 按分类获取文章 |
| GET | `/api/image?url=...` | 图片代理 |
| POST | `/feeds/preview` | 预览 Web 新闻源 |

---

## 社交媒体源管理

### 添加 Twitter 源

```bash
curl -X POST http://localhost:9090/feeds/new \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "name=X @realDonaldTrump" \
  --data-urlencode "url=https://x.com/realDonaldTrump" \
  --data-urlencode "sourceType=TWITTER" \
  --data-urlencode "enabled=true" \
  --data-urlencode "category=UNCATEGORIZED"
```

- `url` 格式：`https://x.com/{username}`
- 仅保留原创 tweet，过滤 reply / retweet / quote / pinned
- 提取：正文、发布时间、hashtag 标签、媒体图片

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
- 批量注册脚本：`scripts/add_threads_sources.py`
- 账号配置：`scripts/threads_accounts.json`

### 工具脚本

| 脚本 | 说明 |
|------|------|
| `scripts/test_threads_api.py` | API 连通性诊断（Threads + Twitter） |
| `scripts/fetch_threads_accounts.py` | 批量抓取 Threads 帖子 → JSON |
| `scripts/add_threads_sources.py` | 批量注册 Threads 源到后端 |
| `scripts/add_music_rss_sources.py` | 添加 RSS 源 |
| `scripts/add_game_rss_sources.py` | 添加游戏类 RSS 源 |

---

## 核心机制

### 定时刷新

`ScheduledTasks` 每 30 分钟（`:00` 和 `:30`）触发 `refreshFromRssFeeds()`：
1. 查询所有 `enabled=true` 的 FeedItem
2. `IngestPipelineService.ingestAll()` 使用 Java 并行流并发抓取
3. 按 `sourceType` 分发到对应的 ingest service
4. 去重后保存到数据库

### 去重策略

`NewsArticleDedupeService` 两层过滤：
1. **URL 精确匹配**：`sourceURL` 已存在则跳过
2. **标题相似度**：对同一来源的最近 500 篇文章计算 Jaccard 相似度，≥ 0.9 判定为重复

### RSS 条件请求

`RssIngestService.ingest(FeedItem)` 支持 HTTP 缓存：
- 发送 `If-None-Match` (ETag) 和 `If-Modified-Since` 头
- 收到 `304 Not Modified` 则跳过解析
- 更新后的缓存头保存到 `FeedItem.etag` / `FeedItem.lastModified`

---

## (可选) 公网 HTTPS 访问

使用 Cpolar 内网穿透：

```bash
cpolar authtoken <your-token>
pkill cpolar
nohup cpolar http 9090 > cpolar.log 2>&1 &
```

查看 `cpolar.log` 获取公网 HTTPS 地址。

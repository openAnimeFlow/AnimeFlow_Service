# Anime Flow Service

AnimeFlow 客户端的后端服务，基于 **Spring Boot 3** 与 **Maven 多模块** 构建。根目录 `pom.xml` 为父工程（`packaging=pom`），统一依赖版本与构建配置。

默认监听端口 **1024**，与客户端开发环境 `AnimeFlowApi.animeFlowApiDev` 一致。

---

## 模块说明

| 模块 | 说明 |
|------|------|
| **common** | 公共实体、DTO/VO、常量、异常、Bangumi 上游模型等，被各业务模块依赖。 |
| **third-party-api** | 第三方 HTTP 客户端封装：`BangumiClient`（next.bgm.tv）、`BgmTvClient`（bgm.tv 页面）、`DandanplayClient`、`ResendClient` 等。 |
| **flow-client** | 面向客户端的 REST API：账户/OAuth、Bangumi 元数据代理、弹幕、论坛评论、邮件验证等。 |
| **flow-scheduler** | 定时任务：Bangumi Archive 离线数据同步等。详见 [flow-scheduler/README.md](flow-scheduler/README.md)。 |
| **flow-bootstrap** | **唯一启动入口**：`AnimeFlowApplication` 扫描 `com.ligg` 下全部 Spring 组件；可执行 fat jar 由此模块构建。 |

构建顺序见根 `pom.xml` 的 `<modules>` 声明：`common` → `third-party-api` → `flow-client` / `flow-scheduler` → `flow-bootstrap`。

---

## 环境要求

- **JDK 17**
- **MySQL 8**（库名默认 `anime_flow`）
- **Redis**（Flow Token、限流、Bangumi Archive 同步锁与状态等）
- 可选：Resend 账号（邮件验证码）、Bangumi OAuth 应用、弹弹 Play 应用凭证

---

## 快速开始

### 1. 初始化数据库

在 MySQL 中创建库并导入表结构：

```bash
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS anime_flow DEFAULT CHARSET utf8mb4;"
mysql -u root -p anime_flow < db/anime_flow.sql
```

`db/anime_flow.sql` 包含 Bangumi 归档表（`bangumi_*`）、用户表（`user`）、OAuth 绑定（`user_oauth`）、用户收藏（`user_bgm_collection`）等。

### 2. 配置环境变量

在**项目根目录**（与 `flow-bootstrap` 同级）创建 `.env` 文件。Spring 通过 `optional:file:.env[.properties]` 加载：

```properties
# Redis
REDIS_HOST=127.0.0.1
REDIS_PORT=6379
REDIS_PASSWORD=
REDIS_USERNAME=

# 客户端 API 签名（须与 AnimeFlow 客户端 dart_defines 一致）
ANIME_FLOW_APP_ID=your_app_id
ANIME_FLOW_SECRET=your_secret

# Bangumi OAuth（绑定 / 收藏同步）
CLIENT_ID=your_bangumi_client_id
CLIENT_SECRET=your_bangumi_client_secret
REDIRECT_URI=http://127.0.0.1:1024/api/oauth/callback

# JWT（Flow Token）
JWT_SECRET=your_jwt_secret

# 弹弹 Play（弹幕搜索等，可选）
DANDANPLAY_ID=
DANDANPLAY_SECRET=

# Resend 邮件（注册/登录验证码，可选）
RESEND_API_KEY=
RESEND_DOMAIN=
```

数据库连接可在 `flow-bootstrap/src/main/resources/application.yaml` 中修改 `spring.datasource.*`。

### 3. 构建与运行

在项目根目录：

```bash
mvn clean install
```

启动应用（请使用入口模块）：

```bash
cd flow-bootstrap
mvn spring-boot:run
```

或在根目录：

```bash
mvn -pl flow-bootstrap spring-boot:run
```

启动成功后日志会输出本地与局域网访问地址，例如 `http://localhost:1024`。

---

## HTTP API 概览

除 `/api/oauth/**` 回调等白名单路径外，大部分 `/api/**` 请求需携带 AnimeFlow 签名头（`anime-flow.api-auth` 配置）。

### 账户与 OAuth（`/api/v1/account`）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/register` | 邮箱注册 |
| POST | `/email/login` | 邮箱登录 |
| POST | `/refresh` | 刷新 Flow Token |
| POST | `/oauth/bangumi/login` | Bangumi OAuth 登录 |
| GET | `/oauth/bangumi` | 查询当前 Bangumi 绑定状态 |
| POST | `/oauth/bangumi/bind` | 绑定 Bangumi 账号 |
| POST | `/oauth/bangumi/collections/sync` | **触发 Bangumi 收藏同步**（异步） |
| GET | `/oauth/bangumi/collections/sync` | **查询收藏同步任务状态** |

账户 OAuth 相关路径（除登录外）需 `Authorization: Bearer {Flow JWT}`。

### Bangumi 元数据（`/api/v1/bangumi`）

日历、Trending、条目搜索、角色/章节评论等；条目详情与关联数据见 `/api/v1/bangumi/subjects/**`；用户主页与公开收藏见 `/api/v1/bangumi/users/**`。

### 其他

| 前缀 | 说明 |
|------|------|
| `/api/v1/danmaku` | 弹幕发送与查询 |
| `/api/v1/email` | 发送验证码 |
| `/api/v1/users` | Flow 用户信息 |
| `/api/forum/comment` | 论坛评论 |
| `/api/verify` | 图形验证码 |
| `/api/oauth` | Bangumi OAuth 回调、Token 交换（Web 流程） |

---

## Bangumi 收藏同步

用户绑定 Bangumi 后，客户端可调用收藏同步接口，将上游收藏写入本地表 `user_bgm_collection`。

- **鉴权**：客户端携带 **Flow JWT**；服务端从 `user_oauth` 读取 **Bangumi OAuth Token** 调用 `next.bgm.tv`。
- **执行方式**：接口立即返回任务状态，分页拉取在后台 `@Async` 线程完成。
- **Bangumi Token 401**：由 `BangumiOAuthExecutor` 统一 refresh 并重试，与 Flow Token 刷新无关。

完整设计、状态机、Redis 键与类职责见：

**[flow-client/bgm-collection-sync.md](flow-client/bgm-collection-sync.md)**

---

## Bangumi Archive 离线同步

`flow-scheduler` 模块按 cron 从 [animeFlow-assets](https://github.com/openAnimeFlow/animeFlow-assets) 拉取 Bangumi wiki 归档（jsonlines），增量 upsert 到 `bangumi_*` 表，供条目详情等接口查询本地库。

配置前缀 `anime-flow.bangumi-archive-sync`，详见 **[flow-scheduler/README.md](flow-scheduler/README.md)**。

---

## 包结构

```
anime_flow_service/
├── common/                 # com.ligg.common.*
├── third-party-api/        # com.ligg.api.*
├── flow-client/            # com.ligg.flowclient.*
│   ├── controller/         # REST 入口
│   ├── service/            # 业务逻辑
│   ├── mapper/             # MyBatis Mapper
│   └── bgm-collection-sync.md
├── flow-scheduler/         # com.ligg.flowscheduler.*
├── flow-bootstrap/         # com.ligg.flow_bootstrap.AnimeFlowApplication
└── db/anime_flow.sql
```

- 启动类：`com.ligg.flow_bootstrap.AnimeFlowApplication`
- `@ComponentScan("com.ligg")` + `@MapperScan("com.ligg.**.mapper")` 装配全部模块

---

## 相关仓库

| 仓库 | 关系 |
|------|------|
| [AnimeFlow](https://github.com/openAnimeFlow/AnimeFlow) | Flutter 客户端 |
| [animeFlow-assets](https://github.com/openAnimeFlow/animeFlow-assets) | Bangumi Archive 发布与静态资源 |

客户端联调说明见 AnimeFlow 仓库 README 中的「联调本地服务端」章节。

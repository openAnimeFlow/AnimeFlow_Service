# Anime Flow Service

本仓库为 **Maven 多模块** 项目，根目录 `pom.xml` 为父工程（`packaging=pom`），统一 Spring Boot 版本与公共构建配置。

## 模块说明

| 模块 | 说明 |
|------|------|
| **flow-bootstrap** | **唯一启动入口**：`AnimeFlowBootstrapApplication` 扫描并装配 `flow-client`、`bangumi-api` 中的 Spring 组件；可执行 fat jar 由此模块构建。 |
| **flow-client** | 面向客户端的 HTTP 服务：OAuth、弹幕、论坛评论、Thymeleaf 页面等（普通 jar，不含 `main`）。 |
| **bangumi-api** | Bangumi 相关扩展代码（普通 jar，不含 `main`）。 |

## 构建与运行

在项目根目录执行：

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

## 包结构说明

- 启动类包名：**`com.ligg.flow_bootstrap`**
- 业务代码位于 **`com.ligg.flowclient`** 及其子包。
- 扩展模块包名：**`com.ligg.bangumiapi`**（由 `scanBasePackages` 一并扫描）。

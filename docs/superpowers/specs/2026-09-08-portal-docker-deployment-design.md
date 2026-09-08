# Portal Docker 部署设计

## 目标

为 Portal 提供一套默认可用于生产部署的 Docker 构建与 Compose 编排文件：React 前端打包进 Spring Boot 可执行 JAR，Portal 通过 Docker Compose 连接独立的 MySQL 8.4 服务。

## 架构

`Dockerfile` 使用多阶段构建。构建阶段以 Maven 和项目已有的前端 Maven 插件生成包含前端静态资源的 Spring Boot JAR；运行阶段仅保留 Java 17 JRE、JAR 和非 root 应用用户。

`docker-compose.yml` 提供两个服务：

- `portal`：发布宿主机 `PORTAL_PORT`（默认 8084），等待 MySQL 健康检查后启动。
- `mysql`：使用 MySQL 8.4，数据保存在命名卷，不向宿主机发布数据库端口。

Flyway 在 Portal 启动时负责初始化与迁移数据库，Compose 不另行运行迁移容器。

## 配置与安全

Compose 仅引用环境变量，不包含实际密钥。数据库密码、Portal session key、NewAPI 凭据、PayPal 凭据、TronGrid API key 与 TRC20 地址池均从部署环境传入。

Compose 为数据库提供仅限容器网络的默认值和持久化卷；若未提供生产密码，启动应显式失败，而不是使用弱默认密码。Portal 默认连接 Compose 内部主机名 `mysql`。

`.dockerignore` 排除 Git 元数据、前端依赖、构建产物、IDE/临时文件和本地环境文件，缩小镜像构建上下文，并避免意外复制密钥。

## 验证

通过 `docker compose config` 验证 Compose 语法与环境变量引用；通过 `docker compose build` 验证镜像可构建。若本机 Docker 可用，再启动 `mysql` 与 `portal` 并检查 Portal HTTP 健康响应。

## 范围

本次不新增反向代理、TLS 终止、镜像发布流水线、日志平台或 secrets manager 集成；这些由部署环境负责。

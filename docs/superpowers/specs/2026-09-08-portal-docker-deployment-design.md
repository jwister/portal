# Portal Docker 部署设计

## 目标

为 Portal 提供与旧 `usdt` 项目一致的远程 Docker daemon 构建与部署文件：本地将 React 前端打包进 Spring Boot 可执行 JAR，远程 daemon 仅组装运行镜像，Compose 连接现有外部 MySQL。

## 架构

`scripts/build-remote.ps1` 和 `scripts/build-remote.sh` 先在本地运行 Maven，生成包含前端静态资源的 Spring Boot JAR，再通过 Docker CLI 将最小运行上下文发送至远程 daemon，以 `linux/amd64` 构建 `wenyou7/ztoken-portal:latest` 及可选版本标签。Dockerfile 仅保留 Java 17 JRE、JAR 和非 root 应用用户。

`docker-compose.yml` 仅提供 `portal` 服务，运行已发布的 `wenyou7/ztoken-portal:latest`。现有 MySQL、NewAPI 和 TronGrid 均为外部依赖，Flyway 在 Portal 启动时负责迁移 Portal 所连接的数据库。

## 配置与安全

Compose 仅引用环境变量，不包含实际密钥。数据库地址与密码、Portal session key、NewAPI 凭据、PayPal 凭据、TronGrid API key 与 TRC20 地址池均从部署环境传入；缺少必填值时 Compose 显式失败，不使用弱默认值。

`.dockerignore` 排除 Git 元数据、前端依赖、构建产物、IDE/临时文件和本地环境文件，缩小镜像构建上下文，并避免意外复制密钥。

## 验证

通过脚本语法检查、`mvn clean package`、`docker compose config` 及 Dockerfile 静态检查验证。实际远程构建和推送只由操作者显式执行，不在生成文件时自动访问远程 daemon。

## 范围

本次不新增反向代理、TLS 终止、镜像发布流水线、日志平台或 secrets manager 集成；这些由部署环境负责。

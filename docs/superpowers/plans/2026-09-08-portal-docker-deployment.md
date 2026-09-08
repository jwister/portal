# Portal 远程 Docker 构建 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 本地打包 Portal，再在远程 Docker daemon 构建/可选推送 `wenyou7/ztoken-portal`，并通过 Compose 运行该镜像。

**Architecture:** Maven 是前后端单体 JAR 的唯一打包入口。远程 Docker daemon 只接收该 JAR 与运行时 Dockerfile，Compose 只运行发布镜像，连接现有外部 MySQL、NewAPI 和 TronGrid。

**Tech Stack:** PowerShell、Bash、Maven、Docker CLI、Eclipse Temurin 17 JRE、Docker Compose、Spring Boot/Flyway。

---

### Task 1: 添加最小化运行镜像上下文

**Files:**
- Create: `Dockerfile`
- Create: `.dockerignore`
- Test: `mvn clean package -DskipTests -B`

- [ ] **Step 1: 写入运行时 Dockerfile**

```dockerfile
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
RUN addgroup -S portal && adduser -S -G portal portal
COPY --chown=portal:portal backend/target/ztoken-portal-*.jar /app/app.jar
EXPOSE 8084
USER portal
ENTRYPOINT ["java", "-Xms256m", "-Xmx512m", "-jar", "/app/app.jar"]
```

- [ ] **Step 2: 限制远程 daemon 接收的构建上下文**

```dockerignore
**
!Dockerfile
!backend
!backend/target
!backend/target/ztoken-portal-*.jar
backend/target/*.jar.original
```

- [ ] **Step 3: 生成镜像输入 JAR**

Run: `mvn clean package -DskipTests -B`

Expected: `backend/target/ztoken-portal-0.1.0-SNAPSHOT.jar` 存在，且 `.dockerignore` 只放行该可执行 JAR。

### Task 2: 添加远程构建脚本

**Files:**
- Create: `scripts/build-remote.ps1`
- Create: `scripts/build-remote.sh`
- Test: PowerShell/Bash 语法解析

- [ ] **Step 1: 实现 PowerShell 远程构建入口**

提供 `Remote`、`Image`、`Tag`、`Platform` 与 `Push` 参数，默认值依次为 `tcp://192.168.100.153:2375`、`wenyou7/ztoken-portal`、空、`linux/amd64` 与 false。脚本检查 Docker/Maven，本地执行 `mvn clean package -DskipTests -B`，验证唯一 `ztoken-portal-*.jar`，随后使用 `docker -H <remote> build` 同时打 `latest` 和可选版本标签；只有 `-Push` 时推送。

- [ ] **Step 2: 实现 Bash 等价入口**

使用 `REMOTE`、`IMAGE`、`PLATFORM`、`PUSH` 环境变量与可选首个位置版本标签。流程与 PowerShell 版本一致，并用 `set -euo pipefail` 在任何打包、连接、构建、列镜像或推送失败时退出。

- [ ] **Step 3: 验证脚本语法，不连接远程 daemon**

Run: `$tokens = $null; $errors = $null; [void][System.Management.Automation.Language.Parser]::ParseFile((Resolve-Path 'scripts/build-remote.ps1'), [ref]$tokens, [ref]$errors); if ($errors.Count) { exit 1 }`

Expected: `$errors.Count` 为 0。

Run: `bash -n scripts/build-remote.sh`

Expected: Bash 可用时命令 exit code 为 0。

### Task 3: 添加仅运行发布镜像的 Compose 配置

**Files:**
- Create: `docker-compose.yml`
- Test: YAML 语法解析与 `docker compose config`

- [ ] **Step 1: 编排单一 Portal 服务**

使用 `${PORTAL_IMAGE:-wenyou7/ztoken-portal:latest}`，配置 `restart: unless-stopped`、`${PORTAL_PORT:-8084}:8084` 和 json-file 日志轮转。不得添加 `build`、`depends_on`、MySQL 服务或网络，因为数据库、NewAPI 与 TronGrid 均为外部依赖。

- [ ] **Step 2: 明确传递外部依赖配置**

`environment` 必须传递 `PORTAL_DB_URL`、`PORTAL_DB_USERNAME`、`PORTAL_DB_PASSWORD`、`PORTAL_SESSION_KEY`、`PORTAL_NEW_API_BASE_URL`、`PORTAL_NEW_API_PRICING_TOKEN`、`PAYMENT_NEWAPI_CREDIT_ACCESS_TOKEN`、`PAYMENT_TRC20_TRONGRID_API_KEY`、`PAYMENT_TRC20_ADDRESSES`；缺失时以 `${NAME:?message}` 失败。可安全使用的行为默认值包括支付期限、20 确认数、TronGrid public base URL 和安全 Cookie。

- [ ] **Step 3: 验证 YAML 和 Compose**

Run: `python -c "import yaml; yaml.safe_load(open('docker-compose.yml', encoding='utf-8')); print('PASS')"`

Expected: `PASS`。

Run: `docker compose config`

Expected: Docker Compose 安装后，仅解析一个 `portal` 服务；该验证不启动容器。

### Task 4: 提交部署文件

**Files:**
- Modify: `docs/superpowers/specs/2026-09-08-portal-docker-deployment-design.md`
- Modify: `docs/superpowers/plans/2026-09-08-portal-docker-deployment.md`
- Test: `git diff --check`

- [ ] **Step 1: 更新设计文档**

明确采用远程 daemon 构建、`wenyou7/ztoken-portal` 镜像、外部 MySQL 和显式执行远程构建的安全边界。

- [ ] **Step 2: 验证改动无空白错误**

Run: `git diff --check`

Expected: 仅忽略用户已有改动的无关警告；部署文件没有 diff 错误。

- [ ] **Step 3: 提交本次部署文件**

```powershell
git add Dockerfile .dockerignore docker-compose.yml scripts/build-remote.ps1 scripts/build-remote.sh docs/superpowers/specs/2026-09-08-portal-docker-deployment-design.md docs/superpowers/plans/2026-09-08-portal-docker-deployment.md
git commit -m "build: 添加Portal远程Docker构建配置"
```

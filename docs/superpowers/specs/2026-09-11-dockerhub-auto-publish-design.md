# Portal Docker Hub 自动发布设计

## 目标

当代码推送到 `master` 分支时，自动构建 Portal 的前后端产物、发布 Docker 镜像到 Docker Hub，并为该提交创建可追溯的 Git 版本标签。

## 发布范围

- 触发条件：`master` 分支的 `push` 事件。
- 镜像仓库：`wenyou7/ztoken-portal`。
- 镜像标签：仅推送精确的 `X.Y.Z` 版本，不推送 `latest`。
- 初始版本：仓库不存在 `vX.Y.Z` Git 标签时，首个成功发布版本为 `0.1.0`。
- 递增规则：以最新 `vX.Y.Z` Git 标签为基准，将最后一位加一；例如 `v0.1.0` 之后为 `v0.1.1`。

## 工作流

1. 工作流完整检出代码与 Git 标签。每个 `master` 推送都可独立运行，不使用 GitHub Actions 的单一并发组，以免排队工作流被替换。
2. 若当前提交已有符合格式的 `vX.Y.Z` 标签，视为已发布并跳过构建；若当前提交已有 `ci/release/vX.Y.Z` 预约标签，则复用该版本。
3. 其他提交通过原子推送 `ci/release/vX.Y.Z` 预约标签获得唯一版本；预约标签参与后续版本计算，避免并发工作流发布同一版本。构建失败会保留预约并跳过该版本，因此首个成功镜像可能是 `0.1.1` 或更高版本。
4. 运行 `mvn -f backend/pom.xml clean package`，由 Maven 同时完成 React 前端构建和 Spring Boot 可执行 JAR 打包。
5. 登录 Docker Hub 后检查精确版本标签：不存在则构建；存在但 OCI 提交和版本标签与本次预约匹配时，视为可恢复的已推送镜像；不匹配则失败。
6. 使用现有根目录 `Dockerfile` 推送 `wenyou7/ztoken-portal:X.Y.Z`，并写入 OCI 源码、提交和版本标签。
7. 镜像推送成功后，将相应 `vX.Y.Z` 标签推送回 GitHub，建立代码提交与镜像版本的对应关系。

## 凭据与失败处理

- Docker Hub 用户名从 GitHub Actions Secret `DOCKERHUB_USERNAME` 读取。
- Docker Hub Access Token 从 Secret `DOCKERHUB_TOKEN` 读取；令牌必须具有推送 `wenyou7/ztoken-portal` 的权限。
- 工作流需要 `contents: write` 权限，以便推送版本标签。
- 构建、登录或推送失败时任务失败且不推送正式 Git 标签；镜像成功前不会产生远端正式发布标签。
- Docker Hub 中已存在但 OCI 提交或版本标签不匹配的精确版本标签会使任务失败，禁止覆盖可回滚的发布镜像。
- Docker Hub 仓库必须启用不可变标签；这是处理检查与推送之间竞争条件的最终覆盖保护。
- 若重新运行已被打上正式版本标签的提交，工作流跳过发布，避免重新构建或覆盖镜像；若镜像已成功推送但正式 Git 标签失败，工作流会校验 OCI 提交/版本标签后补写正式标签。
- `docker-compose.yml` 不提供镜像默认值；部署者必须显式设置 `PORTAL_IMAGE` 为精确发布版本。

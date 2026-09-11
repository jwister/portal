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

1. 工作流完整检出代码与 Git 标签，并以 `concurrency` 串行化 `master` 的发布任务。
2. 若当前提交已有符合格式的 `vX.Y.Z` 标签，则复用该版本；否则计算下一个补丁版本。
3. 运行 `mvn -f backend/pom.xml clean package`，由 Maven 同时完成 React 前端构建和 Spring Boot 可执行 JAR 打包。
4. 使用现有根目录 `Dockerfile` 构建镜像，推送 `wenyou7/ztoken-portal:X.Y.Z` 到 Docker Hub。
5. 镜像推送成功后，将相应 `vX.Y.Z` 标签推送回 GitHub，建立代码提交与镜像版本的对应关系。

## 凭据与失败处理

- Docker Hub 用户名从 GitHub Actions Secret `DOCKERHUB_USERNAME` 读取。
- Docker Hub Access Token 从 Secret `DOCKERHUB_TOKEN` 读取；令牌必须具有推送 `wenyou7/ztoken-portal` 的权限。
- 工作流需要 `contents: write` 权限，以便推送版本标签。
- 构建、登录或推送失败时任务失败且不推送 Git 标签；镜像成功前不会产生远端发布标签。
- 若重新运行已被打上版本标签的提交，工作流复用已有版本，避免重复递增。

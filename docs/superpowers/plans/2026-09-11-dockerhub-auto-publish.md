# Portal Docker Hub 自动发布 Implementation Plan

> **For agentic workers:** Execute this plan task-by-task. The final workflow is the source of truth for executable YAML.

**Goal:** 每次推送到 `master` 后，自动打包 Portal、推送带唯一递增补丁版本的 Docker Hub 镜像，并创建对应 Git 标签。

**Architecture:** 每次推送独立运行。工作流通过原子推送 `ci/release/vX.Y.Z` 标签预约版本，避免并发任务获取相同版本；镜像携带 OCI 提交和版本标签。正式 `vX.Y.Z` 标签只在镜像存在且可验证后推送，支持“镜像已推送、正式 Git 标签失败”的安全重试。

**Tech Stack:** GitHub Actions、Maven、Java 17、Docker Buildx、Docker Hub、Git 标签。

---

## 最终实施记录

### Task 1: 发布工作流

**Files:**

- Create: `.github/workflows/docker-publish.yml`
- Test: YAML 解析、版本规则静态检查

- [x] 工作流仅监听 `master` 的 `push` 事件，并申请 `contents: write` 用于预约和正式标签。
- [x] 通过 `ci/release/vX.Y.Z` 预约标签分配版本；预约失败会有限次重试，构建失败保留预约并使后续版本跳过该编号。
- [x] 首次无预约和正式标签时生成 `0.1.0`；已有 `v0.1.9` 或预约 `ci/release/v0.1.9` 时生成 `0.1.10`。
- [x] 已有正式 `vX.Y.Z` 的提交不重新构建；已推送同一提交和版本的镜像会通过 OCI 标签校验后补写正式 Git 标签。
- [x] Docker Hub 中已有但与当前提交或版本不匹配的镜像标签会失败，禁止覆盖；Docker Hub 不可变标签是最终竞态保护。
- [x] 使用 `mvn -B -f backend/pom.xml clean package` 打包，再使用现有 Dockerfile 推送 `wenyou7/ztoken-portal:X.Y.Z`，从不推送 `latest`。
- [x] 所有第三方 GitHub Actions 固定到完整提交 SHA。

### Task 2: 部署契约和凭据

**Files:**

- Modify: `docker-compose.yml`
- Modify: `README.md`
- Modify: GitHub 仓库 `jwister/portal` 的 Actions Secrets（不写入 Git）

- [x] `docker-compose.yml` 要求显式设置精确的 `PORTAL_IMAGE`，不再以 `latest` 作为默认镜像。
- [x] README 说明部署时设置 `PORTAL_IMAGE=wenyou7/ztoken-portal:X.Y.Z`，以及首次发布前在 Docker Hub 启用不可变标签。
- [ ] 在 GitHub 仓库 Settings → Secrets and variables → Actions 中设置 `DOCKERHUB_USERNAME=wenyou7`。
- [ ] 在相同页面设置具有仓库推送权限的 `DOCKERHUB_TOKEN`。
- [ ] 在 Docker Hub 的 `wenyou7/ztoken-portal` 仓库中启用不可变标签。
- [ ] 推送本次提交到 `master`，并验证 Docker Hub 中存在精确版本镜像和 Git 中对应的 `vX.Y.Z` 标签。

## 已执行验证

- YAML 已使用 PyYAML 解析，并验证触发分支、权限、版本镜像标签和关键步骤。
- 在无版本标签场景验证得到 `0.1.0`，在 `0.1.9` 场景验证得到 `0.1.10`。
- 已运行 `git diff --check`。
- 本机 Docker 守护进程未运行，未执行本地镜像构建；首次端到端验证将在 GitHub Actions Runner 执行。

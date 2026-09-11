# Portal Docker Hub 自动发布 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 每次推送到 `master` 后，自动打包 Portal、推送带递增补丁版本的 Docker Hub 镜像，并创建对应 Git 标签。

**Architecture:** GitHub Actions 完整检出 Git 历史与标签，以最新 `vX.Y.Z` 计算下一个补丁版本；同一分支发布使用并发组串行运行。Maven 先产出包含前端资源的 Spring Boot JAR，既有 Dockerfile 将 JAR 打入运行镜像；Docker Hub 推送成功后才将 Git 标签推回远端。

**Tech Stack:** GitHub Actions、Maven、Java 17、Docker Buildx、Docker Hub、Git 标签。

---

## 文件结构

- 创建 `.github/workflows/docker-publish.yml`：负责 `master` 分支发布、版本计算及 Git 标签回写。
- 已创建 `docs/superpowers/specs/2026-09-11-dockerhub-auto-publish-design.md`：发布设计。
- 创建 `docs/superpowers/plans/2026-09-11-dockerhub-auto-publish.md`：本计划。

### Task 1: 创建 Docker Hub 发布工作流

**Files:**

- Create: `.github/workflows/docker-publish.yml`
- Test: YAML 和 GitHub Actions 静态语法

- [ ] **Step 1: 验证工作流尚不存在**

```powershell
Test-Path .github/workflows/docker-publish.yml
```

Expected: `False`。

- [ ] **Step 2: 创建工作流**

创建 `.github/workflows/docker-publish.yml`，内容如下：

```yaml
name: 发布 Portal Docker 镜像

on:
  push:
    branches:
      - master

permissions:
  contents: write

concurrency:
  group: dockerhub-publish-master
  cancel-in-progress: false

jobs:
  publish:
    runs-on: ubuntu-latest
    steps:
      - name: 检出代码与标签
        uses: actions/checkout@v4
        with:
          fetch-depth: 0

      - name: 配置 Java
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'
          cache: maven

      - name: 计算发布版本
        id: version
        shell: bash
        run: |
          existing_tag="$(git tag --points-at HEAD --list 'v[0-9]*' | grep -E '^v[0-9]+\.[0-9]+\.[0-9]+$' | sort -V | tail -n 1 || true)"
          if [[ -n "$existing_tag" ]]; then
            version="${existing_tag#v}"
          else
            latest_tag="$(git tag --list 'v[0-9]*' | grep -E '^v[0-9]+\.[0-9]+\.[0-9]+$' | sort -V | tail -n 1 || true)"
            if [[ -z "$latest_tag" ]]; then
              version="0.1.0"
            else
              IFS='.' read -r major minor patch <<< "${latest_tag#v}"
              version="$major.$minor.$((patch + 1))"
            fi
          fi
          echo "version=$version" >> "$GITHUB_OUTPUT"
          echo "git_tag=v$version" >> "$GITHUB_OUTPUT"

      - name: 构建 Portal JAR
        run: mvn -B -f backend/pom.xml clean package

      - name: 登录 Docker Hub
        uses: docker/login-action@v3
        with:
          username: ${{ secrets.DOCKERHUB_USERNAME }}
          password: ${{ secrets.DOCKERHUB_TOKEN }}

      - name: 配置 Docker Buildx
        uses: docker/setup-buildx-action@v3

      - name: 构建并推送版本镜像
        uses: docker/build-push-action@v6
        with:
          context: .
          push: true
          tags: wenyou7/ztoken-portal:${{ steps.version.outputs.version }}

      - name: 推送发布标签
        shell: bash
        run: |
          git config user.name "github-actions[bot]"
          git config user.email "41898282+github-actions[bot]@users.noreply.github.com"
          if ! git ls-remote --exit-code --tags origin "refs/tags/${{ steps.version.outputs.git_tag }}" >/dev/null 2>&1; then
            git tag -a "${{ steps.version.outputs.git_tag }}" -m "发布 Portal 镜像 ${{ steps.version.outputs.version }}"
            git push origin "${{ steps.version.outputs.git_tag }}"
          fi
```

工作流只监听 `master`；并发组不取消已开始的发布；首个版本为 `0.1.0`；Docker 镜像只能推送精确版本标签。

- [ ] **Step 3: 运行工作流静态校验**

```powershell
actionlint .github/workflows/docker-publish.yml
```

Expected: 退出码为 `0` 且无输出。若本机没有 `actionlint`，则执行：

```powershell
Get-Content -Raw .github/workflows/docker-publish.yml | Select-String -Pattern 'master|DOCKERHUB_USERNAME|DOCKERHUB_TOKEN|docker/build-push-action@v6'
git diff --check -- .github/workflows/docker-publish.yml
```

Expected: 四个配置项都存在，且 `git diff --check` 无输出。

- [ ] **Step 4: 提交工作流**

```powershell
git add -- .github/workflows/docker-publish.yml
git commit -m "ci: 增加 Portal Docker Hub 自动发布"
```

Expected: 提交只包含工作流文件，不能包含用户现有前端或业务改动。

### Task 2: 配置凭据并验证首次发布

**Files:**

- Modify: GitHub 仓库 `jwister/portal` 的 Actions Secrets（不写入 Git）
- Test: GitHub Actions “发布 Portal Docker 镜像”运行记录

- [ ] **Step 1: 设置 Docker Hub 用户名 Secret**

在 GitHub 仓库 Settings → Secrets and variables → Actions 中新建：

```text
Name: DOCKERHUB_USERNAME
Secret: wenyou7
```

- [ ] **Step 2: 设置 Docker Hub 令牌 Secret**

先在 Docker Hub 创建可推送 `wenyou7/ztoken-portal` 的 Personal Access Token，再新建：

```text
Name: DOCKERHUB_TOKEN
Secret: <Docker Hub Personal Access Token>
```

- [ ] **Step 3: 推送工作流提交并检查首次发布**

```powershell
git push origin master
```

Expected: 产生首个 Git 标签 `v0.1.0`，并在 Docker Hub 中产生 `wenyou7/ztoken-portal:0.1.0`；不得产生 `wenyou7/ztoken-portal:latest`。

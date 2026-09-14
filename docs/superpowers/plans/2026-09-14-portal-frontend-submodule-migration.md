# Portal Frontend Submodule Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 portal/frontend 规范为指向 portal-front 固定 commit 的 Git Submodule，并让 Jenkins 在 Maven 打包前检出该固定前端版本。

**Architecture:** `portal-front` 保留独立开发和提交历史。`portal` 不再跟踪 frontend 的普通文件，而以 gitlink 和 `.gitmodules` 引用一个前端 commit。Jenkins 先检出 portal，再初始化递归 Submodule，最后执行现有 Maven 前端构建和镜像发布。

**Tech Stack:** Git Submodule、GitHub、Jenkins Pipeline、Maven frontend-maven-plugin、Docker。

---

## File structure

- Modify: `frontend` Git repository index and history — commit all currently authorized frontend changes to `origin/main`.
- Create: `.gitmodules` — records the frontend Submodule path, GitHub URL, and branch metadata.
- Modify: root Git index — replace tracked `frontend/**` files with one `frontend` gitlink entry.
- Modify: `Jenkinsfile` — initialize and verify the fixed Submodule before `Package Backend`.
- Create: `docs/superpowers/plans/2026-09-14-portal-frontend-submodule-migration.md` — this plan.

### Task 1: Commit the authorized frontend work

**Files:**
- Modify: `frontend/**` — all existing changed and untracked frontend files authorized by the user.

- [ ] **Step 1: Review the exact frontend change set**

Run from the portal root:

```bash
git -C frontend status --short
git -C frontend diff --stat
git -C frontend ls-files --others --exclude-standard
```

Expected: the output lists only intended frontend work. Do not include files outside `frontend`.

- [ ] **Step 2: Stage and inspect the authorized frontend commit**

```bash
git -C frontend add --all
git -C frontend diff --cached --check
git -C frontend diff --cached --stat
```

Expected: whitespace check exits `0`; the stat contains the current frontend changes.

- [ ] **Step 3: Commit and push frontend main**

```bash
git -C frontend commit -m "前端：同步门户页面最新改动"
git -C frontend push origin main
git -C frontend rev-parse HEAD
```

Expected: push succeeds and the final command prints the frontend commit that portal will pin.

### Task 2: Convert the parent repository entry to a Submodule

**Files:**
- Create: `.gitmodules`
- Modify: Git index entry `frontend`

- [ ] **Step 1: Preserve the nested frontend worktree and remove only parent index entries**

Run from portal root:

```bash
git rm -r --cached frontend
git status --short -- frontend
```

Expected: Git stages removal of parent-tracked `frontend/**` files, while `frontend/.git` and all frontend files remain on disk.

- [ ] **Step 2: Register the existing repository as the frontend Submodule**

```bash
git submodule add --force --branch main https://github.com/jwister/portal-front.git frontend
git submodule status
git config --file .gitmodules --list
```

Expected: `.gitmodules` declares path `frontend`, URL `https://github.com/jwister/portal-front.git`, and branch `main`; `git submodule status` shows the commit from Task 1.

- [ ] **Step 3: Verify the staged migration contents**

```bash
git diff --cached --check
git diff --cached --submodule=log --stat
git ls-files --stage frontend
```

Expected: whitespace check exits `0`; `git ls-files --stage frontend` shows a single mode `160000` gitlink entry rather than ordinary frontend files.

- [ ] **Step 4: Commit and push the portal Submodule migration**

```bash
git add .gitmodules frontend
git commit -m "构建：将前端改为 Git 子模块"
git push origin master
```

Expected: the portal remote now records `.gitmodules` plus the frontend gitlink. Use the actual current portal branch if it is not `master`.

### Task 3: Initialize the Submodule in Jenkins before packaging

**Files:**
- Modify: `Jenkinsfile`

- [ ] **Step 1: Extend the Checkout stage after `checkout scm`**

Insert this shell step directly after `checkout scm`:

```groovy
sh '''
  git submodule sync --recursive
  git submodule update --init --recursive
  test -f frontend/package.json
  echo "Frontend commit: $(git -C frontend rev-parse HEAD)"
'''
```

Expected: a Jenkins build logs the exact frontend SHA before the Package Backend stage starts.

- [ ] **Step 2: Validate Jenkinsfile syntax and commit**

Run from portal root:

```bash
git diff -- Jenkinsfile
git add Jenkinsfile
git commit -m "构建：Jenkins 初始化前端子模块"
git push origin master
```

Expected: the only Pipeline behavior change is Submodule initialization. Use the actual current portal branch if it is not `master`.

### Task 4: Verify reproducible artifact content

**Files:**
- Verify: Jenkins workspace, `backend/target/ztoken-portal-*.jar`, and Docker Hub image tags.

- [ ] **Step 1: Trigger one Jenkins build from the portal migration commit**

Confirm the console includes:

```text
Frontend commit: the fixed portal-front SHA printed in Task 1
Generated backend JAR files:
backend/target/ztoken-portal-a-versioned-jar.jar
```

- [ ] **Step 2: Verify the generated JAR contains frontend static resources**

Run in the Jenkins workspace or an equivalent build checkout:

```bash
jar tf backend/target/ztoken-portal-*.jar | grep '^BOOT-INF/classes/static/index.html$'
```

Expected: the command prints `BOOT-INF/classes/static/index.html`.

- [ ] **Step 3: Verify the release metadata**

Record the Jenkins output values in the release note:

```text
portal commit: Jenkins Git commit printed by Checkout
frontend commit: Jenkins Frontend commit printed by Checkout
image: wenyou7/portal followed by the Jenkins IMAGE_VERSION value
```

Expected: rebuilding the same portal commit checks out the same frontend SHA and produces the same source content.

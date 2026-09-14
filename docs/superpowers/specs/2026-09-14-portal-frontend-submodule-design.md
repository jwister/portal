# Portal 前端 Git Submodule 迁移设计

## 目标

将 `portal/frontend` 从“主仓库跟踪文件且目录内另有独立 Git 仓库”的混合状态，迁移为标准 Git Submodule。`portal` 发布时固定引用 `portal-front` 的一个明确 commit，Jenkins 根据该 commit 构建前端并打入后端 JAR。

## 当前状态

- `portal` 是主仓库。
- `frontend` 是独立 Git 仓库，远程为 `https://github.com/jwister/portal-front.git`，当前分支为 `main`。
- `portal` 没有 `.gitmodules`，但 Git 索引跟踪了 `frontend` 内的普通文件。
- `frontend` 有已修改和未跟踪文件；用户明确授权先将这些前端改动提交到 `portal-front/main`。

## 迁移步骤

1. 在 `frontend` 仓库复核状态、暂存全部当前改动、使用中文提交信息提交并推送到 `origin/main`。
2. 记录推送后的前端 commit SHA。
3. 在 `portal` 主仓库中移除 `frontend` 普通文件的 Git 索引记录，但保留工作区文件直到 Submodule 初始化完成。
4. 将 `https://github.com/jwister/portal-front.git` 作为 `frontend` 路径的 Git Submodule 加入，引用步骤 2 的 commit，并生成 `.gitmodules`。
5. 提交 `portal` 的 `.gitmodules` 和 Gitlink 变更，使用中文提交信息。
6. 修改 Jenkins Checkout，使其初始化并递归更新 Submodule；Maven 原有的前端构建顺序保持不变。
7. 验证 Jenkins 工作区的 `frontend` HEAD 与 `portal` 记录的 Submodule SHA 相同，并确认 JAR 含有 `BOOT-INF/classes/static/` 资源。

## 发布规则

- 前端改动先在 `portal-front/main` 提交。
- 要发布该前端版本时，在 `portal` 更新 Submodule 指针并提交；Jenkins 只构建 `portal` 指向的前端 commit。
- 不允许 Jenkins 在构建时直接拉取前端仓库的最新 `main`，以保证同一 portal commit 的镜像可复现。

## 风险控制

- 提交前显示前端 diff 和待提交文件，避免遗漏未跟踪文件。
- 每次 Git 操作均限定在 `portal` 或 `frontend` 的明确路径，不使用清理、重置或强制覆盖命令。
- 迁移成功后不删除任何远程分支或历史记录。
- 如果远程推送失败，停止后续主仓库迁移，保留本地提交与工作区供用户处理。

## 验证

1. `git -C frontend status --short` 在提交后为空。
2. `git submodule status` 在 portal 根目录显示 frontend 的固定 SHA。
3. `git ls-files frontend` 只显示一个 gitlink 条目，而不再列出前端源码文件。
4. Jenkins Checkout 使用递归 Submodule 更新，构建控制台打印 frontend commit SHA。
5. Maven 打包后 JAR 中存在 `BOOT-INF/classes/static/index.html`。

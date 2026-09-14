$ErrorActionPreference = 'Stop'

$projectRoot = Split-Path -Parent $PSScriptRoot
$gitmodulesPath = Join-Path $projectRoot '.gitmodules'
$jenkinsfilePath = Join-Path $projectRoot 'Jenkinsfile'

if (-not (Test-Path -LiteralPath $gitmodulesPath -PathType Leaf)) {
    throw '缺少 .gitmodules 文件。'
}

$gitlink = git -C $projectRoot ls-files --stage frontend
if ($gitlink -notmatch '^160000\s+[0-9a-f]{40}\s+0\s+frontend$') {
    throw "frontend 不是有效的 Git Submodule：$gitlink"
}

$jenkinsfile = Get-Content -LiteralPath $jenkinsfilePath -Raw
foreach ($expectedText in @(
    'git submodule sync --recursive',
    'git submodule update --init --recursive',
    'test -f frontend/package.json',
    'git -C frontend rev-parse HEAD'
)) {
    if ($jenkinsfile -notmatch [regex]::Escape($expectedText)) {
        throw "Jenkinsfile 缺少前端子模块检出命令：$expectedText"
    }
}

Write-Output '前端 Git Submodule 与 Jenkins 检出配置验证通过。'

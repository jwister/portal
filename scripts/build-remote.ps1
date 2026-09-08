# 在远程 Docker daemon 上构建 Portal 镜像。
#
# 用法：
#   .\scripts\build-remote.ps1
#   .\scripts\build-remote.ps1 -Tag v1.0.0
#   .\scripts\build-remote.ps1 -Tag v1.0.0 -Push
#   .\scripts\build-remote.ps1 -Remote tcp://host:2375 -Image wenyou7/ztoken-portal

[CmdletBinding()]
param(
    [string]$Remote = 'tcp://192.168.100.153:2375',
    [string]$Image = 'wenyou7/ztoken-portal',
    [string]$Tag = '',
    [string]$Platform = 'linux/amd64',
    [switch]$Push
)

$ErrorActionPreference = 'Stop'

function Log($Message) {
    Write-Host "[build-remote] $Message" -ForegroundColor Cyan
}

function Fail($Message) {
    Write-Host "[build-remote] $Message" -ForegroundColor Red
    exit 1
}

function Invoke-NativeStep {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Command,

        [string[]]$Arguments = @(),

        [Parameter(Mandatory = $true)]
        [string]$FailureMessage
    )

    & $Command @Arguments
    if ($LASTEXITCODE -ne 0) {
        Fail "$FailureMessage（退出码：$LASTEXITCODE）"
    }
}

$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$BackendRoot = Join-Path $RepoRoot 'backend'
$TargetRoot = Join-Path $BackendRoot 'target'

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    Fail 'PATH 中未找到 docker CLI'
}
if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) {
    Fail 'PATH 中未找到 Maven 命令 mvn'
}

$Tags = @("$Image`:latest")
if ($Tag) {
    $Tags += "$Image`:$Tag"
}

Log "远程 Docker daemon：$Remote"
Log "镜像标签：$($Tags -join ', ')"
Log "目标平台：$Platform"
Log "构建上下文：$RepoRoot"

if ($Remote -match '^tcp://.+:2375$') {
    Log '警告：正在使用未加密的 tcp:2375，请确认该 Docker daemon 位于可信网络。'
}

Log '步骤 1/2：本地打包前后端单体 JAR...'
Set-Location $BackendRoot
Invoke-NativeStep -Command 'mvn' -Arguments @('clean', 'package', '-DskipTests', '-B') -FailureMessage 'Portal 本地打包失败'

$Jars = @(Get-ChildItem -LiteralPath $TargetRoot -Filter 'ztoken-portal-*.jar' -File |
    Where-Object { $_.Name -notlike '*.jar.original' })
if ($Jars.Count -ne 1) {
    Fail "期望在 $TargetRoot 中找到一个可执行 JAR，实际找到 $($Jars.Count) 个"
}
Log "打包产物：$($Jars[0].Name)"

Log '步骤 2/2：在远程 Docker daemon 构建镜像...'
Set-Location $RepoRoot
Invoke-NativeStep -Command 'docker' -Arguments @('-H', $Remote, 'version', '--format', '{{.Server.Version}}') -FailureMessage "无法连接远程 Docker daemon：$Remote"

$BuildArgs = @('-H', $Remote, 'build', '--platform', $Platform)
foreach ($CurrentTag in $Tags) {
    $BuildArgs += @('-t', $CurrentTag)
}
$BuildArgs += @('-f', 'Dockerfile', '.')
Invoke-NativeStep -Command 'docker' -Arguments $BuildArgs -FailureMessage '远程 Docker 镜像构建失败'

Log '镜像构建完成。'
Invoke-NativeStep -Command 'docker' -Arguments @('-H', $Remote, 'image', 'ls', '--filter', "reference=$Image", '--format', 'table {{.Repository}}:{{.Tag}}\t{{.ID}}\t{{.Size}}\t{{.CreatedSince}}') -FailureMessage '查询远程镜像列表失败'

if ($Push) {
    foreach ($CurrentTag in $Tags) {
        Log "推送镜像：$CurrentTag"
        Invoke-NativeStep -Command 'docker' -Arguments @('-H', $Remote, 'push', $CurrentTag) -FailureMessage "推送镜像失败：$CurrentTag"
    }
}

Log '完成。'

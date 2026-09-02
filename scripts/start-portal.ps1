$ErrorActionPreference = 'Stop'

$portalRoot = Split-Path -Parent $PSScriptRoot
$jarPath = Join-Path $portalRoot 'backend\target\ztoken-portal-0.1.0-SNAPSHOT.jar'
$sessionKey = [Environment]::GetEnvironmentVariable('PORTAL_SESSION_KEY', 'User')
$newApiBaseUrl = [Environment]::GetEnvironmentVariable('NEWAPI_BASE_URL', 'User')
$javaPath = 'C:\Program Files\Java\jdk-17.0.12\bin\java.exe'

if (-not (Test-Path -LiteralPath $jarPath)) {
    throw "Portal JAR not found: $jarPath. Run 'mvn -f backend/pom.xml package' first."
}

if (-not (Test-Path -LiteralPath $javaPath)) {
    throw "Java 17 executable not found: $javaPath"
}

if ([string]::IsNullOrWhiteSpace($sessionKey)) {
    throw 'PORTAL_SESSION_KEY is not configured in the current user environment.'
}

try {
    $decodedLength = [Convert]::FromBase64String($sessionKey).Length
} catch {
    throw 'PORTAL_SESSION_KEY is not valid Base64.'
}

if ($decodedLength -ne 32) {
    throw "PORTAL_SESSION_KEY must decode to 32 bytes; current length is $decodedLength."
}

$env:PORTAL_SESSION_KEY = $sessionKey
if (-not [string]::IsNullOrWhiteSpace($newApiBaseUrl)) {
    $env:NEWAPI_BASE_URL = $newApiBaseUrl
}
& $javaPath -jar $jarPath @args

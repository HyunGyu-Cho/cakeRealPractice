<#
.SYNOPSIS
    [레거시] docs/sql의 SQL 한 파일을 .env의 로컬 DB에 적용한다.

.DESCRIPTION
    ⚠️ 일상적인 스키마 반영에는 더 이상 쓰지 않는다. 마이그레이션은 Flyway가 앱 부팅 시
    src/main/resources/db/migration에서 자동 적용한다(README, docs/sql/README.md 참조).

    이 스크립트는 docs/sql/legacy에 보관된 전환 이전 V파일을 예외적으로 다시 돌려봐야 할 때만
    남겨둔 도구다. 새 마이그레이션을 여기로 적용하면 Flyway 이력에 기록되지 않아
    "DB에는 있는데 Flyway는 모르는" 어긋난 상태가 된다.

    PowerShell 5.1에서 지원하지 않는 '< file.sql' 입력 리다이렉션과
    경로·계정·비밀번호 하드코딩을 피하기 위한 공용 실행기다.
    대상은 프로젝트의 docs/sql 아래 V*.sql 파일로 제한한다.

.PARAMETER File
    적용할 SQL 파일. 프로젝트 루트 기준 상대 경로나 절대 경로를 받을 수 있다.

.PARAMETER ValidateOnly
    파일·설정·MariaDB 클라이언트만 확인하고 SQL은 실행하지 않는다.
#>
param(
    [Parameter(Mandatory = $true)]
    [string]$File,
    [switch]$ValidateOnly
)

$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = New-Object System.Text.UTF8Encoding $false

$root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$sqlRoot = (Resolve-Path (Join-Path $root "docs/sql")).Path
$candidate = if ([System.IO.Path]::IsPathRooted($File)) { $File } else { Join-Path $root $File }
$sqlFile = (Resolve-Path -LiteralPath $candidate -ErrorAction Stop).Path

if (-not $sqlFile.StartsWith($sqlRoot + [System.IO.Path]::DirectorySeparatorChar,
        [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "docs/sql 밖의 파일은 적용할 수 없습니다: $sqlFile"
}
if ([System.IO.Path]::GetFileName($sqlFile) -notmatch '^V\d+_[A-Za-z0-9_-]+\.sql$') {
    throw "V번호 규칙에 맞는 SQL 파일만 적용할 수 있습니다: $sqlFile"
}

$envFile = Join-Path $root ".env"
if (-not (Test-Path -LiteralPath $envFile)) {
    throw "프로젝트 루트에 .env가 없습니다. .env_sample을 복사해 LOCAL_DB_* 값을 먼저 설정하세요."
}

$dbCfg = @{ host = "localhost"; port = "3307"; db = "cakeshop"; user = "root"; pass = "" }
foreach ($line in Get-Content -LiteralPath $envFile -Encoding UTF8) {
    if ($line -notmatch '^\s*([A-Z0-9_]+)\s*=\s*(.*)$') { continue }
    $key = $Matches[1]
    $value = $Matches[2].Trim().Trim('"').Trim("'")
    switch ($key) {
        "LOCAL_DB_HOST"     { $dbCfg.host = $value }
        "LOCAL_DB_PORT"     { $dbCfg.port = $value }
        "LOCAL_DB_DATABASE" { $dbCfg.db   = $value }
        "LOCAL_DB_USERNAME" { $dbCfg.user = $value }
        "LOCAL_DB_PASSWORD" { $dbCfg.pass = $value }
    }
}

foreach ($required in @{
        host = "LOCAL_DB_HOST"
        port = "LOCAL_DB_PORT"
        db   = "LOCAL_DB_DATABASE"
        user = "LOCAL_DB_USERNAME"
    }.GetEnumerator()) {
    if ([string]::IsNullOrWhiteSpace([string]$dbCfg[$required.Key])) {
        throw ".env의 $($required.Value) 값이 비어 있습니다."
    }
}

$mariadb = Get-ChildItem "C:\Program Files\MariaDB *\bin\mariadb.exe" -ErrorAction SilentlyContinue |
    Sort-Object FullName -Descending |
    Select-Object -First 1 -ExpandProperty FullName
if (-not $mariadb) {
    throw "MariaDB 클라이언트(mariadb.exe)를 찾지 못했습니다."
}

Write-Host "SQL: $sqlFile"
Write-Host "DB : $($dbCfg.host):$($dbCfg.port)/$($dbCfg.db) (user=$($dbCfg.user))"
if ($ValidateOnly) {
    Write-Host "검증 완료 — ValidateOnly이므로 SQL은 실행하지 않았습니다." -ForegroundColor Green
    exit 0
}

$hadPassword = Test-Path Env:MYSQL_PWD
$previousPassword = $env:MYSQL_PWD
try {
    # 비밀번호를 명령행 인수에 넣지 않아 프로세스 목록에 노출되지 않게 한다.
    $env:MYSQL_PWD = $dbCfg.pass
    $process = Start-Process -FilePath $mariadb `
        -ArgumentList @(
            "--host=$($dbCfg.host)",
            "--port=$($dbCfg.port)",
            "--user=$($dbCfg.user)",
            "--database=$($dbCfg.db)",
            "--ssl=0",
            "--default-character-set=utf8mb4"
        ) `
        -RedirectStandardInput $sqlFile `
        -NoNewWindow `
        -Wait `
        -PassThru
} finally {
    if ($hadPassword) {
        $env:MYSQL_PWD = $previousPassword
    } else {
        Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue
    }
}

if ($process.ExitCode -ne 0) {
    throw "마이그레이션 적용에 실패했습니다. exit=$($process.ExitCode)"
}

Write-Host "마이그레이션 적용 완료: $([System.IO.Path]::GetFileName($sqlFile))" -ForegroundColor Green

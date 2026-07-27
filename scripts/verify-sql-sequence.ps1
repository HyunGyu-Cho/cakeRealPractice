<#
.SYNOPSIS
    V0/V1 두 기준에서 최신 증분 SQL까지 임시 DB에 순차 적용한다.

.DESCRIPTION
    cakeshop_schema_verify_v0, cakeshop_schema_verify_v1 두 임시 DB만 생성·삭제한다.
    프로젝트 .env의 LOCAL_DB_*와 설치된 MariaDB 클라이언트를 사용한다.
#>

$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = New-Object System.Text.UTF8Encoding $false

$root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$sqlRoot = Join-Path $root "docs/sql"
$envFile = Join-Path $root ".env"
$dbCfg = @{ host = "localhost"; port = "3307"; user = "root"; pass = "" }

foreach ($line in Get-Content -LiteralPath $envFile -Encoding UTF8) {
    if ($line -notmatch '^\s*([A-Z0-9_]+)\s*=\s*(.*)$') { continue }
    $key = $Matches[1]
    $value = $Matches[2].Trim().Trim('"').Trim("'")
    switch ($key) {
        "LOCAL_DB_HOST"     { $dbCfg.host = $value }
        "LOCAL_DB_PORT"     { $dbCfg.port = $value }
        "LOCAL_DB_USERNAME" { $dbCfg.user = $value }
        "LOCAL_DB_PASSWORD" { $dbCfg.pass = $value }
    }
}

$mariadb = Get-ChildItem "C:\Program Files\MariaDB *\bin\mariadb.exe" -ErrorAction SilentlyContinue |
    Sort-Object FullName -Descending |
    Select-Object -First 1 -ExpandProperty FullName
if (-not $mariadb) {
    throw "MariaDB 클라이언트(mariadb.exe)를 찾지 못했습니다."
}

$commonArgs = @(
    "--host=$($dbCfg.host)",
    "--port=$($dbCfg.port)",
    "--user=$($dbCfg.user)",
    "--ssl=0",
    "--default-character-set=utf8mb4"
)

function Invoke-Statement([string]$statement) {
    $escapedStatement = $statement.Replace('"', '\"')
    $process = Start-Process -FilePath $mariadb `
        -ArgumentList ($commonArgs + @("--execute=`"$escapedStatement`"")) `
        -NoNewWindow -Wait -PassThru
    if ($process.ExitCode -ne 0) {
        throw "SQL 문장 실행 실패(exit=$($process.ExitCode)): $statement"
    }
}

function Invoke-SqlFile([string]$database, [string]$path) {
    Write-Host "[$database] $([System.IO.Path]::GetFileName($path))"
    $process = Start-Process -FilePath $mariadb `
        -ArgumentList ($commonArgs + @("--database=$database")) `
        -RedirectStandardInput $path `
        -NoNewWindow -Wait -PassThru
    if ($process.ExitCode -ne 0) {
        throw "SQL 파일 적용 실패(exit=$($process.ExitCode)): $path"
    }
}

# 증분 V파일 목록을 파일 시스템에서 직접 읽어 번호 순으로 정렬한다.
# 범위를 하드코딩하면 V파일이 추가될 때마다 스크립트가 조용히 뒤처진다(과거 V9에서 멈춰 있었다).
# 사전순 정렬은 V10이 V2보다 앞서므로 반드시 숫자로 정렬해야 한다.
function Get-IncrementalSqlFiles([int]$from) {
    Get-ChildItem $sqlRoot -Filter "V*.sql" |
        ForEach-Object {
            if ($_.Name -match '^V(\d+)_') {
                [pscustomobject]@{ Number = [int]$Matches[1]; Name = $_.Name }
            }
        } |
        Where-Object { $_.Number -ge $from } |
        Sort-Object Number |
        Select-Object -ExpandProperty Name
}

$targets = @(
    @{
        name = "cakeshop_schema_verify_v0"
        files = @("V0_ERD.sql") + (Get-IncrementalSqlFiles 2)
    },
    @{
        name = "cakeshop_schema_verify_v1"
        # V1은 comments 등 커뮤니티 하위 테이블을 의도적으로 제외한 최소 정본이므로
        # 해당 테이블을 ALTER하는 V2는 V0 기준에서만 검증한다.
        files = @("V1_first_MVC_table.sql") + (Get-IncrementalSqlFiles 3)
    }
)

$hadPassword = Test-Path Env:MYSQL_PWD
$previousPassword = $env:MYSQL_PWD
try {
    $env:MYSQL_PWD = $dbCfg.pass
    foreach ($target in $targets) {
        $database = $target.name
        if ($database -notmatch '^cakeshop_schema_verify_v[01]$') {
            throw "허용되지 않은 임시 DB 이름입니다: $database"
        }
        Invoke-Statement "DROP DATABASE IF EXISTS $database; CREATE DATABASE $database CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"
        try {
            foreach ($file in $target.files) {
                Invoke-SqlFile $database (Join-Path $sqlRoot $file)
            }
        } finally {
            Invoke-Statement "DROP DATABASE IF EXISTS $database"
        }
    }
} finally {
    if ($hadPassword) {
        $env:MYSQL_PWD = $previousPassword
    } else {
        Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue
    }
}

Write-Host "V0/V1 기준 SQL 순차 적용 검증 완료" -ForegroundColor Green

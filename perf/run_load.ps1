<#
.SYNOPSIS
  cakeshop 부하 측정 러너 — .env의 LOCAL_DB_* 를 읽어 load_sim.py 에 넘긴다.

.DESCRIPTION
  load_sim.py 의 기본값(3306 / cake_performance)은 개발자 PC마다 다르다.
  이 스크립트는 프로젝트 루트 .env 의 LOCAL_DB_HOST/PORT/USERNAME/PASSWORD/DATABASE 를
  PERF_DB_* 환경변수로 변환해 넘기므로, .env만 맞으면 어느 PC에서도 그대로 돈다.

.EXAMPLE
  .\perf\run_load.ps1 -Seed              # 대량 시드 재적재 (수 초~수 분)
  .\perf\run_load.ps1 -Label baseline    # 개선 전 측정
  .\perf\run_load.ps1 -Label improved -Rewrite   # 개선 후 측정 (mapper 리라이트 SQL)
  .\perf\run_load.ps1 -Explain           # 병목 EXPLAIN 증거만 출력
#>
[CmdletBinding()]
param(
    [int]$Threads = 16,
    [int]$Duration = 15,
    [string]$Label = 'baseline',
    [switch]$Rewrite,
    [switch]$Seed,
    [switch]$Explain
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot

# ---------- .env 파싱 ----------
$envFile = Join-Path $root '.env'
if (-not (Test-Path $envFile)) { throw ".env 를 찾을 수 없습니다: $envFile" }
$cfg = @{}
Get-Content $envFile | Where-Object { $_ -match '^\s*[A-Za-z_][A-Za-z0-9_]*\s*=' } | ForEach-Object {
    $k, $v = $_ -split '=', 2
    $cfg[$k.Trim()] = $v.Trim()
}

$dbHost = $cfg['LOCAL_DB_HOST']; if (-not $dbHost -or $dbHost -eq 'localhost') { $dbHost = '127.0.0.1' }
$dbPort = $cfg['LOCAL_DB_PORT']
$dbUser = $cfg['LOCAL_DB_USERNAME']
$dbPass = $cfg['LOCAL_DB_PASSWORD']
$dbName = $cfg['LOCAL_DB_DATABASE']

if ($dbName -notmatch 'performance') {
    Write-Warning "LOCAL_DB_DATABASE='$dbName' — 부하용 DB가 아닌 것 같습니다. 개발 DB에 시드를 넣으면 데이터가 지워집니다."
    if ($Seed) { throw "안전장치: -Seed 는 이름에 'performance' 가 든 DB에만 실행합니다." }
}

# ---------- 실행 파일 탐색 ----------
function Find-Exe([string[]]$candidates, [string]$onPath) {
    $c = Get-Command $onPath -ErrorAction SilentlyContinue
    # WindowsApps 스텁(python.exe)은 실행하면 스토어가 열리므로 제외한다
    if ($c -and $c.Source -notmatch 'WindowsApps') { return $c.Source }
    foreach ($p in $candidates) { if (Test-Path $p) { return $p } }
    return $null
}

$python = Find-Exe @(
    "$env:USERPROFILE\anaconda3\python.exe",
    "$env:USERPROFILE\miniconda3\python.exe",
    "$env:LOCALAPPDATA\Programs\Python\Python312\python.exe"
) 'python'
if (-not $python) { throw "python.exe 를 찾을 수 없습니다. Anaconda 또는 python.org 배포판을 설치하세요." }

$mysql = Find-Exe @(
    (Get-ChildItem 'C:\Program Files\MariaDB *\bin\mysql.exe' -ErrorAction SilentlyContinue | Select-Object -First 1 -ExpandProperty FullName),
    (Get-ChildItem 'C:\Program Files\MySQL\*\bin\mysql.exe' -ErrorAction SilentlyContinue | Select-Object -First 1 -ExpandProperty FullName)
) 'mysql'

Write-Host "DB     : $dbUser@${dbHost}:$dbPort/$dbName" -ForegroundColor Cyan
Write-Host "python : $python" -ForegroundColor Cyan

# ---------- -Seed : 대량 시드 재적재 ----------
if ($Seed) {
    if (-not $mysql) { throw "mysql.exe 를 찾을 수 없습니다 (-Seed 에 필요)." }
    Write-Host "`n대량 시드 적재 중... (게시글 10만 · 댓글 30만 · 주문 10만)" -ForegroundColor Yellow
    $env:MYSQL_PWD = $dbPass
    $seedPath = (Join-Path $PSScriptRoot 'seed_load_data.sql') -replace '\\', '/'
    & $mysql -h $dbHost -P $dbPort -u $dbUser -D $dbName --default-character-set=utf8mb4 -e "source $seedPath"
    $env:MYSQL_PWD = $null
    if ($LASTEXITCODE -ne 0) { throw "시드 실패 (exit $LASTEXITCODE)" }
    Write-Host "시드 완료" -ForegroundColor Green
    if (-not $Explain -and -not $PSBoundParameters.ContainsKey('Label')) { return }
}

# ---------- -Explain : 병목 실행계획 증거 ----------
if ($Explain) {
    if (-not $mysql) { throw "mysql.exe 를 찾을 수 없습니다 (-Explain 에 필요)." }
    $env:MYSQL_PWD = $dbPass
    Write-Host "`n=== EXPLAIN: 메인 인기 글 (findPopularPosts) ===" -ForegroundColor Yellow
    $sql = @'
EXPLAIN SELECT p.id, p.member_id, c.code, c.name, p.title, p.like_count,
       (SELECT COUNT(*) FROM comments cm WHERE cm.post_id = p.id AND cm.status='ACTIVE') AS comment_count,
       p.created_at
  FROM posts p JOIN post_categories c ON c.id = p.category_id
 WHERE p.status='ACTIVE' ORDER BY p.like_count DESC, p.id DESC LIMIT 4;
'@
    & $mysql -h $dbHost -P $dbPort -u $dbUser -D $dbName --default-character-set=utf8mb4 -e $sql
    Write-Host "`n=== posts / comments 보조 인덱스 현황 ===" -ForegroundColor Yellow
    & $mysql -h $dbHost -P $dbPort -u $dbUser -D $dbName -e "SELECT TABLE_NAME, INDEX_NAME, GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) cols FROM information_schema.STATISTICS WHERE TABLE_SCHEMA='$dbName' AND TABLE_NAME IN ('posts','comments') GROUP BY TABLE_NAME, INDEX_NAME;"
    $env:MYSQL_PWD = $null
    return
}

# ---------- 부하 측정 ----------
$env:PERF_DB_HOST = $dbHost
$env:PERF_DB_PORT = $dbPort
$env:PERF_DB_USER = $dbUser
$env:PERF_DB_PASSWORD = $dbPass
$env:PERF_DB_NAME = $dbName
if ($Rewrite) { $env:REWRITE = '1' } else { $env:REWRITE = $null }

Write-Host "`n측정 시작: $Threads 커넥션 x ${Duration}초 / label=$Label / rewrite=$([bool]$Rewrite)" -ForegroundColor Yellow
& $python (Join-Path $PSScriptRoot 'load_sim.py') $Threads $Duration $Label
$code = $LASTEXITCODE

'PERF_DB_HOST','PERF_DB_PORT','PERF_DB_USER','PERF_DB_PASSWORD','PERF_DB_NAME','REWRITE' | ForEach-Object {
    Remove-Item "Env:$_" -ErrorAction SilentlyContinue
}
exit $code

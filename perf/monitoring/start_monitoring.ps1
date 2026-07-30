<#
.SYNOPSIS
  Prometheus + Grafana 를 띄워 cakeshop 대시보드를 연다.

.DESCRIPTION
  둘 다 무설치(zip) 배포판이라 서비스 등록도 관리자 권한도 필요 없다.
  바이너리는 %USERPROFILE%\tools\monitoring 에 두고, 설정은 이 저장소(perf/monitoring)에서 읽는다.

  포트 배치:
    8080  앱 서비스
    9090  앱 actuator (--spring.profiles.active=local,monitor 로 떠 있어야 함)
    9091  Prometheus  ← 기본 9090이 actuator와 겹쳐 옮겼다
    3000  Grafana

.EXAMPLE
  .\perf\monitoring\start_monitoring.ps1          # 기동
  .\perf\monitoring\start_monitoring.ps1 -Status  # 상태 확인
  .\perf\monitoring\start_monitoring.ps1 -Stop    # 종료
#>
[CmdletBinding()]
param(
    [switch]$Stop,
    [switch]$Status
)

$ErrorActionPreference = 'Stop'
$confDir = $PSScriptRoot
$tools = Join-Path $env:USERPROFILE 'tools\monitoring'

function Get-Listener([int]$Port) {
    Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue |
        Select-Object -First 1 -ExpandProperty OwningProcess
}

function Show-Status {
    foreach ($svc in @(
        @{ n = '앱 서비스';   p = 8080; u = 'http://localhost:8080' },
        @{ n = '앱 actuator'; p = 9090; u = 'http://localhost:9090/actuator/prometheus' },
        @{ n = 'Prometheus';  p = 9091; u = 'http://localhost:9091' },
        @{ n = 'Grafana';     p = 3000; u = 'http://localhost:3000' }
    )) {
        $procId = Get-Listener $svc.p
        if ($procId) {
            $nm = (Get-Process -Id $procId -ErrorAction SilentlyContinue).ProcessName
            Write-Host ("  {0,-12} 실행중  :{1}  ({2}, PID {3})  {4}" -f $svc.n, $svc.p, $nm, $procId, $svc.u) -ForegroundColor Green
        } else {
            Write-Host ("  {0,-12} 꺼짐    :{1}" -f $svc.n, $svc.p) -ForegroundColor DarkGray
        }
    }
}

if ($Status) { Write-Host "`n=== 상태 ===" -ForegroundColor Cyan; Show-Status; return }

if ($Stop) {
    Write-Host "`n=== 종료 ===" -ForegroundColor Cyan
    foreach ($port in @(9091, 3000)) {
        $procId = Get-Listener $port
        if ($procId) {
            $nm = (Get-Process -Id $procId -ErrorAction SilentlyContinue).ProcessName
            Stop-Process -Id $procId -Force -ErrorAction SilentlyContinue
            Write-Host "  :$port ($nm) 종료" -ForegroundColor Yellow
        } else {
            Write-Host "  :$port 이미 꺼져 있음" -ForegroundColor DarkGray
        }
    }
    Write-Host "  (앱 8080/9090 은 건드리지 않는다)" -ForegroundColor DarkGray
    return
}

# ---------- 바이너리 확인 ----------
$promExe = Get-ChildItem (Join-Path $tools 'prometheus-*') -Filter 'prometheus.exe' -Recurse -ErrorAction SilentlyContinue |
    Select-Object -First 1 -ExpandProperty FullName
$grafHome = Get-ChildItem (Join-Path $tools 'grafana-*') -Directory -ErrorAction SilentlyContinue |
    Select-Object -First 1 -ExpandProperty FullName
$grafExe = if ($grafHome) { Join-Path $grafHome 'bin\grafana.exe' } else { $null }

if (-not $promExe)  { throw "prometheus.exe 를 찾을 수 없습니다. $tools 아래에 압축을 풀었는지 확인하세요." }
if (-not $grafExe -or -not (Test-Path $grafExe)) { throw "grafana.exe 를 찾을 수 없습니다. $tools 아래에 압축을 풀었는지 확인하세요." }

# ---------- 앱이 떠 있는지 ----------
if (-not (Get-Listener 9090)) {
    Write-Warning "앱 actuator(:9090)가 꺼져 있습니다. 지표가 수집되지 않습니다."
    Write-Warning '먼저 실행: .\gradlew.bat bootRun "--args=--spring.profiles.active=local,monitor"'
}

# ---------- Grafana 프로비저닝 생성 ----------
# dashboards.yml 은 절대경로가 필요한데 저장소에는 상대적으로 둬야 하므로,
# 기동 시점에 실제 경로를 치환한 사본을 tools 밑에 만든다.
$provSrc = Join-Path $confDir 'provisioning'
$provDst = Join-Path $tools 'grafana-provisioning'
if (Test-Path $provDst) { Remove-Item $provDst -Recurse -Force }
Copy-Item $provSrc $provDst -Recurse -Force

$dashDir = (Join-Path $confDir 'dashboards') -replace '\\', '/'
$dashYml = Join-Path $provDst 'dashboards\dashboards.yml'
(Get-Content $dashYml -Raw -Encoding UTF8).Replace('${CAKESHOP_DASHBOARD_DIR}', $dashDir) |
    Set-Content $dashYml -Encoding UTF8 -NoNewline

# ---------- Prometheus ----------
if (Get-Listener 9091) {
    Write-Host "Prometheus 이미 실행중 (:9091)" -ForegroundColor DarkGray
} else {
    $promData = Join-Path $tools 'prometheus-data'
    New-Item -ItemType Directory -Force -Path $promData | Out-Null
    Write-Host "Prometheus 기동..." -ForegroundColor Yellow
    Start-Process -FilePath $promExe -WindowStyle Hidden -ArgumentList @(
        "--config.file=$(Join-Path $confDir 'prometheus.yml')",
        "--storage.tsdb.path=$promData",
        "--web.listen-address=:9091",
        # 부하 실험은 짧게 여러 번 돌리므로 보관 기간은 짧게 잡는다.
        "--storage.tsdb.retention.time=7d"
    )
}

# ---------- Grafana ----------
if (Get-Listener 3000) {
    Write-Host "Grafana 이미 실행중 (:3000)" -ForegroundColor DarkGray
} else {
    $gfData = Join-Path $tools 'grafana-data'
    $gfLogs = Join-Path $tools 'grafana-logs'
    New-Item -ItemType Directory -Force -Path $gfData, $gfLogs | Out-Null
    Write-Host "Grafana 기동..." -ForegroundColor Yellow
    $env:GF_PATHS_DATA = $gfData
    $env:GF_PATHS_LOGS = $gfLogs
    $env:GF_PATHS_PROVISIONING = $provDst
    $env:GF_SERVER_HTTP_PORT = '3000'
    # 로컬 실험용이라 로그인 없이 바로 대시보드를 본다. 외부에 열지 말 것.
    $env:GF_AUTH_ANONYMOUS_ENABLED = 'true'
    $env:GF_AUTH_ANONYMOUS_ORG_ROLE = 'Admin'
    $env:GF_AUTH_BASIC_ENABLED = 'false'
    $env:GF_USERS_ALLOW_SIGN_UP = 'false'
    $env:GF_ANALYTICS_REPORTING_ENABLED = 'false'
    $env:GF_ANALYTICS_CHECK_FOR_UPDATES = 'false'
    Start-Process -FilePath $grafExe -WindowStyle Hidden -WorkingDirectory $grafHome -ArgumentList @('server')
}

# ---------- 기동 대기 ----------
Write-Host "`n기동 대기..." -NoNewline
$ok = $false
foreach ($i in 1..60) {
    Start-Sleep -Milliseconds 1000
    Write-Host "." -NoNewline
    if ((Get-Listener 9091) -and (Get-Listener 3000)) { $ok = $true; break }
}
Write-Host ""

Write-Host "`n=== 상태 ===" -ForegroundColor Cyan
Show-Status

if ($ok) {
    Write-Host "`n대시보드: http://localhost:3000/d/cakeshop-overview" -ForegroundColor Green
    Write-Host "Prometheus: http://localhost:9091/targets  (수집 대상 상태 확인)" -ForegroundColor Green
    Write-Host "`n지표가 비어 있으면 앱에 트래픽을 넣어야 한다. 부하를 걸려면:" -ForegroundColor DarkGray
    Write-Host "  .\perf\run_load.ps1 -Label improved -Rewrite" -ForegroundColor DarkGray
} else {
    Write-Warning "기동을 확인하지 못했습니다. 로그: $tools\grafana-logs\grafana.log"
}

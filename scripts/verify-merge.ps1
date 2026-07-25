<#
.SYNOPSIS
    머지 후 검증. 통과하면 머지 검증 게이트를 해제한다.

.DESCRIPTION
    각 기능 브랜치에서 테스트가 통과해도, 합쳐진 dev는 아무도 실행해 본 적이 없다.
    이 스크립트는 "합쳐진 상태"를 실제로 돌려 본다.

      1) 병합 잔재 + V번호 충돌 — 두 브랜치가 같은 V번호를 붙여도 git은 충돌 없이 둘 다 받아들인다
      2) 마이그레이션 반영     — 이번 머지가 들여온 V파일의 테이블/컬럼이 로컬 DB에 실제로 있는지
      3) 전체 테스트           — 컴파일 오류, 의미상 충돌(지워진 클래스를 참조하는 테스트 등)
      4) 부팅 + 화면 스모크    — 테스트가 원리상 못 잡는 것: Thymeleaf 렌더 오류, 실행되는 MyBatis SQL,
                                 관리자 화면(테스트 커버리지 거의 0)

    상태코드만 보지 않고 본문 완결성(</html>)과 오류 페이지 여부까지 확인한다.
    렌더 도중 실패는 부분 200으로 나갈 수 있기 때문이다.

.PARAMETER Skip
    검증을 통과시킬 수 없는 예외 상황에서만 사용한다. 사유가 로그에 남는다.
#>
param(
    [string]$Skip = "",
    [int]$BootTimeoutSec = 120
)

$ErrorActionPreference = "Continue"
[Console]::OutputEncoding = New-Object System.Text.UTF8Encoding $false

$root     = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$stateDir = Join-Path $root ".claude/state"
$pending  = Join-Path $stateDir "merge-gate-pending.json"
$logFile  = Join-Path $stateDir "merge-gate.log"
$bootLog  = Join-Path $stateDir "verify-boot.log"
$baseUrl  = "http://localhost:8080"
$errorTitles = @("<title>500</title>", "<title>404</title>", "<title>요청 오류</title>")

if (-not (Test-Path $stateDir)) { New-Item -ItemType Directory -Force -Path $stateDir | Out-Null }

function Write-Log([string]$line) {
    Add-Content -Path $logFile -Value ("[{0}] {1}" -f (Get-Date).ToString("s"), $line) -Encoding UTF8
}
function Step([string]$n) { Write-Host ""; Write-Host "== $n" -ForegroundColor Cyan }
function Ok([string]$m)   { Write-Host "   [통과] $m" -ForegroundColor Green }
function Fail([string]$m) { Write-Host "   [실패] $m" -ForegroundColor Red }
function Warn([string]$m) { Write-Host "   [주의] $m" -ForegroundColor Yellow }

$head = (& git -C $root rev-parse --short HEAD 2>$null)

# ── 예외 해제 경로 ──────────────────────────────────────────────
if ($Skip) {
    if (Test-Path $pending) { Remove-Item $pending -Force }
    Write-Log "SKIPPED head=$head 사유: $Skip"
    Write-Host "검증을 건너뛰고 게이트를 해제했습니다. 사유 기록: $Skip" -ForegroundColor Yellow
    exit 0
}

$failures = New-Object System.Collections.Generic.List[string]

# ── 1. 병합 잔재 + V번호 충돌 ───────────────────────────────────
Step "1/4 병합 잔재 · V번호 충돌"
if (& git -C $root ls-files -u) {
    Fail "병합되지 않은 경로가 남아 있습니다."; $failures.Add("unmerged paths")
} elseif (& git -C $root grep -l -E "^(<<<<<<<|>>>>>>>) " -- "src" "docs" "scripts" 2>$null) {
    Fail "충돌 표시가 남은 파일이 있습니다."; $failures.Add("conflict markers")
} else { Ok "충돌 잔재 없음" }

# 두 브랜치가 각각 V6을 붙이면 파일명이 달라 git은 충돌 없이 둘 다 머지한다. 번호로만 잡을 수 있다.
$dupes = Get-ChildItem (Join-Path $root "docs/sql") -Filter "V*.sql" |
    ForEach-Object { if ($_.Name -match '^(V\d+)_') { $Matches[1] } } |
    Group-Object | Where-Object { $_.Count -gt 1 }
if ($dupes) {
    Fail "V번호 중복: $(($dupes | ForEach-Object { "$($_.Name)(x$($_.Count))" }) -join ', ')"
    $failures.Add("duplicate V numbers")
} else { Ok "V번호 중복 없음" }

# ── 2. 마이그레이션 반영 ────────────────────────────────────────
Step "2/4 마이그레이션 반영 확인"
$mariadb = Get-ChildItem "C:\Program Files\MariaDB *\bin\mariadb.exe" -ErrorAction SilentlyContinue |
    Select-Object -First 1 -ExpandProperty FullName
$envFile = Join-Path $root ".env"
$dbCfg = @{ port = "3307"; db = "cakeshop"; user = "root"; pass = "" }
if (Test-Path $envFile) {
    foreach ($line in Get-Content $envFile -Encoding UTF8) {
        if ($line -match '^\s*LOCAL_DB_PORT\s*=\s*(.+)$')     { $dbCfg.port = $Matches[1].Trim() }
        if ($line -match '^\s*LOCAL_DB_DATABASE\s*=\s*(.+)$') { $dbCfg.db   = $Matches[1].Trim() }
        if ($line -match '^\s*LOCAL_DB_USERNAME\s*=\s*(.+)$') { $dbCfg.user = $Matches[1].Trim() }
        if ($line -match '^\s*LOCAL_DB_PASSWORD\s*=\s*(.+)$') { $dbCfg.pass = $Matches[1].Trim() }
    }
}

# "이번 머지가 들여온 V파일"을 기준점으로 계산하려 하면 반드시 틀린다:
#   - gh pr merge 는 서버에서 머지해 로컬 HEAD가 움직이지 않고(훅 시점의 HEAD는 무관한 브랜치일 수 있다)
#   - 이 저장소는 squash·rebase 머지도 허용해 머지 커밋이 아예 없을 수 있으며
#   - 검증 실패 후 전진 수정하면 HEAD가 머지 커밋에서 멀어진다.
# 그래서 기준점을 쓰지 않는다. **모든 증분 V파일(V2+)의 산출물이 DB에 있는지**를 매번 확인한다.
# 머지 전략·훅 타이밍과 무관하게 항상 옳고, 이미 적용된 것은 그냥 통과하므로 반복 실행도 안전하다.
$newSql = @(Get-ChildItem (Join-Path $root "docs/sql") -Filter "V*.sql" |
    Where-Object { $_.Name -match '^V([2-9]|\d{2,})_' } |    # V0/V1은 보관용 정본이라 적용 대상이 아니다
    Sort-Object Name | ForEach-Object { "docs/sql/$($_.Name)" })

if ($newSql.Count -eq 0) {
    Ok "확인할 증분 마이그레이션 없음"
} elseif (-not $mariadb) {
    Warn "mariadb 클라이언트를 찾지 못해 스키마 대조를 건너뜁니다. 확인 대상: $($newSql.Count)건"
} else {
    # V파일이 만드는 테이블/컬럼을 뽑아 information_schema로 실제 존재를 확인한다.
    # 로컬 DB가 이미 앞서 있으면 통과하고, 적용을 잊었으면 여기서 잡힌다.
    $missing = @()
    foreach ($rel in $newSql) {
        $sqlText = Get-Content (Join-Path $root $rel) -Raw -Encoding UTF8
        $wanted = @()
        foreach ($m in [regex]::Matches($sqlText, '(?is)CREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?`?(\w+)`?')) {
            $wanted += @{ table = $m.Groups[1].Value; column = $null }
        }
        foreach ($m in [regex]::Matches($sqlText, '(?is)ALTER\s+TABLE\s+`?(\w+)`?(.*?)(?=;\s*(?:ALTER|CREATE|INSERT|UPDATE|DROP|$))')) {
            $tbl = $m.Groups[1].Value
            foreach ($c in [regex]::Matches($m.Groups[2].Value, '(?is)ADD\s+COLUMN\s+`?(\w+)`?')) {
                $wanted += @{ table = $tbl; column = $c.Groups[1].Value }
            }
        }
        foreach ($w in $wanted) {
            if ($w.column) {
                $q = "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='$($dbCfg.db)' AND TABLE_NAME='$($w.table)' AND COLUMN_NAME='$($w.column)';"
                $label = "$($w.table).$($w.column)"
            } else {
                $q = "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='$($dbCfg.db)' AND TABLE_NAME='$($w.table)';"
                $label = "테이블 $($w.table)"
            }
            $res = & $mariadb -h localhost -P $dbCfg.port -u $dbCfg.user "-p$($dbCfg.pass)" $dbCfg.db -N -B -e $q 2>$null
            if (($res | Select-Object -Last 1) -notmatch '^\s*[1-9]') { $missing += "$rel -> $label" }
        }
    }
    if ($missing.Count -gt 0) {
        Fail "로컬 DB에 미반영된 마이그레이션:"
        $missing | ForEach-Object { Write-Host "        $_" -ForegroundColor Red }
        # V3의 DROP COLUMN, V6의 ADD COLUMN에는 IF (NOT) EXISTS가 없어 이미 적용된 파일을 다시 돌리면 실패한다.
        Write-Host "        위에 나온 파일만 번호 순서대로 적용하세요(이미 적용된 V파일 재실행 금지 — 멱등하지 않습니다)." -ForegroundColor Red
        $failures.Add("migration not applied")
    } else {
        Ok "증분 마이그레이션 $($newSql.Count)건이 모두 DB에 반영돼 있음"
    }
}

# ── 3. 전체 테스트 ──────────────────────────────────────────────
Step "3/4 전체 테스트"
# 중단된 gradle 실행은 컴파일러 워커를 남기고, 그 워커가 build/classes 를 붙잡으면
# 다음 빌드가 'Failed to clean up stale outputs'로 실패한다(실제로 워커 3개가 누적된 이력 있음).
# 주의: java.exe 를 무차별 종료하지 않는다 — IDE 등 무관한 프로세스를 죽일 수 있다.
function Clear-BuildOutputs {
    Push-Location $root; & cmd /c ".\gradlew.bat --stop 2>&1" | Out-Null; Pop-Location
    Start-Sleep -Milliseconds 500
    $p = Join-Path $root "build"
    if (Test-Path $p) { Remove-Item -Recurse -Force $p -ErrorAction SilentlyContinue }
}
function Invoke-GradleTest {
    Push-Location $root
    $out = & cmd /c ".\gradlew.bat test --console=plain 2>&1"
    $code = $LASTEXITCODE
    Pop-Location
    return @{ Output = $out; Exit = $code }
}
Clear-BuildOutputs
$r = Invoke-GradleTest
if ($r.Exit -ne 0 -and ($r.Output -join "`n") -match "Failed to clean up stale outputs") {
    Warn "산출물 잠금 감지 — 정리 후 재시도"
    Clear-BuildOutputs
    $r = Invoke-GradleTest
}
$r.Output | Select-Object -Last 10 | ForEach-Object { Write-Host "   $_" }
if ($r.Exit -eq 0) { Ok "gradlew test 통과" } else { Fail "gradlew test 실패 (exit=$($r.Exit))"; $failures.Add("gradlew test") }

# ── 4. 부팅 + 화면 스모크 ───────────────────────────────────────
Step "4/4 부팅 + 화면 스모크"

function Test-Page([string]$path, $session) {
    try {
        $p = @{ Uri = "$baseUrl$path"; UseBasicParsing = $true; TimeoutSec = 20 }
        if ($session) { $p.WebSession = $session }
        $resp = Invoke-WebRequest @p
    } catch {
        return @{ Ok = $false; Why = "요청 실패: $($_.Exception.Message)" }
    }
    if ($resp.StatusCode -ne 200) { return @{ Ok = $false; Why = "상태 $($resp.StatusCode)" } }
    # 렌더 도중 예외는 부분 응답으로 200이 나갈 수 있어 본문 완결성까지 본다.
    if ($resp.Content -notmatch '(?i)</html>') { return @{ Ok = $false; Why = "본문이 완결되지 않음(렌더 중단 의심)" } }
    foreach ($t in $errorTitles) { if ($resp.Content -like "*$t*") { return @{ Ok = $false; Why = "오류 페이지가 반환됨 ($t)" } } }
    return @{ Ok = $true; Content = $resp.Content }
}

if (Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue) {
    Fail "포트 8080이 이미 사용 중입니다. 실행 중인 서버를 종료한 뒤 다시 검증하세요."
    $failures.Add("port 8080 busy")
} else {
    $proc = Start-Process -FilePath "cmd.exe" `
        -ArgumentList "/c", ".\gradlew.bat bootRun --args=`"--spring.profiles.active=local`" --console=plain > `"$bootLog`" 2>&1" `
        -WorkingDirectory $root -PassThru -WindowStyle Hidden

    $up = $false
    for ($i = 0; $i -lt $BootTimeoutSec; $i++) {
        Start-Sleep -Seconds 1
        try { if ((Invoke-WebRequest -Uri "$baseUrl/actuator/health" -UseBasicParsing -TimeoutSec 3).StatusCode -eq 200) { $up = $true; break } } catch { }
    }

    if (-not $up) {
        Fail "앱이 ${BootTimeoutSec}초 안에 뜨지 않았습니다. 로그: $bootLog"
        $failures.Add("boot timeout")
    } else {
        Ok "앱 부팅 완료"
        foreach ($r2 in @("/", "/screens", "/login", "/signup", "/community", "/products", "/cart")) {
            $t = Test-Page $r2 $null
            if ($t.Ok) { Ok "GET $r2" } else { Fail "GET $r2 — $($t.Why)"; $failures.Add("GET $r2") }
        }

        # 관리자 화면은 테스트 커버리지가 거의 없어 실제 로그인해서 렌더까지 확인한다.
        try {
            $login = Invoke-WebRequest -Uri "$baseUrl/login" -UseBasicParsing -SessionVariable sess -TimeoutSec 20
            $token = ([regex]'name="_csrf"\s+value="([^"]+)"').Match($login.Content).Groups[1].Value
            $null = Invoke-WebRequest -Uri "$baseUrl/login" -Method Post -UseBasicParsing -WebSession $sess -TimeoutSec 20 `
                -Body @{ _csrf = $token; email = "admin@cakeshop.local"; password = "Admin1234!" }

            foreach ($r2 in @("/admin", "/admin/store", "/admin/products", "/admin/products/new", "/admin/community", "/admin/orders", "/admin/members", "/mypage")) {
                $t = Test-Page $r2 $sess
                if (-not $t.Ok) { Fail "GET $r2 — $($t.Why)"; $failures.Add("GET $r2") }
                # 로그인이 풀리면 로그인 화면이 200으로 돌아오므로 내용으로 구분한다.
                elseif ($t.Content -match 'autocomplete="current-password"') { Fail "GET $r2 — 로그인 화면으로 돌아옴"; $failures.Add("GET $r2 (auth)") }
                else { Ok "GET $r2 (로그인 상태)" }
            }
        } catch {
            Fail "관리자 로그인 실패: $($_.Exception.Message)"; $failures.Add("admin login")
        }
    }

    # 종료 처리: 앱을 확실히 죽이지 않으면 포트 8080이 물려 다음 검증이 실패하고,
    # 남은 JVM이 build/classes 를 붙잡아 이후 gradle 빌드가 깨진다. 게이트가 환경을 오염시키면 안 된다.
    if ($proc -and -not $proc.HasExited) { & taskkill /PID $proc.Id /T /F 2>&1 | Out-Null }
    for ($i = 0; $i -lt 20; $i++) {
        $listener = Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue
        if (-not $listener) { break }
        $listener | Select-Object -ExpandProperty OwningProcess -Unique | ForEach-Object { & taskkill /PID $_ /T /F 2>&1 | Out-Null }
        Start-Sleep -Milliseconds 500
    }
    Push-Location $root; & cmd /c ".\gradlew.bat --stop 2>&1" | Out-Null; Pop-Location
    if (Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue) {
        Fail "검증 후에도 포트 8080이 남아 있습니다. 수동으로 종료하세요."; $failures.Add("teardown: port 8080")
    } else { Ok "앱 종료 완료 (포트 8080 해제)" }
}

# ── 결과 ────────────────────────────────────────────────────────
Write-Host ""
if ($failures.Count -eq 0) {
    if (Test-Path $pending) { Remove-Item $pending -Force }
    Write-Log "VERIFIED head=$head 잔재·마이그레이션·테스트·스모크 통과"
    Write-Host "검증 통과 — 머지 검증 게이트를 해제했습니다. (HEAD $head)" -ForegroundColor Green
    exit 0
} else {
    Write-Log "FAILED head=$head 실패항목: $($failures -join '; ')"
    Write-Host "검증 실패 — 게이트는 열리지 않습니다. 실패 항목:" -ForegroundColor Red
    $failures | ForEach-Object { Write-Host "  - $_" -ForegroundColor Red }
    Write-Host "원인을 고친 뒤 다시 실행하세요. 로그: $logFile"
    exit 1
}

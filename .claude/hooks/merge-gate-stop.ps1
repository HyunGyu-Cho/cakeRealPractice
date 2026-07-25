$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = New-Object System.Text.UTF8Encoding $false

# Stop: 머지 후 검증이 끝나지 않았으면 턴 종료를 막는다.
# 판정만 한다 — 검증 자체는 실행하지 않는다(빌드/부팅은 훅에서 돌리기엔 느리고 실패 원인이 안 보인다).
# 안전장치: (1) 오류 시 통과 (2) 최대 차단 횟수 후 기록을 남기고 통과
#             (3) 머지를 실행한 세션만 차단 (4) 상태 파일은 gitignore라 팀원에게 전파되지 않음

$MAX_BLOCKS = 3   # 상한을 둬 세션이 갇히지 않게 한다. 이 횟수를 넘으면 통과시킨다.

try {
    $payload = [Console]::In.ReadToEnd() | ConvertFrom-Json
} catch {
    exit 0
}

$root = if ($env:CLAUDE_PROJECT_DIR) { $env:CLAUDE_PROJECT_DIR } else { (Get-Location).Path }
$pending  = Join-Path $root ".claude/state/merge-gate-pending.json"
# 차단 횟수는 마커와 별도 파일에 둔다 — verify 스크립트가 마커를 통째로 쓰기 때문에 같은 파일에 두면 서로 덮어쓴다.
$blockFile = Join-Path $root ".claude/state/merge-gate-blocks.txt"

if (-not (Test-Path $pending)) {
    if (Test-Path $blockFile) { Remove-Item $blockFile -Force -ErrorAction SilentlyContinue }
    exit 0
}

$armedAt = "(알 수 없음)"
$command = "(알 수 없음)"
$armedSessionId = ""
try {
    $state = Get-Content $pending -Raw -Encoding UTF8 | ConvertFrom-Json
    if ($state.armedAt) { $armedAt = [string]$state.armedAt }
    if ($state.command) { $command = [string]$state.command }
    if ($state.sessionId) { $armedSessionId = [string]$state.sessionId }
} catch { }

# 같은 로컬 프로젝트를 연 다른 Claude 세션까지 남의 미완료 검증으로 막지 않는다.
$currentSessionId = [string]$payload.session_id
if ($armedSessionId -and $currentSessionId -and $armedSessionId -ne $currentSessionId) {
    exit 0
}

# 자체 카운터로 여러 번 막되 세션이 영구히 갇히지 않도록 상한을 둔다.
$blocks = 0
try { if (Test-Path $blockFile) { $blocks = [int](Get-Content $blockFile -Raw).Trim() } } catch { $blocks = 0 }
if ($blocks -ge $MAX_BLOCKS) {
    # 상한을 넘기면 통과시키되 조용히 넘어가지 않는다 — 검증 없이 지나간 머지가 있었다는 흔적을 남긴다.
    try {
        Add-Content -Path (Join-Path $root ".claude/state/merge-gate.log") -Encoding UTF8 `
            -Value ("[{0}] ESCALATED 차단 상한({1}회) 초과 — 검증 없이 턴이 종료됨" -f (Get-Date).ToString("s"), $MAX_BLOCKS)
    } catch { }
    exit 0
}
try { Set-Content -Path $blockFile -Value ([string]($blocks + 1)) -Encoding ASCII } catch { }

$reason = "[머지 검증 게이트] 머지 후 검증이 아직 통과하지 않았습니다. ($($blocks + 1)/$MAX_BLOCKS 회 차단)`n" +
    "  머지 명령: $command`n" +
    "  대기 시작: $armedAt`n" +
    "지금 해야 할 일 — 아래 중 하나를 수행한 뒤 응답을 마치세요.`n" +
    "  1) 검증 실행:  powershell -NoProfile -ExecutionPolicy Bypass -File scripts\verify-merge.ps1`n" +
    "     (전체 테스트 -> 앱 부팅 -> 주요 화면 스모크. 통과하면 게이트가 자동 해제됩니다)`n" +
    "  2) 검증 실패 시: 원인을 고치고 다시 검증하거나, 되돌릴 수 없는 상황이면 사용자에게 상황을 보고하세요.`n" +
    "  3) 검증을 건너뛰어야만 하는 예외 상황: -Skip '사유' 를 붙여 실행하면 사유가 기록되고 해제됩니다.`n" +
    "     (사유 없이 상태 파일을 직접 지우지 마세요 — 검증했다는 거짓 기록이 남습니다)"

@{ decision = "block"; reason = $reason } | ConvertTo-Json -Compress
exit 0

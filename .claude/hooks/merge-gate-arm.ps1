$ErrorActionPreference = "Stop"
# PowerShell 5.1은 stdout을 CP949로 쓰고 Claude Code는 UTF-8로 읽는다. 이 줄이 없으면 한글이 깨진다.
# (스크립트 파일 자체도 UTF-8 BOM으로 저장해야 한글 리터럴이 안 깨진다)
[Console]::OutputEncoding = New-Object System.Text.UTF8Encoding $false

# PostToolUse(Bash): 머지 명령을 감지하면 "검증 대기" 상태를 남긴다.
# 이 상태 파일이 있는 동안 Stop 훅이 턴 종료를 막는다.
# 실패 시에는 항상 조용히 통과한다(fail-open) — 훅 오류가 작업을 막아서는 안 된다.

try {
    $payload = [Console]::In.ReadToEnd() | ConvertFrom-Json
    $command = [string]$payload.tool_input.command
} catch {
    exit 0
}

if ([string]::IsNullOrWhiteSpace($command)) { exit 0 }

# 'merge'로 시작하는 다른 하위명령(merge-base·merge-tree·merge-file)은 읽기 전용 조회라 제외한다.
# (?![-\w]) 가 없으면 'git merge-base --is-ancestor' 한 줄에도 게이트가 무장된다 — 실제로 겪은 오탐.
$isMerge = ($command -match '(?i)\bgh\s+pr\s+merge(?![-\w])') -or ($command -match '(?i)\bgit\s+merge(?![-\w])')
if (-not $isMerge) { exit 0 }
# 머지 취소·중단은 대상이 아니다.
if ($command -match '(?i)\bgit\s+merge\s+--(abort|quit)\b') { exit 0 }

$root = if ($env:CLAUDE_PROJECT_DIR) { $env:CLAUDE_PROJECT_DIR } else { (Get-Location).Path }
$stateDir = Join-Path $root ".claude/state"
$pending = Join-Path $stateDir "merge-gate-pending.json"
$blockFile = Join-Path $stateDir "merge-gate-blocks.txt"

try {
    if (-not (Test-Path $stateDir)) { New-Item -ItemType Directory -Force -Path $stateDir | Out-Null }

    $head = ""
    try { $head = (& git -C $root rev-parse --short HEAD 2>$null) } catch { $head = "" }

    # 재머지 시 이전 대기 상태를 덮어쓴다(중첩되지 않는다).
    @{
        armedAt   = (Get-Date).ToString("s")
        sessionId = [string]$payload.session_id
        command   = $command
        headAtArm = "$head"
    } | ConvertTo-Json -Compress | Set-Content -Path $pending -Encoding UTF8
    # 새 머지는 새 검증 사이클이다. 이전 사이클의 Stop 차단 횟수를 이어받지 않는다.
    if (Test-Path $blockFile) { Remove-Item $blockFile -Force -ErrorAction SilentlyContinue }
} catch {
    exit 0
}

@{
    hookSpecificOutput = @{
        hookEventName    = "PostToolUse"
        additionalContext = "[머지 검증 게이트] 머지를 감지해 검증 대기 상태로 전환했습니다. " +
            "scripts\verify-merge.ps1 을 실행해 통과시켜야 합니다. 미검증 종료는 Stop 훅이 최대 3회 차단하고 기록합니다. " +
            "머지 후 절차는 /merge-feature 스킬을 따르세요."
    }
} | ConvertTo-Json -Compress
exit 0

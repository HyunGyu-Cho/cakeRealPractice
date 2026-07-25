# spec-driven 관문: 스펙 문서 없는 도메인에 새 클래스 파일을 만들려 하면 차단한다.
# - 대상: Write 도구로 src/main/java/com/cakeshop/domain/<도메인>/ 아래 "새 파일" 생성
# - 통과: 기존 파일 수정, docs/specs/<도메인>.md 존재, 이미 구현·조합 계층인 도메인(allowlist)
# - 차단 시 /new-domain 스킬로 스펙부터 작성하도록 안내한다.

$ErrorActionPreference = "Stop"

# PowerShell 5.1은 stdout을 콘솔 기본 인코딩(CP949)으로 쓴다. Claude Code는 UTF-8로 읽으므로
# 이 줄이 없으면 아래 한글 사유가 깨져서 전달된다. (스크립트 파일 자체는 UTF-8 BOM으로 저장해야 한다)
[Console]::OutputEncoding = New-Object System.Text.UTF8Encoding $false

try {
    $payload = [Console]::In.ReadToEnd() | ConvertFrom-Json
    $filePath = $payload.tool_input.file_path
} catch {
    exit 0  # 입력을 못 읽으면 차단하지 않는다 (안전망은 열림 기본)
}

if (-not $filePath) { exit 0 }

$normalized = $filePath -replace '\\', '/'

# 도메인 자바 소스가 아니면 통과
if ($normalized -notmatch 'src/main/java/com/cakeshop/domain/([a-z][a-z0-9]*)/') { exit 0 }
$domain = $Matches[1]

# 이미 구현됐거나(store·community) 조합 전용 계층(home)은 관문 대상이 아니다
$allowlist = @('store', 'community', 'home')
if ($allowlist -contains $domain) { exit 0 }

# 기존 파일 수정(덮어쓰기)은 통과 — 관문은 "새 클래스 생성 = 구현 착수"만 본다
if (Test-Path $filePath) { exit 0 }

$root = if ($env:CLAUDE_PROJECT_DIR) { $env:CLAUDE_PROJECT_DIR } else { (Get-Location).Path }
$spec = Join-Path $root "docs/specs/$domain.md"
if (Test-Path $spec) { exit 0 }

$reason = "spec-driven 관문: docs/specs/$domain.md 가 없습니다. " +
          "'$domain' 도메인 구현에 착수하기 전에 /new-domain 스킬로 스펙(유스케이스·상태값·비즈니스 규칙)을 " +
          "먼저 작성·확정하세요. 템플릿: docs/specs/_template.md"

@{
    hookSpecificOutput = @{
        hookEventName            = "PreToolUse"
        permissionDecision       = "deny"
        permissionDecisionReason = $reason
    }
} | ConvertTo-Json -Compress
exit 0

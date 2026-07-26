# spec-driven 관문: 승인된 스펙 없는 도메인의 운영 코드를 만들거나 수정하려 하면 차단한다.
# - 대상: Write/Edit 도구로 src/main/java/com/cakeshop/domain/<도메인>/ 아래 파일 생성·수정
# - 통과: docs/specs/<도메인>.md frontmatter가 status: approved, 또는 조합 전용 계층(home)
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

# home은 다른 도메인의 공개 View를 조합하는 전용 계층이라 독립 비즈니스 스펙 대상이 아니다.
$allowlist = @('home')
if ($allowlist -contains $domain) { exit 0 }

# 스펙 문서 하나가 여러 도메인을 함께 확정한 경우의 별칭.
# order·payment는 결제 흐름을 공유해 docs/specs/order-payment.md 한 문서로 확정했다.
$specAlias = @{
    'order'   = 'order-payment'
    'payment' = 'order-payment'
}
$specName = if ($specAlias.ContainsKey($domain)) { $specAlias[$domain] } else { $domain }

$root = if ($env:CLAUDE_PROJECT_DIR) { $env:CLAUDE_PROJECT_DIR } else { (Get-Location).Path }
$spec = Join-Path $root "docs/specs/$specName.md"
if (Test-Path $spec) {
    try {
        $specText = Get-Content -LiteralPath $spec -Raw -Encoding UTF8
        $lines = @($specText -split '\r?\n')
        if ($lines.Count -gt 2 -and $lines[0].Trim() -eq '---') {
            $frontmatterEnd = -1
            for ($i = 1; $i -lt $lines.Count; $i++) {
                if ($lines[$i].Trim() -eq '---') {
                    $frontmatterEnd = $i
                    break
                }
            }
            if ($frontmatterEnd -gt 1) {
                $frontmatter = $lines[1..($frontmatterEnd - 1)]
                if ($frontmatter -match '^status:\s*approved\s*$') { exit 0 }
            }
        }
    } catch { }
}

$specState = if (Test-Path $spec) { "있지만 status: approved 가 아닙니다" } else { "없습니다" }
$reason = "spec-driven 관문: docs/specs/$specName.md 가 $specState. " +
          "'$domain' 도메인의 운영 코드를 만들거나 수정하기 전에 /new-domain 스킬로 " +
          "스펙(유스케이스·상태값·비즈니스 규칙)을 작성하고 사용자 확정 후 status: approved 로 바꾸세요. " +
          "템플릿: docs/specs/_template.md"

@{
    hookSpecificOutput = @{
        hookEventName            = "PreToolUse"
        permissionDecision       = "deny"
        permissionDecisionReason = $reason
    }
} | ConvertTo-Json -Compress
exit 0

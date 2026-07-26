param(
    [string]$FrontendRoot = (Join-Path $PSScriptRoot "..\..\cakeProjectSample")
)

$ErrorActionPreference = "Stop"

$backendRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$frontendRoot = (Resolve-Path $FrontendRoot).Path
$templateRoot = Join-Path $backendRoot "src\main\resources\templates"
$staticRoot = Join-Path $backendRoot "src\main\resources\static"

# Keep DB-backed and Security-backed pages intact.
# 고객 화면은 전부 실구현으로 전환됐다 — 덮어쓰면 백엔드 바인딩과 전용 동작이 사라지므로 맵을 비워 둔다.
# 프론트 원본이 갱신되면 CSS·JavaScript만 다시 가져온다(아래 $cssSources / $javascript).
# 새 목업 화면을 이관할 때만 여기에 항목을 추가하고, 실구현 전환 시 다시 제외한다.
$screenMap = [ordered]@{
}

# Replace longer URLs first so query strings are not consumed by shorter routes.
$routeMap = [ordered]@{
    "/customer/product-list.html?type=sameday" = "/products?type=sameday"
    "/customer/product-list.html?type=season" = "/products?type=season"
    "/customer/product-list.html?type=custom" = "/products?type=custom"
    "/customer/product-list.html?type=normal" = "/products?type=general"
    "/customer/product-detail.html" = "/products/1"
    "/customer/product-list.html" = "/products"
    "/customer/cart.html" = "/cart"
    "/customer/pickup-setting.html" = "/orders/pickup"
    "/customer/custom-option.html" = "/orders/custom/options"
    "/customer/custom-request.html" = "/orders/custom/request"
    "/customer/order-form.html" = "/orders/checkout"
    "/customer/payment.html" = "/orders/1/payment"
    "/customer/order-complete.html" = "/orders/complete"
    "/customer/order-detail.html" = "/orders/1"
    "/customer/notification.html" = "/notifications"
    "/customer/review-form.html" = "/reviews/new"
    "/customer/coupon-list.html" = "/mypage/coupons"
    "/customer/profile-edit.html" = "/mypage/profile"
    "/customer/mypage.html" = "/mypage"
    "/customer/signup.html" = "/signup"
    "/customer/login.html" = "/login"
    "/customer/main.html" = "/"
    "/admin/dashboard.html" = "/admin"
}

$headTemplate = @'
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>__TITLE__</title>
  <link rel="stylesheet" th:href="@{/css/app.css}">
  <script defer th:src="@{/js/app.js}"></script>
  <script defer th:src="@{/js/customer-mockup.js}"></script>
</head>
'@

foreach ($entry in $screenMap.GetEnumerator()) {
    $sourcePath = Join-Path $frontendRoot ("customer\" + $entry.Key)
    $destinationPath = Join-Path $templateRoot $entry.Value
    $content = Get-Content -Raw -Encoding UTF8 $sourcePath
    $titleMatch = [regex]::Match($content, "<title>(.*?)</title>")
    $title = if ($titleMatch.Success) { $titleMatch.Groups[1].Value } else { "Cake Shop" }
    $head = $headTemplate.Replace("__TITLE__", $title)

    $content = $content.Replace('<html lang="ko">', '<html lang="ko" xmlns:th="http://www.thymeleaf.org">')
    $content = [regex]::Replace($content, "(?s)<head>.*?</head>", [System.Text.RegularExpressions.MatchEvaluator]{ param($match) $head }, 1)
    $content = $content.Replace('<div data-include="/components/customer-header.html"></div>', '<header th:replace="~{fragments/common/header :: header}"></header>')
    $content = $content.Replace('<div data-include="/components/customer-footer.html"></div>', '<footer th:replace="~{fragments/common/footer :: footer(${store})}"></footer>')
    $content = [regex]::Replace($content, '(?m)^\s*<script src="/js/(include|common|customer|cart)\.js"></script>\r?\n?', '')
    $content = [regex]::Replace(
        $content,
        '(<main\s+class="[^"]*">)',
        '$1' + "`r`n" + '    <div th:replace="~{fragments/customer/mock-notice :: notice}"></div>',
        1
    )

    foreach ($route in $routeMap.GetEnumerator()) {
        $content = $content.Replace($route.Key, $route.Value)
    }

    $destinationDirectory = Split-Path -Parent $destinationPath
    New-Item -ItemType Directory -Force -Path $destinationDirectory | Out-Null
    Set-Content -Path $destinationPath -Value $content -Encoding UTF8
}

# customer-mockup.css는 app.css의 부분집합이며 어떤 템플릿도 로드하지 않는다(참조 0).
# 새 목업을 이관할 때 원본을 대조하는 스냅샷 용도로만 재생성한다.
# ⚠ $headTemplate에 이 파일 링크를 되살리지 말 것 — 구 팔레트 :root가 app.css를
#   덮어써 토스 테마가 통째로 되돌아간다.
$cssSources = @("reset.css", "variables.css", "common.css", "customer.css", "responsive.css")
$css = $cssSources | ForEach-Object {
    "/* source: cakeProjectSample/css/$_ */`r`n" +
        (Get-Content -Raw -Encoding UTF8 (Join-Path $frontendRoot "css\$_"))
}
Set-Content -Path (Join-Path $staticRoot "css\customer-mockup.css") -Value ($css -join "`r`n") -Encoding UTF8

$javascript = @(
    "/* source: cakeProjectSample/js/common.js */",
    (Get-Content -Raw -Encoding UTF8 (Join-Path $frontendRoot "js\common.js")),
    "/* source: cakeProjectSample/js/customer.js */",
    (Get-Content -Raw -Encoding UTF8 (Join-Path $frontendRoot "js\customer.js")),
    "/* source: cakeProjectSample/js/cart.js */",
    (Get-Content -Raw -Encoding UTF8 (Join-Path $frontendRoot "js\cart.js"))
) -join "`r`n"

# JavaScript redirects use the same static mockup URLs as anchor tags.
foreach ($route in $routeMap.GetEnumerator()) {
    $javascript = $javascript.Replace($route.Key, $route.Value)
}
Set-Content -Path (Join-Path $staticRoot "js\customer-mockup.js") -Value $javascript -Encoding UTF8

Write-Host "Imported $($screenMap.Count) customer mockup templates from $frontendRoot"

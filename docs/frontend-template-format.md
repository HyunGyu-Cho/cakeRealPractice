# 프론트엔드 공통 템플릿 규격

개별 화면은 공통 CSS와 프래그먼트를 재사용하고, 화면 고유 마크업만 각 템플릿에 작성한다. API, 서비스, DB 연동 방식은 이 규격의 범위에 포함하지 않는다.

## 고객 화면

```html
<!doctype html>
<html lang="ko" xmlns:th="http://www.thymeleaf.org">
<head th:replace="~{fragments/common/head :: head('화면명 | 케이크 쇼핑몰')}"></head>
<body>
  <header th:replace="~{fragments/common/header :: header}"></header>

  <main class="page-container">
    <!-- 화면 고유 내용 -->
  </main>

  <footer th:replace="~{fragments/common/footer :: footer(${store})}"></footer>
</body>
</html>
```

`store`를 제공하지 않는 화면은 공통 푸터를 생략한다. 고객 메뉴를 변경할 때는 `fragments/customer/gnb.html`만 수정한다.

## 관리자 화면

```html
<!doctype html>
<html lang="ko" xmlns:th="http://www.thymeleaf.org">
<head th:replace="~{fragments/common/head :: head('화면명 | 관리자')}"></head>
<body>
<div class="admin-layout">
  <aside th:replace="~{fragments/admin/sidebar :: sidebar('activeMenu')}"></aside>
  <div class="admin-main">
    <header th:replace="~{fragments/admin/header :: header('화면명')}"></header>
    <main class="admin-content">
      <!-- 화면 고유 내용 -->
    </main>
  </div>
</div>
</body>
</html>
```

관리자 메뉴 키는 `dashboard`, `store`, `products`, `orders`, `fulfillment`, `payments`, `coupons`, `members`, `reviews`, `notifications`, `statistics` 중 하나를 사용한다.

## 공통 클래스

- 본문 폭: `page-container`, `admin-content`
- 배치: `grid`, `grid--2`, `grid--3`, `grid--4`, `cluster`, `stack`
- 영역: `section`, `section-title`, `panel`
- 폼: `form-group`, `form-control`, `btn`
- 표와 상태: `table-wrap`, `data-table`, `badge`
- 색 변형자: `btn--primary`, `btn--danger`, `badge--success/--warning/--danger/--info`, `panel--soft/--danger/--success/--dashed`, `alert--success/--error`

공통 스타일은 `static/css/app.css`, 공통 스크립트는 `static/js/app.js`에만 둔다. 화면 전용 스타일이나 스크립트가 필요하면 기능별 파일을 추가하되 공통 파일에 특정 화면 로직을 넣지 않는다.

## 디자인 토큰

색·모서리·그림자의 정본은 `app.css` 최상단 `:root` 한 곳이다. 팔레트는 토스(TDS) 라이트 블루 기준이다.

**색 리터럴을 규칙 안에 쓰지 않는다.** hex·`rgb()`·`rgba()`·색 이름 전부 해당하며, 새 색이 필요하면 `:root`에 토큰을 먼저 만든다. 이 규칙이 깨지면 팔레트를 바꿔도 일부 화면만 바뀌는 상태로 돌아간다. 검사 방법:

```bash
# 주석과 :root 를 뺀 나머지에 색 리터럴이 있으면 위반 (기대값 0)
perl -0pe 's{/\*.*?\*/}{}gs' src/main/resources/static/css/app.css \
  | grep -v '^:root{' | grep -oE '#[0-9a-fA-F]{3,8}|rgba?\(' | wc -l
```

주요 토큰:

| 용도 | 토큰 |
|---|---|
| 파란 **면**(버튼·막대·말풍선·활성 항목) | `--color-primary` `#3182f6` |
| 파란 **글자**(링크·배지·범례) | `--color-info` `#1b64da` |
| hover / pressed | `--color-primary-strong` |
| 옅은 파란 배경(hover 면·선택 행) | `--color-primary-soft` |
| 본문 / 보조 / 흐린 글자 | `--color-text` `--color-text-sub` `--color-muted` |
| 카드 배경 / 테두리 | `--color-surface` `--color-border-light` |
| 상태색 | `--color-danger` `--color-success` `--color-warning` (+ `-soft` 배경, `-border` 컨테이너 테두리, `-border-strong` 컨트롤 테두리) |
| 차트 시리즈 | `--color-chart-1` `--color-chart-2` `--color-chart-grid` |
| 모서리 | `--radius-xs/-sm/-md/-lg/-pill` |
| 그림자 | `--shadow-xs/-sm/-md/-lg` `--shadow-focus` |
| 컨트롤 높이 | `--control-height`(버튼 36px) `--control-height-lg`(입력 40px) |

지켜야 할 세 가지:

- **파란 글자에는 `--color-primary`를 쓰지 않는다.** `#3182f6`은 흰 배경에서 명도대비 3.71:1이라 WCAG AA(4.5:1)에 미달한다. 글자는 `--color-info`(5.41:1), 면은 `--color-primary`.
- **`--color-dark`는 역사적 이름이고 실제 의미는 primary(파란 면)다.** 외부 참조가 있어 이름만 남겨 둔 것이니 "어두운 색"이 필요해서 쓰면 안 된다.
- **카드에서 테두리를 없앨 때는 `border:1px solid transparent`로 둔다.** `--danger`/`--success`/`--dashed` 같은 변형자가 `border-color`만 지정하므로, `border:0`으로 지우면 변형자가 통째로 사라진다.

차트는 Java(`TrendChartView`·`BarItemView`)가 좌표와 수치만 넘기고 색은 CSS 클래스가 정한다. 시리즈를 색으로만 구분하지 말고 점선 등 색 외 수단을 함께 준다.

`static/css/customer-mockup.css`는 `app.css`의 부분집합이면서 `:root`를 재선언하는 생성 파일이다. **어떤 템플릿에서도 링크하지 않는다**(테스트가 강제한다). 링크하면 app.css 뒤에 로드되어 팔레트가 통째로 되돌아간다.

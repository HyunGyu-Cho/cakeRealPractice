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

공통 스타일은 `static/css/app.css`, 공통 스크립트는 `static/js/app.js`에만 둔다. 화면 전용 스타일이나 스크립트가 필요하면 기능별 파일을 추가하되 공통 파일에 특정 화면 로직을 넣지 않는다.

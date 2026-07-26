/* 관리자 목업 상호작용: cakeProjectSample/js/common.js(범용) + admin.js(상태 토글) 이식.
   사이드바 활성 표시는 서버사이드(th:classappend)가 담당하므로 네비게이션 처리는 제외한다. */
(function () {
  "use strict";

  // 필터 버튼 그룹: 클릭한 버튼만 활성 표시로 바꾼다.
  function selectButton(button) {
    const group = button.closest("[data-select-group]");
    if (!group) return;
    group.querySelectorAll("button").forEach(function (item) {
      item.classList.remove("is-active");
      item.setAttribute("aria-pressed", "false");
    });
    button.classList.add("is-active");
    button.setAttribute("aria-pressed", "true");
  }

  // 목업 폼: 필수값 검증 후 successUrl 이동 또는 successMessage 알림.
  function validateForm(form) {
    let valid = true;
    form.querySelectorAll("[required]").forEach(function (field) {
      const group = field.closest(".form-group") || field.parentElement;
      let error = group.querySelector(".form-error[data-generated]");
      if (!field.checkValidity()) {
        valid = false;
        if (!error) {
          error = document.createElement("div");
          error.className = "form-error";
          error.dataset.generated = "true";
          group.appendChild(error);
        }
        error.textContent = "필수 입력값을 확인해 주세요.";
      } else if (error) {
        error.remove();
      }
    });
    return valid;
  }

  function swapAttr(el, curName, altName) {
    const cur = el.getAttribute(curName) || "";
    const alt = el.getAttribute(altName) || "";
    el.setAttribute(curName, alt);
    el.setAttribute(altName, cur);
  }

  // 토글 버튼: 클릭 후 자기 자신을 반대 상태(라벨·동작·색상·확인문구)로 교체.
  // data-alt-* 속성이 없는 단방향 버튼은 그대로 둔다.
  function toggleButton(button) {
    if (button.dataset.altLabel === undefined) return;
    const label = button.textContent;
    button.textContent = button.dataset.altLabel;
    button.dataset.altLabel = label;
    swapAttr(button, "data-set-status", "data-alt-set-status");
    swapAttr(button, "data-status-class", "data-alt-status-class");
    swapAttr(button, "data-confirm", "data-alt-confirm");
    swapAttr(button, "class", "data-alt-class");
  }

  document.addEventListener("click", function (event) {
    const button = event.target.closest("button");
    if (!button) return;

    if (button.matches("[data-select]")) selectButton(button);

    // [data-confirm] 확인창은 먼저 로드되는 app.js가 띄운다(확인창 중복 방지).
    // 거기서 취소하면 preventDefault가 걸리므로 이후 목업 상태 변경까지 중단한다.
    if (event.defaultPrevented) return;

    const status = button.closest("[data-set-status]");
    if (status) {
      const scope = status.closest("tr") || status.closest(".panel") || status.closest(".admin-action-panel");
      const badge = scope && scope.querySelector("[data-status-badge]");
      if (badge) {
        badge.textContent = status.dataset.setStatus;
        badge.className = status.dataset.statusClass || "badge badge--info";
      }
      window.alert("목업 상태가 ‘" + status.dataset.setStatus + "’(으)로 변경되었습니다.");
      toggleButton(status);
    }
  });

  document.addEventListener("submit", function (event) {
    const form = event.target;
    if (!form.matches("[data-mock-form]")) return;
    event.preventDefault();
    if (validateForm(form)) {
      const destination = form.dataset.successUrl;
      if (destination) location.href = destination;
      else window.alert(form.dataset.successMessage || "목업 상태로 처리되었습니다.");
    }
  });
})();

/** 패널 탭: [data-panel-tabs] 안의 버튼이 data-panel-target으로 가리키는 패널만 남긴다.
 *  서버가 이미 모든 패널을 렌더한 뒤의 표시 전환일 뿐이라 데이터를 다시 불러오지 않는다. */
function activatePanelTab(button) {
  const group = button.closest("[data-panel-tabs]");
  if (!group) return;
  group.querySelectorAll("[data-panel-target]").forEach((tab) => {
    const active = tab === button;
    tab.classList.toggle("is-active", active);
    tab.setAttribute("aria-pressed", String(active));
    const panel = document.getElementById(tab.dataset.panelTarget);
    if (panel) panel.hidden = !active;
  });
}

/** 모달: [data-modal-open]이 가리키는 id를 열고 [data-modal-close]가 닫는다. 서버 렌더된 마크업의 표시 전환일 뿐이다. */
function openModal(id) {
  const modal = document.getElementById(id);
  if (!modal) return;
  modal.hidden = false;
  const focusable = modal.querySelector("button,input,a");
  if (focusable) focusable.focus();
}

/** 전체 동의: 같은 폼 안의 나머지 체크박스를 [data-check-all] 상태에 맞춘다. */
function syncCheckAll(source) {
  const form = source.closest("form");
  if (!form) return;
  form.querySelectorAll('input[type="checkbox"]:not([data-check-all])').forEach((box) => {
    box.checked = source.checked;
  });
}

document.addEventListener("click", (event) => {
  const tab = event.target.closest("[data-panel-target]");
  if (tab) activatePanelTab(tab);

  const button = event.target.closest("button");
  if (!button) return;
  if (button.dataset.modalOpen) openModal(button.dataset.modalOpen);
  if (button.matches("[data-modal-close]")) {
    const modal = button.closest(".modal");
    if (modal) modal.hidden = true;
  }
  // 되돌릴 수 없는 동작(탈퇴 등)의 확인창. 취소하면 제출 자체를 막는다.
  // admin-mockup.js에도 같은 핸들러가 있어, 먼저 도는 쪽만 띄우도록 이벤트에 표시를 남긴다
  // (관리자 화면은 두 스크립트를 함께 로드하고 순서도 화면마다 다르다).
  if (button.dataset.confirm && !event.cakeshopConfirmHandled) {
    event.cakeshopConfirmHandled = true;
    if (!window.confirm(button.dataset.confirm)) event.preventDefault();
  }
});

document.addEventListener("change", (event) => {
  if (event.target.matches("[data-check-all]")) syncCheckAll(event.target);
});

/** 되돌릴 수 없는 제출 확인. 버튼이 아니라 폼에 붙일 때 쓴다. */
document.addEventListener("submit", (event) => {
  const message = event.target.dataset && event.target.dataset.confirmSubmit;
  if (message && !window.confirm(message)) event.preventDefault();
});

document.addEventListener("DOMContentLoaded", () => {
  const currentYear = String(new Date().getFullYear());
  document.querySelectorAll("[data-current-year]").forEach((element) => {
    element.textContent = currentYear;
  });
});

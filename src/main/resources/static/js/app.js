function updateMockCartCount(items) {
  let cartItems = items;
  if (!Array.isArray(cartItems)) {
    try {
      cartItems = JSON.parse(localStorage.getItem("cakeShopCart") || "[]");
    } catch (error) {
      cartItems = [];
    }
  }

  const count = Array.isArray(cartItems)
    ? cartItems.reduce((sum, item) => sum + Math.max(0, Number(item && item.quantity) || 0), 0)
    : 0;
  document.querySelectorAll("[data-cart-count]").forEach((element) => {
    element.textContent = `장바구니 (${count})`;
  });
}

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

document.addEventListener("click", (event) => {
  const tab = event.target.closest("[data-panel-target]");
  if (tab) activatePanelTab(tab);
});

document.addEventListener("DOMContentLoaded", () => {
  const currentYear = String(new Date().getFullYear());
  document.querySelectorAll("[data-current-year]").forEach((element) => {
    element.textContent = currentYear;
  });
  updateMockCartCount();
});

document.addEventListener("cart:updated", (event) => updateMockCartCount(event.detail));
window.addEventListener("storage", (event) => {
  if (event.key === "cakeShopCart") updateMockCartCount();
});

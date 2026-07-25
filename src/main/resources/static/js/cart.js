(function () {
  "use strict";

  document.addEventListener("DOMContentLoaded", function () {
    const root = document.querySelector("[data-cart-root]");
    if (!root) return;

    const checkAll = root.querySelector("[data-cart-check-all]");
    const checks = Array.from(root.querySelectorAll("[data-cart-item-check]:not(:disabled)"));
    const selectionLabel = root.querySelector("[data-cart-selection-label]");
    const selectedCount = root.querySelector("[data-cart-selected-count]");
    const total = root.querySelector("[data-cart-total]");
    const orderButton = root.querySelector("[data-cart-order]");

    function renderSelection() {
      const selected = checks.filter(function (check) { return check.checked; });
      const amount = selected.reduce(function (sum, check) {
        const item = check.closest("[data-cart-item]");
        return sum + Number(item.dataset.unitPrice) * Number(item.dataset.quantity);
      }, 0);
      const label = selected.length + "개";

      selectionLabel.textContent = "전체 선택 (" + label + ")";
      selectedCount.textContent = label;
      total.textContent = amount.toLocaleString("ko-KR") + "원";
      orderButton.textContent = "선택 상품 주문하기 (" + label + ")";
      orderButton.disabled = selected.length === 0;
      checkAll.checked = checks.length > 0 && selected.length === checks.length;
      checkAll.indeterminate = selected.length > 0 && selected.length < checks.length;
    }

    checkAll.addEventListener("change", function () {
      checks.forEach(function (check) { check.checked = checkAll.checked; });
      renderSelection();
    });
    checks.forEach(function (check) {
      check.addEventListener("change", renderSelection);
    });
    renderSelection();
  });
})();

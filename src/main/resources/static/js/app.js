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

/* source: cakeProjectSample/js/common.js */
(function () {
  "use strict";
  function markNavigation() {
    const current = location.pathname.replace(/\\/g, "/");
    document.querySelectorAll("a[href]").forEach(function (link) {
      const target = new URL(link.href, location.href).pathname;
      if (target === current) link.setAttribute("aria-current", "page");
    });
    document.querySelectorAll(".admin-sidebar__link").forEach(function (link) {
      if (new URL(link.href, location.href).pathname === current) link.classList.add("is-current");
    });
    const title = document.body.dataset.pageTitle;
    const titleNode = document.querySelector("[data-current-title]");
    if (title && titleNode) titleNode.textContent = title + " 관리";
  }
  function selectButton(button) {
    const group = button.closest("[data-select-group]");
    if (!group) return;
    group.querySelectorAll("button").forEach(function (item) { item.classList.remove("is-active"); item.setAttribute("aria-pressed", "false"); });
    button.classList.add("is-active");
    button.setAttribute("aria-pressed", "true");
  }
  function changeQuantity(button) {
    const wrap = button.closest("[data-quantity]");
    const output = wrap && wrap.querySelector("[data-quantity-value]");
    if (!output) return;
    const min = Number(wrap.dataset.min || 1);
    const max = Number(wrap.dataset.max || 99);
    const next = Math.min(max, Math.max(min, Number(output.textContent) + Number(button.dataset.quantityChange)));
    output.textContent = String(next);
  }
  function activateTab(button) {
    const tabs = button.closest("[data-tabs]");
    if (!tabs) return;
    const id = button.dataset.tab;
    tabs.querySelectorAll("[data-tab]").forEach(function (tab) { tab.classList.toggle("is-active", tab === button); tab.setAttribute("aria-selected", String(tab === button)); });
    tabs.querySelectorAll("[data-tab-panel]").forEach(function (panel) { panel.hidden = panel.dataset.tabPanel !== id; });
  }
  function openModal(id) { const modal = document.getElementById(id); if (modal) { modal.hidden = false; const focusable = modal.querySelector("button,input,a"); if (focusable) focusable.focus(); } }
  function closeModal(button) { const modal = button.closest(".modal"); if (modal) modal.hidden = true; }
  function validateForm(form) {
    let valid = true;
    form.querySelectorAll("[required]").forEach(function (field) {
      const group = field.closest(".form-group") || field.parentElement;
      let error = group.querySelector(".form-error[data-generated]");
      if (!field.checkValidity()) {
        valid = false;
        if (!error) { error = document.createElement("div"); error.className = "form-error"; error.dataset.generated = "true"; group.appendChild(error); }
        error.textContent = "필수 입력값을 확인해 주세요.";
      } else if (error) error.remove();
    });
    return valid;
  }
  document.addEventListener("click", function (event) {
    const button = event.target.closest("button");
    if (!button) return;
    if (button.matches("[data-select]")) selectButton(button);
    if (button.matches("[data-quantity-change]")) changeQuantity(button);
    if (button.matches("[data-tab]")) activateTab(button);
    if (button.dataset.modalOpen) openModal(button.dataset.modalOpen);
    if (button.matches("[data-modal-close]")) closeModal(button);
    if (button.dataset.confirm && !window.confirm(button.dataset.confirm)) event.preventDefault();
  });
  document.addEventListener("submit", function (event) {
    const form = event.target;
    if (!form.matches("[data-mock-form]")) return;
    event.preventDefault();
    if (validateForm(form)) {
      const destination = form.dataset.successUrl;
      if (destination) location.href = destination; else window.alert(form.dataset.successMessage || "목업 상태로 처리되었습니다.");
    }
  });
  document.addEventListener("includes:loaded", markNavigation);
  document.addEventListener("DOMContentLoaded", markNavigation);
})();

/* source: cakeProjectSample/js/customer.js */
(function () {
  "use strict";
  const CART_KEY = "cakeShopCart";
  const PENDING_KEY = "cakeShopPendingProduct";
  const PICKUP_TARGET_KEY = "cakeShopCartPickupTarget";

  function readCart() {
    try {
      const value = JSON.parse(localStorage.getItem(CART_KEY) || "[]");
      return Array.isArray(value) ? value.filter(function (item) { return item && item.id && item.name; }) : [];
    } catch (error) {
      return [];
    }
  }

  function writeCart(items) {
    try { localStorage.setItem(CART_KEY, JSON.stringify(items)); } catch (error) { /* 저장소를 사용할 수 없는 환경에서는 화면만 유지 */ }
    document.dispatchEvent(new CustomEvent("cart:updated", { detail: items }));
  }

  function addCartItem(item) {
    const items = readCart();
    const same = items.find(function (current) { return current.id === item.id; });
    if (same) same.quantity = Math.min(Number(same.stock || 99), Number(same.quantity || 0) + Number(item.quantity || 1));
    else items.push(item);
    writeCart(items);
  }

  function cartQuantity(items) {
    return items.reduce(function (sum, item) { return sum + Math.max(0, Number(item.quantity) || 0); }, 0);
  }

  function updateCartCount(items) {
    const count = cartQuantity(items || readCart());
    document.querySelectorAll("[data-cart-count]").forEach(function (link) { link.textContent = "장바구니 (" + count + ")"; });
  }

  function readPendingProduct() {
    try { return JSON.parse(sessionStorage.getItem(PENDING_KEY) || "null"); } catch (error) { return null; }
  }

  function writePendingProduct(product) {
    try { sessionStorage.setItem(PENDING_KEY, JSON.stringify(product)); } catch (error) { /* 세션 저장소 미지원 */ }
  }

  function prepareProduct() {
    const output = document.querySelector("[data-quantity-value]");
    try { sessionStorage.removeItem(PICKUP_TARGET_KEY); } catch (error) { /* 세션 저장소 미지원 */ }
    writePendingProduct({ productId: "cake-strawberry-cream", name: "딸기 생크림 케이크", type: "일반 케이크", basePrice: 35000, optionPrice: 0, quantity: Number(output && output.textContent) || 1, stock: 12 });
  }

  function addProductFromDetail() {
    const output = document.querySelector("[data-quantity-value]");
    addCartItem({
      id: "cake-strawberry-cream|pickup-pending",
      productId: "cake-strawberry-cream",
      name: "딸기 생크림 케이크",
      type: "일반 케이크",
      basePrice: 35000,
      optionPrice: 0,
      quantity: Number(output && output.textContent) || 1,
      stock: 12,
      pickupDate: "",
      pickupTime: "",
      available: true
    });
    location.href = "/cart";
  }

  function pickupTarget() {
    try { return sessionStorage.getItem(PICKUP_TARGET_KEY) || ""; } catch (error) { return ""; }
  }

  function initializePickup() {
    if (!document.querySelector("[data-add-normal-cart]")) return;
    const targetId = pickupTarget();
    const targetItem = targetId ? readCart().find(function (item) { return item.id === targetId; }) : null;
    const pending = targetItem || readPendingProduct();
    if (!pending) return;
    const output = document.querySelector("[data-quantity-value]");
    if (output) output.textContent = String(pending.quantity || 1);
    if (targetItem) {
      writePendingProduct(targetItem);
      const addButton = document.querySelector("[data-add-normal-cart]");
      const orderLink = document.querySelector("[data-pickup-order-link]");
      if (addButton) addButton.textContent = "픽업 일시 저장";
      if (orderLink) orderLink.hidden = true;
      const date = document.getElementById("pickup-date");
      if (date && targetItem.pickupDate) date.value = targetItem.pickupDate;
      if (targetItem.pickupTime) {
        document.querySelectorAll("[data-select-group] [data-select]").forEach(function (button) {
          const active = button.textContent.trim() === targetItem.pickupTime;
          button.classList.toggle("is-active", active);
          button.setAttribute("aria-pressed", String(active));
        });
      }
    }
    updatePickupPrice();
  }

  function updatePickupPrice() {
    const output = document.querySelector("[data-quantity-value]");
    const quantity = Number(output && output.textContent) || 1;
    const pending = readPendingProduct() || { basePrice: 35000 };
    const formatted = Number(pending.basePrice || 35000).toLocaleString("ko-KR") + "원";
    const total = (Number(pending.basePrice || 35000) * quantity).toLocaleString("ko-KR") + "원";
    const priceNode = document.querySelector("[data-pickup-price]");
    const totalNode = document.querySelector("[data-pickup-total]");
    if (priceNode) priceNode.textContent = formatted + " × " + quantity + "개";
    if (totalNode) totalNode.textContent = total;
  }

  function showPickupError(message) {
    const node = document.querySelector("[data-pickup-error]");
    if (!node) return;
    node.textContent = message;
    node.hidden = !message;
  }

  function addNormalProduct() {
    const date = document.getElementById("pickup-date");
    const time = document.querySelector("[data-select-group] [data-select].is-active");
    if (!date || !date.value || !time) {
      showPickupError("픽업 날짜와 시간을 모두 선택해 주세요.");
      return;
    }
    const pending = readPendingProduct() || { productId: "cake-strawberry-cream", name: "딸기 생크림 케이크", type: "일반 케이크", basePrice: 35000, optionPrice: 0, quantity: 1, stock: 12 };
    const output = document.querySelector("[data-quantity-value]");
    const pickup = date.value + " " + time.textContent.trim();
    const item = Object.assign({}, pending, {
      id: pending.productId + "|" + pickup,
      quantity: Number(output && output.textContent) || pending.quantity || 1,
      pickupDate: date.value,
      pickupTime: time.textContent.trim(),
      available: true
    });
    const targetId = pickupTarget();
    if (targetId) {
      const items = readCart();
      const targetIndex = items.findIndex(function (current) { return current.id === targetId; });
      const sameIndex = items.findIndex(function (current) { return current.id === item.id && current.id !== targetId; });
      if (targetIndex !== -1 && sameIndex !== -1) {
        items[sameIndex].quantity = Math.min(Number(items[sameIndex].stock || 99), Number(items[sameIndex].quantity || 0) + Number(item.quantity || 1));
        items.splice(targetIndex, 1);
      } else if (targetIndex !== -1) {
        items[targetIndex] = item;
      } else {
        items.push(item);
      }
      writeCart(items);
      try { sessionStorage.removeItem(PICKUP_TARGET_KEY); } catch (error) { /* 세션 저장소 미지원 */ }
    } else {
      addCartItem(item);
    }
    showPickupError("");
    location.href = "/cart";
  }

  function optionFrom(form, name) {
    const input = form.querySelector('input[name="' + name + '"]:checked');
    return input ? { name: name, label: input.value, price: Number(input.dataset.optionPrice || 0) } : null;
  }

  function initializeCustomOption() {
    const form = document.querySelector("[data-add-custom-cart]") && document.querySelector("[data-add-custom-cart]").closest("form");
    const targetId = pickupTarget();
    const targetItem = targetId ? readCart().find(function (item) { return item.id === targetId && item.type === "주문 제작"; }) : null;
    if (!form || !targetItem) return;
    (targetItem.options || []).forEach(function (option) {
      form.querySelectorAll('input[name="' + option.name + '"]').forEach(function (input) { input.checked = input.value === option.label; });
    });
    form.querySelector("#lettering").value = targetItem.lettering || "";
    form.querySelector("#custom-note").value = targetItem.request || "";
    form.querySelector("#custom-date").value = targetItem.pickupDate || "";
    form.querySelector("#custom-time").value = targetItem.pickupTime || "";
    const addButton = form.querySelector("[data-add-custom-cart]");
    const requestButton = form.querySelector("[data-custom-request-button]");
    if (addButton) addButton.textContent = "장바구니 정보 저장";
    if (requestButton) requestButton.hidden = true;
  }

  function escapeHtml(value) {
    return String(value == null ? "" : value).replace(/[&<>'"]/g, function (char) { return { "&": "&amp;", "<": "&lt;", ">": "&gt;", "'": "&#39;", '"': "&quot;" }[char]; });
  }

  function initializeOrderForm() {
    const container = document.querySelector("[data-order-items]");
    if (!container || new URLSearchParams(location.search).get("source") !== "cart") return;
    let items;
    try { items = JSON.parse(localStorage.getItem("cakeShopCheckoutItems") || "[]"); } catch (error) { items = []; }
    if (!Array.isArray(items) || !items.length) return;
    container.innerHTML = items.map(function (item) {
      const itemTotal = (Number(item.basePrice || 0) + Number(item.optionPrice || 0)) * Number(item.quantity || 1);
      return '<div class="panel cluster"><div class="placeholder-image placeholder-image--sm">상품 이미지</div><div><strong>' + escapeHtml(item.name) + '</strong><p class="text-muted">' + escapeHtml(item.type) + " | " + Number(item.quantity || 1) + "개 | " + itemTotal.toLocaleString("ko-KR") + '원</p><div class="data-row"><span class="data-row__label">픽업 일시</span><span>' + escapeHtml([item.pickupDate, item.pickupTime].filter(Boolean).join(" ")) + "</span></div></div></div>";
    }).join("");
    const base = items.reduce(function (sum, item) { return sum + Number(item.basePrice || 0) * Number(item.quantity || 1); }, 0);
    const options = items.reduce(function (sum, item) { return sum + Number(item.optionPrice || 0) * Number(item.quantity || 1); }, 0);
    const baseNode = document.querySelector("[data-order-base]");
    const optionNode = document.querySelector("[data-order-options]");
    const totalNode = document.querySelector("[data-total]");
    if (baseNode) baseNode.textContent = base.toLocaleString("ko-KR") + "원";
    if (optionNode) optionNode.textContent = options ? "+" + options.toLocaleString("ko-KR") + "원" : "0원";
    if (totalNode) { totalNode.dataset.orderTotal = String(base + options); totalNode.textContent = (base + options).toLocaleString("ko-KR") + "원"; }
  }

  function addCustomProduct(button) {
    const form = button.closest("form");
    if (!form) return;
    const size = optionFrom(form, "size");
    const flavor = optionFrom(form, "flavor");
    const color = optionFrom(form, "color");
    if (!size || !flavor || !color) {
      const missing = !size ? "size" : (!flavor ? "flavor" : "color");
      const field = form.querySelector('input[name="' + missing + '"]');
      if (field) field.reportValidity();
      return;
    }
    const lettering = form.querySelector("#lettering").value.trim();
    const request = form.querySelector("#custom-note").value.trim();
    const date = form.querySelector("#custom-date").value;
    const time = form.querySelector("#custom-time").value;
    if (Boolean(date) !== Boolean(time)) {
      const missingPickup = date ? form.querySelector("#custom-time") : form.querySelector("#custom-date");
      if (missingPickup) missingPickup.reportValidity();
      return;
    }
    const options = [size, flavor, color];
    const optionPrice = options.reduce(function (sum, option) { return sum + Number(option.price || 0); }, 0);
    const identity = [size.label, flavor.label, color.label, lettering, request].join("|");
    const requestedTargetId = pickupTarget();
    const target = requestedTargetId ? readCart().find(function (item) { return item.id === requestedTargetId && item.type === "주문 제작"; }) : null;
    const targetId = target ? requestedTargetId : "";
    const pickupIdentity = date && time ? date + " " + time : "pickup-pending";
    const item = {
      id: "cake-custom-lettering|" + pickupIdentity + "|" + identity,
      productId: "cake-custom-lettering",
      name: "레터링 주문 케이크",
      type: "주문 제작",
      basePrice: 55000,
      optionPrice: optionPrice,
      quantity: Number(target && target.quantity) || 1,
      stock: 99,
      pickupDate: date,
      pickupTime: time,
      available: true,
      options: options,
      lettering: lettering,
      request: request
    };
    if (targetId) {
      const items = readCart();
      const targetIndex = items.findIndex(function (current) { return current.id === targetId; });
      const sameIndex = items.findIndex(function (current) { return current.id === item.id && current.id !== targetId; });
      if (targetIndex !== -1 && sameIndex !== -1) {
        items[sameIndex].quantity = Math.min(Number(items[sameIndex].stock || 99), Number(items[sameIndex].quantity || 0) + Number(item.quantity || 1));
        items.splice(targetIndex, 1);
      } else if (targetIndex !== -1) {
        items[targetIndex] = item;
      } else {
        items.push(item);
      }
      writeCart(items);
      try { sessionStorage.removeItem(PICKUP_TARGET_KEY); } catch (error) { /* 세션 저장소 미지원 */ }
    } else {
      addCartItem(item);
    }
    location.href = "/cart";
  }

  window.CakeCart = { read: readCart, write: writeCart, add: addCartItem, updateCount: updateCartCount };

  document.addEventListener("click", function (event) {
    const prepare = event.target.closest("[data-prepare-product]");
    if (prepare) prepareProduct();
    const addProduct = event.target.closest("[data-add-product-cart]");
    if (addProduct) addProductFromDetail();
    const addNormal = event.target.closest("[data-add-normal-cart]");
    if (addNormal) addNormalProduct();
    const addCustom = event.target.closest("[data-add-custom-cart]");
    if (addCustom) addCustomProduct(addCustom);
    if (event.target.closest("[data-quantity-change]") && document.querySelector("[data-add-normal-cart]")) updatePickupPrice();
    const coupon = event.target.closest("[data-coupon-select]");
    if (coupon) {
      document.querySelectorAll("[data-coupon-select]").forEach(function (item) { item.classList.remove("is-active"); item.textContent = "사용"; });
      coupon.classList.add("is-active"); coupon.textContent = "선택됨";
      const discount = document.querySelector("[data-discount]"); if (discount) discount.textContent = "-5,000원";
      const total = document.querySelector("[data-total]"); if (total) total.textContent = Math.max(0, Number(total.dataset.orderTotal || 35000) - 5000).toLocaleString("ko-KR") + "원";
    }
  });
  document.addEventListener("change", function (event) {
    if (!event.target.matches("[data-check-all]")) return;
    const form = event.target.closest("form");
    if (form) form.querySelectorAll('input[type="checkbox"]:not([data-check-all])').forEach(function (box) { box.checked = event.target.checked; });
  });
  document.addEventListener("cart:updated", function (event) { updateCartCount(event.detail); });
  document.addEventListener("includes:loaded", function () { updateCartCount(); initializePickup(); initializeCustomOption(); initializeOrderForm(); });
  document.addEventListener("DOMContentLoaded", function () { updateCartCount(); initializePickup(); initializeCustomOption(); initializeOrderForm(); });
})();

/* source: cakeProjectSample/js/cart.js */
(function () {
  "use strict";
  const root = document.querySelector("[data-cart-root]");
  if (!root || !window.CakeCart) return;
  let selected = new Set();

  function money(value) { return Number(value || 0).toLocaleString("ko-KR") + "원"; }
  function escapeHtml(value) {
    return String(value == null ? "" : value).replace(/[&<>'"]/g, function (char) { return { "&": "&amp;", "<": "&lt;", ">": "&gt;", "'": "&#39;", '"': "&quot;" }[char]; });
  }
  function available(item) { return item.available !== false && item.status !== "soldout" && item.status !== "stopped"; }
  function unitPrice(item) { return Number(item.basePrice || 0) + Number(item.optionPrice || 0); }
  function itemPickup(item) { return item.pickupDate && item.pickupTime ? item.pickupDate + " " + item.pickupTime : ""; }
  function optionsMarkup(item) {
    if (!Array.isArray(item.options) || !item.options.length) return "";
    const rows = item.options.map(function (option) {
      return "<div>" + escapeHtml(option.label) + (option.price ? " +" + money(option.price) : "") + "</div>";
    }).join("");
    const lettering = item.lettering ? "<div>레터링: " + escapeHtml(item.lettering) + "</div>" : "";
    return '<div class="cart-options"><strong>선택 옵션 요약</strong>' + rows + lettering + '<div class="cart-options__price">옵션 추가 금액: +' + money(item.optionPrice) + "</div></div>";
  }
  function stateMarkup(item) {
    const messages = [];
    if (item.status === "soldout") messages.push("품절된 상품입니다. 주문이 불가합니다.");
    if (item.status === "stopped") messages.push("판매가 중지된 상품입니다.");
    if (Number(item.quantity) > Number(item.stock || 99)) messages.push("수량이 재고보다 많습니다. (재고: " + Number(item.stock) + "개)");
    if (item.pickupClosed) messages.push("선택한 픽업 일시가 마감되었습니다.");
    if (item.priceChanged) messages.push("가격 또는 옵션 금액이 변경되어 최신 금액으로 업데이트되었습니다.");
    const notices = messages.map(function (message) { return '<p class="form-error cart-item__error">⚠ ' + escapeHtml(message) + "</p>"; }).join("");
    const missingPickup = !itemPickup(item);
    const pickupAction = item.pickupClosed || missingPickup;
    const pickupLabel = item.pickupClosed ? "픽업 일시 변경" : "픽업 일시 선택";
    const pickupButton = pickupAction ? '<button class="btn cart-item__state-action" type="button" ' + (item.type === "주문 제작" ? "data-cart-custom-edit" : "data-cart-pickup") + ">" + pickupLabel + "</button>" : "";
    const missingNotice = missingPickup ? '<p class="form-error cart-item__error">⚠ 픽업 일시를 선택해 주세요.</p>' : "";
    return notices + missingNotice + pickupButton;
  }
  function itemMarkup(item) {
    const disabled = !available(item);
    const checked = selected.has(item.id) && !disabled;
    const typeClass = item.type === "주문 제작" ? "" : " badge--info";
    const status = disabled ? (item.status === "stopped" ? "판매 중지" : "품절") : (item.type === "주문 제작" ? "주문 가능" : "재고 있음");
    const statusClass = disabled ? "badge--danger" : "badge--success";
    const controls = item.type === "주문 제작"
      ? '<button class="btn" type="button" data-cart-custom-edit aria-label="' + escapeHtml(item.name) + ' 옵션 수정">옵션 수정</button>'
      : '<div class="quantity-control"><button class="btn" type="button" data-cart-quantity="-1" aria-label="' + escapeHtml(item.name) + ' 수량 감소">−</button><output class="panel quantity-control__value">' + Number(item.quantity) + '</output><button class="btn" type="button" data-cart-quantity="1" aria-label="' + escapeHtml(item.name) + ' 수량 증가">+</button></div>';
    return '<article class="panel cart-item' + (disabled ? " is-disabled" : "") + '" data-cart-id="' + escapeHtml(item.id) + '">' +
      '<label class="cart-item__check"><input type="checkbox" data-cart-check' + (checked ? " checked" : "") + (disabled ? " disabled" : "") + '><span class="visually-hidden">' + escapeHtml(item.name) + " 선택</span></label>" +
      '<a class="placeholder-image placeholder-image--sm cart-item__image" href="/products/1" aria-label="' + escapeHtml(item.name) + ' 상세 보기">상품 이미지</a>' +
      '<div class="cart-item__body"><div class="cluster cluster--between"><div><a class="cart-item__name" href="/products/1">' + escapeHtml(item.name) + '</a><div class="cluster cart-item__badges"><span class="badge' + typeClass + '">' + escapeHtml(item.type) + '</span><span class="badge ' + statusClass + '">' + status + '</span></div></div><button class="btn btn--danger" type="button" data-cart-delete aria-label="' + escapeHtml(item.name) + ' 삭제">삭제</button></div>' +
      '<p class="text-muted">기본 가격: ' + money(item.basePrice) + '</p>' + optionsMarkup(item) + '<p class="text-muted">픽업 일시: ' + (itemPickup(item) ? escapeHtml(itemPickup(item)) : '<span class="badge badge--warning">미선택</span>') + '</p>' + stateMarkup(item) +
      '<div class="cluster cart-item__bottom">' + controls + '<strong class="cart-item__price">' + money(unitPrice(item) * Number(item.quantity)) + "</strong></div></div></article>";
  }

  function currentItems() { return window.CakeCart.read(); }
  function selectableItems(items) { return items.filter(available); }
  function selectedItems(items) { return items.filter(function (item) { return selected.has(item.id) && available(item); }); }
  function updateSummary(items) {
    const chosen = selectedItems(items);
    const quantity = chosen.reduce(function (sum, item) { return sum + Number(item.quantity); }, 0);
    const base = chosen.reduce(function (sum, item) { return sum + Number(item.basePrice) * Number(item.quantity); }, 0);
    const options = chosen.reduce(function (sum, item) { return sum + Number(item.optionPrice || 0) * Number(item.quantity); }, 0);
    root.querySelector("[data-cart-selected-count]").textContent = quantity + "개";
    root.querySelector("[data-cart-base-total]").textContent = money(base);
    root.querySelector("[data-cart-option-total]").textContent = options ? "+" + money(options) : "0원";
    root.querySelector("[data-cart-total]").textContent = money(base + options);
    root.querySelector("[data-cart-order]").textContent = "선택 상품 주문하기 (" + quantity + "개)";
    root.querySelector("[data-cart-selection-label]").textContent = "전체 선택 (" + selectableItems(items).length + "개)";
    const all = root.querySelector("[data-cart-check-all]");
    const selectable = selectableItems(items);
    all.checked = selectable.length > 0 && selectable.every(function (item) { return selected.has(item.id); });
    all.indeterminate = selected.size > 0 && !all.checked;
    const pickups = new Set(chosen.map(itemPickup).filter(Boolean));
    root.querySelector("[data-cart-pickup-warning]").hidden = pickups.size < 2;
  }
  function render() {
    const items = currentItems();
    const validIds = new Set(items.map(function (item) { return item.id; }));
    selected.forEach(function (id) { if (!validIds.has(id)) selected.delete(id); });
    root.querySelector("[data-cart-items]").innerHTML = items.map(itemMarkup).join("");
    root.querySelector("[data-cart-empty]").hidden = items.length !== 0;
    root.querySelector("[data-cart-items]").hidden = items.length === 0;
    root.querySelector(".cart-toolbar").hidden = items.length === 0;
    updateSummary(items);
  }
  function save(items) { window.CakeCart.write(items); render(); }
  function showError(message) {
    const error = root.querySelector("[data-cart-error]");
    error.textContent = message;
    error.hidden = !message;
  }

  document.addEventListener("change", function (event) {
    if (event.target.matches("[data-cart-check-all]")) {
      selected = event.target.checked ? new Set(selectableItems(currentItems()).map(function (item) { return item.id; })) : new Set();
      showError(""); render();
    }
    if (event.target.matches("[data-cart-check]")) {
      const id = event.target.closest("[data-cart-id]").dataset.cartId;
      if (event.target.checked) selected.add(id); else selected.delete(id);
      showError(""); updateSummary(currentItems());
    }
  });
  document.addEventListener("click", function (event) {
    const row = event.target.closest("[data-cart-id]");
    if (event.target.closest("[data-cart-delete]") && row) {
      save(currentItems().filter(function (item) { return item.id !== row.dataset.cartId; }));
    }
    const quantity = event.target.closest("[data-cart-quantity]");
    if (quantity && row) {
      const items = currentItems();
      const item = items.find(function (current) { return current.id === row.dataset.cartId; });
      if (item) item.quantity = Math.max(1, Math.min(Number(item.stock || 99), Number(item.quantity) + Number(quantity.dataset.cartQuantity)));
      save(items);
    }
    const pickup = event.target.closest("[data-cart-pickup]");
    if (pickup && row) {
      try { sessionStorage.setItem("cakeShopCartPickupTarget", row.dataset.cartId); } catch (error) { /* 세션 저장소 미지원 */ }
      location.href = "/orders/pickup?intent=cart-edit";
    }
    const customEdit = event.target.closest("[data-cart-custom-edit]");
    if (customEdit && row) {
      try { sessionStorage.setItem("cakeShopCartPickupTarget", row.dataset.cartId); } catch (error) { /* 세션 저장소 미지원 */ }
      location.href = "/orders/custom/options?intent=cart-edit";
    }
    if (event.target.closest("[data-cart-delete-selected]")) {
      if (!selected.size) { showError("삭제할 상품을 선택해 주세요."); return; }
      save(currentItems().filter(function (item) { return !selected.has(item.id); }));
      selected.clear();
    }
    if (event.target.closest("[data-cart-delete-all]")) {
      window.CakeCart.write([]); selected.clear(); render();
    }
    if (event.target.closest("[data-cart-order]")) {
      const chosen = selectedItems(currentItems());
      if (!chosen.length) { showError("주문할 상품을 선택해주세요."); return; }
      if (chosen.some(function (item) { return !itemPickup(item); })) { showError("픽업 일시를 선택하지 않은 상품이 있습니다."); return; }
      if (new Set(chosen.map(itemPickup)).size > 1) { showError("픽업 일시가 같은 상품끼리 선택해 주문해 주세요."); return; }
      try { localStorage.setItem("cakeShopCheckoutItems", JSON.stringify(chosen)); } catch (error) { /* 저장소 미지원 */ }
      location.href = "/orders/checkout?source=cart";
    }
  });
  document.addEventListener("cart:updated", render);
  currentItems().filter(available).forEach(function (item) { selected.add(item.id); });
  render();
})();

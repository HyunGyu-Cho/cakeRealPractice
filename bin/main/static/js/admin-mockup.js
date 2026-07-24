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

    // 확인창을 취소하면 이후 상태 변경까지 중단한다.
    if (button.dataset.confirm && !window.confirm(button.dataset.confirm)) {
      event.preventDefault();
      return;
    }

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

/* source: cakeProjectSample/js/admin/notification.js — 관리자 실시간 토스트 알림 데모(전 페이지 공통) */
(function () {
  "use strict";
  document.addEventListener("DOMContentLoaded", function () {
    function showAdminToast(message, linkUrl) {
      let container = document.querySelector(".toast-container");
      if (!container) {
        container = document.createElement("div");
        container.className = "toast-container";
        document.body.appendChild(container);
      }
      const toast = document.createElement("div");
      toast.className = "toast-item";
      toast.innerHTML = '<span>' + message + '</span><span style="font-size:11px; opacity:0.8;">[이동 ➔]</span>';
      if (linkUrl) {
        toast.addEventListener("click", function () { location.href = linkUrl; });
      }
      container.appendChild(toast);
      setTimeout(function () { toast.classList.add("is-show"); }, 50);
      setTimeout(function () {
        toast.classList.remove("is-show");
        setTimeout(function () { toast.remove(); }, 400);
      }, 4000);
    }
    // 관리자 페이지 접속 1.2초 후 토스트 알림 목업을 자동 시연한다.
    setTimeout(function () {
      showAdminToast("💬 [신규 채팅 문의] 김철수: 파란색 레터링 가능한가요?", "/admin/chat");
    }, 1200);
  });
})();

/* source: cakeProjectSample/js/admin/chat.js — 관리자 채팅방 상호작용 */
(function () {
  "use strict";
  document.addEventListener("DOMContentLoaded", function () {
    const quickSelect = document.getElementById("quickTemplateSelect");
    const chatInput = document.getElementById("adminChatInput");
    const chatForm = document.getElementById("adminChatForm");
    const chatMessages = document.getElementById("adminChatMessages");
    const imageInput = document.getElementById("adminChatImageInput");
    const imageName = document.getElementById("adminImageFileName");

    // 빠른 답변 문구를 선택하면 채팅 입력창에 자동으로 채운다.
    if (quickSelect && chatInput) {
      quickSelect.addEventListener("change", function () {
        if (this.value) {
          chatInput.value = this.value;
          chatInput.focus();
        }
      });
    }

    // 채팅 이미지 선택 시 실제 업로드 대신 선택된 파일명을 표시한다.
    if (imageInput && imageName) {
      imageInput.addEventListener("change", function () {
        imageName.textContent = this.files && this.files[0] ? this.files[0].name : "선택된 파일 없음";
      });
    }

    // 관리자 채팅 폼을 제출하면 메시지 요소를 만들어 대화창에 추가한다.
    if (chatForm && chatInput && chatMessages) {
      chatForm.addEventListener("submit", function (e) {
        e.preventDefault();
        const text = chatInput.value.trim();
        const file = imageInput && imageInput.files ? imageInput.files[0] : null;
        if (!text && !file) return;
        const now = new Date();
        const timeStr = String(now.getHours()).padStart(2, "0") + ":" + String(now.getMinutes()).padStart(2, "0");
        let imageHtml = "";
        if (file) {
          imageHtml = '<div class="placeholder-image placeholder-image--sm" style="width:140px; height:100px; margin-bottom:4px;">[' + file.name + ']</div>';
        }
        const msgDiv = document.createElement("div");
        msgDiv.className = "chat-msg chat-msg--me";
        msgDiv.innerHTML = '<div class="chat-msg__body">' + imageHtml + '<div class="chat-msg__content">' + text + '</div></div><div class="chat-msg__meta"><span class="chat-msg__read">읽음</span><span class="chat-msg__time">' + timeStr + '</span></div>';
        chatMessages.appendChild(msgDiv);
        chatInput.value = "";
        if (quickSelect) quickSelect.value = "";
        if (imageInput) imageInput.value = "";
        if (imageName) imageName.textContent = "선택된 파일 없음";
        chatMessages.scrollTop = chatMessages.scrollHeight;
      });
    }

    // 제작 승인 / 거절 처리 목업 버튼
    const btnApprove = document.getElementById("btnApproveOrder");
    const btnReject = document.getElementById("btnRejectOrder");
    const badge001 = document.getElementById("orderStatusBadge001");

    if (btnApprove && chatMessages) {
      btnApprove.addEventListener("click", function () {
        if (badge001) {
          badge001.textContent = "결제 대기";
          badge001.className = "badge badge--info";
        }
        const sysMsg = document.createElement("div");
        sysMsg.className = "chat-msg chat-msg--system";
        sysMsg.innerHTML = '<div class="chat-msg__content"><strong>[시스템] 주문(ORD-001) 제작 승인이 완료되었습니다.</strong><br>고객 대화방에 주문서 작성 및 결제 안내 버튼 메시지가 전송되었습니다.</div>';
        chatMessages.appendChild(sysMsg);
        chatMessages.scrollTop = chatMessages.scrollHeight;
        alert("ORD-001 주문 제작이 승인되었습니다!");
      });
    }

    if (btnReject && chatMessages) {
      btnReject.addEventListener("click", function () {
        if (badge001) {
          badge001.textContent = "제작 거절됨";
          badge001.className = "badge badge--danger";
        }
        const sysMsg = document.createElement("div");
        sysMsg.className = "chat-msg chat-msg--system";
        sysMsg.innerHTML = '<div class="chat-msg__content"><strong>[시스템] 주문(ORD-001) 제작이 거절되었습니다.</strong><br>사유: 일정 마감 또는 제작 불가 옵션</div>';
        chatMessages.appendChild(sysMsg);
        chatMessages.scrollTop = chatMessages.scrollHeight;
        alert("ORD-001 주문 제작이 거절 처리되었습니다.");
      });
    }

    // 우측 연동 주문 카드 클릭 시 중앙 대화창 내부 해당 위치로 스크롤 이동
    document.querySelectorAll(".admin-order-item[data-scroll-to]").forEach(function (item) {
      item.addEventListener("click", function (e) {
        if (e.target.closest("button") || e.target.closest("a")) return;
        const targetId = this.dataset.scrollTo;
        const targetElem = document.getElementById(targetId);
        if (targetElem && chatMessages) {
          const containerRect = chatMessages.getBoundingClientRect();
          const targetRect = targetElem.getBoundingClientRect();
          const relativeTop = targetRect.top - containerRect.top + chatMessages.scrollTop;
          chatMessages.scrollTo({ top: relativeTop, behavior: "smooth" });
          targetElem.style.transition = "background-color 0.5s";
          targetElem.style.backgroundColor = "#fef08a";
          setTimeout(function () { targetElem.style.backgroundColor = ""; }, 1500);
        }
      });
    });
  });
})();

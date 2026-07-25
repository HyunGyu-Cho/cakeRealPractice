(function () {
  "use strict";

  const root = document.querySelector("[data-admin-chat-root]");
  if (!root) return;

  const csrfToken = document.querySelector('meta[name="_csrf"]').content;
  const csrfHeader = document.querySelector('meta[name="_csrf_header"]').content;
  const connection = root.querySelector("[data-chat-connection]");
  const messages = root.querySelector("[data-chat-messages]");
  const form = root.querySelector("[data-chat-form]");
  const input = root.querySelector("[data-chat-input]");
  const imageInput = root.querySelector("[data-chat-image]");
  const sendButton = root.querySelector("[data-chat-send]");
  const errorBox = root.querySelector("[data-chat-error]");
  const olderButton = root.querySelector("[data-load-older]");
  const closeButton = root.querySelector("[data-close-room]");
  const statusBadge = root.querySelector("[data-room-status]");
  const preview = root.querySelector("[data-image-preview]");
  const ids = new Set(messages
    ? Array.from(messages.querySelectorAll("[data-message-id]"))
      .map((element) => Number(element.dataset.messageId))
    : []);
  let previewUrl = null;
  let connectedOnce = false;

  function roomId() {
    return root.dataset.roomId ? Number(root.dataset.roomId) : null;
  }
  function orderedIds() {
    return Array.from(ids).sort((a, b) => a - b);
  }
  function setError(message) {
    if (!errorBox) return;
    errorBox.textContent = message || "";
    errorBox.hidden = !message;
  }
  function setConnection(text, state) {
    connection.textContent = text;
    connection.classList.toggle("is-online", state === "online");
    connection.classList.toggle("is-offline", state === "offline");
  }
  async function responseError(response) {
    try {
      const body = await response.json();
      return body.message || "요청을 처리하지 못했습니다.";
    } catch (_) {
      return "요청을 처리하지 못했습니다.";
    }
  }
  function formatTime(value) {
    return new Intl.DateTimeFormat("ko-KR", {
      month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit",
      hour12: false
    }).format(new Date(value)).replace(/\./g, ".").trim();
  }

  function messageElement(message) {
    const article = document.createElement("article");
    article.className = "chat-msg";
    article.dataset.messageId = message.id;
    article.dataset.senderType = message.senderType;
    article.classList.add(message.senderType === "SYSTEM"
      ? "chat-msg--system"
      : message.senderType === "ADMIN" ? "chat-msg--me" : "chat-msg--other");
    if (message.senderType === "CUSTOMER") {
      const sender = document.createElement("span");
      sender.className = "chat-msg__sender";
      sender.textContent = "고객";
      article.appendChild(sender);
    }
    const body = document.createElement("div");
    body.className = "chat-msg__body";
    if (message.hasImage) {
      const image = document.createElement("img");
      image.className = "chat-msg__image";
      image.src = message.imageUrl;
      image.alt = "채팅 첨부 이미지";
      body.appendChild(image);
    }
    if (message.content) {
      const content = document.createElement("p");
      content.className = "chat-msg__content";
      content.textContent = message.content;
      body.appendChild(content);
    }
    if (message.actionUrl) {
      const action = document.createElement("a");
      action.className = "btn btn--primary chat-msg__action";
      action.href = message.actionUrl;
      action.textContent = "주문제작 시작하기";
      body.appendChild(action);
    }
    article.appendChild(body);
    if (message.senderType !== "SYSTEM") {
      const meta = document.createElement("div");
      meta.className = "chat-msg__meta";
      if (message.senderType === "ADMIN") {
        const read = document.createElement("span");
        read.className = "chat-msg__read";
        read.textContent = message.read ? "읽음" : "읽지 않음";
        meta.appendChild(read);
      }
      const time = document.createElement("time");
      time.textContent = formatTime(message.createdAt);
      meta.appendChild(time);
      article.appendChild(meta);
    }
    return article;
  }

  function appendMessage(message, position) {
    if (!messages || ids.has(Number(message.id))) return false;
    ids.add(Number(message.id));
    const empty = messages.querySelector("[data-chat-empty]");
    if (empty) empty.hidden = true;
    const element = messageElement(message);
    if (position === "before") {
      const oldHeight = messages.scrollHeight;
      messages.insertBefore(element, messages.querySelector("[data-message-id]"));
      messages.scrollTop += messages.scrollHeight - oldHeight;
    } else {
      messages.appendChild(element);
      messages.scrollTop = messages.scrollHeight;
    }
    return true;
  }

  async function sendMessage() {
    const content = input.value.trim();
    const file = imageInput.files[0];
    if (!content && !file) return setError("메시지 또는 이미지를 입력해 주세요.");
    sendButton.disabled = true;
    const clientMessageId = form.dataset.pendingId || crypto.randomUUID();
    form.dataset.pendingId = clientMessageId;
    const body = new FormData();
    body.append("clientMessageId", clientMessageId);
    body.append("content", content);
    if (file) body.append("image", file);
    try {
      const response = await fetch(`/admin/api/chat/rooms/${roomId()}/messages`, {
        method: "POST", headers: { [csrfHeader]: csrfToken }, body
      });
      if (!response.ok) throw new Error(await responseError(response));
      appendMessage(await response.json());
      form.dataset.pendingId = "";
      input.value = "";
      clearImage();
      setError("");
    } catch (error) {
      setError(error.message + " 같은 UUID로 다시 시도하면 중복 저장되지 않습니다.");
    } finally {
      sendButton.disabled = false;
    }
  }

  async function markRead() {
    if (!messages || !roomId()) return;
    const incoming = Array.from(messages.querySelectorAll('[data-sender-type="CUSTOMER"]'));
    if (!incoming.length) return;
    const lastId = Number(incoming[incoming.length - 1].dataset.messageId);
    if (Number(root.dataset.lastReadId || 0) >= lastId) return;
    try {
      const response = await fetch(`/admin/api/chat/rooms/${roomId()}/read`, {
        method: "POST",
        headers: { "Content-Type": "application/json", [csrfHeader]: csrfToken },
        body: JSON.stringify({ lastMessageId: lastId })
      });
      if (response.ok) root.dataset.lastReadId = String(lastId);
    } catch (_) {}
  }

  function updateRead(lastId) {
    if (!messages) return;
    messages.querySelectorAll('[data-sender-type="ADMIN"]').forEach((element) => {
      if (Number(element.dataset.messageId) <= lastId) {
        const label = element.querySelector(".chat-msg__read");
        if (label) label.textContent = "읽음";
      }
    });
  }

  async function loadOlder() {
    const first = orderedIds()[0];
    if (!first) return;
    olderButton.disabled = true;
    try {
      const response = await fetch(
        `/admin/api/chat/rooms/${roomId()}/messages?beforeId=${first}&size=50`);
      if (!response.ok) throw new Error(await responseError(response));
      const slice = await response.json();
      slice.messages.slice().reverse().forEach((message) => appendMessage(message, "before"));
      olderButton.hidden = !slice.hasMore || slice.messages.length === 0;
    } catch (error) {
      setError(error.message);
    } finally {
      olderButton.disabled = false;
    }
  }

  async function recoverMissed() {
    const ordered = orderedIds();
    let cursor = ordered[ordered.length - 1];
    if (!cursor || !roomId()) return;
    try {
      let hasMore;
      do {
        const response = await fetch(
          `/admin/api/chat/rooms/${roomId()}/messages?afterId=${cursor}&size=100`);
        if (!response.ok) return;
        const slice = await response.json();
        slice.messages.forEach((message) => appendMessage(message));
        if (slice.messages.length) {
          cursor = slice.messages[slice.messages.length - 1].id;
        }
        hasMore = slice.hasMore && slice.messages.length > 0;
      } while (hasMore);
      markRead();
    } catch (_) {}
  }

  async function closeRoom() {
    if (!confirm("이 상담을 종료할까요? 고객이 다시 메시지를 보내면 자동으로 열립니다.")) return;
    closeButton.disabled = true;
    try {
      const response = await fetch(`/admin/api/chat/rooms/${roomId()}/close`, {
        method: "POST", headers: { [csrfHeader]: csrfToken }
      });
      if (!response.ok) throw new Error(await responseError(response));
      setClosed(true);
    } catch (error) {
      setError(error.message);
      closeButton.disabled = false;
    }
  }

  function setClosed(closed) {
    if (!form) return;
    statusBadge.textContent = closed ? "상담 종료" : "상담 중";
    statusBadge.classList.toggle("badge--success", !closed);
    closeButton.disabled = closed;
    form.querySelectorAll("textarea,input,select,button").forEach((control) => {
      control.disabled = closed;
    });
    form.closest(".chat-compose").classList.toggle("is-disabled", closed);
  }

  function updateSummary(event) {
    const summary = root.querySelector(`[data-room-summary="${event.roomId}"]`);
    if (!summary || !event.message) return;
    const preview = summary.querySelector("[data-room-preview]");
    preview.textContent = event.message.messageType === "IMAGE" && !event.message.content
      ? "이미지" : event.message.content || "주문제작 안내";
    if (event.message.senderType === "CUSTOMER" && Number(event.roomId) !== roomId()) {
      const unread = summary.querySelector("[data-room-unread]");
      unread.hidden = false;
      const current = Number((unread.textContent.match(/\d+/) || [0])[0]);
      unread.textContent = `안 읽음 ${current + 1}`;
    }
  }

  function handleEvent(event) {
    if (event.type === "MESSAGE_CREATED" && event.message) {
      updateSummary(event);
      if (Number(event.roomId) === roomId()) {
        appendMessage(event.message);
        if (event.message.senderType === "CUSTOMER") markRead();
      }
    } else if (event.type === "MESSAGES_READ"
        && Number(event.roomId) === roomId() && event.lastReadMessageId) {
      updateRead(Number(event.lastReadMessageId));
    } else if (event.type === "ROOM_STATUS_CHANGED"
        && Number(event.roomId) === roomId()) {
      setClosed(event.roomStatus === "CLOSED");
    }
  }

  function connect() {
    if (!window.StompJs) return setConnection("실시간 모듈 오류", "offline");
    const scheme = location.protocol === "https:" ? "wss" : "ws";
    const client = new StompJs.Client({
      brokerURL: `${scheme}://${location.host}/ws/chat`,
      connectHeaders: { [csrfHeader]: csrfToken },
      reconnectDelay: 5000,
      heartbeatIncoming: 20000,
      heartbeatOutgoing: 10000,
      onConnect: function () {
        setConnection("실시간 연결됨", "online");
        client.subscribe("/topic/admin/chat-events", (frame) => {
          handleEvent(JSON.parse(frame.body));
        });
        if (connectedOnce) recoverMissed();
        connectedOnce = true;
      },
      onWebSocketClose: function () { setConnection("재연결 중", "offline"); },
      onStompError: function () { setConnection("재연결 중", "offline"); }
    });
    client.activate();
  }

  function clearImage() {
    if (!imageInput || !preview) return;
    imageInput.value = "";
    preview.hidden = true;
    if (previewUrl) URL.revokeObjectURL(previewUrl);
    previewUrl = null;
    preview.querySelector("img").removeAttribute("src");
  }

  if (form) {
    form.addEventListener("submit", (event) => {
      event.preventDefault();
      sendMessage();
    });
    imageInput.addEventListener("change", function () {
      const file = imageInput.files[0];
      if (!file) return clearImage();
      if (!["image/jpeg", "image/png"].includes(file.type) || file.size > 5 * 1024 * 1024) {
        setError("JPG/PNG 형식의 5MB 이하 이미지만 선택해 주세요.");
        return clearImage();
      }
      if (previewUrl) URL.revokeObjectURL(previewUrl);
      previewUrl = URL.createObjectURL(file);
      preview.querySelector("img").src = previewUrl;
      preview.querySelector("[data-image-name]").textContent = file.name;
      preview.hidden = false;
    });
    preview.querySelector("[data-image-clear]").addEventListener("click", clearImage);
    root.querySelector("[data-quick-reply]").addEventListener("change", function () {
      if (this.value) {
        input.value = this.value;
        input.focus();
        this.value = "";
      }
    });
    olderButton.addEventListener("click", loadOlder);
    closeButton.addEventListener("click", closeRoom);
    messages.scrollTop = messages.scrollHeight;
    markRead();
  }
  window.addEventListener("focus", markRead);
  connect();
})();

(function () {
  "use strict";

  const root = document.querySelector("[data-chat-root]");
  if (!root) return;

  const messages = root.querySelector("[data-chat-messages]");
  const form = root.querySelector("[data-chat-form]");
  const input = root.querySelector("[data-chat-input]");
  const imageInput = root.querySelector("[data-chat-image]");
  const sendButton = root.querySelector("[data-chat-send]");
  const olderButton = root.querySelector("[data-load-older]");
  const empty = root.querySelector("[data-chat-empty]");
  const errorBox = root.querySelector("[data-chat-error]");
  const connection = root.querySelector("[data-chat-connection]");
  const statusBadge = root.querySelector("[data-room-status]");
  const preview = root.querySelector("[data-image-preview]");
  const previewImage = preview.querySelector("img");
  const previewName = preview.querySelector("[data-image-name]");
  const csrfToken = document.querySelector('meta[name="_csrf"]').content;
  const csrfHeader = document.querySelector('meta[name="_csrf_header"]').content;
  const ids = new Set(
    Array.from(messages.querySelectorAll("[data-message-id]"))
      .map((element) => Number(element.dataset.messageId))
  );
  let previewUrl = null;
  let connectedOnce = false;

  function roomId() {
    return root.dataset.roomId ? Number(root.dataset.roomId) : null;
  }

  function orderedIds() {
    return Array.from(ids).sort((a, b) => a - b);
  }

  function setError(message) {
    errorBox.textContent = message || "";
    errorBox.hidden = !message;
  }

  function setConnection(text, state) {
    connection.textContent = text;
    connection.classList.toggle("is-online", state === "online");
    connection.classList.toggle("is-offline", state === "offline");
  }

  function messageElement(message) {
    const article = document.createElement("article");
    article.className = "chat-msg";
    article.dataset.messageId = message.id;
    article.dataset.senderType = message.senderType;
    article.classList.add(
      message.senderType === "SYSTEM"
        ? "chat-msg--system"
        : message.senderType === "CUSTOMER"
          ? "chat-msg--me"
          : "chat-msg--other"
    );

    if (message.senderType === "ADMIN") {
      const sender = document.createElement("span");
      sender.className = "chat-msg__sender";
      sender.textContent = "케이크샵 담당자";
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
      if (message.senderType === "CUSTOMER") {
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

  function formatTime(value) {
    const date = new Date(value);
    return new Intl.DateTimeFormat("ko-KR", {
      month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit",
      hour12: false
    }).format(date).replace(/\./g, ".").trim();
  }

  function appendMessage(message, position) {
    if (ids.has(Number(message.id))) return false;
    ids.add(Number(message.id));
    empty.hidden = true;
    const element = messageElement(message);
    if (position === "before") {
      const previousHeight = messages.scrollHeight;
      const anchor = messages.querySelector("[data-message-id]");
      messages.insertBefore(element, anchor);
      messages.scrollTop += messages.scrollHeight - previousHeight;
    } else {
      messages.appendChild(element);
      messages.scrollTop = messages.scrollHeight;
    }
    return true;
  }

  async function responseError(response) {
    try {
      const body = await response.json();
      return body.message || "요청을 처리하지 못했습니다.";
    } catch (_) {
      return "요청을 처리하지 못했습니다.";
    }
  }

  async function sendMessage() {
    const content = input.value.trim();
    const file = imageInput.files[0];
    if (!content && !file) {
      setError("메시지 또는 이미지를 입력해 주세요.");
      return;
    }
    setError("");
    sendButton.disabled = true;
    const clientMessageId = form.dataset.pendingId || crypto.randomUUID();
    form.dataset.pendingId = clientMessageId;
    const body = new FormData();
    body.append("clientMessageId", clientMessageId);
    body.append("content", content);
    if (file) body.append("image", file);

    try {
      const response = await fetch("/api/chat/messages", {
        method: "POST",
        headers: { [csrfHeader]: csrfToken },
        body
      });
      if (!response.ok) throw new Error(await responseError(response));
      const message = await response.json();
      if (!roomId()) root.dataset.roomId = message.roomId;
      appendMessage(message);
      form.dataset.pendingId = "";
      input.value = "";
      clearImage();
    } catch (error) {
      setError(error.message + " 같은 내용으로 다시 전송하면 중복 저장되지 않습니다.");
    } finally {
      sendButton.disabled = false;
    }
  }

  async function loadOlder() {
    const first = orderedIds()[0];
    if (!first) return;
    olderButton.disabled = true;
    try {
      const response = await fetch(`/api/chat/messages?beforeId=${first}&size=50`);
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
        const response = await fetch(`/api/chat/messages?afterId=${cursor}&size=100`);
        if (!response.ok) return;
        const slice = await response.json();
        slice.messages.forEach((message) => appendMessage(message));
        if (slice.messages.length) {
          cursor = slice.messages[slice.messages.length - 1].id;
        }
        hasMore = slice.hasMore && slice.messages.length > 0;
      } while (hasMore);
      markRead();
    } catch (_) {
      setConnection("REST 복구 대기", "offline");
    }
  }

  async function markRead() {
    const incoming = Array.from(
      messages.querySelectorAll('[data-sender-type="ADMIN"]')
    );
    if (!incoming.length || !roomId()) return;
    const lastId = Number(incoming[incoming.length - 1].dataset.messageId);
    if (Number(root.dataset.lastReadId || 0) >= lastId) return;
    try {
      const response = await fetch("/api/chat/read", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          [csrfHeader]: csrfToken
        },
        body: JSON.stringify({ lastMessageId: lastId })
      });
      if (response.ok) root.dataset.lastReadId = String(lastId);
    } catch (_) {
      // 다음 수신·포커스 때 멱등 재시도한다.
    }
  }

  function updateRead(lastId) {
    messages.querySelectorAll('[data-sender-type="CUSTOMER"]').forEach((element) => {
      if (Number(element.dataset.messageId) <= lastId) {
        const label = element.querySelector(".chat-msg__read");
        if (label) label.textContent = "읽음";
      }
    });
  }

  function handleEvent(event) {
    if (roomId() && Number(event.roomId) !== roomId()) return;
    if (event.type === "MESSAGE_CREATED" && event.message) {
      if (!roomId()) root.dataset.roomId = event.roomId;
      appendMessage(event.message);
      if (event.message.senderType === "ADMIN") markRead();
    } else if (event.type === "MESSAGES_READ" && event.lastReadMessageId) {
      updateRead(Number(event.lastReadMessageId));
    } else if (event.type === "ROOM_STATUS_CHANGED" && event.roomStatus) {
      statusBadge.textContent = event.roomStatus === "OPEN" ? "상담 중" : "상담 종료";
      statusBadge.classList.toggle("badge--success", event.roomStatus === "OPEN");
    }
  }

  function connect() {
    if (!window.StompJs) {
      setConnection("실시간 모듈 오류", "offline");
      return;
    }
    const scheme = location.protocol === "https:" ? "wss" : "ws";
    const client = new StompJs.Client({
      brokerURL: `${scheme}://${location.host}/ws/chat`,
      connectHeaders: { [csrfHeader]: csrfToken },
      reconnectDelay: 5000,
      heartbeatIncoming: 20000,
      heartbeatOutgoing: 10000,
      onConnect: function () {
        setConnection("실시간 연결됨", "online");
        client.subscribe("/user/queue/chat-events", (frame) => {
          handleEvent(JSON.parse(frame.body));
        });
        if (connectedOnce) recoverMissed();
        connectedOnce = true;
      },
      onWebSocketClose: function () {
        setConnection("재연결 중", "offline");
      },
      onStompError: function () {
        setConnection("재연결 중", "offline");
      }
    });
    client.activate();
  }

  function clearImage() {
    imageInput.value = "";
    preview.hidden = true;
    if (previewUrl) URL.revokeObjectURL(previewUrl);
    previewUrl = null;
    previewImage.removeAttribute("src");
    previewName.textContent = "";
  }

  imageInput.addEventListener("change", function () {
    const file = imageInput.files[0];
    if (!file) return clearImage();
    if (!["image/jpeg", "image/png"].includes(file.type) || file.size > 5 * 1024 * 1024) {
      setError("JPG/PNG 형식의 5MB 이하 이미지만 선택해 주세요.");
      return clearImage();
    }
    setError("");
    if (previewUrl) URL.revokeObjectURL(previewUrl);
    previewUrl = URL.createObjectURL(file);
    previewImage.src = previewUrl;
    previewName.textContent = file.name;
    preview.hidden = false;
  });
  preview.querySelector("[data-image-clear]").addEventListener("click", clearImage);
  root.querySelector("[data-custom-order]").addEventListener("click", function () {
    clearImage();
    input.value = "/주문제작";
    form.requestSubmit();
  });
  form.addEventListener("submit", function (event) {
    event.preventDefault();
    sendMessage();
  });
  olderButton.addEventListener("click", loadOlder);
  window.addEventListener("focus", markRead);
  document.addEventListener("visibilitychange", function () {
    if (!document.hidden) markRead();
  });

  messages.scrollTop = messages.scrollHeight;
  markRead();
  connect();
})();

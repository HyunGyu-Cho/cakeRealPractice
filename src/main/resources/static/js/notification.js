(function () {
  "use strict";

  const root = document.querySelector("[data-notification-root]");
  if (!root) return;

  const list = root.querySelector("[data-notification-list]");
  const empty = root.querySelector("[data-notification-empty]");
  const errorBox = root.querySelector("[data-notification-error]");
  const readAllButton = root.querySelector("[data-read-all]");
  const moreButton = root.querySelector("[data-load-more]");
  const badge = document.querySelector("[data-notification-badge]");
  const destination = root.dataset.subscribe;
  const csrfToken = document.querySelector('meta[name="_csrf"]').content;
  const csrfHeader = document.querySelector('meta[name="_csrf_header"]').content;

  function setError(message) {
    if (!errorBox) return;
    errorBox.textContent = message || "";
    errorBox.hidden = !message;
  }

  function setUnread(count) {
    if (badge) badge.textContent = "알림 (" + count + ")";
    if (readAllButton) {
      readAllButton.disabled = count === 0;
      readAllButton.textContent = count === 0 ? "모두 읽음" : "전체 읽음";
    }
  }

  function unreadCount() {
    return list.querySelectorAll(".notification-item.is-unread").length;
  }

  function markElementRead(element) {
    element.classList.remove("is-unread");
    const dot = element.querySelector(".notification-dot");
    if (dot) dot.remove();
  }

  function formatTime(value) {
    if (!value) return "";
    const time = new Date(value);
    const pad = (number) => String(number).padStart(2, "0");
    return pad(time.getMonth() + 1) + "." + pad(time.getDate())
      + " " + pad(time.getHours()) + ":" + pad(time.getMinutes());
  }

  function itemElement(item, occurredAt) {
    const article = document.createElement("article");
    article.className = "notification-item";
    if (!item.read) article.classList.add("is-unread");
    if (item.id !== null && item.id !== undefined) {
      article.dataset.notificationId = item.id;
    }

    const cluster = document.createElement("div");
    cluster.className = "cluster";
    cluster.style.alignItems = "flex-start";
    if (!item.read) {
      const dot = document.createElement("span");
      dot.className = "notification-dot";
      dot.setAttribute("aria-hidden", "true");
      cluster.appendChild(dot);
    }

    const body = document.createElement("div");
    const label = document.createElement("span");
    label.className = "badge";
    label.textContent = item.typeLabel;
    const content = document.createElement("p");
    content.textContent = item.content;
    const time = document.createElement("time");
    time.className = "text-muted";
    time.textContent = formatTime(item.createdAt || occurredAt);
    body.append(label, content, time);
    cluster.appendChild(body);
    article.appendChild(cluster);

    if (item.targetUrl) {
      const link = document.createElement("a");
      link.className = "btn";
      link.href = item.targetUrl;
      link.dataset.notificationLink = "";
      link.textContent = "상세";
      article.appendChild(link);
    }
    return article;
  }

  function render(items, occurredAt, append) {
    if (!append) {
      list.querySelectorAll(".notification-item").forEach((item) => item.remove());
    }
    items.forEach((item) => list.appendChild(itemElement(item, occurredAt)));
    if (empty) empty.hidden = list.querySelector(".notification-item") !== null;
  }

  async function call(url, options) {
    const response = await fetch(url, options);
    if (!response.ok) {
      const body = await response.json().catch(() => ({}));
      throw new Error(body.message || "요청을 처리하지 못했습니다.");
    }
    return response.status === 204 ? null : response.json();
  }

  function post(url) {
    return call(url, {
      method: "POST",
      headers: { [csrfHeader]: csrfToken }
    });
  }

  async function reload() {
    const slice = await call("/api/notifications?size=20");
    render(slice.content, null, false);
    setUnread(slice.unreadCount);
    if (moreButton) {
      moreButton.hidden = !slice.hasNext;
      moreButton.dataset.cursor = slice.nextCursor === null ? "" : slice.nextCursor;
    }
  }

  if (readAllButton) {
    readAllButton.addEventListener("click", async () => {
      try {
        setError(null);
        await post("/api/notifications/read-all");
        list.querySelectorAll(".notification-item").forEach(markElementRead);
        setUnread(0);
      } catch (error) {
        setError(error.message);
      }
    });
  }

  if (moreButton) {
    moreButton.addEventListener("click", async () => {
      try {
        setError(null);
        const cursor = moreButton.dataset.cursor;
        const slice = await call("/api/notifications?size=20"
          + (cursor ? "&cursor=" + encodeURIComponent(cursor) : ""));
        render(slice.content, null, true);
        moreButton.hidden = !slice.hasNext;
        moreButton.dataset.cursor = slice.nextCursor === null ? "" : slice.nextCursor;
      } catch (error) {
        setError(error.message);
      }
    });
  }

  // 상세로 이동하기 전에 읽음 처리한다. 실패해도 이동 자체는 막지 않는다.
  list.addEventListener("click", (event) => {
    const link = event.target.closest("[data-notification-link]");
    if (!link) return;
    const item = link.closest(".notification-item");
    if (!item || !item.dataset.notificationId || !item.classList.contains("is-unread")) return;
    post("/api/notifications/" + item.dataset.notificationId + "/read").catch(() => {});
    markElementRead(item);
    setUnread(unreadCount());
  });

  if (!destination || typeof StompJs === "undefined") return;

  const scheme = location.protocol === "https:" ? "wss" : "ws";
  const client = new StompJs.Client({
    brokerURL: scheme + "://" + location.host + "/ws/chat",
    connectHeaders: { [csrfHeader]: csrfToken },
    reconnectDelay: 5000,
    heartbeatIncoming: 10000,
    heartbeatOutgoing: 10000,
    onConnect: () => {
      client.subscribe(destination, (frame) => {
        const event = JSON.parse(frame.body);
        // 관리자 브로드캐스트는 수신자별 알림 id가 없어 첫 페이지를 다시 읽는다.
        if (event.adminBroadcast) {
          reload().catch(() => {});
          return;
        }
        list.insertBefore(
          itemElement(event.notification, event.occurredAt),
          list.querySelector(".notification-item")
        );
        if (empty) empty.hidden = true;
        setUnread(unreadCount());
      });
    }
  });
  client.activate();
})();

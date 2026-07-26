---
domain: notification
status: approved
owner: 민정
approved-at: 2026-07-26
---

# notification 스펙

## 1. 개요와 유스케이스

- 이 도메인이 해결하는 문제 한 줄: 고객이 자기 주문·결제·채팅에서 벌어진 일을 놓치지 않도록 알림을 저장하고, 실시간으로 전달하고, 읽음을 관리한다.
- 주요 유스케이스 (액터 → 행동 → 결과):
  1. 업무 도메인(chat·order·payment) → 사건 발생 시 `NotificationService.notify(...)` 호출 → 알림이 저장되고 커밋 후 수신자에게 STOMP로 푸시된다.
  2. 고객 → `/notifications` 방문 → 자기 알림을 최신순으로 조회하고 헤더에서 미읽음 개수를 본다.
  3. 고객 → 알림 클릭 또는 "전체 읽음" → 해당 알림이 읽음 처리되고 미읽음 개수가 줄어든다.
  4. 고객 → 페이지에 머무는 동안 새 알림 발생 → 새로고침 없이 목록 맨 위에 추가되고 뱃지가 증가한다.
  5. 고객 활동(새 채팅 메시지·결제 완료·취소) → 전체 관리자에게 알림 발행 → 관리자 화면 헤더 뱃지와 `/admin/notifications` "내 알림"에 실시간으로 나타난다.
  6. 관리자 → `/admin/notifications` 방문 → 자기 알림을 읽음 처리하고, 전체 발송 내역을 유형·읽음 여부로 필터링해 페이징 조회한다.

## 2. 상태값 (conventions.md 상태값 공통 규칙 준수)

알림에는 **상태(status) 컬럼이 없다.** 읽음 여부는 불리언이고, 알림 종류는 type 컬럼이다.

| 컬럼 | 값(영문 enum 이름) | 시작 상태 | 최종 상태 | 전이 규칙 요약 |
|---|---|---|---|---|
| `notifications.notification_type` | `NotificationType` 12개(아래 표) | 해당 없음(생성 시 확정) | 해당 없음 | 전이 없음 — 생성 후 불변 |
| `notifications.is_read` | BOOLEAN (status 아님) | `false` (DDL DEFAULT 0) | `true` | 수신자 본인만 `false → true` 단방향, 되돌리기 없음 |

`NotificationType` — 영문 enum 이름만 저장하고 한글 라벨은 화면에서만 매핑한다.

| 값 | 라벨 | 발행 시점 |
|---|---|---|
| `CHAT_MESSAGE` | 새 메시지 | 관리자가 고객에게 채팅 메시지 전송 |
| `ORDER_PAID` | 결제 완료 | 결제 성공(주문 `PAID` + 결제 `DONE`) |
| `ORDER_IN_PRODUCTION` | 제작 시작 | 주문 상태 전이 → `IN_PRODUCTION` |
| `ORDER_READY_FOR_PICKUP` | 픽업 준비 | 주문 상태 전이 → `READY_FOR_PICKUP` |
| `ORDER_PICKED_UP` | 픽업 완료 | 주문 상태 전이 → `PICKED_UP` |
| `ORDER_CANCELED` | 취소·환불 완료 | 환불 처리 완료 |
| `ORDER_REJECTED` | 주문 거절 | 주문 상태 전이 → `REJECTED` |
| `CUSTOM_ORDER_QUOTE` | 견적 도착 | 주문제작 견적 등록 (주문제작 실구현 시 발행) |
| `PAYMENT_REQUESTED` | 결제 요청 | 주문제작 결제 링크 발송 (주문제작 실구현 시 발행) |
| `ADMIN_CHAT_MESSAGE` | 고객 문의 | 고객이 채팅 메시지 전송 → 전체 관리자 |
| `ADMIN_ORDER_PLACED` | 신규 주문 | 고객 결제 성공 → 전체 관리자 |
| `ADMIN_ORDER_CANCELED` | 주문 취소 | 고객이 주문 취소·환불 → 전체 관리자 |

수신자 구분 컬럼은 두지 않는다. 고객용 타입은 `receiver_id`가 고객, `ADMIN_*` 타입은 `receiver_id`가 관리자 회원이며, 타입 이름만으로 구분된다.

- status가 아닌 것 점검:
  - 읽음/미읽음 → **불리언** `is_read` (conventions.md 인벤토리 규칙). enum으로 만들지 않는다.
  - "주문 승인 / 결제 완료 / 픽업 준비" → **type** (`NotificationType`), 상태가 아니다.
  - 주문 상태·결제 상태 → **다른 도메인의 status**. `notifications`에 복사 저장하지 않고, 알림 생성 시점의 title·content 문구로만 남긴다.
  - 미읽음 개수 → **파생값**. 저장하지 않고 매번 집계한다.

## 3. DB

- 사용할 테이블 (V0 기준): `notifications` 단일 테이블
  - `id`, `receiver_id`(FK members), `order_id`(FK orders, NULL), `chat_message_id`(FK chat_messages, NULL — V9에서 추가), `notification_type VARCHAR(50)`, `title VARCHAR(200)`, `content TEXT`, `is_read TINYINT(1) DEFAULT 0`, `read_at DATETIME(6) NULL`, `target_url VARCHAR(500) NULL`, `created_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6)`
- 스키마 변경 필요 여부: **필요** — `docs/sql/V10_notification.sql` 신규 (V0_ERD.sql·V1_first_MVC_table.sql 소급 반영)
  - `chk_notifications_type CHECK (notification_type IN (...))` — 12개 값
  - `idx_notifications_receiver (receiver_id, id DESC)` — 목록 키셋 페이징
  - `idx_notifications_unread (receiver_id, is_read)` — 헤더 미읽음 카운트
  - 컬럼 타입 변경 없음. `created_at`은 DDL DEFAULT에 위임(자바에서 세팅 금지).
- 범위 밖: `V0_ERD.sql`에만 있고 실제 생성된 적 없는 `notification_deliveries`(외부 채널 발송 이력)는 이번 범위에서 다루지 않는다.

## 4. 도메인 간 인터페이스

- 내가 제공할 공개 Service 메서드 (`NotificationService`):
  - `void notify(NotificationCommand command)` — 업무 도메인이 호출하는 **공개 발행 진입점**. 수신자·유형·제목·본문·targetUrl·연관 id(order/chatMessage)를 받는다.
  - `void notifyAdmins(NotificationCommand command)` — 수신자를 비운 command로 호출하면 전체 ADMIN 회원 각각에게 한 건씩 저장한다.
  - `long countUnread(Long receiverId)` — 헤더 뱃지용(고객·관리자 공통).
  - 화면용 조회/읽음 메서드(`getSlice`, `markRead`, `markAllRead`)는 notification 자신의 Controller만 사용한다.
- 내가 사용할 다른 도메인의 공개 Service 메서드 (Mapper 직접 호출 금지):
  - `MemberService.getProfileMap` / `searchMemberIds` — 관리자 발송 내역의 대상 회원 표시·검색. **members 테이블 JOIN 금지.**
  - `MemberService.findAdminMemberIds()` — **신규 공개 계약**(member 도메인에 추가). 관리자 알림 팬아웃 대상 조회용. 시그니처 변경 시 수민↔민정 합의 필요.
- 호출 방향: `ChatService`·`OrderService`·`CheckoutPaymentProcessor`·`RefundService` → `NotificationService` (단방향). notification은 업무 도메인을 역참조하지 않는다.

## 5. 화면

- 대상 화면과 URL (목업 → 실구현 전환 시 URL·템플릿 유지):
  - 고객 `GET /notifications` → `customer/notification/list.html`
  - 고객 API `GET /api/notifications?cursor=&size=`, `POST /api/notifications/{id}/read`, `POST /api/notifications/read-all`
  - 관리자 `GET /admin/notifications?type=&read=&page=` → `admin/notification/list.html` (상단 "내 알림" + 하단 "전체 발송 내역")
  - 관리자 API `POST /admin/api/notifications/{id}/read`, `POST /admin/api/notifications/read-all`
  - 실시간: STOMP 엔드포인트는 chat과 공유(`/ws/chat`). 고객 구독 `/user/queue/notifications`, 관리자 구독 `/topic/admin/notifications`(관리자 전원 공통 브로드캐스트)
- 목업 JS가 시연하는 임시 동작 중 규칙으로 확정할 것:
  - `data-read-all` "전체 읽음" 버튼(`customer-mockup.js`)의 클래스 토글 → 실제 `POST /api/notifications/read-all` 호출로 대체하고 미읽음 0이면 버튼을 비활성화한다.
  - `.notification-item.is-unread` + `.notification-dot` 표시는 `is_read = false`에 대응한다.
  - 목업의 6개 뱃지(주문 승인/결제 완료/제작 시작/픽업 준비/취소 완료/주문 거절)는 위 `NotificationType` 라벨로 확정한다.
  - `/notifications`는 목업 공개 미리보기 대상에서 제외하고 로그인 필수로 전환한다.

## 6. 비즈니스 규칙 확정

- team-plan.md 8장에서 이 도메인과 관련된 항목: "알림 생성 범위와 읽음 처리 방식" (미결)
- 확정한 규칙 (확정 후 team-plan 8장 표 갱신):
  1. 알림 수신자는 **고객과 관리자 모두**다. 관리자 알림은 발행 시점의 전체 ACTIVE ADMIN 회원에게 각각 한 건씩 저장(팬아웃)하고, 실시간 푸시는 `/topic/admin/notifications` 한 번으로 처리한다.
  2. 알림은 업무 트랜잭션과 **같은 트랜잭션**에서 저장한다. 알림 저장 실패는 업무 트랜잭션을 롤백시킨다(누락 방지).
  3. 실시간 푸시는 `@TransactionalEventListener(AFTER_COMMIT)`에서 수행하며, **푸시 실패는 롤백하지 않고 로그만 남긴다**. 클라이언트는 재접속 시 REST 조회로 복구한다.
  4. 채팅 알림은 발신자의 반대편에게 발행한다. 관리자→고객 메시지는 `CHAT_MESSAGE`(고객 수신), 고객→관리자 메시지는 `ADMIN_CHAT_MESSAGE`(관리자 수신). `SYSTEM_CARD`는 발행하지 않으며, 재전송 멱등 처리로 기존 메시지를 반환하는 경우에도 발행하지 않는다.
  5. 알림은 수정·삭제하지 않는다. 조회·읽음 처리는 **수신자 본인만** 가능하며, 타인 알림 접근은 `NOTIFICATION_002 FORBIDDEN`이다.
  6. 읽음은 단방향(`false → true`)이며 이미 읽은 알림을 다시 읽어도 성공으로 처리한다(멱등). `read_at`은 최초 읽음 시각을 유지한다.
  7. 관리자 화면의 "전체 발송 내역"은 **읽기 전용**이다(수동 발송·재발송·삭제 없음). 읽음 처리는 관리자 본인 수신 알림에만 허용한다.
  8. 목록 정렬은 `id DESC`(최신순)이며 읽은 알림도 함께 보여준다. 별도 보관·만료 정책은 두지 않는다.

## 7. 완료 기준

- [x] 정상 흐름·주요 실패 흐름 동작
- [x] 입력 검증(form DTO)과 접근 권한 적용
- [x] 상태 전이·트랜잭션 규칙 준수
- [x] 전용 테스트 통과 (Service + Controller + Security, store 패턴)
- [x] 관련 SQL·문서 함께 수정

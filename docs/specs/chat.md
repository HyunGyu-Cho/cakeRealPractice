---
domain: chat
status: approved
owner: 민정
approved-at: 2026-07-26
---

# 채팅 도메인 명세

## 범위

- 로그인 고객과 공용 관리자 사이의 영구 1:1 채팅방
- 텍스트, JPG/PNG 이미지 1개와 선택 설명, 읽음 표시
- 관리자의 상담 종료와 고객 활동 시 자동 재개
- 정확한 `/주문제작` 명령을 `SYSTEM_CARD`로 저장
- REST 저장 후 트랜잭션 커밋이 완료된 이벤트만 STOMP로 전달

알림 생성, 주문제작 요청 처리, 견적·결제 링크, 관리자 메모와 주문 연결 조작은 후속 범위다.

## 상태와 파생값

- `ChatRoomStatus`: `OPEN`, `CLOSED`
- 고객당 방은 하나이며 삭제하지 않는다.
- 관리자가 방을 닫는다. 닫힌 방에는 관리자가 메시지를 보낼 수 없다.
- 고객이 메시지 또는 `/주문제작` 명령을 보내면 닫힌 방은 `OPEN`으로 자동 재개된다.
- `미답변`은 마지막 일반 메시지(`TEXT`/`IMAGE`) 발신자가 `CUSTOMER`일 때 참이다.
- `SYSTEM_CARD`는 미답변 계산에 영향을 주지 않는다.

## 메시지

- 발신자 유형: `CUSTOMER`, `ADMIN`, `SYSTEM`
- 메시지 유형: `TEXT`, `IMAGE`, `SYSTEM_CARD`
- 내용은 trim 후 2,000자 이하이다.
- 텍스트와 이미지가 모두 없는 요청은 거부한다.
- 이미지는 메시지당 JPG/PNG 하나, 최대 5MB이다.
- 메시지는 상담 이력으로 보존하며 수정·삭제하지 않는다.
- `(chat_room_id, client_message_id)`를 유일하게 유지해 재전송을 멱등 처리한다.
- 정확히 `/주문제작`만 입력한 요청은 일반 메시지 대신 주문제작 안내 카드가 되며 액션 URL은 `/orders/custom/options`다.

## 이미지 보안

- 채팅 이미지는 공개 `/uploads/**` 저장소와 분리한다.
- DB에는 비공개 키와 원본 파일명·콘텐츠 타입·크기만 저장한다.
- `GET /api/chat/messages/{messageId}/image`는 방 소유 고객 또는 관리자만 허용한다.
- 파일 저장 후 DB 트랜잭션이 실패하면 저장 파일을 삭제한다.

## HTTP 계약

고객:

- `GET /chat`
- `GET /api/chat/messages?beforeId=&afterId=&size=`
- `POST /api/chat/messages` (multipart)
- `POST /api/chat/read`

관리자:

- `GET /admin/chat?keyword=&filter=&page=&roomId=`
- `GET /admin/api/chat/rooms/{roomId}/messages`
- `POST /admin/api/chat/rooms/{roomId}/messages`
- `POST /admin/api/chat/rooms/{roomId}/read`
- `POST /admin/api/chat/rooms/{roomId}/close`

## 실시간 계약

- STOMP endpoint: `/ws/chat`
- 고객 구독: `/user/queue/chat-events`
- 관리자 구독: `/topic/admin/chat-events`
- 클라이언트의 STOMP `SEND`는 전부 거부한다.
- 이벤트: `MESSAGE_CREATED`, `MESSAGES_READ`, `ROOM_STATUS_CHANGED`, `ROOM_SUMMARY_CHANGED`
- WebSocket 전송 실패는 DB 저장을 롤백하지 않는다. 재접속 시 REST `afterId` 조회로 누락을 복구한다.

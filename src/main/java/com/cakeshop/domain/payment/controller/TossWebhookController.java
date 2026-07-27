package com.cakeshop.domain.payment.controller;

import com.cakeshop.domain.payment.service.WebhookEventService;
import tools.jackson.databind.JsonNode;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 결제 상태 변경 웹훅 수신. 인증·CSRF 예외 경로다(SecurityConfig).
 *
 * <p>제공자는 10초 안에 200을 받지 못하면 재전송한다. 그래서 여기서는 <b>저장만</b> 하고 바로 응답하며,
 * 결제 반영은 {@code WebhookEventProcessor}가 따로 한다. 재전송은 전송 ID UNIQUE로 걸러지므로
 * 같은 이벤트가 여러 번 와도 저장은 1건이고 응답은 항상 200이다.
 */
@RestController
public class TossWebhookController {
    private final WebhookEventService webhookEventService;

    public TossWebhookController(WebhookEventService webhookEventService) {
        this.webhookEventService = webhookEventService;
    }

    @PostMapping("/webhooks/toss")
    public ResponseEntity<Map<String, Object>> receive(@RequestBody JsonNode body) {
        JsonNode data = body.path("data");
        boolean stored = webhookEventService.receive(
            eventId(body),
            text(body, "eventType"),
            text(data, "paymentKey"),
            text(data, "orderId"),
            text(data, "status"),
            body.toString());
        // 재전송(stored=false)도 정상 수신이다. 200이 아니면 제공자가 계속 다시 보낸다.
        return ResponseEntity.ok(Map.of("received", true, "stored", stored));
    }

    /** 전송 ID가 없는 형식이면 중복을 판별할 수 없다. 임시 ID를 붙여 일단 남긴다. */
    private String eventId(JsonNode body) {
        String eventId = text(body, "eventId");
        return eventId == null ? "NO-ID-" + UUID.randomUUID() : eventId;
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asString();
    }
}

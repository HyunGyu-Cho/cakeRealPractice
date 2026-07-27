package com.cakeshop.domain.payment.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cakeshop.domain.payment.service.WebhookEventService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class TossWebhookControllerTests {
    private static final String BODY = """
        {"eventId":"evt-1","eventType":"PAYMENT_STATUS_CHANGED",
         "data":{"paymentKey":"PK-1","orderId":"ORD-1","status":"DONE"}}
        """;

    @Mock WebhookEventService webhookEventService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
            new TossWebhookController(webhookEventService)).build();
    }

    @Test
    void storesEventAndAnswersOk() throws Exception {
        when(webhookEventService.receive(any(), any(), any(), any(), any(), any())).thenReturn(true);

        mockMvc.perform(post("/webhooks/toss").contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.stored").value(true));

        verify(webhookEventService).receive(eq("evt-1"), eq("PAYMENT_STATUS_CHANGED"),
            eq("PK-1"), eq("ORD-1"), eq("DONE"), any());
    }

    /** 재전송도 200이어야 한다. 200이 아니면 제공자가 계속 다시 보낸다. */
    @Test
    void resentEventIsStoredOnceAndStillAnswersOk() throws Exception {
        when(webhookEventService.receive(any(), any(), any(), any(), any(), any()))
            .thenReturn(true, false);

        mockMvc.perform(post("/webhooks/toss").contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.stored").value(true));
        mockMvc.perform(post("/webhooks/toss").contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.stored").value(false));

        verify(webhookEventService, times(2))
            .receive(eq("evt-1"), any(), any(), any(), any(), any());
    }
}

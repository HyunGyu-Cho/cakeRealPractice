package com.cakeshop.global.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

@SpringBootTest
class ChatWebSocketSecurityTests {

    @Autowired
    private AuthorizationManager<Message<?>> messageAuthorizationManager;

    @Test
    void customerCanOnlySubscribeToPersonalChatEvents() {
        Authentication customer = authentication("ROLE_USER");

        assertThat(authorized(customer,
            message(SimpMessageType.SUBSCRIBE, "/user/queue/chat-events"))).isTrue();
        assertThat(authorized(customer,
            message(SimpMessageType.SUBSCRIBE, "/topic/admin/chat-events"))).isFalse();
    }

    @Test
    void adminCanSubscribeToAdminTopicButClientSendIsDenied() {
        Authentication admin = authentication("ROLE_ADMIN");

        assertThat(authorized(admin,
            message(SimpMessageType.SUBSCRIBE, "/topic/admin/chat-events"))).isTrue();
        assertThat(authorized(admin,
            message(SimpMessageType.MESSAGE, "/topic/admin/chat-events"))).isFalse();
    }

    @Test
    void notificationQueueIsOpenToBothRolesButAdminTopicIsNot() {
        Authentication customer = authentication("ROLE_USER");
        Authentication admin = authentication("ROLE_ADMIN");

        assertThat(authorized(customer,
            message(SimpMessageType.SUBSCRIBE, "/user/queue/notifications"))).isTrue();
        assertThat(authorized(admin,
            message(SimpMessageType.SUBSCRIBE, "/user/queue/notifications"))).isTrue();
        assertThat(authorized(customer,
            message(SimpMessageType.SUBSCRIBE, "/topic/admin/notifications"))).isFalse();
        assertThat(authorized(admin,
            message(SimpMessageType.SUBSCRIBE, "/topic/admin/notifications"))).isTrue();
        assertThat(authorized(admin,
            message(SimpMessageType.MESSAGE, "/user/queue/notifications"))).isFalse();
    }

    private boolean authorized(Authentication authentication, Message<?> message) {
        return messageAuthorizationManager.authorize(() -> authentication, message).isGranted();
    }

    private Authentication authentication(String authority) {
        return new UsernamePasswordAuthenticationToken(
            "member", "password", List.of(new SimpleGrantedAuthority(authority)));
    }

    private Message<byte[]> message(SimpMessageType type, String destination) {
        SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.create(type);
        accessor.setDestination(destination);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}

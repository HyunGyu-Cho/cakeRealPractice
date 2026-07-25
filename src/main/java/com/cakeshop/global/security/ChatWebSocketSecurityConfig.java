package com.cakeshop.global.security;

import static org.springframework.messaging.simp.SimpMessageType.MESSAGE;
import static org.springframework.messaging.simp.SimpMessageType.SUBSCRIBE;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.annotation.web.socket.EnableWebSocketSecurity;
import org.springframework.security.messaging.access.intercept.MessageMatcherDelegatingAuthorizationManager;

@Configuration
@EnableWebSocketSecurity
public class ChatWebSocketSecurityConfig {

    @Bean
    AuthorizationManager<Message<?>> messageAuthorizationManager(
            MessageMatcherDelegatingAuthorizationManager.Builder messages) {
        messages
            .nullDestMatcher().authenticated()
            .simpSubscribeDestMatchers("/user/queue/chat-events").hasRole("USER")
            .simpSubscribeDestMatchers("/topic/admin/chat-events").hasRole("ADMIN")
            .simpTypeMatchers(MESSAGE, SUBSCRIBE).denyAll()
            .anyMessage().denyAll();
        return messages.build();
    }
}

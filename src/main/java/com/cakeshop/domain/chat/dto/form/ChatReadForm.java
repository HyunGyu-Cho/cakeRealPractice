package com.cakeshop.domain.chat.dto.form;

import jakarta.validation.constraints.NotNull;

public record ChatReadForm(@NotNull Long lastMessageId) {
}

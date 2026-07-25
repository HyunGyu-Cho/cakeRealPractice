package com.cakeshop.domain.chat.dto.view;

import org.springframework.core.io.Resource;

public record ChatImageView(
    Resource resource,
    String contentType,
    String originalName,
    long size
) {
}

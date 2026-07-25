package com.cakeshop.domain.chat.dto.form;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

@Getter
@Setter
public class ChatMessageForm {

    @NotNull
    private UUID clientMessageId;

    @Size(max = 2000)
    private String content;

    private MultipartFile image;
}

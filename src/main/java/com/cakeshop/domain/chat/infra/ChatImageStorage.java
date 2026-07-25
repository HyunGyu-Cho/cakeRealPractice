package com.cakeshop.domain.chat.infra;

import com.cakeshop.domain.chat.dto.view.ChatImageView;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class ChatImageStorage {

    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyyMM");
    private final Path baseDir;

    public ChatImageStorage(@Value("${app.chat.private-image-dir}") String baseDir) {
        this.baseDir = Path.of(baseDir).toAbsolutePath().normalize();
    }

    public String store(MultipartFile file, String contentType) {
        String extension = "image/png".equals(contentType) ? ".png" : ".jpg";
        String key = LocalDate.now().format(MONTH) + "/" + UUID.randomUUID() + extension;
        Path target = resolve(key);
        try {
            Files.createDirectories(target.getParent());
            file.transferTo(target);
            return key;
        } catch (IOException e) {
            throw new UncheckedIOException("채팅 이미지 저장에 실패했습니다.", e);
        }
    }

    public ChatImageView load(String key, String contentType, String originalName, long size) {
        Path path = resolve(key);
        if (!Files.isRegularFile(path)) {
            throw new IllegalStateException("채팅 이미지 파일이 존재하지 않습니다.");
        }
        return new ChatImageView(new FileSystemResource(path), contentType, originalName, size);
    }

    public void delete(String key) {
        if (key == null || key.isBlank()) {
            return;
        }
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException e) {
            throw new UncheckedIOException("채팅 이미지 삭제에 실패했습니다.", e);
        }
    }

    private Path resolve(String key) {
        Path resolved = baseDir.resolve(key).normalize();
        if (!resolved.startsWith(baseDir)) {
            throw new IllegalArgumentException("허용되지 않은 채팅 이미지 키입니다.");
        }
        return resolved;
    }
}

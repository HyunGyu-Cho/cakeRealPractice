package com.cakeshop.global.infra;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

// MVP 구현 — 로컬 디스크 저장 (추후 S3 구현체로 교체 가능)
// 실제 파일은 app.file.upload-dir 아래에 저장하고,
// 반환/DB 저장 값은 웹 접근 경로(app.file.url-prefix 접두)를 사용한다.
@Component
public class LocalFileStorageClient implements FileStorageClient {

    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyyMM");

    /**
     * 저장 확장자는 <b>클라이언트가 보낸 파일명이 아니라 content type 에서 역산</b>한다.
     *
     * <p>저장 파일은 {@code /uploads/**} 로 공개 서빙되고, Spring 의 리소스 핸들러는
     * <b>확장자로 Content-Type 을 결정</b>한다. 파일명을 믿으면 {@code image/png} 로 선언한 채
     * 이름만 {@code x.html} 로 보내 앱과 같은 오리진에서 HTML 이 실행되게 만들 수 있다.
     * 매핑에 없는 형식은 아래에서 거부되므로 {@code image/svg+xml} 도 여기서 막힌다.
     */
    private static final java.util.Map<String, String> EXTENSION_BY_CONTENT_TYPE = java.util.Map.of(
        "image/jpeg", ".jpg",
        "image/png", ".png",
        "image/gif", ".gif",
        "image/webp", ".webp");

    private final Path baseDir;
    private final String urlPrefix;

    public LocalFileStorageClient(
            @Value("${app.file.upload-dir}") String uploadDir,
            @Value("${app.file.url-prefix}") String urlPrefix) {
        this.baseDir = Path.of(uploadDir).toAbsolutePath().normalize();
        this.urlPrefix = stripTrailingSlash(urlPrefix);
    }

    // 저장 경로 규칙: /{도메인}/{yyyyMM}/{uuid}.{ext}
    @Override
    public String store(MultipartFile file, String directory) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("저장할 파일이 비어 있습니다.");
        }
        String relativeDir = directory + "/" + LocalDate.now().format(MONTH);
        String filename = UUID.randomUUID() + extension(file.getContentType());
        Path targetDir = baseDir.resolve(relativeDir).normalize();
        // directory 에 '..' 등이 섞여 baseDir 밖으로 나가는 것을 차단한다.
        if (!targetDir.startsWith(baseDir)) {
            throw new IllegalArgumentException("허용되지 않은 저장 경로입니다: " + directory);
        }
        Path target = targetDir.resolve(filename);
        try {
            Files.createDirectories(targetDir);
            file.transferTo(target);
        } catch (IOException e) {
            throw new UncheckedIOException("파일 저장에 실패했습니다: " + target, e);
        }
        return urlPrefix + "/" + relativeDir + "/" + filename;
    }

    @Override
    public void delete(String path) {
        if (!StringUtils.hasText(path) || !path.startsWith(urlPrefix + "/")) {
            return;
        }
        String relative = path.substring(urlPrefix.length() + 1);
        Path target = baseDir.resolve(relative).normalize();
        // baseDir 밖 경로는 무시한다.
        if (!target.startsWith(baseDir)) {
            return;
        }
        try {
            Files.deleteIfExists(target);
        } catch (IOException e) {
            throw new UncheckedIOException("파일 삭제에 실패했습니다: " + target, e);
        }
    }

    /**
     * 도메인 검증({@code ImageValidator})을 통과했더라도 여기서 한 번 더 막는다 — 저장 계층이
     * 스스로 안전한 확장자만 쓰게 만들어, 새 호출처가 검증을 빠뜨려도 실행 가능한 파일이 생기지 않게 한다.
     */
    private String extension(String contentType) {
        String normalized = contentType == null ? null
            : contentType.toLowerCase(java.util.Locale.ROOT).trim();
        String extension = normalized == null ? null : EXTENSION_BY_CONTENT_TYPE.get(normalized);
        if (extension == null) {
            throw new IllegalArgumentException("허용되지 않은 파일 형식입니다: " + contentType);
        }
        return extension;
    }

    private String stripTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}

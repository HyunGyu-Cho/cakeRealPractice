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
        String filename = UUID.randomUUID() + extension(file.getOriginalFilename());
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

    private String extension(String originalFilename) {
        String ext = StringUtils.getFilenameExtension(originalFilename);
        return StringUtils.hasText(ext) ? "." + ext.toLowerCase() : "";
    }

    private String stripTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}

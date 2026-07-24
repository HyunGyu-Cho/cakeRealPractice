package com.cakeshop.global.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

class LocalFileStorageClientTests {

    private static final String URL_PREFIX = "/uploads";

    private Path baseDir;
    private LocalFileStorageClient client;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        this.baseDir = tempDir;
        this.client = new LocalFileStorageClient(tempDir.toString(), URL_PREFIX);
    }

    @Test
    void store_savesFileAndReturnsWebPath() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "cake.JPG", "image/jpeg", "hello".getBytes());

        String path = client.store(file, "product");

        String month = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
        // 반환 경로: /uploads/product/{yyyyMM}/{uuid}.jpg (확장자 소문자)
        assertThat(path).startsWith(URL_PREFIX + "/product/" + month + "/");
        assertThat(path).endsWith(".jpg");

        // 실제 파일이 baseDir 아래에 존재해야 한다.
        Path stored = baseDir.resolve(path.substring(URL_PREFIX.length() + 1));
        assertThat(Files.exists(stored)).isTrue();
    }

    @Test
    void delete_removesStoredFile() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "cake.png", "image/png", "bytes".getBytes());
        String path = client.store(file, "review");
        Path stored = baseDir.resolve(path.substring(URL_PREFIX.length() + 1));
        assertThat(Files.exists(stored)).isTrue();

        client.delete(path);

        assertThat(Files.exists(stored)).isFalse();
    }

    @Test
    void store_rejectsEmptyFile() {
        MockMultipartFile empty = new MockMultipartFile(
                "file", "empty.jpg", "image/jpeg", new byte[0]);

        assertThatThrownBy(() -> client.store(empty, "product"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void store_rejectsPathTraversalDirectory() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "cake.jpg", "image/jpeg", "x".getBytes());

        assertThatThrownBy(() -> client.store(file, "../escape"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void delete_ignoresPathOutsidePrefix() {
        // url-prefix 로 시작하지 않는 경로는 조용히 무시한다 (예외 없음).
        client.delete("/etc/passwd");
        client.delete(null);
    }
}

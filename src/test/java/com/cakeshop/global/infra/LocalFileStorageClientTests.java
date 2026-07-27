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

    /**
     * 저장 확장자는 클라이언트가 보낸 <b>파일명이 아니라 content type</b>에서 나온다.
     *
     * <p>{@code /uploads/**} 는 공개 서빙되고 Spring 리소스 핸들러는 확장자로 Content-Type 을
     * 정한다. 파일명을 믿으면 {@code image/png} 로 선언한 채 이름만 {@code .html} 로 보내
     * 같은 오리진에서 HTML 을 실행시킬 수 있다.
     */
    @Test
    void store_확장자는_파일명이_아니라_contentType에서_나온다() {
        MockMultipartFile disguised = new MockMultipartFile(
                "file", "payload.html", "image/png", "<script>alert(1)</script>".getBytes());

        String path = client.store(disguised, "review");

        assertThat(path).endsWith(".png");
        assertThat(path).doesNotContain(".html");
    }

    @Test
    void store_svg처럼_실행가능한_형식은_거부한다() {
        MockMultipartFile svg = new MockMultipartFile(
                "file", "logo.svg", "image/svg+xml", "<svg onload=\"alert(1)\"/>".getBytes());

        assertThatThrownBy(() -> client.store(svg, "store"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void store_매핑에_없는_형식은_거부한다() {
        MockMultipartFile pdf = new MockMultipartFile(
                "file", "spec.pdf", "application/pdf", "%PDF".getBytes());
        MockMultipartFile noType = new MockMultipartFile(
                "file", "cake.jpg", null, "bytes".getBytes());

        assertThatThrownBy(() -> client.store(pdf, "product"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> client.store(noType, "product"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

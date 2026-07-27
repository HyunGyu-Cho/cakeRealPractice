package com.cakeshop.support;

import org.springframework.mock.web.MockMultipartFile;

/**
 * 테스트용 업로드 이미지.
 *
 * <p>{@code ImageValidator}가 <b>실제 파일 머리 바이트</b>를 검사하므로 아무 바이트나 담은
 * {@code MockMultipartFile}은 검증을 통과하지 못한다. 여기서 진짜 시그니처를 붙여 준다.
 */
public final class TestImages {

    /** JPEG SOI + 마커. */
    private static final byte[] JPEG_HEADER = {(byte) 0xff, (byte) 0xd8, (byte) 0xff, (byte) 0xe0};

    /** PNG 8바이트 시그니처. */
    private static final byte[] PNG_HEADER = {
        (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};

    private TestImages() {
    }

    public static MockMultipartFile jpeg(String field, String filename) {
        return new MockMultipartFile(field, filename, "image/jpeg", JPEG_HEADER.clone());
    }

    public static MockMultipartFile jpeg(String field) {
        return jpeg(field, "cake.jpg");
    }

    public static MockMultipartFile png(String field, String filename) {
        return new MockMultipartFile(field, filename, "image/png", PNG_HEADER.clone());
    }

    public static MockMultipartFile png(String field) {
        return png(field, "cake.png");
    }

    /** 크기 위반을 만들기 위한 큰 JPEG. 머리 바이트는 유효하다. */
    public static MockMultipartFile oversizedJpeg(String field, int size) {
        byte[] body = new byte[size];
        System.arraycopy(JPEG_HEADER, 0, body, 0, JPEG_HEADER.length);
        return new MockMultipartFile(field, "big.jpg", "image/jpeg", body);
    }

    /** content type 은 이미지라고 하지만 내용은 아닌 위장 파일. */
    public static MockMultipartFile disguised(String field, String filename, String contentType) {
        return new MockMultipartFile(field, filename, contentType,
            "<script>alert(1)</script>".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}

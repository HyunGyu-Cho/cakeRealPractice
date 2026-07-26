package com.cakeshop.global.infra;

import static org.assertj.core.api.Assertions.assertThat;

import com.cakeshop.global.infra.ImageValidator.Violation;
import com.cakeshop.support.TestImages;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

/**
 * 원래 chat 에만 있던 매직바이트 검사를 전 도메인 공통으로 올린 검증기.
 * product·store 는 {@code startsWith("image/")} 만 봐서 {@code image/svg+xml} 도 통과했다.
 */
class ImageValidatorTests {

    private final ImageValidator validator = new ImageValidator();

    @Test
    void 정상_jpeg와_png는_통과한다() {
        assertThat(validator.validate(TestImages.jpeg("image"))).isNull();
        assertThat(validator.validate(TestImages.png("image"))).isNull();
    }

    @Test
    void 허용목록에_없는_형식은_TYPE_위반이다() {
        assertThat(validator.validate(new MockMultipartFile(
            "image", "logo.svg", "image/svg+xml", "<svg/>".getBytes()))).isEqualTo(Violation.TYPE);
        assertThat(validator.validate(new MockMultipartFile(
            "image", "note.txt", "text/plain", "hello".getBytes()))).isEqualTo(Violation.TYPE);
        assertThat(validator.validate(new MockMultipartFile(
            "image", "cake.jpg", null, "bytes".getBytes()))).isEqualTo(Violation.TYPE);
    }

    /** content type 은 클라이언트가 정하는 값이라 그것만으로는 부족하다. */
    @Test
    void 형식은_이미지라고_하지만_내용이_아니면_CONTENT_위반이다() {
        assertThat(validator.validate(TestImages.disguised("image", "x.png", "image/png")))
            .isEqualTo(Violation.CONTENT);
        // png 시그니처를 jpeg 라고 선언한 경우도 잡는다.
        MockMultipartFile mismatched = new MockMultipartFile("image", "x.jpg", "image/jpeg",
            new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a});
        assertThat(validator.validate(mismatched)).isEqualTo(Violation.CONTENT);
    }

    @Test
    void 상한을_넘으면_SIZE_위반이_먼저_나온다() {
        assertThat(validator.validate(TestImages.oversizedJpeg("image", 1024), 512))
            .isEqualTo(Violation.SIZE);
    }

    @Test
    void 빈_파일은_CONTENT_위반이다() {
        assertThat(validator.validate(new MockMultipartFile("image", new byte[0])))
            .isEqualTo(Violation.CONTENT);
        assertThat(validator.validate(null)).isEqualTo(Violation.CONTENT);
    }
}

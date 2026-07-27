package com.cakeshop.global.infra;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * 업로드 이미지 한 장을 검증한다 — 선언된 content type, 크기, 그리고 <b>실제 파일 머리 바이트</b>.
 *
 * <p>content type 은 클라이언트가 보내는 값이라 그대로 믿을 수 없다. 그래서 매직바이트까지 본다.
 * 원래 이 검사는 chat 에만 있었고 다른 4개 도메인은 content type 만 봤다(product·store 는
 * {@code startsWith("image/")} 라 {@code image/svg+xml} 도 통과했다). 판정을 한자리로 모은다.
 *
 * <p><b>예외를 던지지 않는다.</b> global 은 도메인 {@code ErrorCode}를 모르므로 위반 사유만
 * {@link Violation} 으로 돌려주고, 각 도메인이 자기 {@code ErrorCode}로 예외를 만든다
 * (도메인마다 다른 안내 문구를 유지하기 위해서다).
 */
@Component
public class ImageValidator {

    /** 저장·서빙까지 안전하다고 판단한 형식. {@code LocalFileStorageClient}의 확장자 매핑과 짝을 이룬다. */
    public static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png");

    public static final long DEFAULT_MAX_SIZE = 5L * 1024 * 1024;

    public enum Violation {
        /** 선언된 content type 이 허용 목록에 없다. */
        TYPE,
        /** 크기 상한 초과. */
        SIZE,
        /** 선언된 형식과 실제 파일 내용이 다르다(위장 업로드). */
        CONTENT
    }

    /** 위반이 없으면 {@code null}. 상한은 {@link #DEFAULT_MAX_SIZE}. */
    public Violation validate(MultipartFile image) {
        return validate(image, DEFAULT_MAX_SIZE);
    }

    public Violation validate(MultipartFile image, long maxSize) {
        if (image == null || image.isEmpty()) {
            return Violation.CONTENT;
        }
        if (image.getSize() > maxSize) {
            return Violation.SIZE;
        }
        String declared = image.getContentType();
        String normalized = declared == null ? null : declared.toLowerCase(Locale.ROOT);
        if (normalized == null || !ALLOWED_CONTENT_TYPES.contains(normalized)) {
            return Violation.TYPE;
        }
        return matchesMagicBytes(image, normalized) ? null : Violation.CONTENT;
    }

    private boolean matchesMagicBytes(MultipartFile image, String contentType) {
        byte[] header = new byte[8];
        int read;
        try (InputStream input = image.getInputStream()) {
            read = input.read(header);
        } catch (IOException e) {
            return false;
        }
        return switch (contentType) {
            case "image/jpeg" -> read >= 3
                && (header[0] & 0xff) == 0xff && (header[1] & 0xff) == 0xd8
                && (header[2] & 0xff) == 0xff;
            case "image/png" -> read >= 8
                && (header[0] & 0xff) == 0x89 && header[1] == 0x50
                && header[2] == 0x4e && header[3] == 0x47
                && header[4] == 0x0d && header[5] == 0x0a
                && header[6] == 0x1a && header[7] == 0x0a;
            default -> false;
        };
    }
}

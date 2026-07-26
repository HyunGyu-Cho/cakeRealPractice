package com.cakeshop.domain.order.dto.form;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.multipart.MultipartFile;

/**
 * 주문제작 요청서 입력. 초안을 저장하지 않으므로 제출 한 번에 필요한 값이 모두 들어온다.
 * 선택한 옵션의 추가 금액은 여기서 받지 않는다 — 서버가 product_options에서 다시 읽는다.
 */
@Getter
@Setter
public class CustomOrderForm {

    @NotNull(message = "주문제작 상품을 선택해 주세요.")
    private Long productId;

    /** 선택한 product_options.id 목록. 필수 그룹 충족 여부는 service가 검증한다. */
    private List<Long> optionIds;

    @Size(max = 20, message = "레터링 문구는 20자 이내로 입력해 주세요.")
    private String lettering;

    @Size(max = 1000, message = "요청사항은 1000자 이내로 입력해 주세요.")
    private String requirements;

    /** 참고 이미지 최대 3장(JPG·PNG, 장당 5MB). 개수·형식·크기는 service가 검증한다. */
    private List<MultipartFile> referenceImages;

    @NotNull(message = "희망 픽업 일시를 선택해 주세요.")
    @Future(message = "희망 픽업 일시는 현재 이후여야 합니다.")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime pickupAt;

    @PositiveOrZero(message = "희망 예산은 0원 이상으로 입력해 주세요.")
    private Long desiredBudget;
}

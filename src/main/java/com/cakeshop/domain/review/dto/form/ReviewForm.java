package com.cakeshop.domain.review.dto.form;

import com.cakeshop.domain.review.entity.Review;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

/**
 * 후기 작성·수정 입력.
 *
 * <p>종합 평점만 필수이고 세부 3축은 비워둘 수 있다 — 네 축을 모두 강제하면 작성률이 떨어진다
 * (스펙 6장 규칙 3). 이미지는 별도 MultipartFile로 받는다(store·주문제작 패턴).
 */
@Getter
@Setter
public class ReviewForm {

    /** 수정 화면에서 어떤 주문 항목의 후기인지 유지하기 위한 값. 작성 시 쿼리 파라미터로 들어온다. */
    private Long orderItemId;

    @NotNull(message = "종합 평점을 선택해 주세요.")
    @Min(value = 1, message = "평점은 1점 이상이어야 합니다.")
    @Max(value = 5, message = "평점은 5점 이하여야 합니다.")
    private Integer overallRating;

    @Min(value = 1, message = "평점은 1점 이상이어야 합니다.")
    @Max(value = 5, message = "평점은 5점 이하여야 합니다.")
    private Integer tasteRating;

    @Min(value = 1, message = "평점은 1점 이상이어야 합니다.")
    @Max(value = 5, message = "평점은 5점 이하여야 합니다.")
    private Integer designRating;

    @Min(value = 1, message = "평점은 1점 이상이어야 합니다.")
    @Max(value = 5, message = "평점은 5점 이하여야 합니다.")
    private Integer serviceRating;

    @Size(min = 10, max = 2000, message = "후기 내용은 10자 이상 2,000자 이하로 입력해 주세요.")
    private String content;

    private List<MultipartFile> images;

    public static ReviewForm from(Review review) {
        ReviewForm form = new ReviewForm();
        form.setOrderItemId(review.getOrderItemId());
        form.setOverallRating(review.getOverallRating());
        form.setTasteRating(review.getTasteRating());
        form.setDesignRating(review.getDesignRating());
        form.setServiceRating(review.getServiceRating());
        form.setContent(review.getContent());
        return form;
    }
}

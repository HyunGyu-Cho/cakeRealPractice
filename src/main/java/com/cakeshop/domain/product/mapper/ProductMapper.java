package com.cakeshop.domain.product.mapper;

import com.cakeshop.domain.product.dto.form.AdminProductSearchForm;
import com.cakeshop.domain.product.dto.form.ProductSearchForm;
import com.cakeshop.domain.product.dto.view.LowStockProductView;
import com.cakeshop.domain.product.dto.view.ProductSummaryRow;
import com.cakeshop.domain.product.dto.view.ProductTypeCountRow;
import com.cakeshop.domain.product.entity.Category;
import com.cakeshop.domain.product.entity.Product;
import com.cakeshop.domain.product.entity.ProductImage;
import com.cakeshop.domain.product.entity.ProductOption;
import com.cakeshop.domain.product.entity.ProductOptionGroup;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ProductMapper {

    // ---- 고객 목록 (카운트·목록이 같은 WHERE를 공유한다 — community 페이징 패턴) ----
    long countProducts(@Param("cond") ProductSearchForm cond);

    List<ProductSummaryRow> findProductPage(@Param("cond") ProductSearchForm cond,
                                            @Param("size") int size, @Param("offset") int offset);

    // ---- 관리자 목록 ----
    long countAdminProducts(@Param("cond") AdminProductSearchForm cond);

    List<ProductSummaryRow> findAdminProductPage(@Param("cond") AdminProductSearchForm cond,
                                                 @Param("size") int size, @Param("offset") int offset);

    // ---- 단건·홈 ----
    Optional<Product> findProductById(@Param("id") Long id);

    List<ProductSummaryRow> findLatestActiveProducts(@Param("limit") int limit);

    /** 홈 노출용 — 판매 가능 상품을 후기 수·평점 순으로. 목록 화면의 sort=popular와 같은 기준이다. */
    List<ProductSummaryRow> findPopularActiveProducts(@Param("limit") int limit);

    /** 홈 카테고리 카드용 — 판매 가능 상품 수를 product_type별로 집계한다. */
    List<ProductTypeCountRow> countActiveByProductType();

    // ---- 쓰기 ----
    int insertProduct(Product product);

    int updateProduct(Product product);

    int updateStatus(@Param("id") Long id, @Param("status") String status);

    int decreaseStockIfAvailable(@Param("id") Long id, @Param("quantity") int quantity);

    int increaseStock(@Param("id") Long id, @Param("quantity") int quantity);

    /** 후기 집계 반영. 값은 review 도메인이 계산해 넘긴다. */
    int updateRatingStats(@Param("id") Long id,
                          @Param("reviewCount") long reviewCount,
                          @Param("averageRating") BigDecimal averageRating);

    // ---- 옵션 (order(수제) 요청서가 공개 계약으로 사용한다) ----
    List<ProductOptionGroup> findOptionGroups(@Param("productId") Long productId);

    /** 그룹 여러 개의 ACTIVE 옵션을 한 번에 읽는다(그룹마다 조회하지 않는다). */
    List<ProductOption> findActiveOptionsByGroupIds(@Param("groupIds") Collection<Long> groupIds);

    // ---- 카테고리 (1차: product_type과 코드 1:1) ----
    Optional<Category> findCategoryByCode(@Param("code") String code);

    // ---- 대표 이미지 (sort_order = 0 한 행) ----
    Optional<ProductImage> findMainImage(@Param("productId") Long productId);

    int insertProductImage(ProductImage image);

    int updateProductImageUrl(@Param("id") Long id, @Param("imageUrl") String imageUrl);

    // ---- 재고 부족 (statistics 대시보드가 공개 계약으로 사용한다) ----
    List<LowStockProductView> findLowStockProducts(@Param("threshold") int threshold);
}

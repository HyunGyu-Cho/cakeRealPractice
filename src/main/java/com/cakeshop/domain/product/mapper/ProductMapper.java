package com.cakeshop.domain.product.mapper;

import com.cakeshop.domain.product.dto.form.AdminProductSearchForm;
import com.cakeshop.domain.product.dto.form.ProductSearchForm;
import com.cakeshop.domain.product.dto.view.ProductSummaryRow;
import com.cakeshop.domain.product.entity.Category;
import com.cakeshop.domain.product.entity.Product;
import com.cakeshop.domain.product.entity.ProductImage;
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

    // ---- 쓰기 ----
    int insertProduct(Product product);

    int updateProduct(Product product);

    int updateStatus(@Param("id") Long id, @Param("status") String status);

    // ---- 카테고리 (1차: product_type과 코드 1:1) ----
    Optional<Category> findCategoryByCode(@Param("code") String code);

    // ---- 대표 이미지 (sort_order = 0 한 행) ----
    Optional<ProductImage> findMainImage(@Param("productId") Long productId);

    int insertProductImage(ProductImage image);

    int updateProductImageUrl(@Param("id") Long id, @Param("imageUrl") String imageUrl);
}

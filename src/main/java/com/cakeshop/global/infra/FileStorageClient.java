package com.cakeshop.global.infra;

import org.springframework.web.multipart.MultipartFile;

// 상품·주문제작·채팅·후기·커뮤니티 공용 파일 저장 인터페이스
public interface FileStorageClient {

    // 저장 경로 규칙: /{도메인}/{yyyyMM}/{uuid}.{ext}
    String store(MultipartFile file, String directory);

    void delete(String path);
}

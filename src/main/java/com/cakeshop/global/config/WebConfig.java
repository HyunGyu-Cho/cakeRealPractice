package com.cakeshop.global.config;

import java.nio.file.Path;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final String uploadDir;
    private final String urlPrefix;

    public WebConfig(
            @Value("${app.file.upload-dir}") String uploadDir,
            @Value("${app.file.url-prefix}") String urlPrefix) {
        this.uploadDir = uploadDir;
        this.urlPrefix = urlPrefix;
    }

    // 업로드된 파일을 {url-prefix}/** URL 로 외부 디렉토리에서 서빙한다.
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = Path.of(uploadDir).toAbsolutePath().normalize().toUri().toString();
        if (!location.endsWith("/")) {
            location = location + "/";
        }
        String pattern = urlPrefix.endsWith("/") ? urlPrefix + "**" : urlPrefix + "/**";
        registry.addResourceHandler(pattern)
                .addResourceLocations(location);
    }
}

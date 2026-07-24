package com.cakeshop.global.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan("com.cakeshop.domain")
public class MyBatisConfig {
}

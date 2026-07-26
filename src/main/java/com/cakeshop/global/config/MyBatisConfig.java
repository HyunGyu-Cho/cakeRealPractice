package com.cakeshop.global.config;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis 매퍼 스캔.
 *
 * <p>{@code annotationClass}를 지정하지 않으면 {@code com.cakeshop.domain} 아래의 <b>모든 인터페이스</b>가
 * 매퍼로 등록된다. 도메인 간 연동용 인터페이스(예: store의 {@code PickupReservationPort})까지
 * MyBatis 프록시로 바뀌어 실제 구현 빈을 밀어내고, 호출 시점에야
 * "Invalid bound statement" 로 터진다. {@code @Mapper}가 붙은 것만 매퍼로 본다.
 */
@Configuration
@MapperScan(basePackages = "com.cakeshop.domain", annotationClass = Mapper.class)
public class MyBatisConfig {
}

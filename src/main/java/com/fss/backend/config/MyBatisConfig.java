package com.fss.backend.config;

import org.mybatis.spring.annotation.MapperScan;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan(basePackages = "com.fss.backend", annotationClass = Mapper.class)
public class MyBatisConfig {
}

package com.zhijing;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.zhijing.mapper")
public class ZhijingBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(ZhijingBackendApplication.class, args);
    }
}

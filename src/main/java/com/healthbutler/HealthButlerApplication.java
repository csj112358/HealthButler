package com.healthbutler;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.healthbutler.mapper")
public class HealthButlerApplication {

    public static void main(String[] args) {
        SpringApplication.run(HealthButlerApplication.class, args);
    }
}
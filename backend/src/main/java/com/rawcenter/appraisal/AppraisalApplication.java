package com.rawcenter.appraisal;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.rawcenter.appraisal.mapper")
public class AppraisalApplication {
    public static void main(String[] args) {
        SpringApplication.run(AppraisalApplication.class, args);
    }
}

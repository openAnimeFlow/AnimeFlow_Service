package com.ligg.flow_bootstrap;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(value = "com.ligg")
@MapperScan("com.ligg.**.mapper")
public class AnimeFlowApplication {

    public static void main(String[] args) {
        SpringApplication.run(AnimeFlowApplication.class, args);
    }
}

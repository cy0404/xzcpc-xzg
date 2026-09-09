package com.xzcpc.mp;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication(scanBasePackages = "com.xzcpc")
@MapperScan("com.xzcpc.**.mapper")
@EnableAsync
public class InventoryMpApplication {

    public static void main(String[] args) {
        SpringApplication.run(InventoryMpApplication.class, args);
    }
}

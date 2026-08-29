package com.xzcpc.mp;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.xzcpc")
@MapperScan("com.xzcpc.**.mapper")
@EnableAsync
@EnableScheduling
public class InventoryMpApplication {

    public static void main(String[] args) {
        SpringApplication.run(InventoryMpApplication.class, args);
    }
}

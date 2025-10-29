package com.lumine3.luminapicturebackend;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("com.lumine3.luminapicturebackend.mapper")
@EnableAspectJAutoProxy(exposeProxy = true)
public class LuminaPictureBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(LuminaPictureBackendApplication.class, args);
    }

}

package com.edf.teamedf;

import com.edf.teamedf.common.config.CoolSmsConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(CoolSmsConfig.class)
public class EdfBeApplication {

    public static void main(String[] args) {
        SpringApplication.run(EdfBeApplication.class, args);
    }

}

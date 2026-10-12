package com.edf.teamedf;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling // 뉴스 자동 수집(NewsFetchScheduler) 등 배치 스케줄링에 필요
public class EdfBeApplication {

    public static void main(String[] args) {
        SpringApplication.run(EdfBeApplication.class, args);
    }

}

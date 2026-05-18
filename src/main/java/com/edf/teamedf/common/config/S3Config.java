package com.edf.teamedf.common.config;

/*
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
public class S3Config {

    @Value("${file.upload.s3-region}")
    private String region;

    @Bean
    public S3Client s3Client() {
        return S3Client.builder()
                .region(Region.of(region))
                // EC2/ECS 환경에서는 IAM Role, 로컬에서는 ~/.aws/credentials 자동 사용
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }
}

*/

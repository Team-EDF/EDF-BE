package com.edf.teamedf.common.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@Getter
@Setter
@ConfigurationProperties(prefix = "file.upload")
public class FileStorageProperties {

    /** 로컬 저장 디렉터리 경로 */
    private String localDir = "./uploads";

    /** 업로드 허용 최대 파일 크기 (MB) */
    private int maxSizeMb = 10;

    /** 허용 MIME 타입 목록 */
    private List<String> allowedTypes = List.of("image/jpeg", "image/png", "image/webp", "image/gif");

    /** 로컬 파일 서빙 URL 접두어 */
    private String urlPrefix = "/files";



    // private String s3BucketName;
    // private String s3Region;

}

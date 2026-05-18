package com.edf.teamedf.common.file;

public record StoredFile(
        String originalFileName,
        String storedFileName,  // UUID 기반 저장 파일명
        String filePath,        // 로컬: 디렉터리 경로 / S3: 버킷 키
        String fileUrl,         // 실제 접근 URL
        long fileSize,
        String contentType
) {}

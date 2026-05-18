package com.edf.teamedf.common.file;

/*
import com.edf.teamedf.common.config.FileStorageProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class S3FileStorageService implements FileStorageService {

    private final S3Client s3Client;
    private final FileStorageProperties properties;

    @Override
    public StoredFile store(MultipartFile file) {
        validate(file);

        String originalFileName = StringUtils.cleanPath(
                file.getOriginalFilename() != null ? file.getOriginalFilename() : "unknown"
        );
        String storedFileName = UUID.randomUUID() + extractExtension(originalFileName);

        try {
            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(properties.getS3BucketName())
                    .key(storedFileName)
                    .contentType(file.getContentType())
                    .contentLength(file.getSize())
                    .build();

            s3Client.putObject(putRequest,
                    RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "S3 업로드 중 오류가 발생했습니다.");
        }

        String fileUrl = "https://" + properties.getS3BucketName()
                + ".s3." + properties.getS3Region() + ".amazonaws.com/" + storedFileName;

        return new StoredFile(
                originalFileName,
                storedFileName,
                storedFileName,
                fileUrl,
                file.getSize(),
                file.getContentType()
        );
    }

    @Override
    public void delete(String storedFileName) {
        DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                .bucket(properties.getS3BucketName())
                .key(storedFileName)
                .build();
        s3Client.deleteObject(deleteRequest);
    }

    private void validate(MultipartFile file) {
        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "파일이 비어있습니다.");
        }
        long maxBytes = (long) properties.getMaxSizeMb() * 1024 * 1024;
        if (file.getSize() > maxBytes) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "파일 크기는 " + properties.getMaxSizeMb() + "MB를 초과할 수 없습니다.");
        }
        String contentType = file.getContentType();
        if (contentType == null || !properties.getAllowedTypes().contains(contentType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "허용되지 않는 파일 형식입니다. 허용 형식: " + String.join(", ", properties.getAllowedTypes()));
        }
    }

    private String extractExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot).toLowerCase() : "";
    }
}

*/

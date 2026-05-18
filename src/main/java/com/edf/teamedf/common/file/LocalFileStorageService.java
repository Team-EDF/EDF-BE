package com.edf.teamedf.common.file;

import com.edf.teamedf.common.config.FileStorageProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * 로컬 디스크에 파일을 저장하는 구현체.
 * S3 전환 시: 이 클래스의 @Primary 제거 후 S3FileStorageService의 @Primary 활성화.
 */
@Service
@Primary
@RequiredArgsConstructor
public class LocalFileStorageService implements FileStorageService {

    private final FileStorageProperties properties;

    @Override
    public StoredFile store(MultipartFile file) {
        validate(file);

        String originalFileName = StringUtils.cleanPath(
                file.getOriginalFilename() != null ? file.getOriginalFilename() : "unknown"
        );
        String storedFileName = UUID.randomUUID() + extractExtension(originalFileName);
        Path uploadDir = Paths.get(properties.getLocalDir()).toAbsolutePath().normalize();

        try {
            Files.createDirectories(uploadDir);
            Files.copy(file.getInputStream(), uploadDir.resolve(storedFileName), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "파일 저장 중 오류가 발생했습니다.");
        }

        return new StoredFile(
                originalFileName,
                storedFileName,
                uploadDir.toString(),
                properties.getUrlPrefix() + "/" + storedFileName,
                file.getSize(),
                file.getContentType()
        );
    }

    @Override
    public void delete(String storedFileName) {
        try {
            Path target = Paths.get(properties.getLocalDir()).toAbsolutePath().normalize().resolve(storedFileName);
            Files.deleteIfExists(target);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "파일 삭제 중 오류가 발생했습니다.");
        }
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

package com.edf.teamedf.domain.image.command.application.service;

import com.edf.teamedf.common.file.FileStorageService;
import com.edf.teamedf.common.file.StoredFile;
import com.edf.teamedf.domain.image.command.application.dto.ImageUploadResponse;
import com.edf.teamedf.domain.image.command.domain.ReferenceType;
import com.edf.teamedf.domain.image.command.domain.StorageType;
import com.edf.teamedf.domain.image.command.domain.UploadedImage;
import com.edf.teamedf.domain.image.command.infrastructure.UploadedImageRepository;
import com.edf.teamedf.domain.user.command.domain.User;
import com.edf.teamedf.domain.user.command.infrastructure.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ImageUploadService {

    private final FileStorageService fileStorageService;
    private final UploadedImageRepository uploadedImageRepository;
    private final UserRepository userRepository;

    @Transactional
    public ImageUploadResponse upload(Long userId, MultipartFile file,
                                      ReferenceType referenceType, Long referenceId) {
        User user = getUser(userId);
        StoredFile stored = fileStorageService.store(file);

        UploadedImage image = UploadedImage.builder()
                .user(user)
                .originalFileName(stored.originalFileName())
                .storedFileName(stored.storedFileName())
                .filePath(stored.filePath())
                .fileUrl(stored.fileUrl())
                .fileSize(stored.fileSize())
                .contentType(stored.contentType())
                .storageType(StorageType.LOCAL)  // S3 전환 시 StorageType.S3 로 변경
                .referenceType(referenceType)
                .referenceId(referenceId)
                .build();

        return ImageUploadResponse.from(uploadedImageRepository.save(image));
    }

    @Transactional
    public void delete(Long userId, Long imageId) {
        UploadedImage image = uploadedImageRepository.findByImageIdAndUser_UserId(imageId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "이미지를 찾을 수 없거나 삭제 권한이 없습니다."));

        fileStorageService.delete(image.getStoredFileName());
        uploadedImageRepository.delete(image);
    }

    public List<ImageUploadResponse> getImagesByReference(ReferenceType referenceType, Long referenceId) {
        return uploadedImageRepository.findByReferenceTypeAndReferenceId(referenceType, referenceId)
                .stream()
                .map(ImageUploadResponse::from)
                .toList();
    }

    public List<ImageUploadResponse> getMyImages(Long userId) {
        return uploadedImageRepository.findByUser_UserId(userId)
                .stream()
                .map(ImageUploadResponse::from)
                .toList();
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."));
    }
}

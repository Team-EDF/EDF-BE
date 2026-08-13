package com.edf.teamedf.domain.image.command.application.dto;

import com.edf.teamedf.domain.image.command.domain.ReferenceType;
import com.edf.teamedf.domain.image.command.domain.StorageType;
import com.edf.teamedf.domain.image.command.domain.UploadedImage;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;

public record ImageUploadResponse(
        Long imageId,
        String originalFileName,
        String fileUrl,
        long fileSize,
        String contentType,
        StorageType storageType,
        ReferenceType referenceType,
        Long referenceId,
        LocalDateTime createdAt,
        String ocrText
) {
    public static ImageUploadResponse from(UploadedImage image) {
        return from(image, null);
    }

    public static ImageUploadResponse from(UploadedImage image, String ocrText) {
        return new ImageUploadResponse(
                image.getImageId(),
                image.getOriginalFileName(),
                image.getFileUrl(),
                image.getFileSize(),
                image.getContentType(),
                image.getStorageType(),
                image.getReferenceType(),
                image.getReferenceId(),
                image.getCreatedAt(),
                ocrText
        );
    }

    /**
     * 영수증 업로드 응답. 이미지 저장은 AI(Python)가 담당하므로
     * 백엔드에 UploadedImage 엔티티가 존재하지 않는다 (imageId, storageType = null).
     * referenceId에는 소비기록(record_id)을 담아, 이후 /api/chat 호출 시 사용하게 한다.
     */
    public static ImageUploadResponse ofReceipt(
            MultipartFile file,
            String imageUrl,
            Long recordId,
            String ocrText
    ) {
        return new ImageUploadResponse(
                null,
                file.getOriginalFilename(),
                imageUrl,
                file.getSize(),
                file.getContentType(),
                null,
                ReferenceType.CONSUMPTION_RECORD,
                recordId,
                LocalDateTime.now(),
                ocrText
        );
    }
}

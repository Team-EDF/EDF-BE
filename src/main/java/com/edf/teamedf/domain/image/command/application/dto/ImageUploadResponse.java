package com.edf.teamedf.domain.image.command.application.dto;

import com.edf.teamedf.domain.image.command.domain.ReferenceType;
import com.edf.teamedf.domain.image.command.domain.StorageType;
import com.edf.teamedf.domain.image.command.domain.UploadedImage;

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
        LocalDateTime createdAt
) {
    public static ImageUploadResponse from(UploadedImage image) {
        return new ImageUploadResponse(
                image.getImageId(),
                image.getOriginalFileName(),
                image.getFileUrl(),
                image.getFileSize(),
                image.getContentType(),
                image.getStorageType(),
                image.getReferenceType(),
                image.getReferenceId(),
                image.getCreatedAt()
        );
    }
}

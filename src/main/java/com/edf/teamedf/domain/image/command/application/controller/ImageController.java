package com.edf.teamedf.domain.image.command.application.controller;

import com.edf.teamedf.common.security.auth.UserPrincipal;
import com.edf.teamedf.domain.dashboard.command.application.RecordConfirmService;
import com.edf.teamedf.domain.image.command.application.dto.ImageUploadResponse;
import com.edf.teamedf.domain.image.command.application.service.ImageUploadService;
import com.edf.teamedf.domain.image.command.domain.ReferenceType;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/images")
@RequiredArgsConstructor
public class ImageController {

    private final ImageUploadService imageUploadService;
    private final RecordConfirmService recordConfirmService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImageUploadResponse> upload(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestPart("file") MultipartFile file,
            @RequestParam(required = false) ReferenceType referenceType,
            @RequestParam(required = false) Long referenceId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(imageUploadService.upload(principal.userId(), file, referenceType, referenceId));
    }

    @DeleteMapping("/{imageId}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long imageId) {
        imageUploadService.delete(principal.userId(), imageId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{imageId}/confirm")
    public ResponseEntity<Void> confirm(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long imageId) {
        recordConfirmService.confirmByImageId(imageId, principal.userId());
        return ResponseEntity.ok().build();
    }

    @GetMapping
    public ResponseEntity<List<ImageUploadResponse>> getByReference(
            @RequestParam ReferenceType referenceType,
            @RequestParam Long referenceId) {
        return ResponseEntity.ok(imageUploadService.getImagesByReference(referenceType, referenceId));
    }

    @GetMapping("/me")
    public ResponseEntity<List<ImageUploadResponse>> getMyImages(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(imageUploadService.getMyImages(principal.userId()));
    }
}

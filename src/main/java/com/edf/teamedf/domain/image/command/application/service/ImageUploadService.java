package com.edf.teamedf.domain.image.command.application.service;

import com.edf.teamedf.common.file.FileStorageService;
import com.edf.teamedf.common.file.StoredFile;
import com.edf.teamedf.domain.image.command.application.dto.ImageUploadResponse;
import com.edf.teamedf.domain.image.command.domain.ReferenceType;
import com.edf.teamedf.domain.image.command.domain.StorageType;
import com.edf.teamedf.domain.image.command.domain.UploadedImage;
import com.edf.teamedf.domain.image.command.infrastructure.UploadedImageRepository;
import com.edf.teamedf.domain.dashboard.command.domain.ConsumptionRecord;
import com.edf.teamedf.domain.dashboard.command.infrastructure.ConsumptionRecordRepository;
import com.edf.teamedf.domain.dashboard.command.domain.IntegratedStat;
import com.edf.teamedf.domain.dashboard.command.infrastructure.IntegratedStatRepository;
import com.edf.teamedf.domain.dashboard.command.domain.CategoryStat;
import com.edf.teamedf.domain.dashboard.command.infrastructure.CategoryStatRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.edf.teamedf.domain.dashboard.command.infrastructure.CategoryStatRepository;
import com.edf.teamedf.domain.user.command.domain.User;
import com.edf.teamedf.domain.user.command.infrastructure.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    private final ConsumptionRecordRepository consumptionRecordRepository;
    private final IntegratedStatRepository integratedStatRepository;
    private final CategoryStatRepository categoryStatRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

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

        UploadedImage savedImage = uploadedImageRepository.save(image);
        String ocrText = null;

        if (referenceType == ReferenceType.CONSUMPTION_RECORD) {
            try {
                org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
                headers.setContentType(org.springframework.http.MediaType.MULTIPART_FORM_DATA);

                org.springframework.util.MultiValueMap<String, Object> body = new org.springframework.util.LinkedMultiValueMap<>();
                body.add("image", new org.springframework.core.io.ByteArrayResource(file.getBytes()) {
                    @Override
                    public String getFilename() {
                        return file.getOriginalFilename();
                    }
                });

                org.springframework.http.HttpEntity<org.springframework.util.MultiValueMap<String, Object>> requestEntity = new org.springframework.http.HttpEntity<>(body, headers);
                org.springframework.web.client.RestTemplate restTemplate = new org.springframework.web.client.RestTemplate();
                Map<String, Object> response = restTemplate.postForObject("http://ai:8000/api/ocr/classify", requestEntity, Map.class);
                
                if (response != null) {
                    if (response.containsKey("ocr_raw_text") && response.get("ocr_raw_text") != null) {
                        ocrText = (String) response.get("ocr_raw_text");
                    }

                    Integer totalAmount = null;
                    if (response.containsKey("total_amount_krw") && response.get("total_amount_krw") != null) {
                        totalAmount = ((Number) response.get("total_amount_krw")).intValue();
                    }

                    Float totalCarbonKg = null;
                    if (response.containsKey("total_carbon_kg") && response.get("total_carbon_kg") != null) {
                        totalCarbonKg = ((Number) response.get("total_carbon_kg")).floatValue();
                    }

                    java.time.LocalDate recordDate = java.time.LocalDate.now();
                    if (response.containsKey("payment_date") && response.get("payment_date") != null) {
                        try {
                            String dateStr = (String) response.get("payment_date");
                            if (dateStr.length() > 10) dateStr = dateStr.substring(0, 10);
                            recordDate = java.time.LocalDate.parse(dateStr);
                        } catch (Exception ignored) {}
                    }

                    String ocrDataJson = null;
                    try {
                        ocrDataJson = objectMapper.writeValueAsString(response);
                    } catch (Exception e) {
                        System.err.println("Failed to serialize OCR data: " + e.getMessage());
                    }

                    ConsumptionRecord record = ConsumptionRecord.builder()
                            .user(user)
                            .sourceType("RECEIPT")
                            .rawText(ocrText)
                            .ocrData(ocrDataJson)
                            .imageUrl(savedImage.getFileUrl())
                            .ocrStatus("WAITING_CONFIRM")
                            .recordDate(recordDate)
                            .totalAmount(totalAmount)
                            .totalCarbonKg(totalCarbonKg)
                            .build();
                    consumptionRecordRepository.save(record);

                    // 통계 업데이트 로직은 RecordConfirmService로 위임됨

                }
            } catch (Exception e) {
                System.err.println("OCR extraction failed: " + e.getMessage());
                ConsumptionRecord record = ConsumptionRecord.builder()
                        .user(user)
                        .sourceType("RECEIPT")
                        .imageUrl(savedImage.getFileUrl())
                        .ocrStatus("FAILED")
                        .ocrErrorMessage(e.getMessage() != null && e.getMessage().length() > 255 ? e.getMessage().substring(0, 255) : e.getMessage())
                        .recordDate(java.time.LocalDate.now())
                        .build();
                consumptionRecordRepository.save(record);
            }
        }

        return ImageUploadResponse.from(savedImage, ocrText);
    }

    private void updateOrCreateCategoryStat(IntegratedStat monthlyStat, Long categoryId, String categoryName, Float carbon, Integer spending) {
        CategoryStat catStat = categoryStatRepository.findByIntegratedStat_StatId(monthlyStat.getStatId())
                .stream()
                .filter(cs -> cs.getCategoryId().equals(categoryId))
                .findFirst()
                .orElse(null);

        if (catStat == null) {
            catStat = CategoryStat.builder()
                    .integratedStat(monthlyStat)
                    .categoryId(categoryId)
                    .categoryName(categoryName)
                    .categoryCarbon(carbon)
                    .categorySpending(spending)
                    .percentage(0f)
                    .build();
        } else {
            catStat.setCategoryCarbon(catStat.getCategoryCarbon() + carbon);
            catStat.setCategorySpending(catStat.getCategorySpending() + spending);
        }
        categoryStatRepository.save(catStat);
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

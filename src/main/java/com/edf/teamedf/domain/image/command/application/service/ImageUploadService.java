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
import com.edf.teamedf.domain.user.command.domain.User;
import com.edf.teamedf.domain.user.command.infrastructure.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ImageUploadService {

    private final FileStorageService fileStorageService;
    private final UploadedImageRepository uploadedImageRepository;
    private final UserRepository userRepository;
    private final ConsumptionRecordRepository consumptionRecordRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${ai.service-url}")
    private String aiServiceUrl;

    @Transactional
    public ImageUploadResponse upload(Long userId, MultipartFile file,
                                      ReferenceType referenceType, Long referenceId) {
        User user = getUser(userId);

        // 영수증은 자바가 파일을 따로 저장하지 않는다. 이미지 저장/보관은 AI(Python)가 담당하고,
        // 자바는 AI 호출 오케스트레이션 + consumption_records 상태 관리만 한다.
        if (referenceType == ReferenceType.CONSUMPTION_RECORD) {
            return uploadReceipt(user, file);
        }

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
        return ImageUploadResponse.from(savedImage);
    }

    private ImageUploadResponse uploadReceipt(User user, MultipartFile file) {
        Map<String, Object> response;

        try {
            response = callAiOcrClassify(user.getUserId(), file);
        } catch (Exception e) {
            System.err.println("OCR extraction failed: " + e.getMessage());
            ConsumptionRecord record = saveFailedReceiptRecord(user, e.getMessage());
            return ImageUploadResponse.ofReceipt(file, null, record.getRecordId(), null);
        }

        if (response == null) {
            ConsumptionRecord record = saveFailedReceiptRecord(user, "AI로부터 응답을 받지 못했습니다.");
            return ImageUploadResponse.ofReceipt(file, null, record.getRecordId(), null);
        }

        String ocrText = response.get("ocr_raw_text") != null ? (String) response.get("ocr_raw_text") : null;
        String imageUrl = response.get("image_url") != null ? (String) response.get("image_url") : null;

        String ocrDataJson = null;
        try {
            ocrDataJson = objectMapper.writeValueAsString(response);
        } catch (Exception e) {
            System.err.println("Failed to serialize OCR data: " + e.getMessage());
        }

        Object recordIdRaw = response.get("record_id");
        ConsumptionRecord record;

        if (recordIdRaw != null) {
            // AI가 이미 consumption_records/items에 저장하고 이미지도 자기 스토리지(S3/로컬)에 저장했다.
            // 그 행을 그대로 이어받는다 - 백엔드가 따로 insert하면 record_id가 갈라지고
            // items 분류 정보 및 image_url이 유실된다.
            Long recordId = ((Number) recordIdRaw).longValue();
            record = consumptionRecordRepository.findById(recordId)
                    .orElseThrow(() -> new IllegalStateException(
                            "AI가 반환한 record_id의 레코드를 찾을 수 없습니다: " + recordId));

            record.setRawText(ocrText);
            record.setOcrData(ocrDataJson);
            record.setOcrStatus("WAITING_CONFIRM");
            // imageUrl은 AI가 저장 시점에 이미 채워뒀으므로 덮어쓰지 않는다.
        } else {
            // AI 쪽 분류/OCR은 성공했지만 AI 내부 DB 저장이 실패했을 때만 백엔드가 자체 레코드를 만든다.
            record = buildRecordFromAiResponse(user, imageUrl, response, ocrText, ocrDataJson, "WAITING_CONFIRM");
        }

        record = consumptionRecordRepository.save(record);

        return ImageUploadResponse.ofReceipt(file, record.getImageUrl(), record.getRecordId(), ocrText);
    }

    private ConsumptionRecord saveFailedReceiptRecord(User user, String errorMessage) {
        ConsumptionRecord record = ConsumptionRecord.builder()
                .user(user)
                .sourceType("RECEIPT")
                .ocrStatus("FAILED")
                .ocrErrorMessage(errorMessage != null && errorMessage.length() > 255
                        ? errorMessage.substring(0, 255) : errorMessage)
                .recordDate(LocalDate.now())
                .build();
        return consumptionRecordRepository.save(record);
    }

    private Map<String, Object> callAiOcrClassify(Long userId, MultipartFile file) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("image", new ByteArrayResource(file.getBytes()) {
            @Override
            public String getFilename() {
                return file.getOriginalFilename();
            }
        });
        // 백엔드가 JWT로 인증한 사용자 ID를 AI에 전달한다.
        // 안 보내면 AI가 consumption_records.user_id를 NULL로 저장해 사용자 소유 조회가 깨진다.
        body.add("user_id", userId);

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
        RestTemplate restTemplate = new RestTemplate();
        return restTemplate.postForObject(aiServiceUrl + "/api/ocr/classify", requestEntity, Map.class);
    }

    private ConsumptionRecord buildRecordFromAiResponse(
            User user,
            String imageUrl,
            Map<String, Object> response,
            String ocrText,
            String ocrDataJson,
            String ocrStatus
    ) {
        Integer totalAmount = response.get("total_amount_krw") != null
                ? ((Number) response.get("total_amount_krw")).intValue() : null;

        Float totalCarbonKg = response.get("total_carbon_kg") != null
                ? ((Number) response.get("total_carbon_kg")).floatValue() : null;

        LocalDate recordDate = LocalDate.now();
        if (response.get("payment_date") != null) {
            try {
                String dateStr = (String) response.get("payment_date");
                if (dateStr.length() > 10) dateStr = dateStr.substring(0, 10);
                recordDate = LocalDate.parse(dateStr);
            } catch (Exception ignored) {
            }
        }

        return ConsumptionRecord.builder()
                .user(user)
                .sourceType("RECEIPT")
                .rawText(ocrText)
                .ocrData(ocrDataJson)
                .imageUrl(imageUrl)
                .ocrStatus(ocrStatus)
                .recordDate(recordDate)
                .totalAmount(totalAmount)
                .totalCarbonKg(totalCarbonKg)
                .build();
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

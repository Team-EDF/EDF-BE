package com.edf.teamedf.domain.image.command.domain;

import com.edf.teamedf.domain.user.command.domain.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "uploaded_images")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class UploadedImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "image_id")
    private Long imageId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "original_file_name", length = 255, nullable = false)
    private String originalFileName;

    @Column(name = "stored_file_name", length = 255, nullable = false, unique = true)
    private String storedFileName;

    @Column(name = "file_path", length = 500, nullable = false)
    private String filePath;


    @Column(name = "file_url", length = 500, nullable = false)
    private String fileUrl;

    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    @Column(name = "content_type", length = 100, nullable = false)
    private String contentType;

    @Enumerated(EnumType.STRING)
    @Column(name = "storage_type", length = 20, nullable = false)
    private StorageType storageType;

    @Enumerated(EnumType.STRING)
    @Column(name = "reference_type", length = 50)
    private ReferenceType referenceType;

    @Column(name = "reference_id")
    private Long referenceId;

    /** 파일 내용의 SHA-256 (hex). 같은 사진을 다시 올려 인증을 반복하는 것을 막는 데 쓴다. */
    @Column(name = "content_hash", length = 64)
    private String contentHash;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    /** 이미지를 도메인 엔티티에 연결한다 (예: 활동 인증에 사용됨 → ECO_ACTIVITY + activityId). */
    public void attachTo(ReferenceType referenceType, Long referenceId) {
        this.referenceType = referenceType;
        this.referenceId = referenceId;
    }
}

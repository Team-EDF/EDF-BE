package com.edf.teamedf.domain.image.command.infrastructure;

import com.edf.teamedf.domain.image.command.domain.ReferenceType;
import com.edf.teamedf.domain.image.command.domain.UploadedImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UploadedImageRepository extends JpaRepository<UploadedImage, Long> {

    // 특정 도메인 엔티티에 연결된 이미지 목록 조회
    List<UploadedImage> findByReferenceTypeAndReferenceId(ReferenceType referenceType, Long referenceId);

    // 사용자의 전체 업로드 이미지 조회
    List<UploadedImage> findByUser_UserId(Long userId);

    // 사용자 소유 이미지 단건 조회 (삭제 권한 확인용)
    Optional<UploadedImage> findByImageIdAndUser_UserId(Long imageId, Long userId);

    // 같은 내용의 사진이 이미 활동 인증에 쓰였는지 (사용자 구분 없이 확인한다)
    boolean existsByContentHashAndReferenceTypeAndReferenceIdIsNotNull(String contentHash, ReferenceType referenceType);
}

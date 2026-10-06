package com.edf.teamedf.domain.user.command.application.service;

import com.edf.teamedf.common.file.FileStorageService;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

/**
 * 회원 탈퇴 시 계정에 연결된 개인 데이터를 삭제한다.
 *
 * 삭제: 알림, 좋아요, 차단 관계, 소비 기록·품목(영수증), 통계, 랭킹, 친환경 활동 인증, 업로드 이미지(파일 포함)
 * 유지: 게시글·댓글 (작성자는 User.withdraw() 에서 '탈퇴한 회원'으로 익명화), 신고 이력(운영 기록)
 *
 * 벌크 delete 를 쓰므로 호출 뒤에는 영속성 컨텍스트가 비워진다. 호출한 쪽은 엔티티를 다시 조회해야 한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserDataCleanupService {

    private final EntityManager em;
    private final FileStorageService fileStorageService;

    @Transactional(propagation = Propagation.MANDATORY)
    public void deleteUserData(Long userId) {
        em.flush();

        // 저장소(S3/로컬)의 파일은 DB 삭제가 커밋된 뒤에 지운다.
        List<String> storedFileNames = em.createQuery(
                        "select i.storedFileName from UploadedImage i where i.user.userId = :userId", String.class)
                .setParameter("userId", userId)
                .getResultList();

        delete("delete from Notification n where n.user.userId = :userId", userId);

        // 좋아요: 게시글의 좋아요 수를 먼저 되돌린 뒤 삭제한다.
        delete("""
                update Post p set p.likeCount = p.likeCount - 1
                where p.likeCount > 0
                  and p.postId in (select l.post.postId from PostLike l where l.user.userId = :userId)
                """, userId);
        delete("delete from PostLike l where l.user.userId = :userId", userId);

        delete("delete from UserBlock b where b.blocker.userId = :userId or b.blocked.userId = :userId", userId);

        // 소비 기록(영수증 OCR) → 품목이 기록을 참조하므로 품목부터.
        delete("""
                delete from ConsumptionItem i
                where i.record.recordId in (
                    select r.recordId from ConsumptionRecord r where r.user.userId = :userId)
                """, userId);
        delete("delete from ConsumptionRecord r where r.user.userId = :userId", userId);

        // 통계 → 카테고리 통계가 통합 통계를 참조하므로 카테고리 통계부터.
        delete("""
                delete from CategoryStat c
                where c.integratedStat.statId in (
                    select s.statId from IntegratedStat s where s.user.userId = :userId)
                """, userId);
        delete("delete from IntegratedStat s where s.user.userId = :userId", userId);
        delete("delete from UserRanking r where r.user.userId = :userId", userId);

        delete("delete from EcoActivity a where a.user.userId = :userId", userId);
        delete("delete from UploadedImage i where i.user.userId = :userId", userId);

        em.clear();
        deleteFilesAfterCommit(userId, storedFileNames);
    }

    private void delete(String jpql, Long userId) {
        em.createQuery(jpql).setParameter("userId", userId).executeUpdate();
    }

    /** 파일 삭제 실패가 탈퇴 자체를 막지 않도록 커밋 후에 최선을 다해 지우고, 실패는 로그로 남긴다. */
    private void deleteFilesAfterCommit(Long userId, List<String> storedFileNames) {
        if (storedFileNames.isEmpty()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                for (String storedFileName : storedFileNames) {
                    try {
                        fileStorageService.delete(storedFileName);
                    } catch (RuntimeException e) {
                        log.warn("탈퇴 회원 파일 삭제 실패 (userId={}, file={})", userId, storedFileName, e);
                    }
                }
            }
        });
    }
}

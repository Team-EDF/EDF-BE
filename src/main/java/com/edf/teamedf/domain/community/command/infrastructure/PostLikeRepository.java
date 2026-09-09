package com.edf.teamedf.domain.community.command.infrastructure;

import com.edf.teamedf.domain.community.command.domain.PostLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface PostLikeRepository extends JpaRepository<PostLike, Long> {

    Optional<PostLike> findByPost_PostIdAndUser_UserId(Long postId, Long userId);

    boolean existsByPost_PostIdAndUser_UserId(Long postId, Long userId);

    long countByUser_UserId(Long userId);

    /** 목록에서 내가 좋아요한 게시글 id 집합을 한 번에 조회. */
    @Query("""
            select l.post.postId from PostLike l
            where l.user.userId = :userId and l.post.postId in :postIds
            """)
    List<Long> findLikedPostIds(@Param("userId") Long userId, @Param("postIds") Collection<Long> postIds);
}

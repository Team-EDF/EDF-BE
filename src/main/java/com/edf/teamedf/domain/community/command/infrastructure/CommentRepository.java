package com.edf.teamedf.domain.community.command.infrastructure;

import com.edf.teamedf.domain.community.command.domain.Comment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface CommentRepository extends JpaRepository<Comment, Long> {

    List<Comment> findAllByPost_PostIdAndIsDeletedFalseOrderByCreatedAtAsc(Long postId);

    long countByPost_PostIdAndIsDeletedFalse(Long postId);

    long countByUser_UserIdAndIsDeletedFalse(Long userId);

    /** 여러 게시글의 댓글 수를 한 번에 조회 (목록 N+1 방지). */
    @Query("""
            select c.post.postId, count(c)
            from Comment c
            where c.isDeleted = false and c.post.postId in :postIds
            group by c.post.postId
            """)
    List<Object[]> countByPostIds(@Param("postIds") Collection<Long> postIds);
}

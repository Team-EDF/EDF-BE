package com.edf.teamedf.domain.community.command.infrastructure;

import com.edf.teamedf.domain.community.command.domain.Post;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PostRepository extends JpaRepository<Post, Long> {

    Page<Post> findAllByIsDeletedFalseOrderByCreatedAtDesc(Pageable pageable);

    /** 내가 작성한 글. */
    Page<Post> findAllByUser_UserIdAndIsDeletedFalseOrderByCreatedAtDesc(Long userId, Pageable pageable);

    /** 인기순 (좋아요 → 조회수). */
    Page<Post> findAllByIsDeletedFalseOrderByLikeCountDescCreatedAtDesc(Pageable pageable);

    /** 제목/본문 검색. */
    @Query("""
            select p from Post p
            where p.isDeleted = false
              and (lower(p.title) like lower(concat('%', :keyword, '%'))
                   or lower(p.content) like lower(concat('%', :keyword, '%')))
            order by p.createdAt desc
            """)
    Page<Post> search(@Param("keyword") String keyword, Pageable pageable);

    /** 내가 좋아요한 글. */
    @Query("""
            select p from Post p
            where p.isDeleted = false
              and exists (select 1 from PostLike l where l.post = p and l.user.userId = :userId)
            order by p.createdAt desc
            """)
    Page<Post> findLikedByUser(@Param("userId") Long userId, Pageable pageable);

    long countByUser_UserIdAndIsDeletedFalse(Long userId);
}

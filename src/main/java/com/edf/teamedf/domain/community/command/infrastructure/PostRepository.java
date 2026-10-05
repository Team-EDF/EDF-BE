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

    /** 카테고리별 최신순. */
    Page<Post> findAllByIsDeletedFalseAndCategoryOrderByCreatedAtDesc(Post.Category category, Pageable pageable);

    /** 내가 작성한 글. */
    Page<Post> findAllByUser_UserIdAndIsDeletedFalseOrderByCreatedAtDesc(Long userId, Pageable pageable);

    /** 인기순 (좋아요 → 조회수). */
    Page<Post> findAllByIsDeletedFalseOrderByLikeCountDescCreatedAtDesc(Pageable pageable);

    /** 카테고리별 인기순 (좋아요 → 조회수). */
    Page<Post> findAllByIsDeletedFalseAndCategoryOrderByLikeCountDescCreatedAtDesc(Post.Category category, Pageable pageable);

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

    // ==================== 차단 반영 목록 ====================
    // viewerId 가 차단한 사용자의 글을 제외한다. (페이지 크기가 어긋나지 않도록 DB 에서 걸러낸다)

    /** 최신순 (차단 반영). */
    @Query("""
            select p from Post p
            where p.isDeleted = false
              and not exists (
                  select 1 from UserBlock b
                  where b.blocker.userId = :viewerId and b.blocked = p.user)
            order by p.createdAt desc
            """)
    Page<Post> findVisibleLatest(@Param("viewerId") Long viewerId, Pageable pageable);

    /** 인기순 (차단 반영). */
    @Query("""
            select p from Post p
            where p.isDeleted = false
              and not exists (
                  select 1 from UserBlock b
                  where b.blocker.userId = :viewerId and b.blocked = p.user)
            order by p.likeCount desc, p.createdAt desc
            """)
    Page<Post> findVisiblePopular(@Param("viewerId") Long viewerId, Pageable pageable);

    /** 카테고리별 최신순 (차단 반영). */
    @Query("""
            select p from Post p
            where p.isDeleted = false and p.category = :category
              and not exists (
                  select 1 from UserBlock b
                  where b.blocker.userId = :viewerId and b.blocked = p.user)
            order by p.createdAt desc
            """)
    Page<Post> findVisibleLatestByCategory(
            @Param("viewerId") Long viewerId, @Param("category") Post.Category category, Pageable pageable);

    /** 카테고리별 인기순 (차단 반영). */
    @Query("""
            select p from Post p
            where p.isDeleted = false and p.category = :category
              and not exists (
                  select 1 from UserBlock b
                  where b.blocker.userId = :viewerId and b.blocked = p.user)
            order by p.likeCount desc, p.createdAt desc
            """)
    Page<Post> findVisiblePopularByCategory(
            @Param("viewerId") Long viewerId, @Param("category") Post.Category category, Pageable pageable);

    /** 제목/본문 검색 (차단 반영). */
    @Query("""
            select p from Post p
            where p.isDeleted = false
              and (lower(p.title) like lower(concat('%', :keyword, '%'))
                   or lower(p.content) like lower(concat('%', :keyword, '%')))
              and not exists (
                  select 1 from UserBlock b
                  where b.blocker.userId = :viewerId and b.blocked = p.user)
            order by p.createdAt desc
            """)
    Page<Post> searchVisible(
            @Param("viewerId") Long viewerId, @Param("keyword") String keyword, Pageable pageable);
}

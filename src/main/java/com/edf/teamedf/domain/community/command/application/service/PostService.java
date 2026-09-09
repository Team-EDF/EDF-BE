package com.edf.teamedf.domain.community.command.application.service;

import com.edf.teamedf.common.security.auth.UserPrincipal;
import com.edf.teamedf.domain.community.command.application.dto.post.PostCreateRequest;
import com.edf.teamedf.domain.community.command.application.dto.post.PostResponse;
import com.edf.teamedf.domain.community.command.application.dto.post.PostSummaryResponse;
import com.edf.teamedf.domain.community.command.application.dto.post.PostUpdateRequest;
import com.edf.teamedf.domain.community.command.domain.Post;
import com.edf.teamedf.domain.community.command.infrastructure.CommentRepository;
import com.edf.teamedf.domain.community.command.infrastructure.PostLikeRepository;
import com.edf.teamedf.domain.community.command.infrastructure.PostRepository;
import com.edf.teamedf.domain.user.command.domain.User;
import com.edf.teamedf.domain.user.command.infrastructure.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostService {

    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final CommentRepository commentRepository;
    private final PostLikeRepository postLikeRepository;

    // ==================== CRUD ====================

    @Transactional
    public PostResponse createPost(UserPrincipal principal, PostCreateRequest request) {
        User user = getUser(principal.userId());
        Post post = Post.builder()
                .user(user)
                .title(request.title())
                .content(request.content())
                .build();
        return PostResponse.from(postRepository.save(post), 0L, false, user.getUserId());
    }

    /**
     * 게시글 목록.
     *
     * @param sort   latest | popular
     * @param search 제목/본문 검색어 (선택)
     */
    public Page<PostSummaryResponse> getPosts(Long viewerId, String sort, String search, Pageable pageable) {
        Pageable page = paging(pageable);
        Page<Post> posts;
        if (search != null && !search.isBlank()) {
            posts = postRepository.search(search.trim(), page);
        } else if ("popular".equalsIgnoreCase(sort)) {
            posts = postRepository.findAllByIsDeletedFalseOrderByLikeCountDescCreatedAtDesc(page);
        } else {
            posts = postRepository.findAllByIsDeletedFalseOrderByCreatedAtDesc(page);
        }
        return enrich(posts, viewerId);
    }

    /** 내가 작성한 글. */
    public Page<PostSummaryResponse> getMyPosts(Long userId, Pageable pageable) {
        return enrich(
                postRepository.findAllByUser_UserIdAndIsDeletedFalseOrderByCreatedAtDesc(userId, paging(pageable)),
                userId);
    }

    /** 내가 좋아요한 글. */
    public Page<PostSummaryResponse> getLikedPosts(Long userId, Pageable pageable) {
        return enrich(postRepository.findLikedByUser(userId, paging(pageable)), userId);
    }

    /**
     * 모든 조회 쿼리가 정렬을 직접 명시하고 있으므로 클라이언트가 넘긴 sort 는 무시한다.
     * (예: ?sort=popular 가 Pageable 정렬 속성으로 해석돼 예외가 나는 것을 방지)
     */
    private Pageable paging(Pageable pageable) {
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
    }

    @Transactional
    public PostResponse getPost(Long postId, Long viewerId) {
        Post post = getActivePost(postId);
        post.incrementViewCount();
        long commentCount = commentRepository.countByPost_PostIdAndIsDeletedFalse(postId);
        boolean liked = viewerId != null
                && postLikeRepository.existsByPost_PostIdAndUser_UserId(postId, viewerId);
        return PostResponse.from(post, commentCount, liked, viewerId);
    }

    @Transactional
    public PostResponse updatePost(UserPrincipal principal, Long postId, PostUpdateRequest request) {
        Post post = getActivePost(postId);
        validateOwner(post, principal.userId());
        post.update(request.title(), request.content());
        long commentCount = commentRepository.countByPost_PostIdAndIsDeletedFalse(postId);
        boolean liked = postLikeRepository.existsByPost_PostIdAndUser_UserId(postId, principal.userId());
        return PostResponse.from(post, commentCount, liked, principal.userId());
    }

    @Transactional
    public void deletePost(UserPrincipal principal, Long postId) {
        Post post = getActivePost(postId);
        validateOwner(post, principal.userId());
        post.delete();
    }

    // ==================== 내부 유틸 ====================

    /** 목록에 댓글 수 / 좋아요 여부를 배치 조회해서 붙인다 (N+1 방지). */
    private Page<PostSummaryResponse> enrich(Page<Post> posts, Long viewerId) {
        List<Long> ids = posts.getContent().stream()
                .map(Post::getPostId)
                .toList();

        if (ids.isEmpty()) {
            return posts.map(p -> PostSummaryResponse.from(p, 0L, false));
        }

        Map<Long, Long> commentCounts = new HashMap<>();
        for (Object[] row : commentRepository.countByPostIds(ids)) {
            commentCounts.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
        }

        Set<Long> likedIds = viewerId == null
                ? Set.of()
                : new HashSet<>(postLikeRepository.findLikedPostIds(viewerId, ids));

        return posts.map(p -> PostSummaryResponse.from(
                p,
                commentCounts.getOrDefault(p.getPostId(), 0L),
                likedIds.contains(p.getPostId())));
    }

    private Post getActivePost(Long postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "게시글을 찾을 수 없습니다."));
        if (post.isDeleted()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "삭제된 게시글입니다.");
        }
        return post;
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."));
    }

    private void validateOwner(Post post, Long userId) {
        if (post.getUser() == null || !post.getUser().getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "작성자만 수정/삭제할 수 있습니다.");
        }
    }
}

package com.edf.teamedf.domain.community.command.application.controller;

import com.edf.teamedf.common.security.auth.AuthUtils;
import com.edf.teamedf.common.security.auth.UserPrincipal;
import com.edf.teamedf.domain.community.command.application.dto.comment.CommentCreateRequest;
import com.edf.teamedf.domain.community.command.application.dto.comment.CommentResponse;
import com.edf.teamedf.domain.community.command.application.dto.comment.CommentUpdateRequest;
import com.edf.teamedf.domain.community.command.application.dto.like.LikeResponse;
import com.edf.teamedf.domain.community.command.application.dto.post.PostCreateRequest;
import com.edf.teamedf.domain.community.command.application.dto.post.PostResponse;
import com.edf.teamedf.domain.community.command.application.dto.post.PostSummaryResponse;
import com.edf.teamedf.domain.community.command.application.dto.post.PostUpdateRequest;
import com.edf.teamedf.domain.community.command.application.service.CommentService;
import com.edf.teamedf.domain.community.command.application.service.PostLikeService;
import com.edf.teamedf.domain.community.command.application.service.PostService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;
    private final CommentService commentService;
    private final PostLikeService postLikeService;

    // ==================== 게시글 CRUD ====================

    @PostMapping
    public ResponseEntity<PostResponse> createPost(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody PostCreateRequest request) {
        AuthUtils.requireUserId(principal);
        return ResponseEntity.status(HttpStatus.CREATED).body(postService.createPost(principal, request));
    }

    /**
     * 게시글 목록.
     *
     * @param sort   latest(기본) | popular
     * @param search 제목/본문 검색어
     */
    @GetMapping
    public ResponseEntity<Page<PostSummaryResponse>> getPosts(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false, defaultValue = "latest") String sort,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Long viewerId = AuthUtils.optionalUserId(principal);
        return ResponseEntity.ok(postService.getPosts(viewerId, sort, search, pageable));
    }

    /** 내가 작성한 글. */
    @GetMapping("/me")
    public ResponseEntity<Page<PostSummaryResponse>> getMyPosts(
            @AuthenticationPrincipal UserPrincipal principal,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Long userId = AuthUtils.requireUserId(principal);
        return ResponseEntity.ok(postService.getMyPosts(userId, pageable));
    }

    /** 내가 좋아요한 글. */
    @GetMapping("/me/liked")
    public ResponseEntity<Page<PostSummaryResponse>> getLikedPosts(
            @AuthenticationPrincipal UserPrincipal principal,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Long userId = AuthUtils.requireUserId(principal);
        return ResponseEntity.ok(postService.getLikedPosts(userId, pageable));
    }

    @GetMapping("/{postId}")
    public ResponseEntity<PostResponse> getPost(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long postId) {
        return ResponseEntity.ok(postService.getPost(postId, AuthUtils.optionalUserId(principal)));
    }

    @PutMapping("/{postId}")
    public ResponseEntity<PostResponse> updatePost(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long postId,
            @Valid @RequestBody PostUpdateRequest request) {
        AuthUtils.requireUserId(principal);
        return ResponseEntity.ok(postService.updatePost(principal, postId, request));
    }

    @DeleteMapping("/{postId}")
    public ResponseEntity<Void> deletePost(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long postId) {
        AuthUtils.requireUserId(principal);
        postService.deletePost(principal, postId);
        return ResponseEntity.noContent().build();
    }

    // ==================== 댓글 ====================

    @PostMapping("/{postId}/comments")
    public ResponseEntity<CommentResponse> createComment(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long postId,
            @Valid @RequestBody CommentCreateRequest request) {
        AuthUtils.requireUserId(principal);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(commentService.createComment(principal, postId, request));
    }

    @GetMapping("/{postId}/comments")
    public ResponseEntity<List<CommentResponse>> getComments(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long postId) {
        return ResponseEntity.ok(commentService.getComments(postId, AuthUtils.optionalUserId(principal)));
    }

    @PutMapping("/{postId}/comments/{commentId}")
    public ResponseEntity<CommentResponse> updateComment(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long postId,
            @PathVariable Long commentId,
            @Valid @RequestBody CommentUpdateRequest request) {
        AuthUtils.requireUserId(principal);
        return ResponseEntity.ok(commentService.updateComment(principal, commentId, request));
    }

    @DeleteMapping("/{postId}/comments/{commentId}")
    public ResponseEntity<Void> deleteComment(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long postId,
            @PathVariable Long commentId) {
        AuthUtils.requireUserId(principal);
        commentService.deleteComment(principal, commentId);
        return ResponseEntity.noContent().build();
    }

    // ==================== 좋아요 ====================

    @PostMapping("/{postId}/likes")
    public ResponseEntity<LikeResponse> toggleLike(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long postId) {
        AuthUtils.requireUserId(principal);
        return ResponseEntity.ok(postLikeService.toggleLike(principal, postId));
    }

    @GetMapping("/{postId}/likes/me")
    public ResponseEntity<Boolean> isLiked(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long postId) {
        return ResponseEntity.ok(postLikeService.isLiked(principal, postId));
    }
}

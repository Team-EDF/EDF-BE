package com.edf.teamedf.domain.community.command.application.controller;

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
        return ResponseEntity.status(HttpStatus.CREATED).body(postService.createPost(principal, request));
    }

    @GetMapping
    public ResponseEntity<Page<PostSummaryResponse>> getPosts(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(postService.getPosts(pageable));
    }

    @GetMapping("/{postId}")
    public ResponseEntity<PostResponse> getPost(@PathVariable Long postId) {
        return ResponseEntity.ok(postService.getPost(postId));
    }

    @PutMapping("/{postId}")
    public ResponseEntity<PostResponse> updatePost(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long postId,
            @Valid @RequestBody PostUpdateRequest request) {
        return ResponseEntity.ok(postService.updatePost(principal, postId, request));
    }

    @DeleteMapping("/{postId}")
    public ResponseEntity<Void> deletePost(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long postId) {
        postService.deletePost(principal, postId);
        return ResponseEntity.noContent().build();
    }

    // ==================== 댓글 ====================

    @PostMapping("/{postId}/comments")
    public ResponseEntity<CommentResponse> createComment(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long postId,
            @Valid @RequestBody CommentCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(commentService.createComment(principal, postId, request));
    }

    @GetMapping("/{postId}/comments")
    public ResponseEntity<List<CommentResponse>> getComments(@PathVariable Long postId) {
        return ResponseEntity.ok(commentService.getComments(postId));
    }

    @PutMapping("/{postId}/comments/{commentId}")
    public ResponseEntity<CommentResponse> updateComment(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long postId,
            @PathVariable Long commentId,
            @Valid @RequestBody CommentUpdateRequest request) {
        return ResponseEntity.ok(commentService.updateComment(principal, commentId, request));
    }

    @DeleteMapping("/{postId}/comments/{commentId}")
    public ResponseEntity<Void> deleteComment(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long postId,
            @PathVariable Long commentId) {
        commentService.deleteComment(principal, commentId);
        return ResponseEntity.noContent().build();
    }

    // ==================== 좋아요 ====================

    @PostMapping("/{postId}/likes")
    public ResponseEntity<LikeResponse> toggleLike(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long postId) {
        return ResponseEntity.ok(postLikeService.toggleLike(principal, postId));
    }

    @GetMapping("/{postId}/likes/me")
    public ResponseEntity<Boolean> isLiked(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long postId) {
        return ResponseEntity.ok(postLikeService.isLiked(principal, postId));
    }
}

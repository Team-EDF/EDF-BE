package com.edf.teamedf.domain.community.command.application.service;

import com.edf.teamedf.common.security.auth.UserPrincipal;
import com.edf.teamedf.domain.community.command.application.dto.comment.CommentCreateRequest;
import com.edf.teamedf.domain.community.command.application.dto.comment.CommentResponse;
import com.edf.teamedf.domain.community.command.application.dto.comment.CommentUpdateRequest;
import com.edf.teamedf.domain.community.command.domain.Comment;
import com.edf.teamedf.domain.community.command.domain.Post;
import com.edf.teamedf.domain.community.command.infrastructure.CommentRepository;
import com.edf.teamedf.domain.community.command.infrastructure.PostRepository;
import com.edf.teamedf.domain.notification.command.application.service.NotificationService;
import com.edf.teamedf.domain.notification.command.domain.NotificationType;
import com.edf.teamedf.domain.user.command.domain.User;
import com.edf.teamedf.domain.user.command.infrastructure.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommentService {

    private final CommentRepository commentRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    @Transactional
    public CommentResponse createComment(UserPrincipal principal, Long postId, CommentCreateRequest request) {
        User user = getUser(principal.userId());
        Post post = getActivePost(postId);

        Comment parent = null;
        if (request.parentCommentId() != null) {
            parent = commentRepository.findById(request.parentCommentId())
                    .filter(c -> !c.isDeleted())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "부모 댓글을 찾을 수 없습니다."));
        }

        Comment comment = Comment.builder()
                .user(user)
                .post(post)
                .parent(parent)
                .content(request.content())
                .build();
        Comment saved = commentRepository.save(comment);

        String preview = request.content() == null ? "" : request.content().trim();
        if (preview.length() > 60) {
            preview = preview.substring(0, 60) + "...";
        }

        if (parent != null) {
            // 답글 → 부모 댓글 작성자에게
            Long parentAuthorId = parent.getUser() == null ? null : parent.getUser().getUserId();
            notificationService.notifyOther(
                    parentAuthorId, principal.userId(), NotificationType.REPLY,
                    "새로운 답글", user.getName() + "님이 회원님의 댓글에 답글을 남겼어요: " + preview,
                    "post", post.getPostId(), user.getName());
        }

        // 게시글 작성자에게 (답글이라도 원글 작성자가 다르면 알림)
        Long postAuthorId = post.getUser() == null ? null : post.getUser().getUserId();
        boolean sameAsParentAuthor = parent != null && parent.getUser() != null
                && parent.getUser().getUserId().equals(postAuthorId);
        if (!sameAsParentAuthor) {
            notificationService.notifyOther(
                    postAuthorId, principal.userId(), NotificationType.COMMENT,
                    "새로운 댓글", user.getName() + "님이 회원님의 글에 댓글을 남겼어요: " + preview,
                    "post", post.getPostId(), user.getName());
        }

        return CommentResponse.from(saved, principal.userId());
    }

    public List<CommentResponse> getComments(Long postId, Long viewerId) {
        getActivePost(postId);
        return commentRepository.findAllByPost_PostIdAndIsDeletedFalseOrderByCreatedAtAsc(postId)
                .stream()
                .map(c -> CommentResponse.from(c, viewerId))
                .toList();
    }

    @Transactional
    public CommentResponse updateComment(UserPrincipal principal, Long commentId, CommentUpdateRequest request) {
        Comment comment = getActiveComment(commentId);
        validateOwner(comment, principal.userId());
        comment.update(request.content());
        return CommentResponse.from(comment, principal.userId());
    }

    @Transactional
    public void deleteComment(UserPrincipal principal, Long commentId) {
        Comment comment = getActiveComment(commentId);
        validateOwner(comment, principal.userId());
        comment.delete();
    }

    private Comment getActiveComment(Long commentId) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "댓글을 찾을 수 없습니다."));
        if (comment.isDeleted()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "삭제된 댓글입니다.");
        }
        return comment;
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

    private void validateOwner(Comment comment, Long userId) {
        if (!comment.getUser().getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "작성자만 수정/삭제할 수 있습니다.");
        }
    }
}

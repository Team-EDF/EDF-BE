package com.edf.teamedf.domain.community.command.application.service;

import com.edf.teamedf.common.security.auth.UserPrincipal;
import com.edf.teamedf.domain.community.command.application.dto.post.PostCreateRequest;
import com.edf.teamedf.domain.community.command.application.dto.post.PostResponse;
import com.edf.teamedf.domain.community.command.application.dto.post.PostSummaryResponse;
import com.edf.teamedf.domain.community.command.application.dto.post.PostUpdateRequest;
import com.edf.teamedf.domain.community.command.domain.Post;
import com.edf.teamedf.domain.community.command.infrastructure.PostRepository;
import com.edf.teamedf.domain.user.command.domain.User;
import com.edf.teamedf.domain.user.command.infrastructure.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostService {

    private final PostRepository postRepository;
    private final UserRepository userRepository;

    @Transactional
    public PostResponse createPost(UserPrincipal principal, PostCreateRequest request) {
        User user = getUser(principal.userId());
        Post post = Post.builder()
                .user(user)
                .title(request.title())
                .content(request.content())
                .build();
        return PostResponse.from(postRepository.save(post));
    }

    public Page<PostSummaryResponse> getPosts(Pageable pageable) {
        return postRepository.findAllByIsDeletedFalseOrderByCreatedAtDesc(pageable)
                .map(PostSummaryResponse::from);
    }

    @Transactional
    public PostResponse getPost(Long postId) {
        Post post = getActivePost(postId);
        post.incrementViewCount();
        return PostResponse.from(post);
    }

    @Transactional
    public PostResponse updatePost(UserPrincipal principal, Long postId, PostUpdateRequest request) {
        Post post = getActivePost(postId);
        validateOwner(post, principal.userId());
        post.update(request.title(), request.content());
        return PostResponse.from(post);
    }

    @Transactional
    public void deletePost(UserPrincipal principal, Long postId) {
        Post post = getActivePost(postId);
        validateOwner(post, principal.userId());
        post.delete();
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
        if (!post.getUser().getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "작성자만 수정/삭제할 수 있습니다.");
        }
    }
}

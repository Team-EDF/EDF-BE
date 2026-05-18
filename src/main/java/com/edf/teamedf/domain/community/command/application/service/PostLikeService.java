package com.edf.teamedf.domain.community.command.application.service;

import com.edf.teamedf.common.security.auth.UserPrincipal;
import com.edf.teamedf.domain.community.command.application.dto.like.LikeResponse;
import com.edf.teamedf.domain.community.command.domain.Post;
import com.edf.teamedf.domain.community.command.domain.PostLike;
import com.edf.teamedf.domain.community.command.infrastructure.PostLikeRepository;
import com.edf.teamedf.domain.community.command.infrastructure.PostRepository;
import com.edf.teamedf.domain.user.command.domain.User;
import com.edf.teamedf.domain.user.command.infrastructure.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PostLikeService {

    private final PostLikeRepository postLikeRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;

    @Transactional
    public LikeResponse toggleLike(UserPrincipal principal, Long postId) {
        Post post = postRepository.findById(postId)
                .filter(p -> !p.isDeleted())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "게시글을 찾을 수 없습니다."));
        User user = userRepository.findById(principal.userId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."));

        Optional<PostLike> existing = postLikeRepository.findByPost_PostIdAndUser_UserId(postId, principal.userId());

        if (existing.isPresent()) {
            postLikeRepository.delete(existing.get());
            post.decrementLikeCount();
            return new LikeResponse(false, post.getLikeCount());
        }

        PostLike like = PostLike.builder().post(post).user(user).build();
        postLikeRepository.save(like);
        post.incrementLikeCount();
        return new LikeResponse(true, post.getLikeCount());
    }

    @Transactional(readOnly = true)
    public boolean isLiked(UserPrincipal principal, Long postId) {
        return postLikeRepository.existsByPost_PostIdAndUser_UserId(postId, principal.userId());
    }
}

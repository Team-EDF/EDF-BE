package com.edf.teamedf.domain.community.command.application.dto.comment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CommentCreateRequest(
        @NotBlank @Size(max = 500) String content,
        Long parentCommentId  // null이면 최상위 댓글
) {}

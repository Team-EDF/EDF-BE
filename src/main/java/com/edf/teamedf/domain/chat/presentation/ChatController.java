package com.edf.teamedf.domain.chat.presentation;

import com.edf.teamedf.common.security.auth.AuthUtils;
import com.edf.teamedf.common.security.auth.UserPrincipal;
import com.edf.teamedf.domain.chat.application.ChatService;
import com.edf.teamedf.domain.chat.presentation.dto.ChatRequest;
import com.edf.teamedf.domain.chat.presentation.dto.ChatResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @PostMapping
    public ResponseEntity<ChatResponse> chat(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody ChatRequest request) {

        Long userId = AuthUtils.requireUserId(principal);

        String message = request.getMessage();
        if (message == null || message.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "질문을 입력해 주세요.");
        }
        if (message.length() > ChatService.MAX_MESSAGE_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "질문은 " + ChatService.MAX_MESSAGE_LENGTH + "자 이하로 입력해 주세요.");
        }
        ChatResponse response = chatService.sendChatMessage(userId, request);
        return ResponseEntity.ok(response);
    }
}

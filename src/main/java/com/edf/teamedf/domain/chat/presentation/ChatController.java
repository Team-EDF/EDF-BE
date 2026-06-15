package com.edf.teamedf.domain.chat.presentation;

import com.edf.teamedf.common.security.auth.UserPrincipal;
import com.edf.teamedf.domain.chat.application.ChatService;
import com.edf.teamedf.domain.chat.presentation.dto.ChatRequest;
import com.edf.teamedf.domain.chat.presentation.dto.ChatResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @PostMapping
    public ResponseEntity<ChatResponse> chat(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody ChatRequest request) {

        Long userId = principal.userId();
        ChatResponse response = chatService.sendChatMessage(userId, request);
        return ResponseEntity.ok(response);
    }
}

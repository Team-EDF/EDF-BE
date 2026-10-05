package com.edf.teamedf.domain.chat.presentation.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ChatRequest {
    private Long recordId;
    // 첫 메시지에서는 null. 응답으로 받은 conversationId 를 다음 메시지부터 그대로 보낸다.
    private Long conversationId;
    private String message;
}

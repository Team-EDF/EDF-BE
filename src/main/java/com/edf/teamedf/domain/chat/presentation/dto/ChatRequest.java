package com.edf.teamedf.domain.chat.presentation.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ChatRequest {
    private Long recordId;
    private String message;
}

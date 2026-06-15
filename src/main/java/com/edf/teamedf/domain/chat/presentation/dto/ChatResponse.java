package com.edf.teamedf.domain.chat.presentation.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ChatResponse {
    private Long chatId;
    private String feedback;
}

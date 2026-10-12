package com.edf.teamedf.common.config;

import com.edf.teamedf.domain.image.command.application.controller.ImageController;
import com.edf.teamedf.domain.dashboard.command.application.controller.RecordController;
import com.edf.teamedf.domain.chat.presentation.ChatController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.ResponseEntity;
import java.util.Map;

@RestControllerAdvice(assignableTypes = {ImageController.class, RecordController.class, ChatController.class})
public class AndroidFlowErrorHandler {
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> status(ResponseStatusException error) {
        return ResponseEntity.status(error.getStatusCode()).body(Map.of("message",
                error.getReason() == null ? "요청을 처리하지 못했어요." : error.getReason()));
    }
}

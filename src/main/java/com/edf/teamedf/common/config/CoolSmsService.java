package com.edf.teamedf.common.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.nurigo.sdk.NurigoApp;
import net.nurigo.sdk.message.model.Message;
import net.nurigo.sdk.message.request.SingleMessageSendingRequest;
import net.nurigo.sdk.message.service.DefaultMessageService;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class CoolSmsService {

    private final CoolSmsConfig coolSmsConfig;
    private DefaultMessageService messageService;

    @PostConstruct
    public void init() {
        this.messageService = NurigoApp.INSTANCE.initialize(
                coolSmsConfig.apiKey(),
                coolSmsConfig.apiSecret(),
                "https://api.coolsms.co.kr"
        );
    }

    public void sendSms(String to, String content) {
        Message message = new Message();
        message.setFrom(coolSmsConfig.sender());
        message.setTo(to);
        message.setText(content);

        messageService.sendOne(new SingleMessageSendingRequest(message));
        log.info("[CoolSMS] SMS 발송 완료 → {}", to);
    }
}

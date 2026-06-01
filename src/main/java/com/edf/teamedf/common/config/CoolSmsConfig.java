package com.edf.teamedf.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "coolsms")
public record CoolSmsConfig(
        String apiKey,
        String apiSecret,
        String sender
) {}

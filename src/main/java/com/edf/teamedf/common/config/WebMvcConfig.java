package com.edf.teamedf.common.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;


@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final FileStorageProperties properties;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String absoluteUploadPath = Paths.get(properties.getLocalDir())
                .toAbsolutePath().normalize().toUri().toString();

        registry.addResourceHandler(properties.getUrlPrefix() + "/**")
                .addResourceLocations(absoluteUploadPath);
    }
}

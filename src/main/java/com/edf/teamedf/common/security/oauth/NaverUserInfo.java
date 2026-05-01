package com.edf.teamedf.common.security.oauth;

import java.util.Map;

public class NaverUserInfo implements OAuth2UserInfo {
    private Map<String, Object> attributes;
    private Map<String, Object> response;

    public NaverUserInfo(Map<String, Object> attributes) {
        this.attributes = attributes;
        this.response = (Map<String, Object>) attributes.get("response");
    }

    @Override
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    @Override
    public String getProviderId() {
        return String.valueOf(response.get("id"));
    }

    @Override
    public String getProvider() {
        return "naver";
    }

    @Override
    public String getEmail() {
        return String.valueOf(response.get("email"));
    }

    @Override
    public String getName() {
        return String.valueOf(response.get("name"));
    }
}

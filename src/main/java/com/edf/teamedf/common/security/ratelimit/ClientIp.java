package com.edf.teamedf.common.security.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;

/**
 * 요청한 클라이언트 IP.
 *
 * <p>운영에서는 AWS ALB 뒤에 있어서 remoteAddr 이 ALB 주소다. ALB 는 X-Forwarded-For 의 <b>맨 뒤</b>에
 * 자신이 받은 접속 IP 를 붙이므로 마지막 값을 쓴다. (맨 앞 값은 클라이언트가 임의로 넣을 수 있어 믿지 않는다)
 * 헤더가 없으면(로컬·docker-compose 직접 접속) remoteAddr 을 쓴다.</p>
 */
public final class ClientIp {

    private ClientIp() {
    }

    public static String of(HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            String[] parts = forwarded.split(",");
            String last = parts[parts.length - 1].trim();
            if (!last.isEmpty()) {
                return last;
            }
        }
        String remote = request.getRemoteAddr();
        return StringUtils.hasText(remote) ? remote : "unknown";
    }
}

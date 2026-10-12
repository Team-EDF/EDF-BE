package com.edf.teamedf.domain.user.command.application.controller;

import com.edf.teamedf.domain.user.command.application.dto.address.AddressValidateRequest;
import com.edf.teamedf.domain.user.command.application.dto.address.AddressValidateResponse;
import com.edf.teamedf.domain.user.command.application.service.AddressValidatorService;
import com.edf.teamedf.common.security.ratelimit.ClientIp;
import com.edf.teamedf.common.security.ratelimit.RateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
@RequestMapping("/auth/address")
@RequiredArgsConstructor
public class AddressController {

    private final AddressValidatorService addressValidatorService;
    private final RateLimiter rateLimiter;

    @PostMapping("/validate")
    public ResponseEntity<AddressValidateResponse> validate(@Valid @RequestBody AddressValidateRequest request,
                                                            HttpServletRequest httpRequest) {
        // 로그인 없이 부를 수 있고 호출마다 Google Geocoding 요금이 나가므로 IP 당 횟수를 제한한다.
        rateLimiter.consume("address-validate:ip:" + ClientIp.of(httpRequest), 30, Duration.ofMinutes(10),
                "주소 확인 요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.");
        String formatted = addressValidatorService.validateAndFormat(request.address());
        return ResponseEntity.ok(new AddressValidateResponse(formatted));
    }
}

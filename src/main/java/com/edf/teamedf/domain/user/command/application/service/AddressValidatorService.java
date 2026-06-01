package com.edf.teamedf.domain.user.command.application.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AddressValidatorService {

    private final RestClient restClient;

    @Value("${google.api.key}")
    private String apiKey;

    private static final String GEOCODING_URL = "https://maps.googleapis.com/maps/api/geocode/json";

    public String validateAndFormat(String address) {
        String uri = UriComponentsBuilder.fromUriString(GEOCODING_URL)
                .queryParam("address", address)
                .queryParam("language", "ko")
                .queryParam("key", apiKey)
                .toUriString();

        GeocodeResponse response = restClient.get()
                .uri(uri)
                .retrieve()
                .body(GeocodeResponse.class);

        if (response == null) {
            return address;
        }

        String status = response.status();

        if ("REQUEST_DENIED".equals(status) || "OVER_QUERY_LIMIT".equals(status)) {
            throw new RuntimeException("주소 검증 서비스 오류: " + status);
        }

        if (!"OK".equals(status) || response.results().isEmpty()) {
            return address;
        }

        return response.results().get(0).formattedAddress();
    }

    record GeocodeResponse(List<Result> results, String status) {
        record Result(@JsonProperty("formatted_address") String formattedAddress) {}
    }
}

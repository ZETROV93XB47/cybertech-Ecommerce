package com.novatech.cybertech.clients;

import com.novatech.cybertech.dto.response.moderation.ModerationResponseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CommentModerationClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;


    @Value("${moderation.api.url}")
    private String moderationApiUrl;

    public ModerationResponseDto moderate(final String comment) {
        Map<String, String> body = Map.of("comment", comment);

        ModerationResponseDto response = restClient
                .post()
                .uri(moderationApiUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(ModerationResponseDto.class);

        log.info("Moderation response: {}", response);

        return response;
    }
}

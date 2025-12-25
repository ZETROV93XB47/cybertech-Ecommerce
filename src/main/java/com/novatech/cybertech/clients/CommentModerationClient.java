package com.novatech.cybertech.clients;

import com.novatech.cybertech.dto.response.moderation.ModerationResponseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CommentModerationClient {

    private final RestTemplate restTemplate;

    //@Value("${moderation.api.url:http://moderation-api:5000/analyze}")
    private String moderationApiUrl = "http://127.0.0.1:5000/analyze";

    public ModerationResponseDto moderate(final String comment) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, String> body = new HashMap<>();
        body.put("comment", comment);

        HttpEntity<Map<String, String>> request = new HttpEntity<>(body, headers);

        ResponseEntity<ModerationResponseDto> response = restTemplate.exchange(
                moderationApiUrl,
                HttpMethod.POST,
                request,
                ModerationResponseDto.class
        );

        log.info("response body  : {}", response.getBody());

        return response.getBody();
    }
}

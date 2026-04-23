package com.novatech.cybertech.fixtures.support.stubs;

import com.novatech.cybertech.clients.CommentModerationClient;
import com.novatech.cybertech.dto.response.moderation.ModerationResponseDto;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Test configuration that exposes a Mockito-based {@link CommentModerationClient} returning a
 * configurable verdict. Default verdict is "non-hateful" (label="OK", score=0.99). Subclasses
 * can re-stub the bean before each test.
 */
@TestConfiguration
public class ModerationStub {

    @Bean
    @Primary
    public CommentModerationClient commentModerationClient() {
        CommentModerationClient client = mock(CommentModerationClient.class);
        when(client.moderate(anyString())).thenReturn(
                ModerationResponseDto.builder()
                        .label("OK")
                        .score(0.99)
                        .build()
        );
        return client;
    }
}
